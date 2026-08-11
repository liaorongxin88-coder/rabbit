package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.auth.service.SmsSender;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class SmsAuthIT extends E2eTestSupport {
    @MockBean
    private SmsSender smsSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> sentCodes = new ConcurrentHashMap<>();

    @BeforeEach
    void captureSmsCodes() throws Exception {
        sentCodes.clear();
        Mockito.doAnswer(invocation -> {
            sentCodes.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(smsSender).sendVerificationCode(Mockito.anyString(), Mockito.anyString());
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

        jdbcTemplate.update("DELETE FROM sms_verification_codes");
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

        jdbcTemplate.update("DELETE FROM sms_verification_codes");
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
        jdbcTemplate.update("DELETE FROM sms_verification_codes");
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
}
