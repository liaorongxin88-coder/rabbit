package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

public class AdminSaasIT extends E2eTestSupport {
    @Test
    void adminApisRequirePlatformTokenAndManageMerchantData() {
        api.expectError("/api/admin/merchants", HttpMethod.GET, null, null, null, 401, "后台未登录");

        UserSession extraUser = register("merchant_user");
        api.expectError("/api/admin/merchants", HttpMethod.GET, extraUser.token, null, null, 401, "后台未登录");

        String adminToken = loginAdmin();
        String merchantUserName = "merchant_owner_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String merchantPassword = "merchant123456";
        JsonNode created = api.postOk("/api/admin/merchants", adminToken, null, obj(
                "name", "测试商户A",
                "contactName", "张三",
                "contactPhone", "13800000000",
                "remark", "e2e",
                "userName", merchantUserName,
                "password", merchantPassword,
                "confirmPassword", merchantPassword
        ));
        long merchantId = created.get("id").asLong();
        Assertions.assertEquals("ENABLED", created.get("status").asText());

        JsonNode initialUsers = api.getOk("/api/admin/merchants/" + merchantId + "/accounts", adminToken, null);
        Assertions.assertEquals(1, initialUsers.size());
        Assertions.assertEquals(merchantUserName, initialUsers.get(0).get("userName").asText());
        long merchantUserId = initialUsers.get(0).get("userId").asLong();

        JsonNode merchantAuth = api.postOk("/api/auth/login", null, null, obj(
                "userName", merchantUserName,
                "password", merchantPassword
        ));
        UserSession merchantUser = new UserSession(
                merchantUserName,
                merchantPassword,
                merchantAuth.get("token").asText(),
                merchantAuth.get("userId").asLong()
        );
        Assertions.assertEquals(merchantUserId, merchantUser.userId);

        String secondaryUserName = "merchant_staff_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String secondaryPassword = "staff123456";
        api.postOk("/api/admin/merchants/" + merchantId + "/accounts", adminToken, null, obj(
                "userName", secondaryUserName,
                "password", secondaryPassword,
                "confirmPassword", secondaryPassword
        ));
        JsonNode secondaryAuth = api.postOk("/api/auth/login", null, null, obj(
                "userName", secondaryUserName,
                "password", secondaryPassword
        ));
        long secondaryUserId = secondaryAuth.get("userId").asLong();
        Assertions.assertNotEquals(merchantUserId, secondaryUserId);

        JsonNode accountPage = api.getOk(
                "/api/admin/accounts/merchant-accounts?page=1&pageSize=20&keyword=" + secondaryUserName,
                adminToken,
                null
        );
        Assertions.assertEquals(1, accountPage.get("total").asLong());
        Assertions.assertEquals(merchantId, accountPage.get("items").get(0).get("merchantId").asLong());
        Assertions.assertEquals("测试商户A", accountPage.get("items").get(0).get("merchantName").asText());

        JsonNode page = api.getOk("/api/admin/merchants?page=1&pageSize=20&keyword=测试商户A", adminToken, null);
        Assertions.assertTrue(page.get("total").asLong() >= 1);
        Assertions.assertTrue(page.get("items").isArray());

        JsonNode updated = api.putOk("/api/admin/merchants/" + merchantId, adminToken, null, obj(
                "name", "测试商户A-更新",
                "contactName", "李四",
                "contactPhone", "13900000000",
                "remark", "updated"
        ));
        Assertions.assertEquals("测试商户A-更新", updated.get("name").asText());

        JsonNode users = api.getOk("/api/admin/merchants/" + merchantId + "/accounts", adminToken, null);
        Assertions.assertEquals(2, users.size());

        long houseId = createHouse(merchantUser, "商户归属兔舍", 1, 1, 1);
        JsonNode overview = api.getOk("/api/admin/merchants/" + merchantId + "/overview", adminToken, null);
        Assertions.assertEquals(1, overview.get("houseCount").asLong());
        Assertions.assertEquals(2, overview.get("userCount").asLong());
        Assertions.assertEquals(1, overview.get("cageCount").asLong());

        api.putOk("/api/admin/merchants/" + merchantId + "/status", adminToken, null, obj("status", "DISABLED"));
        api.expectError("/api/cages", HttpMethod.GET, merchantUser.token, houseId, null, 403, "商户已停用");

        api.putOk("/api/admin/merchants/" + merchantId + "/status", adminToken, null, obj("status", "ENABLED"));
        api.getOk("/api/cages", merchantUser.token, houseId);

    }

    @Test
    void merchantCreationValidatesAccountAndDoesNotLeavePartialData() {
        String adminToken = loginAdmin();
        String userName = "rollback_owner";

        api.expectError("/api/admin/merchants", HttpMethod.POST, adminToken, null, obj(
                "name", "不应创建的商户",
                "userName", userName,
                "password", "123456",
                "confirmPassword", "654321"
        ), 400, "两次输入的密码不一致");

        JsonNode page = api.getOk("/api/admin/merchants?page=1&pageSize=20&keyword=不应创建的商户", adminToken, null);
        Assertions.assertEquals(0, page.get("total").asLong());

        JsonNode registered = api.postOk("/api/auth/register", null, null, obj(
                "userName", userName,
                "password", "123456"
        ));
        Assertions.assertEquals(userName, registered.get("userName").asText());

        api.expectError("/api/admin/merchants", HttpMethod.POST, adminToken, null, obj(
                "name", "重复账号商户",
                "userName", userName,
                "password", "123456",
                "confirmPassword", "123456"
        ), 400, "用户名已存在");
        JsonNode duplicatePage = api.getOk("/api/admin/merchants?page=1&pageSize=20&keyword=重复账号商户", adminToken, null);
        Assertions.assertEquals(0, duplicatePage.get("total").asLong());
    }

    @Test
    void adminApiDoesNotRequireHouseHeader() {
        String adminToken = loginAdmin();
        JsonNode page = api.getOk("/api/admin/merchants", adminToken, null);
        Assertions.assertTrue(page.has("items"));
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
}
