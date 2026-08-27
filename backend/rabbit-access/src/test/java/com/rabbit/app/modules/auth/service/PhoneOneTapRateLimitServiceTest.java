package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.mapper.PhoneOneTapRateBucketMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 一键登录的 IP 限流。它挡的是拿运营商 token 批量刷登录的行为，而限流失效
 * 不会有任何症状——系统照常工作，只是防线没了，等发现时通常已经是一次刷号事件。
 *
 * <p>所以这里逐条钉住阈值语义：判定是 {@code >=}（配额 10 表示第 10 次之后就该拒），
 * 分钟和小时两个桶各自独立生效（任一超限即拒），计数按 IP 分桶，桶起点按自然分钟/
 * 自然小时对齐（不是「首次请求后 60 秒」的滑动窗，那会让桶键无限发散）。
 *
 * <p>还有一条容易被忽略的：计数写不进去时必须报错而不是放行。「记不住就当没发生」
 * 等于给了攻击者一个把限流关掉的开关。
 */
class PhoneOneTapRateLimitServiceTest {
    private static final String IP = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2024-05-06T10:23:45.678Z");

    private PhoneOneTapRateBucketMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = mock(PhoneOneTapRateBucketMapper.class);
        when(mapper.increment(anyString(), anyString(), any())).thenReturn(1);
    }

    // ---------- 配置 ----------

    @Test
    void nonsensicalLimitsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> service(0, 60));
        assertThrows(IllegalArgumentException.class, () -> service(-1, 60));
        assertThrows(IllegalArgumentException.class, () -> service(10, 9));
    }

    @Test
    void anHourlyQuotaEqualToTheMinuteQuotaIsAllowed() {
        service(10, 10);
    }

    // ---------- 阈值 ----------

    @Test
    void aRequestUnderBothQuotasIsCountedInBothBuckets() {
        givenCounts(9, 59);

        service(10, 60).reserve(IP);

        verify(mapper).increment(IP, "MINUTE", minuteStart());
        verify(mapper).increment(IP, "HOUR", hourStart());
    }

    /**
     * 配额 10 表示这一分钟只放 10 次。计数已经是 10 时第 11 次必须被拒；
     * 若判定写成 {@code >}，实际放行的就是 11 次。
     */
    @Test
    void reachingTheMinuteQuotaBlocksTheNextRequest() {
        givenCounts(10, 0);

        BizException error = assertThrows(BizException.class, () -> service(10, 60).reserve(IP));

        assertEquals(429, error.getCode());
        verify(mapper, never()).increment(anyString(), anyString(), any());
    }

    @Test
    void reachingTheHourQuotaBlocksTheNextRequestEvenWhenThisMinuteIsQuiet() {
        givenCounts(0, 60);

        assertEquals(429, assertThrows(BizException.class, () -> service(10, 60).reserve(IP)).getCode());
        verify(mapper, never()).increment(anyString(), anyString(), any());
    }

    @Test
    void bothBucketsAreLockedBeforeEitherIsIncremented() {
        givenCounts(9, 59);

        service(10, 60).reserve(IP);

        verify(mapper).lockOrCreate(IP, "MINUTE", minuteStart());
        verify(mapper).lockOrCreate(IP, "HOUR", hourStart());
    }

    // ---------- 桶起点对齐 ----------

    @Test
    void bucketsAreAlignedToTheWallClockMinuteAndHour() {
        givenCounts(0, 0);

        service(10, 60).reserve(IP);

        verify(mapper).selectCountForUpdate(IP, "MINUTE", Date.from(Instant.parse("2024-05-06T10:23:00Z")));
        verify(mapper).selectCountForUpdate(IP, "HOUR", Date.from(Instant.parse("2024-05-06T10:00:00Z")));
    }

    @Test
    void countingIsScopedToTheRequestingAddress() {
        givenCounts(0, 0);

        service(10, 60).reserve("198.51.100.42");

        verify(mapper).lockOrCreate(eq("198.51.100.42"), eq("MINUTE"), any());
        verify(mapper, never()).lockOrCreate(eq(IP), anyString(), any());
    }

    // ---------- 记账失败必须炸，不能放行 ----------

    @Test
    void aBucketThatCannotBeReadIsAnErrorRatherThanAFreePass() {
        when(mapper.selectCountForUpdate(anyString(), anyString(), any())).thenReturn(null);

        assertEquals(500, assertThrows(BizException.class, () -> service(10, 60).reserve(IP)).getCode());
        verify(mapper, never()).increment(anyString(), anyString(), any());
    }

    @Test
    void anIncrementThatWritesNoRowIsAnErrorRatherThanAFreePass() {
        givenCounts(0, 0);
        when(mapper.increment(IP, "MINUTE", minuteStart())).thenReturn(0);

        assertEquals(500, assertThrows(BizException.class, () -> service(10, 60).reserve(IP)).getCode());
    }

    @Test
    void anHourIncrementThatWritesNoRowIsAlsoAnError() {
        givenCounts(0, 0);
        when(mapper.increment(IP, "HOUR", hourStart())).thenReturn(0);

        assertEquals(500, assertThrows(BizException.class, () -> service(10, 60).reserve(IP)).getCode());
    }

    // ---------- fixtures ----------

    private PhoneOneTapRateLimitService service(int minuteLimit, int hourLimit) {
        return new PhoneOneTapRateLimitService(
                mapper,
                minuteLimit,
                hourLimit,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void givenCounts(int minuteCount, int hourCount) {
        when(mapper.selectCountForUpdate(anyString(), eq("MINUTE"), any())).thenReturn(minuteCount);
        when(mapper.selectCountForUpdate(anyString(), eq("HOUR"), any())).thenReturn(hourCount);
    }

    private Date minuteStart() {
        return Date.from(Instant.parse("2024-05-06T10:23:00Z"));
    }

    private Date hourStart() {
        return Date.from(Instant.parse("2024-05-06T10:00:00Z"));
    }
}
