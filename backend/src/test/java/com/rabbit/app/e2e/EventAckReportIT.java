package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventAckReportIT extends E2eTestSupport {
    @Test
    void productionSummaryIncludesBreedingCycleAcknowledgements() {
        UserSession owner = register("event_ack_cycle");
        long houseId = createHouse(owner, "event_ack_cycle_house", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 0,
                "replacementDays", 30,
                "requestId", requestId("event_ack_settings")
        ));
        long mother = createRabbit(owner, houseId, cages.get(0), "0", "0", "event_ack_mother");
        long father = createRabbit(owner, houseId, cages.get(1), "0", "1", "event_ack_father");
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "ACK-" + requestId("event_ack_batch").substring(0, 8),
                "femaleRabbitIds", List.of(mother),
                "requestId", requestId("event_ack_create")
        ));
        long batchId = batch.get("id").asLong();
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", List.of(mother),
                "requestId", requestId("event_ack_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", List.of(mother),
                "requestId", requestId("event_ack_finish")
        ));
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", mother,
                "maleRabbitId", father,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("event_ack_mating")
        ));

        JsonNode events = api.getOk("/api/events?onlyUnnotified=true", owner.token, houseId);
        JsonNode cycleEvent = null;
        for (JsonNode event : events) {
            if ("生产周期".equals(event.get("category").asText())) {
                cycleEvent = event;
                break;
            }
        }
        Assertions.assertNotNull(cycleEvent);
        api.postOk("/api/events/ack", owner.token, houseId, obj(
                "category", "生产周期",
                "recordId", cycleEvent.get("recordId").asLong(),
                "action", "ack",
                "remark", "handled on production queue"
        ));

        JsonNode summary = api.getOk(
                "/api/reports/event-ack-summary?category=生产",
                owner.token,
                houseId
        );
        Assertions.assertEquals("生产", summary.get("category").asText());
        Assertions.assertEquals(1, summary.get("ackCount").asInt());
        Assertions.assertEquals(0, summary.get("ignoreCount").asInt());
        Assertions.assertEquals(0, summary.get("snoozeCount").asInt());
    }
}
