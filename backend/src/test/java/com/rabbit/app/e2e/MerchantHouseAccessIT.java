package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class MerchantHouseAccessIT extends E2eTestSupport {
    @Test
    void globalAccountCanJoinMerchantsWhileHouseAccessRemainsIndependent() {
        UserSession firstOwner = register("multi_owner");
        JsonNode businessGrant = api.postOk("/api/auth/login", null, null, obj(
                "userName", firstOwner.userName,
                "password", firstOwner.password
        ));
        assertPermission(businessGrant, "account:profile:query", true);
        assertPermission(businessGrant, "merchant:members:list", false);
        JsonNode firstMemberships = api.getOk("/api/merchant-memberships", firstOwner.token, null);
        Assertions.assertEquals(1, firstMemberships.size());
        long firstMerchantId = firstMemberships.get(0).get("merchantId").asLong();
        Assertions.assertEquals("OWNER", firstMemberships.get(0).get("role").asText());
        assertPermission(firstMemberships.get(0), "merchant:members:list", true);
        assertPermission(firstMemberships.get(0), "merchant:houses:add", true);

        String adminToken = loginAdmin();
        String secondOwnerName = "second_owner_" + suffix();
        String secondOwnerPassword = "second123456";
        JsonNode secondMerchant = api.postOk("/api/admin/merchants", adminToken, null, obj(
                "name", "第二商户",
                "userName", secondOwnerName,
                "password", secondOwnerPassword,
                "confirmPassword", secondOwnerPassword
        ));
        long secondMerchantId = secondMerchant.get("id").asLong();
        UserSession secondOwner = login(secondOwnerName, secondOwnerPassword);

        api.postOk("/api/merchant-memberships/" + secondMerchantId + "/members", secondOwner.token, null, obj(
                "userName", firstOwner.userName,
                "role", "ADMIN"
        ));

        JsonNode memberships = api.getOk("/api/merchant-memberships", firstOwner.token, null);
        Assertions.assertEquals(2, memberships.size());

        long firstHouseId = createHouse(firstOwner, "默认商户兔场", 1, 1, 1);
        JsonNode secondHouse = api.postOk("/api/houses", firstOwner.token, null, obj(
                "merchantId", secondMerchantId,
                "name", "第二商户兔场",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("second_merchant_house")
        ));
        long secondHouseId = secondHouse.get("id").asLong();
        Assertions.assertEquals(secondMerchantId, secondHouse.get("merchantId").asLong());
        Assertions.assertEquals(firstOwner.userId, secondHouse.get("ownerUserId").asLong());
        Assertions.assertTrue(containsHouse(api.getOk("/api/houses", firstOwner.token, null), firstHouseId));
        Assertions.assertTrue(containsHouse(api.getOk("/api/houses", secondOwner.token, null), secondHouseId));

        UserSession plainMember = register("plain_member");
        api.postOk("/api/merchant-memberships/" + secondMerchantId + "/members", secondOwner.token, null, obj(
                "userName", plainMember.userName,
                "role", "MEMBER"
        ));
        api.expectError("/api/houses", HttpMethod.POST, plainMember.token, null, obj(
                "merchantId", secondMerchantId,
                "name", "不应创建",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("member_house")
        ), 403, "不能创建兔场");

        api.postOk("/api/house-members", firstOwner.token, secondHouseId, obj(
                "userName", plainMember.userName,
                "role", "VIEWER",
                "requestId", requestId("house_viewer")
        ));
        Assertions.assertEquals(1, api.getOk("/api/cages", plainMember.token, secondHouseId).size());

        JsonNode policy = api.putOk(
                "/api/admin/merchants/" + secondMerchantId + "/house-policy",
                adminToken,
                null,
                obj(
                        "houseCreationEnabled", false,
                        "houseMemberManagementEnabled", false,
                        "maxHouseCount", 2,
                        "maxMembersPerHouse", 3
                )
        );
        Assertions.assertFalse(policy.get("houseCreationEnabled").asBoolean());
        api.expectError("/api/houses", HttpMethod.POST, secondOwner.token, null, obj(
                "merchantId", secondMerchantId,
                "name", "策略禁止建场",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("policy_house")
        ), 403, "未开通兔场创建权限");

        api.putOk(
                "/api/admin/merchants/" + secondMerchantId + "/accounts/" + firstOwner.userId + "/membership",
                adminToken,
                null,
                obj("role", "ADMIN", "status", "DISABLED")
        );
        api.expectError("/api/cages", HttpMethod.GET, firstOwner.token, secondHouseId, null, 403, "无商户权限");
        Assertions.assertFalse(containsHouse(api.getOk("/api/houses", firstOwner.token, null), secondHouseId));
        Assertions.assertEquals(1, api.getOk("/api/cages", firstOwner.token, firstHouseId).size());
        Assertions.assertNotEquals(firstMerchantId, secondMerchantId);
    }

    @Test
    void merchantRolesAndOwnershipTransferAreEnforced() {
        UserSession owner = register("merchant_owner");
        long merchantId = firstMerchantId(owner);
        UserSession merchantAdmin = createMerchantAccount(owner, "merchant_admin");
        UserSession member = createMerchantAccount(owner, "merchant_member");

        api.putOk(
                "/api/merchant-memberships/" + merchantId + "/members/" + merchantAdmin.userId,
                owner.token,
                null,
                obj("role", "ADMIN", "status", "ENABLED")
        );
        api.expectError(
                "/api/merchant-memberships/" + merchantId + "/members",
                HttpMethod.GET,
                merchantAdmin.token,
                null,
                null,
                403,
                "仅商户所有者"
        );

        long adminHouseId = createHouse(merchantAdmin, "管理员创建的兔场", 1, 1, 1);
        Assertions.assertTrue(containsHouse(api.getOk("/api/houses", merchantAdmin.token, null), adminHouseId));
        api.expectError("/api/houses", HttpMethod.POST, member.token, null, obj(
                "merchantId", merchantId,
                "name", "普通成员不可创建",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("merchant_member_house")
        ), 403, "不能创建兔场");

        api.expectError(
                "/api/merchant-memberships/" + merchantId + "/members/" + owner.userId,
                HttpMethod.PUT,
                owner.token,
                null,
                obj("role", "ADMIN", "status", "ENABLED"),
                400,
                "转让商户所有权"
        );
        api.putOk(
                "/api/merchant-memberships/" + merchantId + "/members/" + merchantAdmin.userId,
                owner.token,
                null,
                obj("role", "OWNER", "status", "ENABLED")
        );

        JsonNode members = api.getOk(
                "/api/merchant-memberships/" + merchantId + "/members",
                merchantAdmin.token,
                null
        );
        assertMember(members, merchantAdmin.userId, "OWNER", "ENABLED");
        assertMember(members, owner.userId, "ADMIN", "ENABLED");
        api.expectError(
                "/api/merchant-memberships/" + merchantId + "/members",
                HttpMethod.GET,
                owner.token,
                null,
                null,
                403,
                "仅商户所有者"
        );

        api.putOk(
                "/api/merchant-memberships/" + merchantId + "/members/" + member.userId,
                merchantAdmin.token,
                null,
                obj("role", "MEMBER", "status", "DISABLED")
        );
        api.expectError("/api/houses", HttpMethod.POST, member.token, null, obj(
                "merchantId", merchantId,
                "name", "停用成员不可创建",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("disabled_member_house")
        ), 403, "无商户权限");
        api.deleteOk(
                "/api/merchant-memberships/" + merchantId + "/members/" + member.userId,
                merchantAdmin.token,
                null
        );
        Assertions.assertFalse(containsMember(
                api.getOk("/api/merchant-memberships/" + merchantId + "/members", merchantAdmin.token, null),
                member.userId
        ));
    }

    @Test
    void houseRoleMatrixAndOwnershipTransferAreEnforced() {
        UserSession merchantOwner = register("house_merchant_owner");
        long merchantId = firstMerchantId(merchantOwner);
        UserSession houseOwner = createMerchantAccount(merchantOwner, "house_owner");
        UserSession nextOwner = createMerchantAccount(merchantOwner, "house_next_owner");
        UserSession staff = createMerchantAccount(merchantOwner, "house_staff");
        UserSession viewer = createMerchantAccount(merchantOwner, "house_viewer");

        api.putOk(
                "/api/merchant-memberships/" + merchantId + "/members/" + houseOwner.userId,
                merchantOwner.token,
                null,
                obj("role", "ADMIN", "status", "ENABLED")
        );
        long houseId = createHouse(houseOwner, "角色矩阵兔场", 1, 3, 1);
        api.postOk("/api/house-members", houseOwner.token, houseId, obj(
                "userName", nextOwner.userName,
                "role", "MANAGER",
                "requestId", requestId("manager_add")
        ));
        api.postOk("/api/house-members", houseOwner.token, houseId, obj(
                "userName", staff.userName,
                "role", "STAFF",
                "requestId", requestId("staff_add")
        ));
        api.postOk("/api/house-members", houseOwner.token, houseId, obj(
                "userName", viewer.userName,
                "role", "VIEWER",
                "requestId", requestId("viewer_add")
        ));

        assertHousePermission(nextOwner, houseId, "MANAGER", "control", false);
        assertHousePermission(staff, houseId, "STAFF", "edit", false);
        assertHousePermission(viewer, houseId, "VIEWER", "view", false);
        assertHousePermission(merchantOwner, houseId, "MERCHANT_OWNER", "control", true);

        JsonNode cages = api.getOk("/api/cages", viewer.token, houseId);
        Assertions.assertEquals(3, cages.size());
        api.expectError("/api/rabbits", HttpMethod.POST, viewer.token, houseId, obj(
                "cageId", cages.get(0).get("id").asLong(),
                "type", "0",
                "gender", "0",
                "requestId", requestId("viewer_create")
        ), 403, "权限不足");
        long rabbitId = createRabbit(
                staff,
                houseId,
                cages.get(0).get("id").asLong(),
                "0",
                "0",
                "staff_can_edit"
        );
        Assertions.assertTrue(rabbitId > 0);

        JsonNode updatedHouse = api.putOk("/api/houses/" + houseId, nextOwner.token, houseId, obj(
                "name", "经理可维护兔场",
                "remark", "manager update"
        ));
        Assertions.assertEquals("经理可维护兔场", updatedHouse.get("name").asText());
        api.expectError(
                "/api/house-members",
                HttpMethod.GET,
                nextOwner.token,
                houseId,
                null,
                403,
                "仅管理员"
        );
        api.expectError(
                "/api/houses/" + houseId,
                HttpMethod.DELETE,
                nextOwner.token,
                houseId,
                null,
                403,
                "仅兔场或商户所有者"
        );

        Assertions.assertEquals(4, api.getOk("/api/house-members", merchantOwner.token, houseId).size());
        api.putOk("/api/house-members/" + nextOwner.userId, houseOwner.token, houseId, obj(
                "role", "OWNER",
                "requestId", requestId("house_owner_transfer")
        ));
        assertHousePermission(nextOwner, houseId, "OWNER", "control", true);
        assertHousePermission(houseOwner, houseId, "MANAGER", "control", false);
        api.expectError(
                "/api/house-members",
                HttpMethod.GET,
                houseOwner.token,
                houseId,
                null,
                403,
                "仅管理员"
        );
        Assertions.assertEquals(4, api.getOk("/api/house-members", nextOwner.token, houseId).size());
        api.expectError(
                "/api/house-members/leave?requestId=" + requestId("owner_leave"),
                HttpMethod.POST,
                nextOwner.token,
                houseId,
                null,
                400,
                "所有者不能直接退出"
        );
        api.deleteOk("/api/houses/" + houseId, merchantOwner.token, houseId);
        Assertions.assertFalse(containsHouse(api.getOk("/api/houses", merchantOwner.token, null), houseId));
        api.expectError("/api/cages", HttpMethod.GET, nextOwner.token, houseId, null, 410, "已删除");
    }

    @Test
    void policyLimitsTenantIsolationAndMerchantDisableAreEnforced() {
        String adminToken = loginAdmin();
        MerchantSession tenant = createMerchant(adminToken, "policy_tenant");
        UserSession firstMember = createMerchantAccount(tenant.owner, "policy_member_one");
        UserSession secondMember = createMerchantAccount(tenant.owner, "policy_member_two");
        UserSession outsider = register("policy_outsider");
        long outsiderHouseId = createHouse(outsider, "其他租户兔场", 1, 1, 1);

        api.putOk("/api/admin/merchants/" + tenant.merchantId + "/house-policy", adminToken, null, obj(
                "houseCreationEnabled", true,
                "houseMemberManagementEnabled", true,
                "maxHouseCount", 1,
                "maxMembersPerHouse", 2
        ));
        long houseId = createHouse(tenant.owner, "受限兔场", 1, 1, 1);
        api.expectError("/api/houses", HttpMethod.POST, tenant.owner.token, null, obj(
                "merchantId", tenant.merchantId,
                "name", "超过兔场上限",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("house_limit")
        ), 409, "兔场数量上限");

        api.postOk("/api/house-members", tenant.owner.token, houseId, obj(
                "userName", firstMember.userName,
                "role", "VIEWER",
                "requestId", requestId("member_within_limit")
        ));
        api.expectError("/api/house-members", HttpMethod.POST, tenant.owner.token, houseId, obj(
                "userName", secondMember.userName,
                "role", "VIEWER",
                "requestId", requestId("member_over_limit")
        ), 409, "成员数量上限");
        api.expectError("/api/house-members", HttpMethod.POST, tenant.owner.token, houseId, obj(
                "userName", outsider.userName,
                "role", "VIEWER",
                "requestId", requestId("cross_tenant_member")
        ), 403, "无商户权限");
        api.expectError("/api/cages", HttpMethod.GET, outsider.token, houseId, null, 403, "无商户权限");
        Assertions.assertFalse(containsHouse(api.getOk("/api/houses", outsider.token, null), houseId));
        Assertions.assertTrue(containsHouse(api.getOk("/api/houses", outsider.token, null), outsiderHouseId));

        api.putOk("/api/admin/merchants/" + tenant.merchantId + "/house-policy", adminToken, null, obj(
                "houseCreationEnabled", false,
                "houseMemberManagementEnabled", false,
                "maxHouseCount", 1,
                "maxMembersPerHouse", 2
        ));
        api.expectError("/api/house-members/" + firstMember.userId, HttpMethod.PUT, tenant.owner.token, houseId, obj(
                "role", "STAFF",
                "requestId", requestId("member_management_disabled")
        ), 403, "未开通兔场成员管理权限");
        api.expectError("/api/houses", HttpMethod.POST, tenant.owner.token, null, obj(
                "merchantId", tenant.merchantId,
                "name", "建场开关关闭",
                "layoutRows", 1,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("creation_disabled")
        ), 403, "未开通兔场创建权限");

        api.putOk(
                "/api/admin/merchants/" + tenant.merchantId + "/status",
                adminToken,
                null,
                obj("status", "DISABLED")
        );
        api.expectError("/api/cages", HttpMethod.GET, tenant.owner.token, houseId, null, 403, "商户已停用");
        Assertions.assertFalse(containsHouse(api.getOk("/api/houses", tenant.owner.token, null), houseId));
        JsonNode memberships = api.getOk("/api/merchant-memberships", tenant.owner.token, null);
        Assertions.assertEquals("DISABLED", memberships.get(0).get("merchantStatus").asText());
    }

    private UserSession login(String userName, String password) {
        JsonNode auth = api.postOk("/api/auth/login", null, null, obj(
                "userName", userName,
                "password", password
        ));
        return new UserSession(userName, password, auth.get("token").asText(), auth.get("userId").asLong());
    }

    private MerchantSession createMerchant(String adminToken, String prefix) {
        String unique = suffix();
        String userName = prefix + "_owner_" + unique;
        String password = "tenant123456";
        JsonNode merchant = api.postOk("/api/admin/merchants", adminToken, null, obj(
                "name", prefix + "_" + unique,
                "userName", userName,
                "password", password,
                "confirmPassword", password
        ));
        return new MerchantSession(merchant.get("id").asLong(), login(userName, password));
    }

    private String loginAdmin() {
        return api.postOk("/api/admin/auth/login", null, null, obj(
                "userName", "admin",
                "password", "admin123456"
        )).get("token").asText();
    }

    private boolean containsHouse(JsonNode houses, long houseId) {
        for (JsonNode house : houses) {
            if (house.get("id").asLong() == houseId) {
                return true;
            }
        }
        return false;
    }

    private long firstMerchantId(UserSession user) {
        JsonNode memberships = api.getOk("/api/merchant-memberships", user.token, null);
        Assertions.assertFalse(memberships.isEmpty());
        return memberships.get(0).get("merchantId").asLong();
    }

    private void assertMember(JsonNode members, long userId, String role, String status) {
        for (JsonNode member : members) {
            if (member.get("userId").asLong() == userId) {
                Assertions.assertEquals(role, member.get("role").asText());
                Assertions.assertEquals(status, member.get("status").asText());
                return;
            }
        }
        Assertions.fail("member not found: " + userId);
    }

    private boolean containsMember(JsonNode members, long userId) {
        for (JsonNode member : members) {
            if (member.get("userId").asLong() == userId) {
                return true;
            }
        }
        return false;
    }

    private void assertHousePermission(
            UserSession user,
            long houseId,
            String role,
            String perms,
            boolean admin
    ) {
        JsonNode permission = api.getOk("/api/houses/permission", user.token, houseId);
        Assertions.assertEquals(role, permission.get("role").asText());
        Assertions.assertEquals(perms, permission.get("perms").asText());
        Assertions.assertEquals(admin, permission.get("isAdmin").asBoolean());
        assertPermission(permission, "rabbit:rabbits:list", true);
        assertPermission(permission, "rabbit:rabbits:add", !"VIEWER".equals(role));
        assertPermission(permission, "rabbit:houses:edit", "MANAGER".equals(role) || admin);
        assertPermission(permission, "rabbit:house-members:list", admin);
    }

    private void assertPermission(JsonNode grant, String permission, boolean expected) {
        boolean present = false;
        for (JsonNode item : grant.path("permissions")) {
            if (permission.equals(item.asText())) {
                present = true;
                break;
            }
        }
        Assertions.assertEquals(expected, present, permission);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record MerchantSession(long merchantId, UserSession owner) {
    }
}
