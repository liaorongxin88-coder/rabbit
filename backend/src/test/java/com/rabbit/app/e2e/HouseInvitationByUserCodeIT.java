package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 按兔号邀请成员。
 *
 * <p>兔号存在的理由是：邀请人不该必须知道对方的手机号，而对方能报出来的、
 * 又不该是登录凭证的东西，只能是一个专门的公开标识。所以这里除了验通路，
 * 还盯着两件容易做错的事——兔号不能泄露手机号，兔号邀请不能把已有的高权限降下去。
 */
class HouseInvitationByUserCodeIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyAccountGetsItsOwnVisibleUserCode() {
        UserSession first = register("code_owner");
        UserSession second = register("code_mate");

        String firstCode = userCodeOf(first);
        String secondCode = userCodeOf(second);

        Assertions.assertTrue(
                firstCode.matches("^R[0-9A-F]{10}$"),
                "兔号应形如 R3F9A0C21B7，实际是 " + firstCode
        );
        Assertions.assertNotEquals(firstCode, secondCode, "两个账号的兔号不能撞");
    }

    @Test
    void invitingByUserCodeLetsTheMateInRightAway() {
        UserSession owner = register("code_invite_owner");
        UserSession mate = register("code_invite_mate");
        long houseId = createHouse(owner, "兔号邀请兔场", 1, 1, 1);
        String mateCode = userCodeOf(mate);

        // 入伙前：外人一点权限都没有
        api.expectError("/api/houses/permission", HttpMethod.GET, mate.token, houseId, null, 403, "无兔场权限");

        JsonNode response = inviteByIdentifier(owner, houseId, mateCode, "STAFF", requestId("code_invite"));

        // 手机号邀请只能挂起等对方注册；兔号的主人已经在平台上，没什么好等的
        Assertions.assertEquals("JOINED", response.get("status").asText());
        Assertions.assertEquals("STAFF", response.get("role").asText());
        Assertions.assertFalse(response.has("phone"), "邀请回执不该回显手机号");
        Assertions.assertFalse(response.has("maskedPhone"), "邀请回执不该回显手机号");

        // 关键：不用重新登录，当场就能看见这个兔舍
        Assertions.assertEquals(
                "STAFF",
                api.getOk("/api/houses/permission", mate.token, houseId).get("role").asText()
        );
    }

    @Test
    void userCodeIsForgivingAboutHowPeopleTypeIt() {
        UserSession owner = register("code_typo_owner");
        UserSession mate = register("code_typo_mate");
        long houseId = createHouse(owner, "兔号手抄兔场", 1, 1, 1);
        String mateCode = userCodeOf(mate);

        // 口头传达的兔号常常带空格、连字符、大小写混乱，
        // 而且十六进制里没有 O/I/L，所以把它们当成 0/1/1 才是对的。
        String sloppy = ("  " + mateCode.toLowerCase().replace('0', 'o').replace('1', 'l') + "  ")
                .replaceFirst("(?<=^\\s\\sR|^\\s\\sr)", "-");

        JsonNode response = inviteByIdentifier(owner, houseId, sloppy, "VIEWER", requestId("code_typo"));

        Assertions.assertEquals("JOINED", response.get("status").asText(), "手抄成 " + sloppy + " 也该认出来");
        Assertions.assertEquals(
                "VIEWER",
                api.getOk("/api/houses/permission", mate.token, houseId).get("role").asText()
        );
    }

    @Test
    void replayingTheSameInvitationDoesNotPileUpMemberships() {
        UserSession owner = register("code_replay_owner");
        UserSession mate = register("code_replay_mate");
        long houseId = createHouse(owner, "兔号重放兔场", 1, 1, 1);
        String mateCode = userCodeOf(mate);
        String sharedRequestId = requestId("code_replay");

        JsonNode first = inviteByIdentifier(owner, houseId, mateCode, "STAFF", sharedRequestId);
        JsonNode replay = inviteByIdentifier(owner, houseId, mateCode, "STAFF", sharedRequestId);

        Assertions.assertEquals("JOINED", first.get("status").asText());
        Assertions.assertEquals("JOINED", replay.get("status").asText(), "重放要给同样的回执，不能报错");
        Assertions.assertEquals(1, memberRowCount(houseId, mate.userId), "重放不该多出一条成员记录");

        // 同一个 requestId 换个人，属于客户端串号，必须拦
        UserSession stranger = register("code_replay_stranger");
        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "identifier", userCodeOf(stranger),
                "role", "STAFF",
                "requestId", sharedRequestId
        ), 409, "requestId已用于其他邀请");
    }

    @Test
    void invitingAnExistingMemberNeverDemotesThem() {
        UserSession owner = register("code_demote_owner");
        UserSession mate = register("code_demote_mate");
        long houseId = createHouse(owner, "兔号降权兔场", 1, 1, 1);
        String mateCode = userCodeOf(mate);

        inviteByIdentifier(owner, houseId, mateCode, "MANAGER", requestId("code_manager"));
        Assertions.assertEquals(
                "MANAGER",
                api.getOk("/api/houses/permission", mate.token, houseId).get("role").asText()
        );

        // 手滑再邀请一次、还挑了个更低的角色，不能把人从管理员打回只读
        inviteByIdentifier(owner, houseId, mateCode, "VIEWER", requestId("code_viewer"));
        Assertions.assertEquals(
                "MANAGER",
                api.getOk("/api/houses/permission", mate.token, houseId).get("role").asText(),
                "重复邀请只该抬权限，不该降权限"
        );
    }

    @Test
    void badUserCodesFailWithSomethingActionable() {
        UserSession owner = register("code_bad_owner");
        long houseId = createHouse(owner, "兔号报错兔场", 1, 1, 1);

        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "identifier", "R00000000AB",
                "role", "STAFF",
                "requestId", requestId("code_missing")
        ), 404, "没找到兔号");

        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "identifier", userCodeOf(owner),
                "role", "STAFF",
                "requestId", requestId("code_self")
        ), 400, "不用邀请自己");

        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "identifier", "  ",
                "role", "STAFF",
                "requestId", requestId("code_blank")
        ), 400, "请填写手机号或兔号");

        // 既不像手机号也不像兔号：走手机号那条路给出手机号的报错，别憋着
        api.expectError("/api/house-invitations", HttpMethod.POST, owner.token, houseId, obj(
                "identifier", "隔壁老王",
                "role", "STAFF",
                "requestId", requestId("code_garbage")
        ), 400, "手机号");
    }

    @Test
    void oldClientsSendingPhoneKeepWorking() {
        UserSession owner = register("code_legacy_owner");
        long houseId = createHouse(owner, "老客户端兔场", 1, 1, 1);

        // 老 APK 只会发 phone 字段，identifier 根本不存在。
        // 这条路必须原样保留：对方可能还没注册，只能挂起等他登录。
        JsonNode response = api.postOk("/api/house-invitations", owner.token, houseId, obj(
                "phone", "13800138201",
                "role", "STAFF",
                "requestId", requestId("legacy_phone")
        ));

        Assertions.assertEquals("SUBMITTED", response.get("status").asText());
        Assertions.assertEquals("STAFF", response.get("role").asText());
    }

    private JsonNode inviteByIdentifier(
            UserSession inviter,
            long houseId,
            String identifier,
            String role,
            String requestId
    ) {
        return api.postOk("/api/house-invitations", inviter.token, houseId, obj(
                "identifier", identifier,
                "role", role,
                "requestId", requestId
        ));
    }

    private String userCodeOf(UserSession user) {
        JsonNode profile = api.getOk("/api/auth/me", user.token, null);
        Assertions.assertTrue(profile.hasNonNull("userCode"), "账号资料里必须能看到自己的兔号");
        return profile.get("userCode").asText();
    }

    private int memberRowCount(long houseId, long userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from house_users where house_id = ? and user_id = ?",
                Integer.class,
                houseId,
                userId
        );
    }
}
