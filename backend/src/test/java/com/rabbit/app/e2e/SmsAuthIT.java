package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.auth.service.SmsSender;
import com.rabbit.app.modules.auth.service.SmsVerificationPurpose;
import com.rabbit.app.modules.auth.service.SmsVerificationStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class SmsAuthIT extends E2eTestSupport {
    @MockitoBean
    private SmsSender smsSender;

    @MockitoBean
    private SmsVerificationStore smsVerificationStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> sentCodes = new ConcurrentHashMap<>();
    private final TestSmsVerificationStore testStore = new TestSmsVerificationStore();

    @BeforeEach
    void captureSmsCodes() throws Exception {
        sentCodes.clear();
        Mockito.doAnswer(invocation -> {
            sentCodes.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(smsSender).sendVerificationCode(Mockito.anyString(), Mockito.anyString());
        testStore.clear();
        Mockito.when(smsVerificationStore.reserve(Mockito.any()))
                .thenAnswer(invocation -> testStore.reserve(invocation.getArgument(0)));
        Mockito.when(smsVerificationStore.activate(Mockito.any()))
                .thenAnswer(invocation -> testStore.activate(invocation.getArgument(0)));
        Mockito.doAnswer(invocation -> {
            testStore.cancel(invocation.getArgument(0));
            return null;
        }).when(smsVerificationStore).cancel(Mockito.any());
        Mockito.when(smsVerificationStore.verifyAndConsume(
                        Mockito.anyString(),
                        Mockito.any(SmsVerificationPurpose.class),
                        Mockito.anyString(),
                        Mockito.anyInt()
                ))
                .thenAnswer(invocation -> testStore.verifyAndConsume(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)
                ));
    }

    @Test
    void verifiedPhoneCreatesOnlyAnAccountAndCodeCannotBeReused() {
        JsonNode auth = loginByPhone("+86 13800138000");

        Assertions.assertTrue(auth.get("userId").asLong() > 0);
        Assertions.assertFalse(auth.get("token").asText().isBlank());
        Assertions.assertTrue(auth.get("phoneBound").asBoolean());
        Assertions.assertEquals("138****8000", auth.get("maskedPhone").asText());
        Assertions.assertFalse(auth.has("merchantId"));
        Assertions.assertTrue(api.getOk("/api/houses", auth.get("token").asText(), null).isEmpty());

        JsonNode profile = api.getOk("/api/auth/me", auth.get("token").asText(), null);
        Assertions.assertEquals(auth.get("userId").asLong(), profile.get("userId").asLong());
        Assertions.assertTrue(profile.get("phoneBound").asBoolean());
        Assertions.assertEquals("138****8000", profile.get("maskedPhone").asText());

        api.expectError("/api/auth/sms/login", HttpMethod.POST, null, null, obj(
                "phone", "13800138000",
                "code", sentCodes.get("13800138000")
        ), 400, "验证码无效或已过期");
    }

    @Test
    void phoneUserCreatesAFarmAndKeepsAccessAfterLoggingInAgain() {
        JsonNode firstAuth = loginByPhone("13800138001");
        UserSession firstSession = session(firstAuth);
        long houseId = createHouse(firstSession, "手机号兔场", 1, 2, 1);

        JsonNode createdHouses = api.getOk("/api/houses", firstSession.token, null);
        Assertions.assertEquals(1, createdHouses.size());
        Assertions.assertEquals(houseId, createdHouses.get(0).get("id").asLong());
        Assertions.assertFalse(createdHouses.get(0).has("merchantId"));
        Assertions.assertEquals("ENABLED", createdHouses.get(0).get("status").asText());
        JsonNode permission = api.getOk("/api/houses/permission", firstSession.token, houseId);
        Assertions.assertEquals("OWNER", permission.get("role").asText());
        Assertions.assertEquals(1, countMembersByRole(firstSession, houseId, "OWNER"));

        JsonNode secondAuth = loginByPhone("+86 13800138001");

        Assertions.assertEquals(firstAuth.get("userId").asLong(), secondAuth.get("userId").asLong());
        Assertions.assertEquals(firstAuth.get("userName").asText(), secondAuth.get("userName").asText());
        JsonNode housesAfterLogin = api.getOk("/api/houses", secondAuth.get("token").asText(), null);
        Assertions.assertEquals(1, housesAfterLogin.size());
        Assertions.assertEquals(houseId, housesAfterLogin.get(0).get("id").asLong());
        Assertions.assertEquals(2, api.getOk("/api/cages", secondAuth.get("token").asText(), houseId).size());
    }

    @Test
    void phoneUserCanSetAnInitialPasswordAndLaterChangesRequireTheOldPassword() {
        JsonNode phoneAuth = loginByPhone("13800138004");
        String token = phoneAuth.get("token").asText();
        String userName = phoneAuth.get("userName").asText();
        long userId = phoneAuth.get("userId").asLong();
        Assertions.assertFalse(phoneAuth.get("hasPassword").asBoolean());
        Assertions.assertFalse(api.getOk("/api/auth/me", token, null).get("hasPassword").asBoolean());

        api.putOk("/api/auth/password", token, null, obj(
                "newPassword", "654321"
        ));

        Assertions.assertTrue(api.getOk("/api/auth/me", token, null).get("hasPassword").asBoolean());
        JsonNode passwordLogin = api.postOk("/api/auth/login", null, null, obj(
                "userName", userName,
                "password", "654321"
        ));
        Assertions.assertEquals(userId, passwordLogin.get("userId").asLong());
        Assertions.assertTrue(passwordLogin.get("hasPassword").asBoolean());

        api.expectError("/api/auth/password", HttpMethod.PUT, token, null, obj(
                "newPassword", "765432"
        ), 400, "旧密码不能为空");
        api.expectError("/api/auth/password", HttpMethod.PUT, token, null, obj(
                "oldPassword", "bad-password",
                "newPassword", "765432"
        ), 400, "旧密码不正确");
        api.putOk("/api/auth/password", token, null, obj(
                "oldPassword", "654321",
                "newPassword", "765432"
        ));
        JsonNode changedLogin = api.postOk("/api/auth/login", null, null, obj(
                "userName", userName,
                "password", "765432"
        ));
        Assertions.assertEquals(userId, changedLogin.get("userId").asLong());
    }

    @Test
    void explicitPurposesAreIsolatedAndResetPasswordUsesOnlyResetCode() {
        String phone = "13800138011";
        String registerCode = sendCode(phone, "REGISTER");
        JsonNode registered = api.postOk("/api/auth/sms/login", null, null, obj(
                "phone", phone,
                "code", registerCode,
                "purpose", "REGISTER"
        ));

        String loginCode = sendCode(phone, "LOGIN");
        JsonNode loggedIn = api.postOk("/api/auth/sms/login", null, null, obj(
                "phone", phone,
                "code", loginCode,
                "purpose", "LOGIN"
        ));
        Assertions.assertEquals(registered.get("userId").asLong(), loggedIn.get("userId").asLong());

        String resetCode = sendCode(phone, "RESET_PASSWORD");
        api.expectError("/api/auth/sms/login", HttpMethod.POST, null, null, obj(
                "phone", phone,
                "code", resetCode,
                "purpose", "RESET_PASSWORD"
        ), 400, "该验证码用途不能用于手机号登录");
        api.postOk("/api/auth/sms/reset-password", null, null, obj(
                "phone", phone,
                "code", resetCode,
                "newPassword", "reset-123456"
        ));

        JsonNode passwordLogin = api.postOk("/api/auth/login", null, null, obj(
                "userName", registered.get("userName").asText(),
                "password", "reset-123456"
        ));
        Assertions.assertEquals(registered.get("userId").asLong(), passwordLogin.get("userId").asLong());

        api.expectError("/api/auth/sms/reset-password", HttpMethod.POST, null, null, obj(
                "phone", phone,
                "code", resetCode,
                "newPassword", "another-123456"
        ), 400, "验证码无效或已过期");
    }

    @Test
    void accountCanBindAndChangePhoneAfterPasswordVerification() {
        UserSession account = register("phone_binding_account");
        String firstPhone = "13800138012";
        String firstCode = sendCode(firstPhone, "BIND_PHONE");

        JsonNode bound = api.putOk("/api/auth/phone", account.token, null, obj(
                "phone", firstPhone,
                "code", firstCode
        ));
        Assertions.assertTrue(bound.get("phoneBound").asBoolean());
        Assertions.assertEquals("138****8012", bound.get("maskedPhone").asText());

        String secondPhone = "13800138013";
        String secondCode = sendCode(secondPhone, "BIND_PHONE");
        api.expectError("/api/auth/phone", HttpMethod.PUT, account.token, null, obj(
                "phone", secondPhone,
                "code", secondCode,
                "currentPassword", "wrong-password"
        ), 400, "当前密码不正确");
        JsonNode changed = api.putOk("/api/auth/phone", account.token, null, obj(
                "phone", secondPhone,
                "code", secondCode,
                "currentPassword", account.password
        ));
        Assertions.assertEquals("138****8013", changed.get("maskedPhone").asText());

        String loginCode = sendCode(secondPhone, "LOGIN");
        JsonNode login = api.postOk("/api/auth/sms/login", null, null, obj(
                "phone", secondPhone,
                "code", loginCode,
                "purpose", "LOGIN"
        ));
        Assertions.assertEquals(account.userId, login.get("userId").asLong());

        String conflictPhone = "13800138014";
        loginByPhone(conflictPhone);
        String conflictCode = sendCode(conflictPhone, "BIND_PHONE");
        api.expectError("/api/auth/phone", HttpMethod.PUT, account.token, null, obj(
                "phone", conflictPhone,
                "code", conflictCode,
                "currentPassword", account.password
        ), 409, "该手机号已绑定其他账号");
    }

    @Test
    void passwordlessPhoneAccountMustVerifyItsCurrentPhoneBeforeChangingIt() {
        String currentPhone = "13800138015";
        JsonNode account = loginByPhone(currentPhone);
        String nextPhone = "13800138016";
        String nextCode = sendCode(nextPhone, "BIND_PHONE");

        api.expectError("/api/auth/phone", HttpMethod.PUT, account.get("token").asText(), null, obj(
                "phone", nextPhone,
                "code", nextCode
        ), 400, "请验证当前密码或原手机号");

        String currentCode = sendCode(currentPhone, "VERIFY_CURRENT_PHONE");
        JsonNode changed = api.putOk("/api/auth/phone", account.get("token").asText(), null, obj(
                "phone", nextPhone,
                "code", nextCode,
                "currentPhone", currentPhone,
                "currentPhoneCode", currentCode
        ));
        Assertions.assertEquals("138****8016", changed.get("maskedPhone").asText());

        String loginCode = sendCode(nextPhone, "LOGIN");
        JsonNode login = api.postOk("/api/auth/sms/login", null, null, obj(
                "phone", nextPhone,
                "code", loginCode,
                "purpose", "LOGIN"
        ));
        Assertions.assertEquals(account.get("userId").asLong(), login.get("userId").asLong());
    }

    @Test
    void exactPhoneInvitationsAreRedeemedOnlyAfterTheNextSmsLoginWithoutLeakingPhone() {
        UserSession owner = register("phone_invite_owner");
        long houseId = createHouse(owner, "手机号邀请兔场", 1, 1, 1);

        JsonNode existingAuth = loginByPhone("13800138002");
        UserSession existingUser = session(existingAuth);
        JsonNode existingSubmission = invite(owner, houseId, "+86 13800138002", "STAFF");
        Assertions.assertEquals("SUBMITTED", existingSubmission.get("status").asText());
        Assertions.assertEquals("STAFF", existingSubmission.get("role").asText());
        assertNoPhoneFields(existingSubmission);
        Assertions.assertEquals(0, countMember(owner, houseId, existingUser.userId));
        api.expectError("/api/houses/permission", HttpMethod.GET, existingUser.token, houseId, null, 403, "无兔场权限");

        JsonNode submittedAgain = invite(owner, houseId, "13800138002", "STAFF");
        Assertions.assertEquals("SUBMITTED", submittedAgain.get("status").asText());
        Assertions.assertEquals("STAFF", submittedAgain.get("role").asText());
        assertNoPhoneFields(submittedAgain);
        Assertions.assertEquals(0, countMember(owner, houseId, existingUser.userId));

        JsonNode redeemedExistingAuth = loginByPhone("+86 13800138002");
        UserSession redeemedExistingUser = session(redeemedExistingAuth);
        Assertions.assertEquals(existingUser.userId, redeemedExistingUser.userId);
        Assertions.assertEquals("STAFF", api.getOk(
                "/api/houses/permission",
                redeemedExistingUser.token,
                houseId
        ).get("role").asText());
        Assertions.assertEquals(1, countMember(owner, houseId, existingUser.userId));

        JsonNode futureSubmission = invite(owner, houseId, "13800138003", "VIEWER");
        Assertions.assertEquals("SUBMITTED", futureSubmission.get("status").asText());
        Assertions.assertEquals("VIEWER", futureSubmission.get("role").asText());
        assertNoPhoneFields(futureSubmission);

        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "phone", "13800138005",
                "role", "OWNER",
                "requestId", requestId("owner_invitation")
        ), 400, "邀请不能直接授予兔场所有者");

        JsonNode invitedAuth = loginByPhone("+86 13800138003");
        UserSession invitedUser = session(invitedAuth);
        Assertions.assertEquals("VIEWER", api.getOk(
                "/api/houses/permission",
                invitedUser.token,
                houseId
        ).get("role").asText());
        Assertions.assertEquals(1, api.getOk("/api/cages", invitedUser.token, houseId).size());
        api.expectError("/api/rabbits", HttpMethod.POST, invitedUser.token, houseId, obj(
                "cageId", api.getOk("/api/cages", owner.token, houseId).get(0).get("id").asLong(),
                "type", "0",
                "gender", "0",
                "requestId", requestId("invited_viewer_write")
        ), 403, "权限不足");
    }

    @Test
    void invitationHistoryUsesTheLatestRoleAndRequestIdsCannotBeRebound() {
        UserSession owner = register("invitation_history_owner");
        long houseId = createHouse(owner, "邀请历史兔场", 1, 1, 1);
        String firstRequestId = requestId("invitation_manager");
        String latestRequestId = requestId("invitation_viewer");

        JsonNode first = invite(owner, houseId, "13800138006", "MANAGER", firstRequestId);
        JsonNode latest = invite(owner, houseId, "+86 13800138006", "VIEWER", latestRequestId);
        JsonNode replay = invite(owner, houseId, "13800138006", "MANAGER", firstRequestId);

        Assertions.assertEquals("SUBMITTED", first.get("status").asText());
        Assertions.assertEquals("MANAGER", first.get("role").asText());
        Assertions.assertEquals("VIEWER", latest.get("role").asText());
        Assertions.assertEquals("MANAGER", replay.get("role").asText());
        assertNoPhoneFields(first);
        assertNoPhoneFields(latest);
        assertNoPhoneFields(replay);
        Assertions.assertEquals(2, invitationCount(houseId, "PENDING"));

        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "phone", "13800138007",
                "role", "MANAGER",
                "requestId", firstRequestId
        ), 409, "requestId已用于其他邀请");
        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "phone", "13800138006",
                "role", "STAFF",
                "requestId", firstRequestId
        ), 409, "requestId已用于其他邀请");
        Assertions.assertEquals(2, invitationCount(houseId, "PENDING"));

        UserSession invitedUser = session(loginByPhone("13800138006"));
        Assertions.assertEquals("VIEWER", api.getOk(
                "/api/houses/permission",
                invitedUser.token,
                houseId
        ).get("role").asText());
        Assertions.assertEquals(0, invitationCount(houseId, "PENDING"));
        Assertions.assertEquals(2, invitationCount(houseId, "ACCEPTED"));
    }

    @Test
    void concurrentInvitationsWithTheSameRequestIdAreIdempotent() throws Exception {
        UserSession owner = register("concurrent_invitation_owner");
        long houseId = createHouse(owner, "并发邀请兔场", 1, 1, 1);
        String invitationRequestId = requestId("concurrent_invitation");

        List<JsonNode> responses = runConcurrently(
                () -> invitationResponse(owner, houseId, "13800138009", "STAFF", invitationRequestId),
                () -> invitationResponse(owner, houseId, "+86 13800138009", "STAFF", invitationRequestId)
        );

        for (JsonNode response : responses) {
            Assertions.assertEquals(0, response.path("code").asInt());
            Assertions.assertEquals("SUBMITTED", response.path("data").path("status").asText());
            Assertions.assertEquals("STAFF", response.path("data").path("role").asText());
            assertNoPhoneFields(response.path("data"));
        }
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM house_invitations WHERE house_id = ? AND request_id = ?",
                Integer.class,
                houseId,
                invitationRequestId
        );
        Assertions.assertEquals(1, rows == null ? 0 : rows);
        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "phone", "13800138010",
                "role", "STAFF",
                "requestId", invitationRequestId
        ), 409, "requestId已用于其他邀请");
    }

    @Test
    void disabledOwnerMembershipIsRestoredWithTheInvitedRole() {
        UserSession farmOwner = register("disabled_membership_owner");
        JsonNode phoneAuth = loginByPhone("13800138008");
        UserSession phoneUser = session(phoneAuth);
        long houseId = createHouse(farmOwner, "停用成员邀请兔场", 1, 1, 1);
        api.postOk("/api/house-members", farmOwner.token, houseId, obj(
                "userName", phoneUser.userName,
                "role", "MANAGER",
                "requestId", requestId("disabled_owner_add")
        ));
        api.putOk("/api/house-members/" + phoneUser.userId, farmOwner.token, houseId, obj(
                "role", "OWNER",
                "requestId", requestId("disabled_owner_promote")
        ));
        jdbcTemplate.update(
                "UPDATE house_users SET status = 'DISABLED' WHERE house_id = ? AND user_id = ?",
                houseId,
                phoneUser.userId
        );
        api.expectError("/api/houses/permission", HttpMethod.GET, phoneUser.token, houseId, null, 403, "无兔场权限");

        JsonNode submission = invite(farmOwner, houseId, "13800138008", "STAFF");
        Assertions.assertEquals("SUBMITTED", submission.path("status").asText());
        UserSession restored = session(loginByPhone("+86 13800138008"));

        Assertions.assertEquals(phoneUser.userId, restored.userId);
        Assertions.assertEquals("STAFF", api.getOk(
                "/api/houses/permission",
                restored.token,
                houseId
        ).path("role").asText());
        Assertions.assertEquals("STAFF", jdbcTemplate.queryForObject(
                "SELECT role FROM house_users WHERE house_id = ? AND user_id = ?",
                String.class,
                houseId,
                restored.userId
        ));
        Assertions.assertEquals("ENABLED", jdbcTemplate.queryForObject(
                "SELECT status FROM house_users WHERE house_id = ? AND user_id = ?",
                String.class,
                houseId,
                restored.userId
        ));
        Assertions.assertEquals(1, countMembersByRole(farmOwner, houseId, "OWNER"));
    }

    private JsonNode loginByPhone(String phone) {
        JsonNode delivery = api.postOk("/api/auth/sms/code", null, null, obj("phone", phone));
        Assertions.assertEquals(300, delivery.get("expiresInSeconds").asInt());
        Assertions.assertEquals(60, delivery.get("retryAfterSeconds").asInt());
        String normalized = normalizePhone(phone);
        String code = sentCodes.get(normalized);
        Assertions.assertNotNull(code, "SMS code should be sent to " + normalized);
        return api.postOk("/api/auth/sms/login", null, null, obj(
                "phone", phone,
                "code", code
        ));
    }

    private String sendCode(String phone, String purpose) {
        api.postOk("/api/auth/sms/code", null, null, obj(
                "phone", phone,
                "purpose", purpose
        ));
        String normalized = normalizePhone(phone);
        String code = sentCodes.get(normalized);
        Assertions.assertNotNull(code, "SMS code should be sent to " + normalized);
        return code;
    }

    private JsonNode invite(UserSession owner, long houseId, String phone, String role) {
        return invite(owner, houseId, phone, role, requestId("phone_invitation"));
    }

    private JsonNode invite(UserSession owner, long houseId, String phone, String role, String requestId) {
        return api.postOk("/api/house-invitations", owner.token, houseId, obj(
                "phone", phone,
                "role", role,
                "requestId", requestId
        ));
    }

    private JsonNode invitationResponse(
            UserSession owner,
            long houseId,
            String phone,
            String role,
            String requestId
    ) {
        return api.postResponse("/api/house-invitations", owner.token, houseId, obj(
                "phone", phone,
                "role", role,
                "requestId", requestId
        ));
    }

    private List<JsonNode> runConcurrently(
            Callable<JsonNode> firstCall,
            Callable<JsonNode> secondCall
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> first = executor.submit(() -> awaitAndCall(ready, start, firstCall));
            Future<JsonNode> second = executor.submit(() -> awaitAndCall(ready, start, secondCall));
            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS), "both invitations should be ready");
            start.countDown();
            return List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private JsonNode awaitAndCall(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<JsonNode> call
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("concurrent invitation start timed out");
        }
        return call.call();
    }

    private int invitationCount(long houseId, String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM house_invitations WHERE house_id = ? AND status = ?",
                Integer.class,
                houseId,
                status
        );
        return count == null ? 0 : count;
    }

    private UserSession session(JsonNode auth) {
        return new UserSession(
                auth.get("userName").asText(),
                "",
                auth.get("token").asText(),
                auth.get("userId").asLong()
        );
    }

    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.startsWith("86") && digits.length() == 13 ? digits.substring(2) : digits;
    }

    private long countMembersByRole(UserSession owner, long houseId, String role) {
        long count = 0;
        for (JsonNode member : api.getOk("/api/house-members", owner.token, houseId)) {
            if (role.equals(member.path("role").asText())) {
                count++;
            }
        }
        return count;
    }

    private long countMember(UserSession owner, long houseId, long userId) {
        long count = 0;
        for (JsonNode member : api.getOk("/api/house-members", owner.token, houseId)) {
            if (member.path("userId").asLong() == userId) {
                count++;
            }
        }
        return count;
    }

    private void assertNoPhoneFields(JsonNode response) {
        Assertions.assertFalse(response.has("phone"));
        Assertions.assertFalse(response.has("phoneMasked"));
        Assertions.assertFalse(response.has("maskedPhone"));
    }

    private static final class TestSmsVerificationStore implements SmsVerificationStore {
        private final Map<String, Reservation> pending = new HashMap<>();
        private final Map<String, ActiveChallenge> active = new HashMap<>();

        @Override
        public synchronized ReserveResult reserve(Reservation reservation) {
            pending.put(reservation.token(), reservation);
            return ReserveResult.RESERVED;
        }

        @Override
        public synchronized ActivationResult activate(Reservation reservation) {
            if (pending.remove(reservation.token()) == null) {
                return ActivationResult.MISSING;
            }
            active.put(
                    key(reservation.phoneHash(), reservation.purpose()),
                    new ActiveChallenge(reservation.codeHash(), 0)
            );
            return ActivationResult.ACTIVATED;
        }

        @Override
        public synchronized void cancel(Reservation reservation) {
            pending.remove(reservation.token());
        }

        @Override
        public synchronized VerificationResult verifyAndConsume(
                String phoneHash,
                SmsVerificationPurpose purpose,
                String submittedCodeHash,
                int maxAttempts
        ) {
            String key = key(phoneHash, purpose);
            ActiveChallenge challenge = active.get(key);
            if (challenge == null) {
                return VerificationResult.MISSING;
            }
            if (!challenge.codeHash.equals(submittedCodeHash)) {
                int attempts = challenge.attempts + 1;
                if (attempts >= maxAttempts) {
                    active.remove(key);
                    return VerificationResult.LOCKED;
                }
                active.put(key, new ActiveChallenge(challenge.codeHash, attempts));
                return VerificationResult.WRONG;
            }
            active.remove(key);
            return VerificationResult.VERIFIED;
        }

        synchronized void clear() {
            pending.clear();
            active.clear();
        }

        private static String key(String phoneHash, SmsVerificationPurpose purpose) {
            return purpose.name() + ":" + phoneHash;
        }
    }

    private record ActiveChallenge(String codeHash, int attempts) {
    }
}
