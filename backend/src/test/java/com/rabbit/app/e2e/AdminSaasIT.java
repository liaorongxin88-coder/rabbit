package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

public class AdminSaasIT extends E2eTestSupport {
    @Test
    void adminApisRequirePlatformTokenAndManageMerchantData() {
        api.expectError("/api/admin/merchants", HttpMethod.GET, null, null, null, 401, "后台未登录");

        UserSession user = register("merchant_user");
        api.expectError("/api/admin/merchants", HttpMethod.GET, user.token, null, null, 401, "后台未登录");

        String adminToken = loginAdmin();
        JsonNode created = api.postOk("/api/admin/merchants", adminToken, null, obj(
                "name", "测试商户A",
                "contactName", "张三",
                "contactPhone", "13800000000",
                "remark", "e2e"
        ));
        long merchantId = created.get("id").asLong();
        Assertions.assertEquals("ENABLED", created.get("status").asText());

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

        api.postOk("/api/admin/merchants/" + merchantId + "/users", adminToken, null, obj("userId", user.userId));
        JsonNode users = api.getOk("/api/admin/merchants/" + merchantId + "/users", adminToken, null);
        Assertions.assertEquals(1, users.size());
        Assertions.assertEquals(user.userId, users.get(0).get("userId").asLong());

        long houseId = createHouse(user, "商户绑定兔舍", 1, 1, 1);
        JsonNode overview = api.getOk("/api/admin/merchants/" + merchantId + "/overview", adminToken, null);
        Assertions.assertEquals(1, overview.get("houseCount").asLong());
        Assertions.assertEquals(1, overview.get("userCount").asLong());
        Assertions.assertEquals(1, overview.get("cageCount").asLong());

        api.putOk("/api/admin/merchants/" + merchantId + "/status", adminToken, null, obj("status", "DISABLED"));
        api.expectError("/api/cages", HttpMethod.GET, user.token, houseId, null, 403, "商户已停用");

        api.putOk("/api/admin/merchants/" + merchantId + "/status", adminToken, null, obj("status", "ENABLED"));
        api.getOk("/api/cages", user.token, houseId);

        api.deleteOk("/api/admin/merchants/" + merchantId + "/users/" + user.userId, adminToken, null);
        JsonNode afterRemove = api.getOk("/api/admin/merchants/" + merchantId + "/users", adminToken, null);
        Assertions.assertEquals(0, afterRemove.size());
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
