package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.PhoneOneTapLoginRequest;
import com.rabbit.app.modules.auth.job.PhoneOneTapCleanupJob;
import com.rabbit.app.modules.auth.service.AuthService;
import com.rabbit.app.modules.auth.service.PhoneOneTapProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(properties = {
        "app.phone-one-tap.enabled=true",
        "app.phone-one-tap.allowed-providers=aliyun",
        "app.phone-one-tap.token-hash-secret=e2e-one-tap-token-secret-with-enough-entropy",
        "app.phone-one-tap.success-retry-window-seconds=2",
        "app.phone-one-tap.processing-lease-seconds=7",
        "app.phone-one-tap.attempt-retention-days=1",
        "app.phone-one-tap.rate-bucket-retention-hours=2",
        "app.phone-one-tap.rate-limit.ip-minute-limit=4",
        "app.phone-one-tap.rate-limit.ip-hour-limit=8"
})
public class PhoneOneTapLoginIT extends E2eTestSupport {
    private static final String ENDPOINT = "/api/auth/phone-one-tap-login";

    @MockitoBean
    private PhoneOneTapProvider provider;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PhoneOneTapCleanupJob cleanupJob;

    @BeforeEach
    void configureProvider() {
        Mockito.when(provider.providerId()).thenReturn("aliyun");
    }

    @Test
    void requestDtoContainsOnlyTheProviderTokenAndRequestIdContract() {
        Set<String> fields = Arrays.stream(PhoneOneTapLoginRequest.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        Assertions.assertEquals(Set.of("provider", "accessToken", "requestId"), fields);
    }

    @Test
    void existingAndNewPhoneAccountsUseTheSameAccountModelAndNewUsersHaveNoHouse() {
        AuthTokenResponse existing = authService.loginOrRegisterPhone("13800138100");
        Mockito.when(provider.resolvePhone("existing-token", "existing-request"))
                .thenReturn("+86 13800138100");
        Mockito.when(provider.resolvePhone("new-token", "new-request"))
                .thenReturn("13800138101");

        JsonNode existingLogin = oneTap("existing-token", "existing-request");
        JsonNode newLogin = oneTap("new-token", "new-request");

        Assertions.assertEquals(existing.getUserId().longValue(), existingLogin.get("userId").asLong());
        Assertions.assertTrue(existingLogin.get("phoneBound").asBoolean());
        Assertions.assertEquals("138****8100", existingLogin.get("maskedPhone").asText());
        Assertions.assertFalse(newLogin.get("hasPassword").asBoolean());
        Assertions.assertTrue(api.getOk("/api/houses", newLogin.get("token").asText(), null).isEmpty());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM phone_one_tap_attempts WHERE request_id = ?",
                String.class,
                "new-request"
        );
        Assertions.assertNotNull(storedHash);
        Assertions.assertEquals(64, storedHash.length());
        Assertions.assertNotEquals("new-token", storedHash);
        Integer rawTokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phone_one_tap_attempts WHERE token_hash = ?",
                Integer.class,
                "new-token"
        );
        Assertions.assertEquals(0, rawTokenCount == null ? 0 : rawTokenCount);
    }

    @Test
    void phoneInvitationIsAcceptedByTrustedOneTapLogin() {
        UserSession owner = register("one_tap_invitation_owner");
        long houseId = createHouse(owner, "一键登录邀请兔场", 1, 1, 1);
        api.postOk("/api/house-invitations", owner.token, houseId, obj(
                "phone", "13800138102",
                "role", "STAFF",
                "requestId", requestId("one_tap_invitation")
        ));
        Mockito.when(provider.resolvePhone("invited-token", "invited-request"))
                .thenReturn("13800138102");

        JsonNode invited = oneTap("invited-token", "invited-request");

        JsonNode permission = api.getOk(
                "/api/houses/permission",
                invited.get("token").asText(),
                houseId
        );
        Assertions.assertEquals("STAFF", permission.get("role").asText());
    }

    @Test
    void successfulRetryReissuesAuthenticationWithoutCallingTheProviderAgainAndBlocksReplay() {
        Mockito.when(provider.resolvePhone("single-use-token", "stable-request"))
                .thenReturn("13800138103");

        JsonNode first = oneTap("single-use-token", "stable-request");
        JsonNode retried = oneTap("single-use-token", "stable-request");

        Assertions.assertEquals(first.get("userId").asLong(), retried.get("userId").asLong());
        Assertions.assertFalse(retried.get("token").asText().isBlank());
        Mockito.verify(provider, Mockito.times(1))
                .resolvePhone("single-use-token", "stable-request");

        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", "changed-token",
                "requestId", "stable-request"
        ), 409, "requestId已用于其他一键登录请求");
        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", "single-use-token",
                "requestId", "different-request"
        ), 409, "一键登录凭证无效或已过期");
        Assertions.assertEquals(4, currentMinuteRateCount());
    }

    @Test
    void successfulRetryWindowIsAnchoredToFirstSuccessAndExpiresWithoutProviderCall() {
        Mockito.when(provider.resolvePhone("expiring-token", "expiring-request"))
                .thenReturn("13800138109");

        oneTap("expiring-token", "expiring-request");
        java.util.Date successTime = jdbcTemplate.queryForObject(
                "SELECT success_time FROM phone_one_tap_attempts WHERE request_id = ?",
                java.util.Date.class,
                "expiring-request"
        );
        oneTap("expiring-token", "expiring-request");
        java.util.Date afterRetry = jdbcTemplate.queryForObject(
                "SELECT success_time FROM phone_one_tap_attempts WHERE request_id = ?",
                java.util.Date.class,
                "expiring-request"
        );
        Assertions.assertEquals(successTime, afterRetry);

        // Expiration uses Clock.systemUTC(); bind the same absolute timeline instead of
        // relying on the MySQL session timezone for a DATETIME value.
        jdbcTemplate.update(
                "UPDATE phone_one_tap_attempts SET success_time = ? WHERE request_id = ?",
                new java.util.Date(System.currentTimeMillis() - 3_000L),
                "expiring-request"
        );
        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", "expiring-token",
                "requestId", "expiring-request"
        ), 401, "一键登录凭证无效或已过期");

        Mockito.verify(provider, Mockito.times(1))
                .resolvePhone("expiring-token", "expiring-request");
        Assertions.assertEquals(3, currentMinuteRateCount());
    }

    @Test
    void simultaneousIdenticalRequestsCallTheProviderExactlyOnce() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        Mockito.when(provider.resolvePhone("concurrent-token", "concurrent-request"))
                .thenAnswer(invocation -> {
                    providerEntered.countDown();
                    if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("provider release timed out");
                    }
                    return "13800138106";
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> first = executor.submit(() -> oneTapResponse(
                    "concurrent-token",
                    "concurrent-request"
            ));
            Assertions.assertTrue(providerEntered.await(5, TimeUnit.SECONDS));

            Future<JsonNode> second = executor.submit(() -> oneTapResponse(
                    "concurrent-token",
                    "concurrent-request"
            ));
            JsonNode secondResponse = second.get(5, TimeUnit.SECONDS);
            Assertions.assertEquals(409, secondResponse.get("code").asInt());
            Assertions.assertEquals("一键登录请求处理中", secondResponse.get("message").asText());

            releaseProvider.countDown();
            JsonNode firstResponse = first.get(5, TimeUnit.SECONDS);
            Assertions.assertEquals(0, firstResponse.get("code").asInt());

            Mockito.verify(provider, Mockito.times(1))
                    .resolvePhone("concurrent-token", "concurrent-request");
            Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM phone_one_tap_attempts WHERE request_id = ?",
                    Integer.class,
                    "concurrent-request"
            ));
            Assertions.assertEquals("SUCCEEDED", jdbcTemplate.queryForObject(
                    "SELECT status FROM phone_one_tap_attempts WHERE request_id = ?",
                    String.class,
                    "concurrent-request"
            ));
            Assertions.assertEquals(2, currentMinuteRateCount());
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredProcessingLeaseCanBeTakenOverAndOldResultCannotCreateAnAccount() throws Exception {
        CountDownLatch firstProviderEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstProvider = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Mockito.when(provider.resolvePhone("lease-token", "lease-request"))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        firstProviderEntered.countDown();
                        if (!releaseFirstProvider.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("first provider release timed out");
                        }
                        return "13800138107";
                    }
                    return "13800138108";
                });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> stale = executor.submit(() -> oneTapResponse(
                    "lease-token",
                    "lease-request"
            ));
            Assertions.assertTrue(firstProviderEntered.await(5, TimeUnit.SECONDS));
            jdbcTemplate.update(
                    "UPDATE phone_one_tap_attempts SET lease_expires_time = ? WHERE request_id = ?",
                    new java.util.Date(System.currentTimeMillis() - 1_000L),
                    "lease-request"
            );

            JsonNode takeover = oneTap("lease-token", "lease-request");
            releaseFirstProvider.countDown();
            JsonNode staleResponse = stale.get(5, TimeUnit.SECONDS);

            Assertions.assertEquals(409, staleResponse.get("code").asInt());
            Assertions.assertEquals(
                    "一键登录请求已被后续重试接管",
                    staleResponse.get("message").asText()
            );
            Assertions.assertEquals(takeover.get("userId").asLong(), jdbcTemplate.queryForObject(
                    "SELECT user_id FROM phone_one_tap_attempts WHERE request_id = ?",
                    Long.class,
                    "lease-request"
            ));
            Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE phone_masked = '138****8107'",
                    Integer.class
            ));
            Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE phone_masked = '138****8108'",
                    Integer.class
            ));
            Mockito.verify(provider, Mockito.times(2))
                    .resolvePhone("lease-token", "lease-request");
        } finally {
            releaseFirstProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unknownProviderIsRejectedBeforeAttemptCreation() {
        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "untrusted",
                "accessToken", "unknown-provider-token",
                "requestId", "unknown-provider-request"
        ), 400, "不支持的一键登录服务商");

        Mockito.verify(provider, Mockito.never())
                .resolvePhone(Mockito.anyString(), Mockito.anyString());
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phone_one_tap_attempts WHERE request_id = ?",
                Integer.class,
                "unknown-provider-request"
        ));
    }

    @Test
    void disabledAccountFailureIsIdempotentAndDoesNotCallProviderAgain() {
        AuthTokenResponse account = authService.loginOrRegisterPhone("13800138104");
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE user_id = ?", account.getUserId());
        Mockito.when(provider.resolvePhone("disabled-token", "disabled-request"))
                .thenReturn("13800138104");

        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", "disabled-token",
                "requestId", "disabled-request"
        ), 403, "账号已停用");
        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", "disabled-token",
                "requestId", "disabled-request"
        ), 403, "账号已停用");

        Mockito.verify(provider, Mockito.times(1))
                .resolvePhone("disabled-token", "disabled-request");
        Assertions.assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM phone_one_tap_attempts WHERE request_id = ?",
                String.class,
                "disabled-request"
        ));
    }

    @Test
    void providerErrorsAreSanitizedInResponsesAttemptsAndAuditLogs() {
        String sensitiveToken = "secret-provider-token";
        String sensitivePhone = "13800138105";
        Mockito.when(provider.resolvePhone(sensitiveToken, "sensitive-request"))
                .thenThrow(new IllegalStateException(
                        "provider failed token=" + sensitiveToken + " phone=" + sensitivePhone
                ));

        JsonNode response = api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", sensitiveToken,
                "requestId", "sensitive-request"
        ), 502, "一键登录服务暂不可用");

        assertNotSensitive(response.toString(), sensitiveToken, sensitivePhone);
        String attemptMessage = jdbcTemplate.queryForObject(
                "SELECT response_message FROM phone_one_tap_attempts WHERE request_id = ?",
                String.class,
                "sensitive-request"
        );
        assertNotSensitive(attemptMessage, sensitiveToken, sensitivePhone);
        String auditError = jdbcTemplate.queryForObject(
                "SELECT error_message FROM audit_logs WHERE path = ? ORDER BY id DESC LIMIT 1",
                String.class,
                ENDPOINT
        );
        assertNotSensitive(auditError, sensitiveToken, sensitivePhone);
    }

    @Test
    void concurrentDistinctTokensOnlyUseTheRemainingAtomicIpQuota() throws Exception {
        Mockito.when(provider.resolvePhone(Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        for (int i = 0; i < 2; i++) {
            api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                    "provider", "aliyun",
                    "accessToken", "rate-token-" + i,
                    "requestId", "rate-request-" + i
            ), 502, "一键登录服务暂不可用");
        }
        Mockito.clearInvocations(provider);

        int concurrentRequests = 4;
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Future<JsonNode>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrentRequests; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("concurrent rate-limit start timed out");
                    }
                    return oneTapResponse("burst-token-" + index, "burst-request-" + index);
                }));
            }
            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> codes = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                codes.add(future.get(10, TimeUnit.SECONDS).get("code").asInt());
            }
            Assertions.assertEquals(2, codes.stream().filter(code -> code == 502).count());
            Assertions.assertEquals(2, codes.stream().filter(code -> code == 429).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Mockito.verify(provider, Mockito.times(2))
                .resolvePhone(Mockito.anyString(), Mockito.anyString());
        Assertions.assertEquals(4, currentMinuteRateCount());
    }

    @Test
    void forgedForwardedForHeadersDoNotBypassDefaultLanIpLimit() {
        Mockito.when(provider.resolvePhone(Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        for (int i = 0; i < 5; i++) {
            JsonNode response = api.postResponseWithHeaders(
                    ENDPOINT,
                    null,
                    null,
                    obj(
                            "provider", "aliyun",
                            "accessToken", "forwarded-token-" + i,
                            "requestId", "forwarded-request-" + i
                    ),
                    Map.of("X-Forwarded-For", "198.51.100." + (10 + i))
            );
            Assertions.assertEquals(i < 4 ? 502 : 429, response.get("code").asInt());
        }

        Mockito.verify(provider, Mockito.times(4))
                .resolvePhone(Mockito.anyString(), Mockito.anyString());
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT request_ip) FROM phone_one_tap_attempts",
                Integer.class
        ));
    }

    @Test
    void cleanupRemovesOldTerminalAttemptsAndExpiredRateBuckets() {
        Mockito.when(provider.resolvePhone("cleanup-token", "cleanup-request"))
                .thenThrow(new IllegalStateException("provider unavailable"));
        api.expectError(ENDPOINT, HttpMethod.POST, null, null, obj(
                "provider", "aliyun",
                "accessToken", "cleanup-token",
                "requestId", "cleanup-request"
        ), 502, "一键登录服务暂不可用");
        jdbcTemplate.update(
                "UPDATE phone_one_tap_attempts SET update_time = DATE_SUB(NOW(), INTERVAL 2 DAY)"
        );
        jdbcTemplate.update(
                "UPDATE phone_one_tap_rate_buckets "
                        + "SET bucket_start = DATE_SUB(bucket_start, INTERVAL 3 HOUR)"
        );

        cleanupJob.cleanup();

        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phone_one_tap_attempts",
                Integer.class
        ));
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phone_one_tap_rate_buckets",
                Integer.class
        ));
    }

    private JsonNode oneTap(String accessToken, String requestId) {
        return api.postOk(ENDPOINT, null, null, obj(
                "provider", "aliyun",
                "accessToken", accessToken,
                "requestId", requestId
        ));
    }

    private JsonNode oneTapResponse(String accessToken, String requestId) {
        return api.postResponse(ENDPOINT, null, null, obj(
                "provider", "aliyun",
                "accessToken", accessToken,
                "requestId", requestId
        ));
    }

    private int currentMinuteRateCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(request_count), 0) "
                        + "FROM phone_one_tap_rate_buckets WHERE bucket_type = 'MINUTE'",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void assertNotSensitive(String value, String token, String phone) {
        Assertions.assertNotNull(value);
        Assertions.assertFalse(value.contains(token));
        Assertions.assertFalse(value.contains(phone));
    }
}
