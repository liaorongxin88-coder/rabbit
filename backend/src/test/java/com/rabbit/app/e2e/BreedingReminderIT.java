package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BreedingReminderIT extends E2eTestSupport {
    @Test
    void breedingBatchRunsThroughWeaningSaleReplacementAndReminderScan() {
        UserSession owner = register("breeding");
        long houseId = createHouse(owner, "breeding_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);

        api.putOk("/api/settings", owner.token, houseId, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 0,
                "replacementDays", 0,
                "remark", "all due immediately",
                "requestId", requestId("settings")
        ));

        long femaleId = createRabbit(owner, houseId, cages.get(0), "0", "0", "female");
        long maleId = createRabbit(owner, houseId, cages.get(1), "0", "1", "male");
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "B" + requestId("code").substring(0, 8),
                "femaleRabbitIds", Arrays.asList(femaleId),
                "remark", "e2e",
                "requestId", requestId("batch")
        ));
        long batchId = batch.get("id").asLong();

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("aph_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("aph_finish")
        ));
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", femaleId,
                "maleRabbitId", maleId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("mating")
        ));
        Assertions.assertTrue(api.getOk("/api/events?onlyUnnotified=true", owner.token, houseId).size() > 0,
                "zero-day settings should make pregnancy check due");

        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "remark", "ok",
                "requestId", requestId("preg")
        ));
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "actionDate", oneMinuteAgo(),
                "remark", "ready",
                "requestId", requestId("prepartum")
        ));
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "birthDate", oneMinuteAgo(),
                "totalKits", 4,
                "liveKits", 4,
                "failed", false,
                "remark", "birth",
                "requestId", requestId("birth")
        ));
        api.postOk("/api/batches/" + batchId + "/weaning", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "weaningDate", oneMinuteAgo(),
                "weaningCount", 4,
                "maleCount", 2,
                "femaleCount", 2,
                "targetCageId", cages.get(2),
                "avgWeight", 1.1,
                "remark", "weaning",
                "requestId", requestId("weaning")
        ));

        JsonNode fattening = api.getOk("/api/batches/" + batchId + "/batch-rabbits?role=fattening&active=true", owner.token, houseId);
        Assertions.assertEquals(4, fattening.size());
        List<Long> kitIds = new ArrayList<Long>();
        for (JsonNode item : fattening) {
            kitIds.add(item.get("rabbitId").asLong());
        }

        api.postOk("/api/batches/" + batchId + "/sale", owner.token, houseId, obj(
                "rabbitIds", kitIds.subList(0, 3),
                "saleDate", oneMinuteAgo(),
                "remark", "batch sale",
                "requestId", requestId("batch_sale")
        ));
        api.postOk("/api/rabbits/replacement", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(kitIds.get(3)),
                "forceExitBatch", true,
                "targetCageId", cages.get(3),
                "requestId", requestId("replacement")
        ));

        JsonNode replacements = api.getOk("/api/replacement-records", owner.token, houseId);
        Assertions.assertTrue(replacements.size() >= 1, "replacement record should be queryable");

        JsonNode scan = api.postOk("/api/maintenance/events/scan", owner.token, houseId, null);
        Assertions.assertTrue(scan.get("prodMarked").asInt() >= 0);
        Assertions.assertTrue(scan.get("repMarked").asInt() >= 0);
        JsonNode logs = api.getOk("/api/event-reminder-logs?limit=50", owner.token, houseId);
        Assertions.assertTrue(logs.size() > 0, "scan should create reminder logs");

        JsonNode recalc = api.postOk("/api/maintenance/breeding-performance/recalc", owner.token, houseId, null);
        Assertions.assertTrue(recalc.get("totalRabbits").asInt() >= 1);
        Assertions.assertTrue(recalc.get("updatedRows").asInt() >= 1);
        Assertions.assertTrue(api.getOk("/api/breeding-performance", owner.token, houseId).size() >= 1);
    }
}
