package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BreedingReminderIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void wholeBatchLifecycleClosesAfterOffspringSaleAndMotherExit() {
        UserSession owner = register("batch_closed_loop");
        long houseId = createHouse(owner, "batch_closed_loop_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);
        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 0,
                "replacementDays", 0,
                "remark", "whole batch closed loop",
                "requestId", requestId("closed_settings")
        ));

        long motherId = createRabbit(owner, houseId, cages.get(0), "0", "0", "closed_loop_mother");
        long fatherId = createRabbit(owner, houseId, cages.get(1), "0", "1", "closed_loop_father");
        JsonNode createdBatch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "C" + requestId("closed_code").substring(0, 8),
                "femaleRabbitIds", Arrays.asList(motherId),
                "remark", "one mother batch closed end to end",
                "requestId", requestId("closed_batch")
        ));
        long batchId = createdBatch.get("id").asLong();
        Assertions.assertEquals("计划中", createdBatch.get("status").asText());

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(motherId),
                "requestId", requestId("closed_aph_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(motherId),
                "requestId", requestId("closed_aph_finish")
        ));
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", motherId,
                "maleRabbitId", fatherId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("closed_mating")
        ));
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", motherId,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "requestId", requestId("closed_pregnancy")
        ));
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, obj(
                "rabbitId", motherId,
                "actionDate", oneMinuteAgo(),
                "requestId", requestId("closed_prepartum")
        ));
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, obj(
                "rabbitId", motherId,
                "birthDate", oneMinuteAgo(),
                "totalKits", 3,
                "liveKits", 3,
                "failed", false,
                "requestId", requestId("closed_parturition")
        ));
        api.postOk("/api/batches/" + batchId + "/weaning", owner.token, houseId, obj(
                "rabbitId", motherId,
                "weaningDate", oneMinuteAgo(),
                "weaningCount", 3,
                "maleCount", 2,
                "femaleCount", 1,
                "targetCageId", cages.get(2),
                "avgWeight", 1.1,
                "requestId", requestId("closed_weaning")
        ));

        JsonNode activeFattening = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=fattening&active=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(3, activeFattening.size());
        List<Long> offspringIds = new ArrayList<Long>();
        for (JsonNode item : activeFattening) {
            offspringIds.add(item.get("rabbitId").asLong());
        }
        JsonNode offspring = api.getOk("/api/rabbits/" + offspringIds.get(0), owner.token, houseId);
        Assertions.assertEquals(motherId, offspring.get("motherId").asLong());
        Assertions.assertEquals(fatherId, offspring.get("fatherId").asLong());
        Assertions.assertEquals(batchId, offspring.get("birthBatchId").asLong());
        Assertions.assertNotNull(offspring.get("birthCycleId"));

        api.postOk("/api/batches/" + batchId + "/sale", owner.token, houseId, obj(
                "rabbitIds", offspringIds,
                "saleDate", oneMinuteAgo(),
                "remark", "all offspring sold",
                "requestId", requestId("closed_sale")
        ));
        JsonNode afterOffspringSale = api.getOk("/api/batches/" + batchId, owner.token, houseId);
        Assertions.assertNotEquals("已完成", afterOffspringSale.get("status").asText(),
                "the mother link keeps the batch open until the mother is handled");
        Assertions.assertEquals(1, api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?active=true", owner.token, houseId
        ).size());

        api.expectError("/api/batches/" + batchId + "/complete", org.springframework.http.HttpMethod.POST,
                owner.token, houseId, obj(
                        "force", false,
                        "remark", "must not close with active mother",
                        "requestId", requestId("closed_reject_active")
                ), 400, "批次仍有活跃兔");

        api.postOk("/api/rabbits/events", owner.token, houseId, obj(
                "rabbitId", motherId,
                "eventType", "cull",
                "actionDate", oneMinuteAgo(),
                "reason", "繁殖批次结束淘汰母兔",
                "forceExitBatch", true,
                "requestId", requestId("closed_mother_exit")
        ));

        JsonNode completed = api.getOk("/api/batches/" + batchId, owner.token, houseId);
        Assertions.assertEquals("已完成", completed.get("status").asText());
        Assertions.assertNotNull(completed.get("endDate"));
        Assertions.assertEquals(0, api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?active=true", owner.token, houseId
        ).size());
        Assertions.assertEquals(3, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where house_id = ? and rabbit_id in (?, ?, ?) and departure_type = 'sale'",
                Integer.class, houseId, offspringIds.get(0), offspringIds.get(1), offspringIds.get(2)));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where house_id = ? and rabbit_id = ? and departure_type = 'cull'",
                Integer.class, houseId, motherId));

        String completionRequestId = requestId("closed_explicit_complete");
        api.postOk("/api/batches/" + batchId + "/complete", owner.token, houseId, obj(
                "force", false,
                "endDate", oneMinuteAgo(),
                "remark", "explicitly confirm already auto-closed batch",
                "requestId", completionRequestId
        ));
        api.postOk("/api/batches/" + batchId + "/complete", owner.token, houseId, obj(
                "force", false,
                "endDate", oneMinuteAgo(),
                "remark", "explicitly confirm already auto-closed batch",
                "requestId", completionRequestId
        ));
        Assertions.assertEquals("已完成", api.getOk("/api/batches/" + batchId, owner.token, houseId).get("status").asText());

        api.expectError("/api/batches/" + batchId + "/mating", org.springframework.http.HttpMethod.POST,
                owner.token, houseId, obj(
                        "femaleRabbitId", motherId,
                        "maleRabbitId", fatherId,
                        "matingDate", oneMinuteAgo(),
                        "requestId", requestId("closed_write_after_complete")
                ), 400, "批次已完成");
    }

    @Test
    void emptyPregnancyCheckClosesOnlyTheFailedOverlappingCycle() {
        UserSession owner = register("overlap_empty");
        long houseId = createHouse(owner, "overlap_empty_house", 1, 5, 1);
        List<Long> cages = cageIds(owner, houseId);
        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 30,
                "replacementDays", 45,
                "remark", "overlap empty pregnancy",
                "requestId", requestId("empty_settings")
        ));

        long femaleId = createRabbit(owner, houseId, cages.get(0), "0", "0", "empty_female");
        long firstMaleId = createRabbit(owner, houseId, cages.get(1), "0", "1", "empty_first_male");
        long secondMaleId = createRabbit(owner, houseId, cages.get(2), "0", "1", "empty_second_male");
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "E" + requestId("empty_code").substring(0, 8),
                "femaleRabbitIds", Arrays.asList(femaleId),
                "remark", "empty overlap e2e",
                "requestId", requestId("empty_batch")
        ));
        long batchId = batch.get("id").asLong();

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("empty_aph_start_a")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("empty_aph_finish_a")
        ));
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", femaleId,
                "maleRabbitId", firstMaleId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("empty_mating_a")
        ));
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "requestId", requestId("empty_preg_a")
        ));
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "actionDate", oneMinuteAgo(),
                "requestId", requestId("empty_prepartum_a")
        ));
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "birthDate", oneMinuteAgo(),
                "totalKits", 7,
                "liveKits", 6,
                "failed", false,
                "requestId", requestId("empty_birth_a")
        ));

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("empty_aph_start_b")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("empty_aph_finish_b")
        ));
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", femaleId,
                "maleRabbitId", secondMaleId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("empty_mating_b")
        ));

        JsonNode overlappingCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId + "&activeOnly=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(2, overlappingCycles.size());
        long secondCycleId = overlappingCycles.get(0).get("id").asLong();

        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "breedingCycleId", secondCycleId,
                "checkDate", oneMinuteAgo(),
                "result", "空怀",
                "remark", "second mating did not conceive",
                "requestId", requestId("empty_preg_b")
        ));

        JsonNode activeCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId + "&activeOnly=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(1, activeCycles.size());
        Assertions.assertEquals("哺乳中", activeCycles.get(0).get("status").asText());
        Assertions.assertEquals(6, activeCycles.get(0).get("currentNursingKits").asInt());

        JsonNode allCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId,
                owner.token,
                houseId
        );
        JsonNode failedCycle = allCycles.get(0);
        Assertions.assertEquals(secondCycleId, failedCycle.get("id").asLong());
        Assertions.assertEquals("空怀", failedCycle.get("status").asText());
        Assertions.assertEquals("空怀", failedCycle.get("pregnancyResult").asText());
        Assertions.assertEquals("孕检空怀", failedCycle.get("closeReason").asText());
        Assertions.assertFalse(failedCycle.get("closedAt").isNull());
        Assertions.assertTrue(failedCycle.get("nextEventDate").isNull());

        JsonNode breedingLinks = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=breeding&active=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(1, breedingLinks.size());
        Assertions.assertEquals("哺乳中", breedingLinks.get(0).get("currentStatus").asText());
        Assertions.assertEquals(6, breedingLinks.get(0).get("currentNursingKits").asInt());
        Assertions.assertEquals(1, breedingLinks.get(0).get("nursingLitterCount").asInt());

        JsonNode dueEvents = api.getOk("/api/events?onlyUnnotified=true", owner.token, houseId);
        boolean hasFirstLitterWeaning = false;
        boolean hasFailedCycleEvent = false;
        for (JsonNode event : dueEvents) {
            if (!"生产周期".equals(event.get("category").asText()) || event.get("rabbitId").asLong() != femaleId) {
                continue;
            }
            hasFirstLitterWeaning |= "断奶".equals(event.get("eventType").asText());
            hasFailedCycleEvent |= event.get("recordId").asLong() == secondCycleId;
        }
        Assertions.assertTrue(hasFirstLitterWeaning);
        Assertions.assertFalse(hasFailedCycleEvent, "an empty cycle must no longer emit pregnancy reminders");

        JsonNode performance = api.getOk(
                "/api/breeding-performance?rabbitId=" + femaleId,
                owner.token,
                houseId
        );
        Assertions.assertEquals(1, performance.get("successBreedingCount").asInt());
        Assertions.assertEquals(1, performance.get("failedBreedingCount").asInt());

        JsonNode dashboard = api.getOk(
                "/api/reports/dashboard?houseId=" + houseId,
                owner.token,
                null
        );
        Assertions.assertEquals(6, dashboard.get("nursingKits").asInt());
    }

    @Test
    void doeCanStartNextCycleBeforeCurrentLitterIsWeaned() {
        UserSession owner = register("overlap_breeding");
        long houseId = createHouse(owner, "overlap_house", 1, 8, 1);
        List<Long> cages = cageIds(owner, houseId);
        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 30,
                "replacementDays", 45,
                "remark", "overlap cycle",
                "requestId", requestId("settings")
        ));

        long femaleId = createRabbit(owner, houseId, cages.get(0), "0", "0", "female");
        long firstMaleId = createRabbit(owner, houseId, cages.get(1), "0", "1", "first_male");
        long secondMaleId = createRabbit(owner, houseId, cages.get(2), "0", "1", "second_male");
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "O" + requestId("code").substring(0, 8),
                "femaleRabbitIds", Arrays.asList(femaleId),
                "remark", "overlap e2e",
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
                "maleRabbitId", firstMaleId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("mating_a")
        ));
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "requestId", requestId("preg_a")
        ));
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "actionDate", oneMinuteAgo(),
                "requestId", requestId("prepartum_a")
        ));
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "birthDate", oneMinuteAgo(),
                "totalKits", 8,
                "liveKits", 7,
                "failed", false,
                "requestId", requestId("birth_a")
        ));

        JsonNode firstCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId,
                owner.token,
                houseId
        );
        long firstCycleId = firstCycles.get(0).get("id").asLong();
        Assertions.assertEquals("哺乳中", firstCycles.get(0).get("status").asText());
        Assertions.assertEquals(7, firstCycles.get(0).get("currentNursingKits").asInt());

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("overlap_aph_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(femaleId),
                "requestId", requestId("overlap_aph_finish")
        ));
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", femaleId,
                "maleRabbitId", secondMaleId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId("mating_b")
        ));

        JsonNode overlappingCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId + "&activeOnly=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(2, overlappingCycles.size());
        JsonNode secondCycle = overlappingCycles.get(0);
        long secondCycleId = secondCycle.get("id").asLong();
        Assertions.assertEquals("已配种", secondCycle.get("status").asText());
        Assertions.assertEquals(secondMaleId, secondCycle.get("maleRabbitId").asLong());
        Assertions.assertEquals(1, secondCycle.get("overlapLitterCycleNo").asInt());

        JsonNode dashboardDuringOverlap = api.getOk(
                "/api/reports/dashboard?houseId=" + houseId,
                owner.token,
                null
        );
        Assertions.assertEquals(7, dashboardDuringOverlap.get("nursingKits").asInt(),
                "the first litter must remain in nursing totals after the second mating");

        JsonNode dueEvents = api.getOk("/api/events?onlyUnnotified=true", owner.token, houseId);
        boolean hasWeaning = false;
        boolean hasPregnancyCheck = false;
        for (JsonNode event : dueEvents) {
            if (!"生产周期".equals(event.get("category").asText()) || event.get("rabbitId").asLong() != femaleId) {
                continue;
            }
            hasWeaning |= "断奶".equals(event.get("eventType").asText());
            hasPregnancyCheck |= "摸胎".equals(event.get("eventType").asText());
        }
        Assertions.assertTrue(hasWeaning, "first litter weaning reminder should remain visible");
        Assertions.assertTrue(hasPregnancyCheck, "second cycle pregnancy-check reminder should be visible");

        api.postOk("/api/batches/" + batchId + "/weaning", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "breedingCycleId", firstCycleId,
                "weaningDate", oneMinuteAgo(),
                "weaningCount", 5,
                "maleCount", 3,
                "femaleCount", 2,
                "targetCageId", cages.get(3),
                "avgWeight", 1.0,
                "requestId", requestId("weaning_a")
        ));

        JsonNode breedingLinks = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=breeding&active=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(1, breedingLinks.size());
        Assertions.assertEquals("已配种", breedingLinks.get(0).get("currentStatus").asText(),
                "weaning the first litter must not regress the second cycle");
        Assertions.assertEquals(0, breedingLinks.get(0).get("currentNursingKits").asInt());
        Assertions.assertEquals(0, breedingLinks.get(0).get("nursingLitterCount").asInt());
        JsonNode dashboardAfterWeaning = api.getOk(
                "/api/reports/dashboard?houseId=" + houseId,
                owner.token,
                null
        );
        Assertions.assertEquals(0, dashboardAfterWeaning.get("nursingKits").asInt());

        JsonNode allCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId,
                owner.token,
                houseId
        );
        JsonNode weanedCycle = allCycles.get(1);
        Assertions.assertEquals("已断奶", weanedCycle.get("status").asText());
        Assertions.assertEquals(5, weanedCycle.get("weanedKits").asInt());
        Assertions.assertEquals(2, weanedCycle.get("preweaningLossKits").asInt());
        Assertions.assertNotNull(secondCycle.get("postpartumRematingDays"));

        JsonNode fattening = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=fattening&active=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(5, fattening.size());
        long kitId = fattening.get(0).get("rabbitId").asLong();
        JsonNode kit = api.getOk("/api/rabbits/" + kitId, owner.token, houseId);
        Assertions.assertEquals(firstMaleId, kit.get("fatherId").asLong());
        Assertions.assertEquals(batchId, kit.get("birthBatchId").asLong());
        Assertions.assertEquals(firstCycleId, kit.get("birthCycleId").asLong());

        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", femaleId,
                "breedingCycleId", secondCycleId,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "requestId", requestId("preg_b")
        ));
        JsonNode activeAfterCheck = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + femaleId + "&activeOnly=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(1, activeAfterCheck.size());
        Assertions.assertEquals("怀孕确认", activeAfterCheck.get(0).get("status").asText());
    }

    @Test
    void breedingBatchRunsThroughWeaningSaleReplacementAndReminderScan() {
        UserSession owner = register("breeding");
        long houseId = createHouse(owner, "breeding_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);

        api.putOk("/api/settings", owner.token, null, obj(
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

        long secondHouseId = createHouse(owner, "breeding_second_house", 1, 2, 1);
        JsonNode sharedSettings = api.getOk("/api/settings", owner.token, null);
        Assertions.assertEquals(0, sharedSettings.get("palpationDays").asInt(),
                "production settings should be shared by all houses of the user");
        JsonNode sharedSettingsWithHouseHeader = api.getOk("/api/settings", owner.token, secondHouseId);
        Assertions.assertEquals(0, sharedSettingsWithHouseHeader.get("palpationDays").asInt(),
                "settings endpoint should ignore house context and use the current user");
        JsonNode inheritedHouseSettings = api.getOk("/api/house-settings", owner.token, secondHouseId);
        Assertions.assertFalse(inheritedHouseSettings.get("customized").asBoolean(),
                "new house should inherit user default setting until customized");
        Assertions.assertEquals(0, inheritedHouseSettings.get("setting").get("palpationDays").asInt());
        api.putOk("/api/house-settings", owner.token, secondHouseId, obj(
                "aphrodisiacDays", 5,
                "palpationDays", 7,
                "prepartumDays", 6,
                "weaningDays", 22,
                "postpartumDays", 9,
                "saleDays", 33,
                "replacementDays", 44,
                "remark", "second house override",
                "requestId", requestId("house_settings")
        ));
        JsonNode customizedHouseSettings = api.getOk("/api/house-settings", owner.token, secondHouseId);
        Assertions.assertTrue(customizedHouseSettings.get("customized").asBoolean());
        Assertions.assertEquals(7, customizedHouseSettings.get("setting").get("palpationDays").asInt());
        Assertions.assertEquals(0, api.getOk("/api/settings", owner.token, null).get("palpationDays").asInt(),
                "house setting should not mutate user default setting");

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
