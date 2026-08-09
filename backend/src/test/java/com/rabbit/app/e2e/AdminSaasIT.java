package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.service.AuthService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class AdminSaasIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Test
    void platformApisRequireAdminTokenAndExposeFarmAndUserData() {
        api.expectError("/api/admin/farms?pageNum=1&pageSize=20", HttpMethod.GET, null, null, null, 401, "后台未登录");

        UserSession owner = register("admin_farm_owner");
        UserSession businessUser = register("admin_business_user");
        long houseId = createHouse(owner, "后台兔场概览", 1, 2, 1);
        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", businessUser.userName,
                "role", "VIEWER",
                "requestId", requestId("admin_farm_member")
        ));

        api.expectError(
                "/api/admin/farms?pageNum=1&pageSize=20",
                HttpMethod.GET,
                businessUser.token,
                null,
                null,
                401,
                "后台未登录"
        );
        api.expectError(
                "/api/admin/users?pageNum=1&pageSize=20",
                HttpMethod.GET,
                businessUser.token,
                null,
                null,
                401,
                "后台未登录"
        );

        String adminToken = loginAdmin();
        JsonNode farms = api.getOk(
                "/api/admin/farms?pageNum=1&pageSize=20&keyword=后台兔场概览&status=ENABLED",
                adminToken,
                null
        );
        Assertions.assertEquals(1, farms.get("total").asLong());
        JsonNode farm = farms.get("items").get(0);
        Assertions.assertEquals(houseId, farm.get("id").asLong());
        Assertions.assertEquals("后台兔场概览", farm.get("name").asText());
        Assertions.assertEquals("ENABLED", farm.get("status").asText());
        Assertions.assertEquals(1, farm.get("ownerCount").asInt());
        Assertions.assertEquals(2, farm.get("memberCount").asInt());
        Assertions.assertEquals(2, farm.get("cageCount").asInt());
        Assertions.assertEquals(0, farm.get("rabbitCount").asInt());
        Assertions.assertFalse(farm.has("merchantId"));

        JsonNode overview = api.getOk("/api/admin/farms/" + houseId + "/overview", adminToken, null);
        Assertions.assertEquals(houseId, overview.get("farm").get("id").asLong());
        Assertions.assertEquals(2, overview.get("memberCount").asInt());
        Assertions.assertEquals(2, overview.get("cageCount").asInt());
        Assertions.assertEquals(0, overview.get("rabbitCount").asInt());
        Assertions.assertEquals(0, overview.get("batchCount").asInt());
        Assertions.assertEquals(2, overview.get("members").size());
        Assertions.assertTrue(overview.get("recentAuditLogs").isArray());
        assertMember(overview.get("members"), owner.userId, "OWNER");
        assertMember(overview.get("members"), businessUser.userId, "VIEWER");

        JsonNode users = api.getOk(
                "/api/admin/users?pageNum=1&pageSize=20&keyword=" + businessUser.userName + "&status=ENABLED",
                adminToken,
                null
        );
        Assertions.assertEquals(1, users.get("total").asLong());
        JsonNode user = users.get("items").get(0);
        Assertions.assertEquals(businessUser.userId, user.get("userId").asLong());
        Assertions.assertEquals(businessUser.userName, user.get("userName").asText());
        Assertions.assertTrue(user.get("enabled").asBoolean());
        Assertions.assertEquals("ENABLED", user.get("status").asText());
        Assertions.assertEquals(1, user.get("houseCount").asInt());
        Assertions.assertFalse(user.has("merchantId"));
    }

    @Test
    void adminFarmAndUserApisDoNotRequireAHouseHeader() {
        String adminToken = loginAdmin();

        Assertions.assertTrue(api.getOk(
                "/api/admin/farms?pageNum=1&pageSize=20",
                adminToken,
                null
        ).has("items"));
        Assertions.assertTrue(api.getOk(
                "/api/admin/users?pageNum=1&pageSize=20",
                adminToken,
                null
        ).has("items"));
    }

    @Test
    void platformCreatesAndEditsAFarmWithAnExistingOwnerIdempotently() {
        UserSession owner = register("platform_create_owner");
        String adminToken = loginAdmin();
        String createRequestId = requestId("platform_farm");
        var payload = obj(
                "name", "平台创建兔场",
                "layoutRows", 2,
                "layoutCols", 2,
                "layoutLayers", 1,
                "remark", "平台初始化",
                "ownerUserId", owner.userId,
                "requestId", createRequestId
        );

        JsonNode created = api.postOk("/api/admin/farms", adminToken, null, payload);
        long houseId = created.get("id").asLong();
        Assertions.assertEquals("平台创建兔场", created.get("name").asText());
        Assertions.assertEquals("ENABLED", created.get("status").asText());
        Assertions.assertEquals(1, created.get("ownerCount").asInt());
        Assertions.assertEquals(4, created.get("cageCount").asInt());
        Assertions.assertEquals("OWNER", api.getOk(
                "/api/houses/permission",
                owner.token,
                houseId
        ).get("role").asText());

        JsonNode retry = api.postOk("/api/admin/farms", adminToken, null, payload);
        Assertions.assertEquals(houseId, retry.get("id").asLong());
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rabbit_houses WHERE create_by = 'platform:1' AND request_id = ?",
                Integer.class,
                createRequestId
        ));
        Assertions.assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cages WHERE house_id = ?",
                Integer.class,
                houseId
        ));

        api.expectError("/api/admin/farms", HttpMethod.POST, adminToken, null, obj(
                "name", "冲突兔场",
                "layoutRows", 2,
                "layoutCols", 2,
                "layoutLayers", 1,
                "ownerUserId", owner.userId,
                "requestId", createRequestId
        ), 409, "requestId已用于其他兔场创建请求");

        JsonNode updated = api.putOk("/api/admin/farms/" + houseId, adminToken, null, obj(
                "name", "平台创建兔场-更新",
                "remark", "仅更新基本资料"
        ));
        Assertions.assertEquals("平台创建兔场-更新", updated.get("name").asText());
        Assertions.assertEquals("仅更新基本资料", updated.get("remark").asText());
        Assertions.assertEquals(2, updated.get("layoutRows").asInt());
        Assertions.assertEquals(2, updated.get("layoutCols").asInt());
        Assertions.assertEquals(1, updated.get("layoutLayers").asInt());
        Assertions.assertEquals(4, updated.get("cageCount").asInt());
    }

    @Test
    void concurrentPlatformFarmCreateRetriesReturnTheSameFarm() throws Exception {
        UserSession owner = register("concurrent_platform_owner");
        String adminToken = loginAdmin();
        String createRequestId = requestId("concurrent_platform_farm");
        var payload = obj(
                "name", "并发幂等兔场",
                "layoutRows", 1,
                "layoutCols", 2,
                "layoutLayers", 1,
                "ownerUserId", owner.userId,
                "requestId", createRequestId
        );

        List<JsonNode> responses = runConcurrently(
                () -> api.postResponse("/api/admin/farms", adminToken, null, payload),
                () -> api.postResponse("/api/admin/farms", adminToken, null, payload)
        );

        Assertions.assertEquals(List.of(0, 0), sortedCodes(responses));
        long firstFarmId = responses.get(0).path("data").path("id").asLong();
        Assertions.assertEquals(firstFarmId, responses.get(1).path("data").path("id").asLong());
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rabbit_houses WHERE request_id = ?",
                Integer.class,
                createRequestId
        ));
        Assertions.assertEquals(1, effectiveOwnerCount(firstFarmId));
        Assertions.assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cages WHERE house_id = ?",
                Integer.class,
                firstFarmId
        ));
    }

    @Test
    void platformPhoneOwnerIsProvisionedWithoutPasswordAndClaimedByVerifiedPhoneResolution() {
        String phone = "13800138901";
        String adminToken = loginAdmin();

        JsonNode created = api.postOk("/api/admin/farms", adminToken, null, obj(
                "name", "手机号所有者兔场",
                "layoutRows", 1,
                "layoutCols", 2,
                "layoutLayers", 1,
                "ownerPhone", "+86 " + phone,
                "requestId", requestId("platform_phone_farm")
        ));
        long houseId = created.get("id").asLong();
        long ownerId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM sys_user WHERE phone_masked = '138****8901'",
                Long.class
        );
        String userName = jdbcTemplate.queryForObject(
                "SELECT user_name FROM sys_user WHERE user_id = ?",
                String.class,
                ownerId
        );
        Assertions.assertFalse(jdbcTemplate.queryForObject(
                "SELECT password_initialized FROM sys_user WHERE user_id = ?",
                Boolean.class,
                ownerId
        ));
        Assertions.assertEquals(1, effectiveOwnerCount(houseId));
        api.expectError("/api/auth/login", HttpMethod.POST, null, null, obj(
                "userName", userName,
                "password", "unverified-phone-cannot-login"
        ), 401, "用户名或密码错误");

        AuthTokenResponse claimed = authService.loginOrRegisterPhone(phone);
        Assertions.assertEquals(ownerId, claimed.getUserId());
        Assertions.assertFalse(claimed.getHasPassword());
        JsonNode houses = api.getOk("/api/houses", claimed.getToken(), null);
        Assertions.assertEquals(1, houses.size());
        Assertions.assertEquals(houseId, houses.get(0).get("id").asLong());
        Assertions.assertEquals("OWNER", api.getOk(
                "/api/houses/permission",
                claimed.getToken(),
                houseId
        ).get("role").asText());
    }

    @Test
    void platformFarmCreateRollsBackWhenTheInitialOwnerIsDisabled() {
        UserSession owner = register("disabled_platform_owner");
        String adminToken = loginAdmin();
        api.putOk("/api/admin/users/" + owner.userId + "/status", adminToken, null, obj(
                "status", "DISABLED"
        ));
        String createRequestId = requestId("disabled_owner_farm");

        api.expectError("/api/admin/farms", HttpMethod.POST, adminToken, null, obj(
                "name", "不应创建的兔场",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "ownerUserId", owner.userId,
                "requestId", createRequestId
        ), 409, "初始所有者账号已停用");

        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rabbit_houses WHERE request_id = ?",
                Integer.class,
                createRequestId
        ));
    }

    @Test
    void platformCannotDisableTheOnlyEnabledOwner() {
        UserSession owner = register("sole_owner");
        UserSession replacementOwner = register("replacement_owner");
        long houseId = createHouse(owner, "所有者停用保护兔场", 1, 1, 1);
        String adminToken = loginAdmin();

        api.expectError(
                "/api/admin/users/" + owner.userId + "/status",
                HttpMethod.PUT,
                adminToken,
                null,
                obj("status", "DISABLED"),
                409,
                "该用户是兔场唯一的有效所有者，请先指定另一名所有者"
        );

        api.postOk("/api/admin/farms/" + houseId + "/members", adminToken, null, obj(
                "userId", replacementOwner.userId,
                "role", "OWNER"
        ));
        JsonNode disabled = api.putOk(
                "/api/admin/users/" + owner.userId + "/status",
                adminToken,
                null,
                obj("status", "DISABLED")
        );

        Assertions.assertEquals(owner.userId, disabled.get("userId").asLong());
        Assertions.assertEquals("DISABLED", disabled.get("status").asText());
        Assertions.assertFalse(disabled.get("enabled").asBoolean());
        JsonNode disabledUsers = api.getOk(
                "/api/admin/users?pageNum=1&pageSize=20&keyword=" + owner.userName + "&status=DISABLED",
                adminToken,
                null
        );
        Assertions.assertEquals(1, disabledUsers.get("total").asLong());
        JsonNode enabledUsers = api.getOk(
                "/api/admin/users?pageNum=1&pageSize=20&keyword=" + owner.userName + "&status=ENABLED",
                adminToken,
                null
        );
        Assertions.assertEquals(0, enabledUsers.get("total").asLong());
        JsonNode overview = api.getOk("/api/admin/farms/" + houseId + "/overview", adminToken, null);
        assertMember(overview.get("members"), replacementOwner.userId, "OWNER");
        Assertions.assertEquals("OWNER", api.getOk(
                "/api/houses/permission",
                replacementOwner.token,
                houseId
        ).get("role").asText());
    }

    @Test
    void concurrentOwnerDisablesLeaveOneEnabledOwner() throws Exception {
        UserSession firstOwner = register("disable_first_owner");
        UserSession secondOwner = register("disable_second_owner");
        long houseId = createHouse(firstOwner, "并发停用所有者兔场", 1, 1, 1);
        String adminToken = loginAdmin();
        api.postOk("/api/admin/farms/" + houseId + "/members", adminToken, null, obj(
                "userId", secondOwner.userId,
                "role", "OWNER"
        ));

        List<JsonNode> responses = runConcurrently(
                () -> api.putResponse(
                        "/api/admin/users/" + firstOwner.userId + "/status",
                        adminToken,
                        null,
                        obj("status", "DISABLED")
                ),
                () -> api.putResponse(
                        "/api/admin/users/" + secondOwner.userId + "/status",
                        adminToken,
                        null,
                        obj("status", "DISABLED")
                )
        );

        Assertions.assertEquals(List.of(0, 409), sortedCodes(responses));
        JsonNode rejected = responses.get(0).path("code").asInt() == 409 ? responses.get(0) : responses.get(1);
        Assertions.assertTrue(rejected.path("message").asText().contains(
                "该用户是兔场唯一的有效所有者，请先指定另一名所有者"
        ));
        Assertions.assertEquals(1, enabledUserCount(firstOwner.userId, secondOwner.userId));
        Assertions.assertEquals(1, effectiveOwnerCount(houseId));
        Assertions.assertEquals("ENABLED", houseStatus(houseId));
    }

    @Test
    void enablingASuspendedFarmAndDisablingItsSoleOwnerCannotCreateAnOwnerlessEnabledFarm() throws Exception {
        UserSession owner = register("enable_disable_owner");
        long houseId = createHouse(owner, "启停竞态兔场", 1, 1, 1);
        String adminToken = loginAdmin();
        api.putOk("/api/admin/farms/" + houseId + "/status", adminToken, null, obj("status", "SUSPENDED"));

        List<JsonNode> responses = runConcurrently(
                () -> api.putResponse(
                        "/api/admin/farms/" + houseId + "/status",
                        adminToken,
                        null,
                        obj("status", "ENABLED")
                ),
                () -> api.putResponse(
                        "/api/admin/users/" + owner.userId + "/status",
                        adminToken,
                        null,
                        obj("status", "DISABLED")
                )
        );

        Assertions.assertEquals(List.of(0, 409), sortedCodes(responses));
        String finalHouseStatus = houseStatus(houseId);
        String finalOwnerStatus = userStatus(owner.userId);
        int enabledOwners = effectiveOwnerCount(houseId);
        Assertions.assertFalse("ENABLED".equals(finalHouseStatus) && enabledOwners == 0);
        if ("ENABLED".equals(finalHouseStatus)) {
            Assertions.assertEquals("ENABLED", finalOwnerStatus);
            Assertions.assertEquals(1, enabledOwners);
        } else {
            Assertions.assertEquals("SUSPENDED", finalHouseStatus);
            Assertions.assertEquals("DISABLED", finalOwnerStatus);
        }
    }

    @Test
    void creatingAFarmAndDisablingTheCreatorCannotCreateAnOwnerlessEnabledFarm() throws Exception {
        UserSession creator = register("create_disable_owner");
        String adminToken = loginAdmin();
        String createRequestId = requestId("create_disable_house");

        List<JsonNode> responses = runConcurrently(
                () -> api.postResponse("/api/houses", creator.token, null, obj(
                        "name", "创建停用竞态兔场",
                        "layoutRows", 1,
                        "layoutCols", 1,
                        "layoutLayers", 1,
                        "requestId", createRequestId
                )),
                () -> api.putResponse(
                        "/api/admin/users/" + creator.userId + "/status",
                        adminToken,
                        null,
                        obj("status", "DISABLED")
                )
        );

        int createCode = responses.get(0).path("code").asInt();
        int disableCode = responses.get(1).path("code").asInt();
        Assertions.assertTrue(createCode == 0 || createCode == 403, "unexpected create code: " + createCode);
        Assertions.assertTrue(disableCode == 0 || disableCode == 409, "unexpected disable code: " + disableCode);
        Assertions.assertEquals(1, (createCode == 0 ? 1 : 0) + (disableCode == 0 ? 1 : 0));

        List<Long> houseIds = jdbcTemplate.queryForList(
                "SELECT id FROM rabbit_houses WHERE request_id = ?",
                Long.class,
                createRequestId
        );
        Assertions.assertTrue(houseIds.size() <= 1);
        if (houseIds.isEmpty()) {
            Assertions.assertEquals("DISABLED", userStatus(creator.userId));
        } else {
            long houseId = houseIds.get(0);
            Assertions.assertEquals("ENABLED", houseStatus(houseId));
            Assertions.assertEquals("ENABLED", userStatus(creator.userId));
            Assertions.assertEquals(1, effectiveOwnerCount(houseId));
        }
    }

    @Test
    void disablingOneOwnerWhileTheOtherLeavesKeepsAnEnabledOwner() throws Exception {
        UserSession firstOwner = register("disable_vs_leave_first");
        UserSession leavingOwner = register("disable_vs_leave_second");
        long houseId = createHouse(firstOwner, "停用退出竞态兔场", 1, 1, 1);
        String adminToken = loginAdmin();
        api.postOk("/api/admin/farms/" + houseId + "/members", adminToken, null, obj(
                "userId", leavingOwner.userId,
                "role", "OWNER"
        ));

        List<JsonNode> responses = runConcurrently(
                () -> api.putResponse(
                        "/api/admin/users/" + firstOwner.userId + "/status",
                        adminToken,
                        null,
                        obj("status", "DISABLED")
                ),
                () -> api.postResponse(
                        "/api/house-members/leave?requestId=" + requestId("disable_vs_leave"),
                        leavingOwner.token,
                        houseId,
                        null
                )
        );

        Assertions.assertEquals(List.of(0, 409), sortedCodes(responses));
        Assertions.assertEquals("ENABLED", houseStatus(houseId));
        Assertions.assertEquals(1, effectiveOwnerCount(houseId));
    }

    @Test
    void platformAdminRoleUsesFarmAndUserPermissionCodes() {
        String superToken = loginAdmin();
        String userName = "limited_admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String password = "admin123456";
        api.postOk("/api/admin/accounts", superToken, null, obj(
                "userName", userName,
                "password", password,
                "role", "ADMIN",
                "enabled", true
        ));

        JsonNode login = api.postOk("/api/admin/auth/login", null, null, obj(
                "userName", userName,
                "password", password
        ));
        String adminToken = login.get("token").asText();
        Assertions.assertTrue(containsPermission(login, "platform:farms:list"));
        Assertions.assertTrue(containsPermission(login, "platform:farms:add"));
        Assertions.assertTrue(containsPermission(login, "platform:farms:edit"));
        Assertions.assertTrue(containsPermission(login, "platform:users:list"));
        Assertions.assertFalse(containsPermission(login, "platform:accounts:list"));
        api.getOk("/api/admin/farms?pageNum=1&pageSize=20", adminToken, null);
        api.getOk("/api/admin/users?pageNum=1&pageSize=20", adminToken, null);
        api.expectError("/api/admin/accounts", HttpMethod.GET, adminToken, null, null, 403, "权限不足");
    }

    private String loginAdmin() {
        JsonNode auth = api.postOk("/api/admin/auth/login", null, null, obj(
                "userName", "admin",
                "password", "admin123456"
        ));
        Assertions.assertEquals("admin", auth.get("userName").asText());
        Assertions.assertEquals("SUPER_ADMIN", auth.get("role").asText());
        return auth.get("token").asText();
    }

    private boolean containsPermission(JsonNode grant, String permission) {
        for (JsonNode item : grant.path("permissions")) {
            if (permission.equals(item.asText())) {
                return true;
            }
        }
        return false;
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
            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS), "both requests should be ready");
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
            throw new AssertionError("concurrent request start timed out");
        }
        return call.call();
    }

    private List<Integer> sortedCodes(List<JsonNode> responses) {
        return responses.stream().map(response -> response.path("code").asInt()).sorted().toList();
    }

    private int enabledUserCount(long firstUserId, long secondUserId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE user_id IN (?, ?) AND status = 'ENABLED'",
                Integer.class,
                firstUserId,
                secondUserId
        );
        return count == null ? 0 : count;
    }

    private int effectiveOwnerCount(long houseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM house_users hu "
                        + "JOIN sys_user u ON u.user_id = hu.user_id AND u.status = 'ENABLED' "
                        + "WHERE hu.house_id = ? AND hu.role = 'OWNER' AND hu.status = 'ENABLED'",
                Integer.class,
                houseId
        );
        return count == null ? 0 : count;
    }

    private String userStatus(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE user_id = ?",
                String.class,
                userId
        );
    }

    private String houseStatus(long houseId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM rabbit_houses WHERE id = ?",
                String.class,
                houseId
        );
    }

    private void assertMember(JsonNode members, long userId, String role) {
        for (JsonNode member : members) {
            if (member.path("userId").asLong() == userId) {
                Assertions.assertEquals(role, member.path("role").asText());
                return;
            }
        }
        Assertions.fail("member not found: " + userId);
    }
}
