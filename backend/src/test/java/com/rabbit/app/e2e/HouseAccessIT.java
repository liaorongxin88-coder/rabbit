package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class HouseAccessIT extends E2eTestSupport {
    @Test
    void globalUsersCanBelongToMultipleFarmsWithIndependentRoles() {
        UserSession firstOwner = register("first_owner");
        UserSession secondOwner = register("second_owner");
        UserSession sharedMember = register("shared_member");
        long firstHouseId = createHouse(firstOwner, "第一兔场", 1, 1, 1);
        long secondHouseId = createHouse(secondOwner, "第二兔场", 1, 1, 1);
        long memberOwnedHouseId = createHouse(sharedMember, "成员自有兔场", 1, 1, 1);

        api.expectError("/api/cages", HttpMethod.GET, sharedMember.token, firstHouseId, null, 403, "无兔场权限");
        api.expectError("/api/cages", HttpMethod.GET, firstOwner.token, secondHouseId, null, 403, "无兔场权限");

        addMember(firstOwner, firstHouseId, sharedMember, "VIEWER");
        addMember(secondOwner, secondHouseId, sharedMember, "STAFF");

        assertHousePermission(sharedMember, firstHouseId, "VIEWER", "view", false);
        assertHousePermission(sharedMember, secondHouseId, "STAFF", "edit", false);
        JsonNode memberHouses = api.getOk("/api/houses", sharedMember.token, null);
        Assertions.assertEquals(3, memberHouses.size());
        Assertions.assertTrue(containsHouse(memberHouses, firstHouseId));
        Assertions.assertTrue(containsHouse(memberHouses, secondHouseId));
        Assertions.assertTrue(containsHouse(memberHouses, memberOwnedHouseId));

        JsonNode firstOwnerHouses = api.getOk("/api/houses", firstOwner.token, null);
        Assertions.assertEquals(1, firstOwnerHouses.size());
        Assertions.assertTrue(containsHouse(firstOwnerHouses, firstHouseId));
        Assertions.assertFalse(containsHouse(firstOwnerHouses, secondHouseId));

        api.deleteOk(
                "/api/house-members/" + sharedMember.userId + "?requestId=" + requestId("remove_shared"),
                firstOwner.token,
                firstHouseId
        );

        api.expectError("/api/cages", HttpMethod.GET, sharedMember.token, firstHouseId, null, 403, "无兔场权限");
        Assertions.assertEquals(1, api.getOk("/api/cages", sharedMember.token, secondHouseId).size());
        JsonNode remaining = api.getOk("/api/houses", sharedMember.token, null);
        Assertions.assertEquals(2, remaining.size());
        Assertions.assertFalse(containsHouse(remaining, firstHouseId));
        Assertions.assertTrue(containsHouse(remaining, secondHouseId));
        Assertions.assertTrue(containsHouse(remaining, memberOwnedHouseId));
    }

    @Test
    void houseRoleMatrixAndMultipleOwnerGuardAreEnforced() {
        UserSession owner = register("role_owner");
        UserSession nextOwner = register("role_next_owner");
        UserSession manager = register("role_manager");
        UserSession staff = register("role_staff");
        UserSession viewer = register("role_viewer");
        long houseId = createHouse(owner, "角色矩阵兔场", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);

        api.expectError("/api/house-members", HttpMethod.POST, owner.token, houseId, obj(
                "userName", nextOwner.userName,
                "role", "OWNER",
                "requestId", requestId("direct_owner")
        ), 400, "新增成员不能直接设为兔场所有者");

        addMember(owner, houseId, nextOwner, "MANAGER");
        addMember(owner, houseId, manager, "MANAGER");
        addMember(owner, houseId, staff, "STAFF");
        addMember(owner, houseId, viewer, "VIEWER");

        assertHousePermission(owner, houseId, "OWNER", "control", true);
        assertHousePermission(nextOwner, houseId, "MANAGER", "control", false);
        assertHousePermission(manager, houseId, "MANAGER", "control", false);
        assertHousePermission(staff, houseId, "STAFF", "edit", false);
        assertHousePermission(viewer, houseId, "VIEWER", "view", false);

        api.expectError("/api/rabbits", HttpMethod.POST, viewer.token, houseId, obj(
                "cageId", cages.get(0),
                "type", "0",
                "gender", "0",
                "requestId", requestId("viewer_create")
        ), 403, "权限不足");
        Assertions.assertTrue(createRabbit(staff, houseId, cages.get(0), "0", "0", "staff_can_edit") > 0);

        JsonNode updated = api.putOk("/api/houses/" + houseId, manager.token, houseId, obj(
                "name", "经理维护后的兔场",
                "remark", "manager update"
        ));
        Assertions.assertEquals("经理维护后的兔场", updated.get("name").asText());
        api.expectError("/api/house-members", HttpMethod.GET, manager.token, houseId, null, 403, "仅兔场所有者可操作");

        api.putOk("/api/house-members/" + nextOwner.userId, owner.token, houseId, obj(
                "role", "OWNER",
                "requestId", requestId("promote_owner")
        ));
        assertHousePermission(nextOwner, houseId, "OWNER", "control", true);
        JsonNode owners = api.getOk("/api/house-members", owner.token, houseId);
        Assertions.assertEquals(2, countRole(owners, "OWNER"));

        api.postOk(
                "/api/house-members/leave?requestId=" + requestId("first_owner_leave"),
                owner.token,
                houseId,
                null
        );
        api.expectError("/api/cages", HttpMethod.GET, owner.token, houseId, null, 403, "无兔场权限");
        JsonNode afterLeave = api.getOk("/api/house-members", nextOwner.token, houseId);
        Assertions.assertEquals(1, countRole(afterLeave, "OWNER"));
        Assertions.assertFalse(containsMember(afterLeave, owner.userId));

        api.expectError(
                "/api/house-members/leave?requestId=" + requestId("last_owner_leave"),
                HttpMethod.POST,
                nextOwner.token,
                houseId,
                null,
                409,
                "兔场至少需要一名启用的所有者"
        );
        api.expectError("/api/house-members/" + nextOwner.userId, HttpMethod.PUT, nextOwner.token, houseId, obj(
                "role", "MANAGER",
                "requestId", requestId("last_owner_demote")
        ), 409, "兔场至少需要一名启用的所有者");

        api.deleteOk("/api/houses/" + houseId, nextOwner.token, houseId);
        Assertions.assertFalse(containsHouse(api.getOk("/api/houses", nextOwner.token, null), houseId));
        api.expectError("/api/cages", HttpMethod.GET, viewer.token, houseId, null, 410, "已删除");
    }

    @Test
    void farmAccessRequiresTheHouseHeaderAndDirectMembership() {
        UserSession owner = register("scope_owner");
        UserSession outsider = register("scope_outsider");
        long houseId = createHouse(owner, "隔离兔场", 1, 1, 1);

        api.expectError("/api/cages", HttpMethod.GET, owner.token, null, null, 400, "缺少X-House-Id");
        api.expectError("/api/cages", HttpMethod.GET, outsider.token, houseId, null, 403, "无兔场权限");
        Assertions.assertFalse(containsHouse(api.getOk("/api/houses", outsider.token, null), houseId));
    }

    @Test
    void concurrentOwnersCannotBothLeaveTheFarm() throws Exception {
        UserSession firstOwner = register("concurrent_first_owner");
        UserSession secondOwner = register("concurrent_second_owner");
        long houseId = createHouse(firstOwner, "并发退出兔场", 1, 1, 1);
        addMember(firstOwner, houseId, secondOwner, "MANAGER");
        api.putOk("/api/house-members/" + secondOwner.userId, firstOwner.token, houseId, obj(
                "role", "OWNER",
                "requestId", requestId("concurrent_promote_owner")
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> firstLeave = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("concurrent leave start timed out");
                }
                return api.postResponse(
                        "/api/house-members/leave?requestId=" + requestId("concurrent_first_leave"),
                        firstOwner.token,
                        houseId,
                        null
                );
            });
            Future<JsonNode> secondLeave = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("concurrent leave start timed out");
                }
                return api.postResponse(
                        "/api/house-members/leave?requestId=" + requestId("concurrent_second_leave"),
                        secondOwner.token,
                        houseId,
                        null
                );
            });

            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS), "both leave requests should be ready");
            start.countDown();
            JsonNode firstResponse = firstLeave.get(10, TimeUnit.SECONDS);
            JsonNode secondResponse = secondLeave.get(10, TimeUnit.SECONDS);
            List<Integer> codes = List.of(
                    firstResponse.path("code").asInt(),
                    secondResponse.path("code").asInt()
            ).stream().sorted().toList();
            Assertions.assertEquals(List.of(0, 409), codes);

            JsonNode rejected = firstResponse.path("code").asInt() == 409 ? firstResponse : secondResponse;
            Assertions.assertTrue(rejected.path("message").asText().contains("兔场至少需要一名启用的所有者"));
            UserSession survivingOwner = firstResponse.path("code").asInt() == 409 ? firstOwner : secondOwner;
            JsonNode members = api.getOk("/api/house-members", survivingOwner.token, houseId);
            Assertions.assertEquals(1, countEnabledRole(members, "OWNER"));
            Assertions.assertTrue(containsMember(members, survivingOwner.userId));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void addMember(UserSession owner, long houseId, UserSession member, String role) {
        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", member.userName,
                "role", role,
                "requestId", requestId("member_" + role.toLowerCase())
        ));
    }

    private boolean containsHouse(JsonNode houses, long houseId) {
        for (JsonNode house : houses) {
            if (house.path("id").asLong() == houseId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMember(JsonNode members, long userId) {
        for (JsonNode member : members) {
            if (member.path("userId").asLong() == userId) {
                return true;
            }
        }
        return false;
    }

    private long countRole(JsonNode members, String role) {
        long count = 0;
        for (JsonNode member : members) {
            if (role.equals(member.path("role").asText())) {
                count++;
            }
        }
        return count;
    }

    private long countEnabledRole(JsonNode members, String role) {
        long count = 0;
        for (JsonNode member : members) {
            if (role.equals(member.path("role").asText())
                    && "ENABLED".equals(member.path("status").asText())) {
                count++;
            }
        }
        return count;
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
}
