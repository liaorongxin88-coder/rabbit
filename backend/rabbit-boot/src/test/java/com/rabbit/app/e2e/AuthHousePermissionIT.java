package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

public class AuthHousePermissionIT extends E2eTestSupport {
    @Test
    void accountHouseAndIdempotencyFlow() {
        UserSession owner = register("owner");
        String requestId = requestId("house_fixed");

        JsonNode first = api.postOk("/api/houses", owner.token, null, obj(
                "name", "e2e_house",
                "layoutRows", 1,
                "layoutCols", 3,
                "layoutLayers", 1,
                "remark", "first",
                "requestId", requestId
        ));
        JsonNode second = api.postOk("/api/houses", owner.token, null, obj(
                "name", "e2e_house_retry",
                "layoutRows", 1,
                "layoutCols", 3,
                "layoutLayers", 1,
                "remark", "retry",
                "requestId", requestId
        ));

        long houseId = first.get("id").asLong();
        Assertions.assertEquals(houseId, second.get("id").asLong(), "same requestId should return same house");
        Assertions.assertEquals(3, api.getOk("/api/cages", owner.token, houseId).size(), "house layout should create cages");

        JsonNode settings = api.getOk("/api/settings", owner.token, null);
        Assertions.assertEquals(2, settings.get("aphrodisiacDays").asInt());
        Assertions.assertEquals(owner.userId, settings.get("userId").asLong());

        JsonNode houses = api.getOk("/api/houses", owner.token, null);
        Assertions.assertEquals(1, houses.size(), "idempotent house create should not duplicate rows");

        api.expectError("/api/houses", HttpMethod.POST, owner.token, null, obj(
                "name", "bad_house",
                "layoutRows", 0,
                "layoutCols", 1,
                "layoutLayers", 1,
                "requestId", requestId("bad_house")
        ), 400, "必须大于0");
    }

    @Test
    void accountProfileAndPasswordSettingsFlow() {
        UserSession user = register("profile");
        UserSession other = register("profile_other");

        JsonNode profile = api.getOk("/api/auth/me", user.token, null);
        Assertions.assertEquals(user.userId, profile.get("userId").asLong());
        Assertions.assertEquals(user.userName, profile.get("userName").asText());
        Assertions.assertFalse(profile.get("openidBound").asBoolean());

        String nextName = "renamed_" + requestId("user").substring(0, 10);
        JsonNode updated = api.putOk("/api/auth/me", user.token, null, obj(
                "userName", "  " + nextName + "  "
        ));
        Assertions.assertEquals(nextName, updated.get("userName").asText());

        api.expectError("/api/auth/me", HttpMethod.PUT, user.token, null, obj(
                "userName", other.userName
        ), 400, "用户名已存在");

        api.expectError("/api/auth/password", HttpMethod.PUT, user.token, null, obj(
                "oldPassword", "bad-password",
                "newPassword", "654321"
        ), 400, "旧密码不正确");

        api.putOk("/api/auth/password", user.token, null, obj(
                "oldPassword", user.password,
                "newPassword", "654321"
        ));

        api.expectError("/api/auth/login", HttpMethod.POST, null, null, obj(
                "userName", nextName,
                "password", user.password
        ), 401, "用户名或密码错误");

        JsonNode login = api.postOk("/api/auth/login", null, null, obj(
                "userName", nextName,
                "password", "654321"
        ));
        Assertions.assertEquals(user.userId, login.get("userId").asLong());
    }

    @Test
    void housePermissionsProgressFromNoAccessToViewEditAndControl() {
        UserSession owner = register("owner");
        UserSession member = register("member");
        long houseId = createHouse(owner, "perm_house", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);

        api.expectError("/api/cages", HttpMethod.GET, member.token, houseId, null, 403, "无兔场权限");
        api.expectError("/api/cages", HttpMethod.GET, owner.token, null, null, 400, "缺少X-House-Id");

        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", member.userName,
                "perms", "view",
                "isAdmin", false,
                "requestId", requestId("member_view")
        ));

        Assertions.assertTrue(api.getOk("/api/cages", member.token, houseId).size() > 0);
        api.expectError("/api/rabbits", HttpMethod.POST, member.token, houseId, obj(
                "cageId", cages.get(0),
                "type", "0",
                "gender", "0",
                "requestId", requestId("view_rabbit")
        ), 403, "权限不足");

        api.putOk("/api/house-members/" + member.userId, owner.token, houseId, obj(
                "perms", "edit",
                "isAdmin", false,
                "requestId", requestId("member_edit")
        ));
        long rabbitId = createRabbit(member, houseId, cages.get(0), "0", "0", "member_can_edit");
        Assertions.assertTrue(rabbitId > 0);
        api.expectError("/api/house-members", HttpMethod.GET, member.token, houseId, null, 403, "仅兔场所有者可操作");

        api.putOk("/api/house-members/" + member.userId, owner.token, houseId, obj(
                "perms", "control",
                "isAdmin", false,
                "requestId", requestId("member_control")
        ));
        api.expectError("/api/house-members", HttpMethod.GET, member.token, houseId, null, 403, "仅兔场所有者可操作");
        JsonNode hardware = api.getOk("/api/hardware/status", member.token, houseId);
        Assertions.assertFalse(hardware.get("enabled").asBoolean());
    }

    @Test
    void houseMemberLeaveAndControlMember() {
        UserSession owner = register("owner_leave");
        UserSession member = register("member_leave");
        long houseId = createHouse(owner, "leave_house", 1, 3, 1);

        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", member.userName,
                "perms", "control",
                "isAdmin", false,
                "requestId", requestId("member_control_add")
        ));

        JsonNode members = api.getOk("/api/house-members", owner.token, houseId);
        Assertions.assertTrue(members.size() >= 2);

        api.postOk(
                "/api/house-members/leave?requestId=" + requestId("member_leave"),
                member.token,
                houseId,
                null
        );
        api.expectError("/api/cages", HttpMethod.GET, member.token, houseId, null, 403, "无兔场权限");
    }
}
