package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.entity.PhoneOneTapAttempt;
import com.rabbit.app.modules.auth.mapper.PhoneOneTapAttemptMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 一键登录的请求记账。它同时扮演幂等键、并发租约和凭证防重放三个角色，
 * 每一个角色松掉都直接变成安全问题：
 *
 * <ul>
 *   <li><b>凭证防重放</b>：同一个运营商 token 只能换一次登录。tokenHash 已存在却配着
 *       另一个 requestId，必须 409——否则截获一次 token 就能反复换 token；</li>
 *   <li><b>requestId 绑定</b>：同一个 requestId 换了 provider 或换了 token 重放，
 *       必须 409。比对用的是 {@code MessageDigest.isEqual} 常量时间比较，
 *       退化成普通 equals 会给出按前缀试探的时间侧信道；</li>
 *   <li><b>成功重试窗口</b>：登录成功后短时间内的重试可以直接拿回 token，
 *       但过了窗口就得重新走一遍。窗口无限长等于凭证永不过期；</li>
 *   <li><b>租约</b>：PROCESSING 且租约未过期时并发请求必须 409；租约过期后靠
 *       {@code replaceLease} 的条件更新抢占，抢不到也必须 409，
 *       否则两个请求会同时以为自己持有这条记录。</li>
 * </ul>
 */
class PhoneOneTapAttemptServiceTest {
    private static final Instant NOW = Instant.parse("2024-05-06T10:00:00Z");
    private static final String REQUEST_ID = "req-1";
    private static final String PROVIDER = "ALIYUN";
    private static final String TOKEN_HASH = "token-hash-1";
    private static final String IP = "203.0.113.7";
    private static final String NEW_LEASE = "lease-new";
    private static final long ATTEMPT_ID = 5L;
    private static final long USER_ID = 11L;

    private PhoneOneTapAttemptMapper mapper;
    private PhoneOneTapRateLimitService rateLimitService;
    private AuthService authService;
    private PhoneOneTapAttemptService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PhoneOneTapAttemptMapper.class);
        rateLimitService = mock(PhoneOneTapRateLimitService.class);
        authService = mock(AuthService.class);
        service = service(30, 15);
    }

    // ---------- 配置 ----------

    @Test
    void nonsensicalWindowsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> service(0, 15));
        assertThrows(IllegalArgumentException.class, () -> service(301, 15));
        assertThrows(IllegalArgumentException.class, () -> service(30, 0));
        assertThrows(IllegalArgumentException.class, () -> service(30, 301));
    }

    // ---------- 限流在最前面 ----------

    @Test
    void everyBeginIsRateLimitedBeforeAnythingIsRead() {
        service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        verify(rateLimitService).reserve(IP);
    }

    @Test
    void aThrottledCallerNeverReachesTheAttemptTable() {
        doThrowOnReserve();

        assertEquals(429, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
        verify(mapper, never()).selectByRequestIdForUpdate(anyString());
        verify(mapper, never()).insert(any());
    }

    // ---------- 全新请求 ----------

    @Test
    void aFreshRequestIsStoredAsProcessingWithALease() {
        when(mapper.insert(any())).thenAnswer(call -> {
            call.<PhoneOneTapAttempt>getArgument(0).setId(ATTEMPT_ID);
            return 1;
        });

        PhoneOneTapAttemptService.BeginResult result = service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        assertEquals(PhoneOneTapAttemptService.BeginResult.State.CREATED, result.getState());
        assertEquals(ATTEMPT_ID, result.getAttemptId());
        assertEquals(NEW_LEASE, result.getLeaseId());
        ArgumentCaptor<PhoneOneTapAttempt> stored = ArgumentCaptor.forClass(PhoneOneTapAttempt.class);
        verify(mapper).insert(stored.capture());
        assertEquals("PROCESSING", stored.getValue().getStatus());
        assertEquals(TOKEN_HASH, stored.getValue().getTokenHash());
        assertEquals(IP, stored.getValue().getRequestIp());
        assertEquals(Date.from(NOW.plusSeconds(15)), stored.getValue().getLeaseExpiresTime());
    }

    /**
     * 同一个运营商 token 被另一个 requestId 拿来用，是重放。放过去就意味着
     * 截获一次 token 可以换出任意多次登录。
     */
    @Test
    void reusingATokenUnderADifferentRequestIdIsRejected() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(null);
        when(mapper.selectByTokenHashForUpdate(TOKEN_HASH)).thenReturn(attempt("PROCESSING"));

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
        verify(mapper, never()).insert(any());
    }

    // ---------- requestId 与凭证的绑定 ----------

    @Test
    void replayingARequestIdWithAnotherProviderIsRejected() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setProvider("SOMEONE_ELSE");
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    @Test
    void replayingARequestIdWithAnotherTokenIsRejected() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setTokenHash("some-other-token");
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    @Test
    void aStoredAttemptWithNoTokenHashDoesNotMatchAnyToken() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setTokenHash(null);
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    // ---------- 成功重试窗口 ----------

    @Test
    void aRetryInsideTheSuccessWindowGetsTheSameUserBack() {
        PhoneOneTapAttempt existing = attempt("SUCCEEDED");
        existing.setUserId(USER_ID);
        existing.setSuccessTime(Date.from(NOW.minusSeconds(29)));
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        PhoneOneTapAttemptService.BeginResult result = service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        assertEquals(PhoneOneTapAttemptService.BeginResult.State.SUCCEEDED, result.getState());
        assertEquals(USER_ID, result.getUserId());
    }

    @Test
    void aRetryExactlyOnTheWindowEdgeIsStillAccepted() {
        PhoneOneTapAttempt existing = attempt("SUCCEEDED");
        existing.setUserId(USER_ID);
        existing.setSuccessTime(Date.from(NOW.minusSeconds(30)));
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(
                PhoneOneTapAttemptService.BeginResult.State.SUCCEEDED,
                service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP).getState()
        );
    }

    @Test
    void aRetryPastTheWindowIsRejected() {
        PhoneOneTapAttempt existing = attempt("SUCCEEDED");
        existing.setUserId(USER_ID);
        existing.setSuccessTime(Date.from(NOW.minusMillis(30_001)));
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    @Test
    void aSucceededAttemptWithNoTimestampIsTreatedAsExpired() {
        PhoneOneTapAttempt existing = attempt("SUCCEEDED");
        existing.setUserId(USER_ID);
        existing.setSuccessTime(null);
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    // ---------- 失败结果的回放 ----------

    @Test
    void replayingAFailedAttemptReturnsTheStoredFailure() {
        PhoneOneTapAttempt existing = attempt("FAILED");
        existing.setResponseCode(403);
        existing.setResponseMessage("运营商拒绝");
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        PhoneOneTapAttemptService.BeginResult result = service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        assertEquals(PhoneOneTapAttemptService.BeginResult.State.FAILED, result.getState());
        assertEquals(403, result.getResponseCode());
        assertEquals("运营商拒绝", result.getResponseMessage());
    }

    @Test
    void aFailedAttemptWithNoDetailFallsBackToAGenericError() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(attempt("FAILED"));

        PhoneOneTapAttemptService.BeginResult result = service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        assertEquals(502, result.getResponseCode());
        assertEquals("一键登录失败，请稍后重试", result.getResponseMessage());
    }

    // ---------- 租约 ----------

    @Test
    void aLiveLeaseBlocksAConcurrentRequest() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setLeaseExpiresTime(Date.from(NOW.plusSeconds(1)));
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
        verify(mapper, never()).replaceLease(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void anExpiredLeaseIsTakenOverWithAFreshLeaseId() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setLeaseId("lease-old");
        existing.setLeaseExpiresTime(Date.from(NOW.minusSeconds(1)));
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);
        when(mapper.replaceLease(ATTEMPT_ID, "lease-old", NEW_LEASE, Date.from(NOW.plusSeconds(15))))
                .thenReturn(1);

        PhoneOneTapAttemptService.BeginResult result = service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        assertEquals(PhoneOneTapAttemptService.BeginResult.State.CREATED, result.getState());
        assertEquals(NEW_LEASE, result.getLeaseId());
        verify(mapper).replaceLease(ATTEMPT_ID, "lease-old", NEW_LEASE, Date.from(NOW.plusSeconds(15)));
    }

    /**
     * 抢租约是一条带旧 leaseId 条件的更新。更新影响 0 行说明别人已经抢走了，
     * 此时必须 409。若忽略返回值继续，两个请求会同时认为自己持有这条记录，
     * 后果是同一个凭证被消费两次。
     */
    @Test
    void losingTheRaceForAnExpiredLeaseIsRejected() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setLeaseId("lease-old");
        existing.setLeaseExpiresTime(Date.from(NOW.minusSeconds(1)));
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);
        when(mapper.replaceLease(anyLong(), anyString(), anyString(), any())).thenReturn(0);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    @Test
    void anAttemptWithNoLeaseDeadlineIsTakenOver() {
        PhoneOneTapAttempt existing = attempt("PROCESSING");
        existing.setLeaseId("lease-old");
        existing.setLeaseExpiresTime(null);
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(existing);
        when(mapper.replaceLease(anyLong(), anyString(), anyString(), any())).thenReturn(1);

        assertEquals(
                PhoneOneTapAttemptService.BeginResult.State.CREATED,
                service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP).getState()
        );
    }

    @Test
    void anUnrecognisedStoredStatusIsAnError() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(attempt("SOMETHING_ELSE"));

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    // ---------- 插入撞唯一键 ----------

    @Test
    void aDuplicateInsertReReadsTheRowTheRaceWinnerWrote() {
        PhoneOneTapAttempt winner = attempt("FAILED");
        winner.setResponseCode(403);
        winner.setResponseMessage("运营商拒绝");
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(null).thenReturn(winner);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        PhoneOneTapAttemptService.BeginResult result = service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP);

        assertEquals(PhoneOneTapAttemptService.BeginResult.State.FAILED, result.getState());
        assertEquals(403, result.getResponseCode());
    }

    @Test
    void aDuplicateCausedByTheTokenIsReportedAsAReplay() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(null);
        when(mapper.selectByTokenHashForUpdate(TOKEN_HASH)).thenReturn(null).thenReturn(attempt("PROCESSING"));
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    @Test
    void aDuplicateThatExplainsNothingIsAnError() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID)).thenReturn(null);
        when(mapper.selectByTokenHashForUpdate(TOKEN_HASH)).thenReturn(null);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
    }

    // ---------- 事务冲突重试 ----------

    @Test
    void aTransientConflictIsRetriedRatherThanSurfaced() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID))
                .thenThrow(new ConcurrencyFailureException("deadlock"))
                .thenReturn(attempt("FAILED"));

        assertEquals(
                PhoneOneTapAttemptService.BeginResult.State.FAILED,
                service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP).getState()
        );
    }

    @Test
    void aPersistentConflictGivesUpAfterThreeTriesAndReportsBusy() {
        when(mapper.selectByRequestIdForUpdate(REQUEST_ID))
                .thenThrow(new ConcurrencyFailureException("deadlock"));

        assertEquals(503, assertThrows(
                BizException.class,
                () -> service.begin(REQUEST_ID, PROVIDER, TOKEN_HASH, IP)
        ).getCode());
        verify(mapper, times(3)).selectByRequestIdForUpdate(REQUEST_ID);
        verify(rateLimitService, times(1)).reserve(IP);
    }

    // ---------- 完成与失败：必须持有租约 ----------

    /**
     * complete 之前必须确认租约还在自己手上。少了这一步，一个租约已被别人抢走的
     * 请求仍然会走完登录并发 token，等于同一个凭证换出两份会话。
     */
    @Test
    void completingWithoutTheLeaseNeverLogsAnybodyIn() {
        when(mapper.selectOwnedProcessingForUpdate(eq(ATTEMPT_ID), eq("lease-mine"), any())).thenReturn(null);

        assertThrows(
                PhoneOneTapLeaseLostException.class,
                () -> service.complete(ATTEMPT_ID, "lease-mine", "13800001111")
        );
        verify(authService, never()).loginOrRegisterPhone(anyString());
    }

    @Test
    void completingMarksTheAttemptSucceededForTheLoggedInUser() {
        AuthTokenResponse response = new AuthTokenResponse("jwt", USER_ID, "alice");
        givenOwnedLease();
        when(authService.loginOrRegisterPhone("13800001111")).thenReturn(response);
        when(mapper.markSucceeded(ATTEMPT_ID, "lease-mine", USER_ID, Date.from(NOW))).thenReturn(1);

        assertSame(response, service.complete(ATTEMPT_ID, "lease-mine", "13800001111"));

        verify(mapper).markSucceeded(ATTEMPT_ID, "lease-mine", USER_ID, Date.from(NOW));
    }

    @Test
    void aSuccessThatCannotBeRecordedIsAnError() {
        givenOwnedLease();
        when(authService.loginOrRegisterPhone("13800001111"))
                .thenReturn(new AuthTokenResponse("jwt", USER_ID, "alice"));
        when(mapper.markSucceeded(anyLong(), anyString(), anyLong(), any())).thenReturn(0);

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.complete(ATTEMPT_ID, "lease-mine", "13800001111")
        ).getCode());
    }

    @Test
    void failingWithoutTheLeaseIsRejected() {
        when(mapper.selectOwnedProcessingForUpdate(eq(ATTEMPT_ID), eq("lease-mine"), any())).thenReturn(null);

        assertThrows(
                PhoneOneTapLeaseLostException.class,
                () -> service.fail(ATTEMPT_ID, "lease-mine", 502, "运营商超时")
        );
        verify(mapper, never()).markFailed(anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    void failingRecordsTheProviderResponse() {
        givenOwnedLease();
        when(mapper.markFailed(ATTEMPT_ID, "lease-mine", 502, "运营商超时")).thenReturn(1);

        service.fail(ATTEMPT_ID, "lease-mine", 502, "运营商超时");

        verify(mapper).markFailed(ATTEMPT_ID, "lease-mine", 502, "运营商超时");
    }

    @Test
    void aFailureThatCannotBeRecordedIsAnError() {
        givenOwnedLease();
        when(mapper.markFailed(anyLong(), anyString(), anyInt(), anyString())).thenReturn(0);

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.fail(ATTEMPT_ID, "lease-mine", 502, "运营商超时")
        ).getCode());
    }

    // ---------- 成功重放取回 token ----------

    @Test
    void replayingASuccessWithoutAUserIdIsAnError() {
        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.retrySucceeded(null)
        ).getCode());
        verify(authService, never()).refreshToken(anyLong());
    }

    @Test
    void replayingASuccessReissuesATokenThroughTheNormalAccountChecks() {
        AuthTokenResponse response = new AuthTokenResponse("jwt", USER_ID, "alice");
        when(authService.refreshToken(USER_ID)).thenReturn(response);

        assertSame(response, service.retrySucceeded(USER_ID));

        verify(authService).refreshToken(USER_ID);
    }

    // ---------- fixtures ----------

    private PhoneOneTapAttemptService service(int successRetryWindowSeconds, int processingLeaseSeconds) {
        return new PhoneOneTapAttemptService(
                mapper,
                rateLimitService,
                authService,
                successRetryWindowSeconds,
                processingLeaseSeconds,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> NEW_LEASE,
                new TransactionTemplate(mock(PlatformTransactionManager.class))
        );
    }

    private void doThrowOnReserve() {
        doThrow(new BizException(429, "当前网络一键登录次数过多，请稍后再试"))
                .when(rateLimitService).reserve(anyString());
    }

    private void givenOwnedLease() {
        when(mapper.selectOwnedProcessingForUpdate(eq(ATTEMPT_ID), eq("lease-mine"), any()))
                .thenReturn(attempt("PROCESSING"));
    }

    private PhoneOneTapAttempt attempt(String status) {
        PhoneOneTapAttempt attempt = new PhoneOneTapAttempt();
        attempt.setId(ATTEMPT_ID);
        attempt.setRequestId(REQUEST_ID);
        attempt.setProvider(PROVIDER);
        attempt.setTokenHash(TOKEN_HASH);
        attempt.setRequestIp(IP);
        attempt.setStatus(status);
        return attempt;
    }
}
