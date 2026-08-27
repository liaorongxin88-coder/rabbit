package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class UnifiedCageIntakeIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void explicitGenderMultiCageKeepsTrustedBalancesAndExplicitInactiveParents() {
        Scenario scenario = scenario("explicit_multi", 6, 3, 3);
        long motherId = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(1), "0", "0", "linked_mother"
        );
        long fatherId = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(2), "0", "1", "linked_father"
        );
        jdbc.update("update rabbits set is_active = false where id in (?, ?)", motherId, fatherId);
        jdbc.update(
            "update breeding_cycles set male_rabbit_id = ? where house_id = ? and id = ?",
            fatherId,
            scenario.houseId(),
            scenario.cycleId()
        );

        JsonNode before = pending(scenario);
        Assertions.assertEquals(fatherId, before.get("sireRabbitId").asLong());
        Assertions.assertEquals(3, before.get("waitingMaleCount").asInt());
        Assertions.assertEquals(3, before.get("waitingFemaleCount").asInt());

        JsonNode result = separate(
            scenario,
            List.of(
                allocation(scenario.firstTargetCage(), 3, 2, 1),
                allocation(scenario.secondTargetCage(), 2, 1, 1)
            ),
            requestId("explicit_multi_separate"),
            motherId,
            fatherId
        );

        Assertions.assertEquals(5, result.get("separatedCount").asInt());
        Assertions.assertEquals(1, result.get("waitingCount").asInt());
        Assertions.assertEquals(5, result.get("generatedRabbitIds").size());
        Assertions.assertEquals(5, count(
            "select count(*) from rabbits where birth_batch_id = ? and birth_cycle_id = ?"
                + " and mother_id = ? and father_id = ? and type = '2' and arrival_method = '1'",
            scenario.batchId(), scenario.cycleId(), motherId, fatherId
        ));
        Assertions.assertEquals(2, count(
            "select count(*) from rabbits where birth_cycle_id = ? and cage_id = ? and gender = '1'",
            scenario.cycleId(), scenario.firstTargetCage()
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from rabbits where birth_cycle_id = ? and cage_id = ? and gender = '0'",
            scenario.cycleId(), scenario.firstTargetCage()
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from rabbits where birth_cycle_id = ? and cage_id = ? and gender = '1'",
            scenario.cycleId(), scenario.secondTargetCage()
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from rabbits where birth_cycle_id = ? and cage_id = ? and gender = '0'",
            scenario.cycleId(), scenario.secondTargetCage()
        ));

        JsonNode after = pending(scenario);
        Assertions.assertEquals(0, after.get("waitingMaleCount").asInt());
        Assertions.assertEquals(1, after.get("waitingFemaleCount").asInt());
        Assertions.assertEquals(3, count(
            "select sum(male_count) from weaning_record_allocations"
                + " where weaning_record_id = ? and cage_id in (?, ?)",
            scenario.recordId(), scenario.firstTargetCage(), scenario.secondTargetCage()
        ));
    }

    @Test
    void supportsMotherOnlyAndFatherOnlyWithoutBatchMembership() {
        Scenario scenario = scenario("single_parent", 2, 1, 1);
        long motherId = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(1), "0", "0", "only_mother"
        );
        long fatherId = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(2), "0", "1", "only_father"
        );

        JsonNode motherOnly = separate(
            scenario,
            List.of(allocation(scenario.firstTargetCage(), 1, 1, 0)),
            requestId("mother_only"),
            motherId,
            null
        );
        long motherKitId = motherOnly.get("generatedRabbitIds").get(0).asLong();
        Assertions.assertEquals(1, count(
            "select count(*) from rabbits where id = ? and mother_id = ? and father_id is null"
                + " and birth_batch_id = ? and birth_cycle_id = ? and gender = '1'",
            motherKitId, motherId, scenario.batchId(), scenario.cycleId()
        ));

        JsonNode fatherOnly = separate(
            scenario,
            List.of(allocation(scenario.secondTargetCage(), 1, 0, 1)),
            requestId("father_only"),
            null,
            fatherId
        );
        long fatherKitId = fatherOnly.get("generatedRabbitIds").get(0).asLong();
        Assertions.assertEquals(1, count(
            "select count(*) from rabbits where id = ? and mother_id is null and father_id = ?"
                + " and birth_batch_id = ? and birth_cycle_id = ? and gender = '0'",
            fatherKitId, fatherId, scenario.batchId(), scenario.cycleId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id in (?, ?)",
            scenario.batchId(), motherId, fatherId
        ));
    }

    @Test
    void rejectsCrossHouseWrongTypeAndWrongSexParentsWithoutWrites() {
        Scenario scenario = scenario("bad_parents", 2, 1, 1);
        long crossHouse = createHouse(scenario.owner(), "cross_parent_house", 1, 1, 1);
        long crossParent = createRabbit(
            scenario.owner(), crossHouse, cageIds(scenario.owner(), crossHouse).get(0),
            "0", "0", "cross_parent"
        );
        long wrongMotherType = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(1),
            "1", "0", "wrong_mother_type"
        );
        long wrongMotherSex = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(2),
            "0", "1", "wrong_mother_sex"
        );
        long wrongFatherType = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(3),
            "1", "1", "wrong_father_type"
        );

        expectParentError(scenario, crossParent, null, "当前兔舍");
        expectParentError(scenario, wrongMotherType, null, "关联母兔必须是种兔");
        expectParentError(scenario, wrongMotherSex, null, "关联母兔性别不正确");
        expectParentError(scenario, null, wrongFatherType, "关联公兔必须是种兔");
        expectParentError(scenario, null, scenario.doeId(), "关联公兔性别不正确");

        Assertions.assertEquals(2, count(
            "select waiting_count from weaning_records where id = ?", scenario.recordId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbits where birth_cycle_id = ?", scenario.cycleId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from weaning_record_allocations where weaning_record_id = ?",
            scenario.recordId()
        ));
    }

    @Test
    void rejectsExplicitSexCountsBeyondTrustedRemainderWithoutWrites() {
        Scenario scenario = scenario("sex_overflow", 2, 1, 1);

        api.expectError(
            separationPath(scenario),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            separationBody(
                List.of(allocation(scenario.firstTargetCage(), 2, 2, 0)),
                requestId("sex_overflow_separate"),
                null,
                null
            ),
            400,
            "超过剩余数量"
        );

        Assertions.assertEquals(2, count(
            "select waiting_count from weaning_records where id = ?", scenario.recordId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbits where birth_cycle_id = ?", scenario.cycleId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from weaning_record_allocations where weaning_record_id = ?",
            scenario.recordId()
        ));
    }

    @Test
    void historicalUnknownAllocationRejectsExplicitSexButAllowsCountOnly() {
        Scenario scenario = scenario("historical_unknown", 2, 1, 1);
        jdbc.update(
            "insert into weaning_record_allocations"
                + " (weaning_record_id, cage_id, alloc_count, male_count, female_count)"
                + " values (?, ?, 1, null, null)",
            scenario.recordId(),
            scenario.firstTargetCage()
        );
        jdbc.update(
            "update weaning_records set waiting_count = 1 where id = ?",
            scenario.recordId()
        );

        JsonNode pending = pending(scenario);
        Assertions.assertTrue(pending.get("waitingMaleCount").isNull());
        Assertions.assertTrue(pending.get("waitingFemaleCount").isNull());

        api.expectError(
            separationPath(scenario),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            separationBody(
                List.of(allocation(scenario.secondTargetCage(), 1, 1, 0)),
                requestId("historical_explicit_rejected"),
                null,
                null
            ),
            400,
            "剩余公母数量不可信"
        );

        JsonNode countOnly = separate(
            scenario,
            List.of(obj("cageId", scenario.secondTargetCage(), "count", 1)),
            requestId("historical_count_only"),
            null,
            null
        );
        long rabbitId = countOnly.get("generatedRabbitIds").get(0).asLong();
        Assertions.assertEquals(1, count(
            "select count(*) from rabbits where id = ? and gender = '2'"
                + " and mother_id is null and father_id is null"
                + " and birth_batch_id = ? and birth_cycle_id = ?",
            rabbitId,
            scenario.batchId(),
            scenario.cycleId()
        ));
        Assertions.assertEquals(0, count(
            "select waiting_count from weaning_records where id = ?",
            scenario.recordId()
        ));
    }

    @Test
    void rejectsDisabledAndWrongPurposeTargetCagesWithoutWrites() {
        Scenario scenario = scenario("bad_cages", 1, 1, 0);
        jdbc.update(
            "update cages set is_enabled = false where house_id = ? and id = ?",
            scenario.houseId(),
            scenario.firstTargetCage()
        );
        api.expectError(
            separationPath(scenario),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            separationBody(
                List.of(allocation(scenario.firstTargetCage(), 1, 1, 0)),
                requestId("disabled_cage"),
                null,
                null
            ),
            400,
            "目标笼位已停用"
        );

        jdbc.update(
            "update cages set is_enabled = true, status = '1' where house_id = ? and id = ?",
            scenario.houseId(),
            scenario.firstTargetCage()
        );
        api.expectError(
            separationPath(scenario),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            separationBody(
                List.of(allocation(scenario.firstTargetCage(), 1, 1, 0)),
                requestId("wrong_purpose_cage"),
                null,
                null
            ),
            400,
            "目标笼位不是商品兔笼位"
        );

        Assertions.assertEquals(1, count(
            "select waiting_count from weaning_records where id = ?", scenario.recordId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbits where birth_cycle_id = ?", scenario.cycleId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from weaning_record_allocations where weaning_record_id = ?",
            scenario.recordId()
        ));
    }

    @Test
    void replayReturnsOriginalResponseAndPayloadChangesConflict() {
        Scenario scenario = scenario("replay_payload", 3, 2, 1);
        long parentId = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.cages().get(1),
            "0", "0", "replay_parent"
        );
        String requestId = requestId("same_separation");
        List<Map<String, Object>> allocations = List.of(
            allocation(scenario.firstTargetCage(), 1, 1, 0),
            allocation(scenario.secondTargetCage(), 1, 0, 1)
        );

        JsonNode first = separate(scenario, allocations, requestId, null, null);
        JsonNode replay = separate(
            scenario,
            List.of(allocations.get(1), allocations.get(0)),
            requestId,
            null,
            null
        );

        Assertions.assertEquals(first.get("weaningRecordId"), replay.get("weaningRecordId"));
        Assertions.assertEquals(first.get("separatedCount"), replay.get("separatedCount"));
        Assertions.assertEquals(first.get("waitingCount"), replay.get("waitingCount"));
        Assertions.assertEquals(first.get("generatedRabbitIds"), replay.get("generatedRabbitIds"));
        Assertions.assertFalse(first.get("replayed").asBoolean());
        Assertions.assertTrue(replay.get("replayed").asBoolean());

        expectConflict(
            scenario,
            List.of(
                allocation(scenario.firstTargetCage(), 1, 0, 1),
                allocation(scenario.secondTargetCage(), 1, 0, 1)
            ),
            requestId,
            null,
            null
        );
        expectConflict(scenario, allocations, requestId, parentId, null);
        Assertions.assertEquals(2, count(
            "select count(*) from rabbits where birth_cycle_id = ?", scenario.cycleId()
        ));
        Assertions.assertEquals(1, count(
            "select waiting_count from weaning_records where id = ?", scenario.recordId()
        ));
    }

    private void expectParentError(
        Scenario scenario,
        Long motherId,
        Long fatherId,
        String message
    ) {
        api.expectError(
            separationPath(scenario),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            separationBody(
                List.of(allocation(scenario.firstTargetCage(), 1, 1, 0)),
                requestId("bad_parent"),
                motherId,
                fatherId
            ),
            400,
            message
        );
    }

    private void expectConflict(
        Scenario scenario,
        List<Map<String, Object>> allocations,
        String requestId,
        Long motherId,
        Long fatherId
    ) {
        api.expectError(
            separationPath(scenario),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            separationBody(allocations, requestId, motherId, fatherId),
            409,
            "requestId"
        );
    }

    private JsonNode separate(
        Scenario scenario,
        List<Map<String, Object>> allocations,
        String requestId,
        Long motherId,
        Long fatherId
    ) {
        return api.postOk(
            separationPath(scenario),
            scenario.owner().token,
            scenario.houseId(),
            separationBody(allocations, requestId, motherId, fatherId)
        );
    }

    private Map<String, Object> separationBody(
        List<Map<String, Object>> allocations,
        String requestId,
        Long motherId,
        Long fatherId
    ) {
        Map<String, Object> body = obj("allocations", allocations, "requestId", requestId);
        if (motherId != null) {
            body.put("motherRabbitId", motherId);
        }
        if (fatherId != null) {
            body.put("fatherRabbitId", fatherId);
        }
        return body;
    }

    private JsonNode pending(Scenario scenario) {
        JsonNode records = api.getOk(
            "/api/batches/" + scenario.batchId() + "/weaning-records",
            scenario.owner().token,
            scenario.houseId()
        );
        for (JsonNode record : records) {
            if (record.get("id").asLong() == scenario.recordId()) {
                return record;
            }
        }
        throw new AssertionError("pending weaning record not found");
    }

    private Map<String, Object> allocation(
        long cageId,
        int count,
        int maleCount,
        int femaleCount
    ) {
        return obj(
            "cageId", cageId,
            "count", count,
            "maleCount", maleCount,
            "femaleCount", femaleCount
        );
    }

    private Scenario scenario(String prefix, int kits, int maleCount, int femaleCount) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 10, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        // 建空批次，再由下面的待分笼入轨把母兔带进来（成员关系由生产周期派生）。
        // 建批时就带母兔会自动开一条待催情周期，那么待分笼就成了同批次内的
        // 第二条未结束周期，V44 起会被 409 拒掉。
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "UCI-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", List.of(),
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();
        long cycleId = api.postOk("/api/repro/cycles", owner.token, houseId, obj(
            "motherRabbitId", doeId,
            "batchId", batchId,
            "stage", "AWAIT_WEANING",
            "occurredAt", now(),
            "birthDate", now() - 25L * 24 * 3600 * 1000,
            "totalKits", kits,
            "liveKits", kits,
            "requestId", requestId(prefix + "_cycle")
        )).get("cycleId").asLong();
        long recordId = api.postOk(
            "/api/repro/cycles/" + cycleId + "/actions",
            owner.token,
            houseId,
            obj(
                "action", "WEANING",
                "occurredAt", now(),
                "weanedCount", kits,
                "maleCount", maleCount,
                "femaleCount", femaleCount,
                "avgWeaningWeight", 1.1,
                "requestId", requestId(prefix + "_wean")
            )
        ).get("weaningRecordId").asLong();
        return new Scenario(
            owner, houseId, batchId, cycleId, recordId, doeId,
            cages.get(8), cages.get(9), new ArrayList<>(cages)
        );
    }

    private String separationPath(Scenario scenario) {
        return "/api/batches/" + scenario.batchId() + "/weaning-records/"
            + scenario.recordId() + "/separation";
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private record Scenario(
        UserSession owner,
        long houseId,
        long batchId,
        long cycleId,
        long recordId,
        long doeId,
        long firstTargetCage,
        long secondTargetCage,
        List<Long> cages
    ) {
    }
}
