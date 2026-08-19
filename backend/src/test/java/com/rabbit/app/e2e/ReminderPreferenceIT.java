package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReminderPreferenceIT extends E2eTestSupport {

    @Test
    void preferencesAreScopedByUserAndHouseAndPersisted() {
        UserSession owner = register("reminder_owner");
        long firstHouse = createHouse(owner, "提醒兔舍一", 1, 1, 1);
        long secondHouse = createHouse(owner, "提醒兔舍二", 1, 1, 1);

        JsonNode defaults = api.getOk("/api/reminder-settings", owner.token, firstHouse);
        Assertions.assertTrue(defaults.get("enabled").asBoolean());
        Assertions.assertEquals(0, defaults.get("advanceDays").asInt());
        Assertions.assertEquals("ALL", defaults.get("taskTypes").get(0).asText());

        api.putOk("/api/reminder-settings", owner.token, firstHouse, obj(
            "enabled", true,
            "advanceDays", 3,
            "notifyOverdue", false,
            "taskTypes", List.of("MATING", "PALPATION"),
            "requestId", requestId("reminder_update")
        ));

        JsonNode saved = api.getOk("/api/reminder-settings", owner.token, firstHouse);
        Assertions.assertEquals(3, saved.get("advanceDays").asInt());
        Assertions.assertFalse(saved.get("notifyOverdue").asBoolean());
        Assertions.assertEquals(List.of("MATING", "PALPATION"),
            List.of(saved.get("taskTypes").get(0).asText(), saved.get("taskTypes").get(1).asText()));

        JsonNode otherHouse = api.getOk("/api/reminder-settings", owner.token, secondHouse);
        Assertions.assertEquals(0, otherHouse.get("advanceDays").asInt());
        Assertions.assertEquals("ALL", otherHouse.get("taskTypes").get(0).asText());
    }
}
