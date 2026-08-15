package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Production-shaped house-level batch: one batch owns all breeding does in a house,
 * while offspring leave through both the legacy batch sale and the house outbound flow.
 */
public class WholeHouseBatchLifecycleIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void wholeRabbitHouseBatchLifecycleHandlesMixedCyclesAndHouseOutbound() {
        UserSession owner = register("whole_house_batch");
        long houseId = createHouse(owner, "whole_house_batch_house", 4, 8, 1);
        List<Long> cages = cageIds(owner, houseId);
        Assertions.assertTrue(cages.size() >= 17, "the scenario needs breeding and commodity cages");

        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 0,
                "replacementDays", 30,
                "remark", "whole rabbit house production cycle",
                "requestId", requestId("whole_house_settings")
        ));

        long motherA = createRabbit(owner, houseId, cages.get(0), "0", "0", "doe_A_overlap");
        long motherB = createRabbit(owner, houseId, cages.get(1), "0", "0", "doe_B_normal");
        long motherC = createRabbit(owner, houseId, cages.get(2), "0", "0", "doe_C_empty");
        long motherD = createRabbit(owner, houseId, cages.get(3), "0", "0", "doe_D_uncertain");
        long motherE = createRabbit(owner, houseId, cages.get(4), "0", "0", "doe_E_failed");
        long motherF = createRabbit(owner, houseId, cages.get(5), "0", "0", "doe_F_normal");
        long fatherOne = createRabbit(owner, houseId, cages.get(6), "0", "1", "buck_one");
        long fatherTwo = createRabbit(owner, houseId, cages.get(7), "0", "1", "buck_two");
        long fatherThree = createRabbit(owner, houseId, cages.get(8), "0", "1", "buck_three");
        List<Long> mothers = Arrays.asList(motherA, motherB, motherC, motherD, motherE, motherF);

        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "HOUSE-" + requestId("batch_code").substring(0, 10),
                "femaleRabbitIds", mothers,
                "remark", "one batch for every breeding doe in the rabbit house",
                "requestId", requestId("whole_house_batch")
        ));
        long batchId = batch.get("id").asLong();
        Assertions.assertEquals("计划中", batch.get("status").asText());

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", mothers,
                "requestId", requestId("whole_house_aph_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", mothers,
                "requestId", requestId("whole_house_aph_finish")
        ));

        // A: first litter, then a second mating before the first litter is weaned.
        mate(owner, houseId, batchId, motherA, fatherOne, "a_first_mating");
        pregnant(owner, houseId, batchId, motherA, "a_first_pregnancy", "怀孕");
        prepartum(owner, houseId, batchId, motherA, "a_first_prepartum");
        parturition(owner, houseId, batchId, motherA, 6, 6, false, "a_first_birth");
        JsonNode firstA = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + motherA,
                owner.token,
                houseId
        );
        long firstACycleId = firstA.get(0).get("id").asLong();
        Assertions.assertEquals("哺乳中", firstA.get(0).get("status").asText());
        Assertions.assertEquals(6, firstA.get(0).get("currentNursingKits").asInt());

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", List.of(motherA),
                "requestId", requestId("a_overlap_aph_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", List.of(motherA),
                "requestId", requestId("a_overlap_aph_finish")
        ));
        mate(owner, houseId, batchId, motherA, fatherTwo, "a_second_mating");
        JsonNode activeACycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + motherA + "&activeOnly=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(2, activeACycles.size());
        long secondACycleId = activeACycles.get(0).get("id").asLong();
        Assertions.assertEquals("已配种", activeACycles.get(0).get("status").asText());
        Assertions.assertEquals(1, activeACycles.get(0).get("overlapLitterCycleNo").asInt());
        Assertions.assertNotNull(activeACycles.get(0).get("postpartumRematingDays"));
        Assertions.assertTrue(activeACycles.get(0).get("postpartumRematingDays").asInt() >= 0);

        wean(owner, houseId, batchId, motherA, firstACycleId, 5, 3, 2, cages.get(12), "a_first_weaning");
        pregnant(owner, houseId, batchId, motherA, "a_second_pregnancy", "怀孕", secondACycleId);
        prepartum(owner, houseId, batchId, motherA, "a_second_prepartum", secondACycleId);
        parturition(owner, houseId, batchId, motherA, 4, 4, false, "a_second_birth", secondACycleId);
        wean(owner, houseId, batchId, motherA, secondACycleId, 4, 2, 2, cages.get(13), "a_second_weaning");

        // B: ordinary successful doe, six offspring.
        mate(owner, houseId, batchId, motherB, fatherTwo, "b_mating");
        pregnant(owner, houseId, batchId, motherB, "b_pregnancy", "怀孕");
        prepartum(owner, houseId, batchId, motherB, "b_prepartum");
        parturition(owner, houseId, batchId, motherB, 6, 6, false, "b_birth");
        wean(owner, houseId, batchId, motherB, null, 6, 3, 3, cages.get(14), "b_weaning");

        // C: empty pregnancy closes only this doe's batch link; the doe remains in the house.
        mate(owner, houseId, batchId, motherC, fatherThree, "c_mating");
        pregnant(owner, houseId, batchId, motherC, "c_empty_pregnancy", "空怀");
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ? and is_active = true",
                Integer.class,
                batchId,
                motherC
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbits where house_id = ? and id = ? and is_active = true",
                Integer.class,
                houseId,
                motherC
        ));

        // D: first check is uncertain, then the same cycle is confirmed pregnant.
        mate(owner, houseId, batchId, motherD, fatherOne, "d_mating");
        pregnant(owner, houseId, batchId, motherD, "d_uncertain_pregnancy", "不确定");
        pregnant(owner, houseId, batchId, motherD, "d_confirmed_pregnancy", "怀孕");
        prepartum(owner, houseId, batchId, motherD, "d_prepartum");
        parturition(owner, houseId, batchId, motherD, 3, 3, false, "d_birth");
        wean(owner, houseId, batchId, motherD, null, 3, 1, 2, cages.get(15), "d_weaning");

        // E: failed parturition records the abnormal outcome and removes the doe from this batch.
        mate(owner, houseId, batchId, motherE, fatherThree, "e_mating");
        pregnant(owner, houseId, batchId, motherE, "e_pregnancy", "怀孕");
        prepartum(owner, houseId, batchId, motherE, "e_prepartum");
        api.expectError(
                "/api/batches/" + batchId + "/parturition",
                HttpMethod.POST,
                owner.token,
                houseId,
                obj(
                        "rabbitId", motherE,
                        "birthDate", oneMinuteAgo(),
                        "totalKits", 5,
                        "liveKits", 1,
                        "failed", true,
                        "requestId", requestId("e_invalid_failed_birth")
                ),
                400,
                "失败产的总仔数和活仔数必须为0"
        );
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from parturition_records where batch_id = ? and rabbit_id = ?",
                Integer.class,
                batchId,
                motherE
        ));
        parturition(owner, houseId, batchId, motherE, 0, 0, true, "e_failed_birth");
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ? and is_active = true",
                Integer.class,
                batchId,
                motherE
        ));

        // F: another ordinary successful doe, four offspring.
        mate(owner, houseId, batchId, motherF, fatherOne, "f_mating");
        pregnant(owner, houseId, batchId, motherF, "f_pregnancy", "怀孕");
        prepartum(owner, houseId, batchId, motherF, "f_prepartum");
        parturition(owner, houseId, batchId, motherF, 4, 4, false, "f_birth");
        wean(owner, houseId, batchId, motherF, null, 4, 2, 2, cages.get(16), "f_weaning");

        JsonNode allFattening = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=fattening&active=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(22, allFattening.size());
        List<Long> firstLitterChildren = new ArrayList<>();
        for (JsonNode item : api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=fattening&active=true",
                owner.token,
                houseId
        )) {
            if (firstLitterChildren.size() < 3) {
                firstLitterChildren.add(item.get("rabbitId").asLong());
            }
        }
        Assertions.assertEquals(3, firstLitterChildren.size());

        JsonNode firstChild = api.getOk("/api/rabbits/" + firstLitterChildren.get(0), owner.token, houseId);
        Assertions.assertEquals(motherA, firstChild.get("motherId").asLong());
        Assertions.assertEquals(fatherOne, firstChild.get("fatherId").asLong());
        Assertions.assertEquals(batchId, firstChild.get("birthBatchId").asLong());
        Assertions.assertEquals(firstACycleId, firstChild.get("birthCycleId").asLong());

        // Three offspring use the direct batch-sale path; the remaining 19 use the house outbound workflow.
        api.postOk("/api/batches/" + batchId + "/sale", owner.token, houseId, obj(
                "rabbitIds", firstLitterChildren,
                "saleDate", oneMinuteAgo(),
                "remark", "three first-litter rabbits sold directly",
                "requestId", requestId("whole_house_direct_sale")
        ));
        JsonNode remainingFattening = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=fattening&active=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(19, remainingFattening.size());

        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", true
        ));
        Assertions.assertEquals(19, task.get("summary").get("normal").asInt());
        Assertions.assertEquals(19, task.get("selectedItems").size());
        List<Map<String, Object>> selected = new ArrayList<>();
        Map<String, Long> versions = new LinkedHashMap<>();
        for (JsonNode item : task.get("selectedItems")) {
            long rabbitId = item.get("rabbitId").asLong();
            long version = item.get("stateVersion").asLong();
            selected.add(obj(
                    "rabbitId", rabbitId,
                    "stateVersion", version,
                    "selectionType", "NORMAL"
            ));
            versions.put(String.valueOf(rabbitId), version);
        }
        JsonNode frozen = api.putOk("/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId, obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selected,
                "saleTime", oneMinuteAgo(),
                "totalWeight", 20.9,
                "unitPrice", 18.0,
                "customer", "whole house customer",
                "remark", "freeze all remaining commodity rabbits in this house"
        ));
        Assertions.assertEquals("WAITING_CONFIRMATION", frozen.get("status").asText());
        JsonNode outbound = api.postOk(
                "/api/outbound/tasks/" + task.get("taskId").asText() + "/submit",
                owner.token,
                houseId,
                obj(
                        "rabbitIds", new ArrayList<>(versions.keySet()).stream().map(Long::valueOf).toList(),
                        "stateVersions", versions,
                        "saleTime", oneMinuteAgo(),
                        "totalWeight", 20.9,
                        "unitPrice", 18.0,
                        "customer", "whole house customer",
                        "requestId", UUID.randomUUID().toString()
                )
        );
        Assertions.assertEquals("COMPLETED", outbound.get("status").asText());
        Assertions.assertEquals(19, outbound.get("rabbitCount").asInt());
        Assertions.assertEquals(19, jdbc.queryForObject(
                "select count(*) from sale_order_items where sale_order_id = ?",
                Integer.class,
                outbound.get("saleOrderId").asLong()
        ));
        Assertions.assertEquals("进行中", api.getOk("/api/batches/" + batchId, owner.token, houseId).get("status").asText());
        Assertions.assertEquals(4, api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=breeding&active=true",
                owner.token,
                houseId
        ).size());
        Assertions.assertEquals(0, api.getOk("/api/reports/dashboard?houseId=" + houseId, owner.token, null).get("nursingKits").asInt());

        cull(owner, houseId, motherA, "whole_house_cull_a");
        cull(owner, houseId, motherB, "whole_house_cull_b");
        cull(owner, houseId, motherD, "whole_house_cull_d");
        cull(owner, houseId, motherF, "whole_house_cull_f");

        JsonNode completed = api.getOk("/api/batches/" + batchId, owner.token, houseId);
        Assertions.assertEquals("已完成", completed.get("status").asText());
        Assertions.assertNotNull(completed.get("endDate"));
        Assertions.assertEquals(0, api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?active=true",
                owner.token,
                houseId
        ).size());

        Assertions.assertEquals(28, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ?",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(7, jdbc.queryForObject(
                "select count(*) from breeding_cycles where batch_id = ?",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(5, jdbc.queryForObject(
                "select count(*) from breeding_cycles where batch_id = ? and status = '已断奶'",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from breeding_cycles where batch_id = ? and status = '空怀'",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from breeding_cycles where batch_id = ? and status = '分娩失败'",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(22, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'sale'",
                Integer.class,
                houseId
        ));
        Assertions.assertEquals(4, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'cull'",
                Integer.class,
                houseId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'parturition_fail'",
                Integer.class,
                houseId
        ));
        Assertions.assertEquals(5, jdbc.queryForObject(
                "select count(*) from weaning_records where batch_id = ?",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(6, jdbc.queryForObject(
                "select count(*) from parturition_records where batch_id = ?",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(6, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'breeding' and current_nursing_kits = 0 and nursing_litter_count = 0",
                Integer.class,
                batchId
        ));

        String completionRequestId = requestId("whole_house_explicit_complete");
        api.postOk("/api/batches/" + batchId + "/complete", owner.token, houseId, obj(
                "force", false,
                "remark", "confirm automatically closed whole-house batch",
                "requestId", completionRequestId
        ));
        api.postOk("/api/batches/" + batchId + "/complete", owner.token, houseId, obj(
                "force", false,
                "remark", "confirm automatically closed whole-house batch",
                "requestId", completionRequestId
        ));
    }

    private void mate(UserSession owner, long houseId, long batchId, long motherId, long fatherId, String requestPrefix) {
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", motherId,
                "maleRabbitId", fatherId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId(requestPrefix)
        ));
    }

    private void pregnant(UserSession owner, long houseId, long batchId, long motherId, String requestPrefix, String result) {
        pregnant(owner, houseId, batchId, motherId, requestPrefix, result, null);
    }

    private void pregnant(UserSession owner, long houseId, long batchId, long motherId, String requestPrefix, String result, Long cycleId) {
        Map<String, Object> body = obj(
                "rabbitId", motherId,
                "checkDate", oneMinuteAgo(),
                "result", result,
                "requestId", requestId(requestPrefix)
        );
        if (cycleId != null) {
            body.put("breedingCycleId", cycleId);
        }
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, body);
    }

    private void prepartum(UserSession owner, long houseId, long batchId, long motherId, String requestPrefix) {
        prepartum(owner, houseId, batchId, motherId, requestPrefix, null);
    }

    private void prepartum(UserSession owner, long houseId, long batchId, long motherId, String requestPrefix, Long cycleId) {
        Map<String, Object> body = obj(
                "rabbitId", motherId,
                "actionDate", oneMinuteAgo(),
                "requestId", requestId(requestPrefix)
        );
        if (cycleId != null) {
            body.put("breedingCycleId", cycleId);
        }
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, body);
    }

    private void parturition(UserSession owner, long houseId, long batchId, long motherId, int totalKits, int liveKits,
                             boolean failed, String requestPrefix) {
        parturition(owner, houseId, batchId, motherId, totalKits, liveKits, failed, requestPrefix, null);
    }

    private void parturition(UserSession owner, long houseId, long batchId, long motherId, int totalKits, int liveKits,
                             boolean failed, String requestPrefix, Long cycleId) {
        Map<String, Object> body = obj(
                "rabbitId", motherId,
                "birthDate", oneMinuteAgo(),
                "totalKits", totalKits,
                "liveKits", liveKits,
                "failed", failed,
                "requestId", requestId(requestPrefix)
        );
        if (cycleId != null) {
            body.put("breedingCycleId", cycleId);
        }
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, body);
    }

    private void wean(UserSession owner, long houseId, long batchId, long motherId, Long cycleId,
                      int count, int maleCount, int femaleCount, long targetCageId, String requestPrefix) {
        Map<String, Object> body = obj(
                "rabbitId", motherId,
                "weaningDate", oneMinuteAgo(),
                "weaningCount", count,
                "maleCount", maleCount,
                "femaleCount", femaleCount,
                "targetCageId", targetCageId,
                "avgWeight", 1.1,
                "requestId", requestId(requestPrefix)
        );
        if (cycleId != null) {
            body.put("breedingCycleId", cycleId);
        }
        api.postOk("/api/batches/" + batchId + "/weaning", owner.token, houseId, body);
    }

    private void cull(UserSession owner, long houseId, long rabbitId, String requestPrefix) {
        api.postOk("/api/rabbits/events", owner.token, houseId, obj(
                "rabbitId", rabbitId,
                "eventType", "cull",
                "actionDate", oneMinuteAgo(),
                "reason", "whole house batch breeding doe exit",
                "forceExitBatch", true,
                "requestId", requestId(requestPrefix)
        ));
    }
}
