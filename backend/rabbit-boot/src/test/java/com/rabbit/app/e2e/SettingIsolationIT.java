package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SettingIsolationIT extends E2eTestSupport {

    @Test
    void userDefaultsAreSnapshottedWhenHousesAreCreatedAndRemainIsolated() {
        UserSession owner = register("setting_isolation_owner");
        updateUserSetting(owner, 11, 22, 31, 4, 26, 9, 33, 44);

        long firstHouse = createHouse(owner, "设置隔离兔舍一", 1, 1, 1);
        JsonNode firstInitial = api.getOk("/api/house-settings", owner.token, firstHouse);
        Assertions.assertTrue(firstInitial.get("customized").asBoolean());
        assertSetting(firstInitial.get("setting"), 11, 22, 4, 26, 9, 33, 44);

        // 修改用户默认模板只能影响之后新建的兔场，不能改变已存在兔场。
        updateUserSetting(owner, 12, 23, 32, 5, 27, 10, 34, 45);
        JsonNode firstAfterUserUpdate = api.getOk("/api/house-settings", owner.token, firstHouse);
        assertSetting(firstAfterUserUpdate.get("setting"), 11, 22, 4, 26, 9, 33, 44);

        long secondHouse = createHouse(owner, "设置隔离兔舍二", 1, 1, 1);
        JsonNode secondInitial = api.getOk("/api/house-settings", owner.token, secondHouse);
        Assertions.assertTrue(secondInitial.get("customized").asBoolean());
        assertSetting(secondInitial.get("setting"), 12, 23, 5, 27, 10, 34, 45);

        api.putOk("/api/house-settings", owner.token, firstHouse, settingBody(
            61, 62, 63, 64, 65, 66, 67, 68, "第一兔场独立配置"
        ));

        JsonNode firstAfterHouseUpdate = api.getOk("/api/house-settings", owner.token, firstHouse);
        assertSetting(firstAfterHouseUpdate.get("setting"), 61, 62, 64, 65, 66, 67, 68);
        JsonNode secondAfterHouseUpdate = api.getOk("/api/house-settings", owner.token, secondHouse);
        assertSetting(secondAfterHouseUpdate.get("setting"), 12, 23, 5, 27, 10, 34, 45);

        JsonNode userSetting = api.getOk("/api/settings", owner.token, null);
        assertSetting(userSetting, 12, 23, 5, 27, 10, 34, 45);
    }

    private void updateUserSetting(UserSession owner, int aphrodisiac, int palpation,
                                   int gestation, int prepartum, int weaning,
                                   int postpartum, int sale, int replacement) {
        api.putOk("/api/settings", owner.token, null, settingBody(
            aphrodisiac, palpation, gestation, prepartum, weaning,
            postpartum, sale, replacement, "用户默认模板"
        ));
    }

    private java.util.Map<String, Object> settingBody(int aphrodisiac, int palpation,
                                                        int gestation, int prepartum,
                                                        int weaning, int postpartum,
                                                        int sale, int replacement,
                                                        String remark) {
        return obj(
            "aphrodisiacDays", aphrodisiac,
            "palpationDays", palpation,
            "gestationDays", gestation,
            "prepartumDays", prepartum,
            "weaningDays", weaning,
            "postpartumDays", postpartum,
            "saleDays", sale,
            "replacementDays", replacement,
            "remark", remark,
            "requestId", requestId("setting")
        );
    }

    private void assertSetting(JsonNode setting, int aphrodisiac, int palpation,
                               int prepartum, int weaning, int postpartum,
                               int sale, int replacement) {
        Assertions.assertEquals(aphrodisiac, setting.get("aphrodisiacDays").asInt());
        Assertions.assertEquals(palpation, setting.get("palpationDays").asInt());
        Assertions.assertEquals(30, setting.get("gestationDays").asInt());
        Assertions.assertEquals(prepartum, setting.get("prepartumDays").asInt());
        Assertions.assertEquals(weaning, setting.get("weaningDays").asInt());
        Assertions.assertEquals(postpartum, setting.get("postpartumDays").asInt());
        Assertions.assertEquals(sale, setting.get("saleDays").asInt());
        Assertions.assertEquals(replacement, setting.get("replacementDays").asInt());
    }
}
