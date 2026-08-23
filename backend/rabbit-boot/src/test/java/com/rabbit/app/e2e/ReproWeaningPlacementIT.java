package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/** Acceptance coverage for deferred weaning and later commodity-cage separation. */
public class ReproWeaningPlacementIT extends E2eTestSupport {

    private static final int CAGE_CAPACITY = 10;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void weaningCreatesOnlyPendingRecordAndIgnoresLegacyTargetCage() {
        Scenario scenario = nursingScenario("pending", 1, 4);
        long cycleId = scenario.cycleIds().get(0);
        long targetCage = scenario.spareCage();
        int beforeCageCount = count("select rabbit_count from cages where id = ?", targetCage);

        JsonNode result = wean(scenario, cycleId, 4, 2, 2, targetCage, "pending_wean");
        long recordId = result.get("weaningRecordId").asLong();

        Assertions.assertEquals(4, result.get("waitingCount").asInt());
        Assertions.assertEquals(0, result.get("generatedRabbitIds").size());
        Assertions.assertEquals(1, count("select count(*) from weaning_records where id = ?", recordId));
        Assertions.assertEquals(4, count("select waiting_count from weaning_records where id = ?", recordId));
        Assertions.assertEquals(2, count("select male_count from weaning_records where id = ?", recordId));
        Assertions.assertEquals(2, count("select female_count from weaning_records where id = ?", recordId));
        Assertions.assertEquals(0, count("select count(*) from rabbits where birth_cycle_id = ?", cycleId));
        Assertions.assertEquals(0, count("select count(*) from batch_rabbits where batch_id = ? and batch_role = 'fattening'", scenario.batchId()));
        Assertions.assertEquals(0, count("select count(*) from work_tasks where batch_id = ? and task_type = 'SALE_READY'", scenario.batchId()));
        Assertions.assertEquals(beforeCageCount, count("select rabbit_count from cages where id = ?", targetCage));
    }

    @Test
    void partialThenFinalSeparationDrainsWaitingCountWithLineageGenderLinksAndTasks() {
        Scenario scenario = nursingScenario("partial", 1, 7);
        long cycleId = scenario.cycleIds().get(0);
        long motherId = scenario.doeIds().get(0);
        long sireId = createRabbit(
            scenario.owner(), scenario.houseId(), scenario.spareCage(), "0", "1", "partial_sire"
        );
        jdbc.update("update breeding_cycles set male_rabbit_id = ? where id = ?", sireId, cycleId);

        long recordId = wean(scenario, cycleId, 7, 3, 4, scenario.spareCage(), "partial_wean")
            .get("weaningRecordId").asLong();
        JsonNode first = separate(scenario, recordId, List.of(allocation(scenario.spareCage(), 3)), "partial_first");
        Assertions.assertEquals(3, first.get("separatedCount").asInt());
        Assertions.assertEquals(4, first.get("waitingCount").asInt());
        Assertions.assertEquals(3, count("select count(*) from rabbits where birth_cycle_id = ?", cycleId));
        Assertions.assertEquals(4, count("select waiting_count from weaning_records where id = ?", recordId));

        JsonNode finalResult = separate(
            scenario, recordId, List.of(allocation(scenario.spareCage(), 4)), "partial_final"
        );
        Assertions.assertEquals(4, finalResult.get("separatedCount").asInt());
        Assertions.assertEquals(0, finalResult.get("waitingCount").asInt());
        Assertions.assertEquals(7, count("select count(*) from rabbits where birth_cycle_id = ?", cycleId));
        Assertions.assertEquals(3, count(
            "select count(*) from rabbits where birth_cycle_id = ? and gender = '1'", cycleId
        ));
        Assertions.assertEquals(4, count(
            "select count(*) from rabbits where birth_cycle_id = ? and gender = '0'", cycleId
        ));
        Assertions.assertEquals(7, count(
            "select count(*) from rabbits where birth_cycle_id = ? and mother_id = ? and father_id = ?",
            cycleId, motherId, sireId
        ));
        Assertions.assertEquals(7, count(
            "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'fattening' and is_active = true",
            scenario.batchId()
        ));
        Assertions.assertEquals(7, count(
            "select count(*) from rabbit_status_history where related_record_id = ? and related_record_table = 'weaning_records'",
            recordId
        ));
        Assertions.assertEquals(7, count(
            "select count(*) from work_tasks where batch_id = ? and task_type = 'SALE_READY' and status = 'PENDING'",
            scenario.batchId()
        ));
        Assertions.assertEquals(7, count(
            "select alloc_count from weaning_record_allocations where weaning_record_id = ? and cage_id = ?",
            recordId, scenario.spareCage()
        ));
        assertCageCountMatchesReality(scenario.spareCage(), 8);
    }

    @Test
    void capacityFailureLeavesPendingRecordAndNoInventory() {
        Scenario scenario = nursingScenario("capacity", 1, 4);
        long targetCage = scenario.spareCage();
        for (int index = 0; index < 8; index++) {
            createRabbit(
                scenario.owner(), scenario.houseId(), targetCage, "2", index % 2 == 0 ? "0" : "1",
                "capacity_occupied_" + index
            );
        }
        long recordId = wean(
            scenario, scenario.cycleIds().get(0), 4, 2, 2, targetCage, "capacity_wean"
        ).get("weaningRecordId").asLong();

        api.expectError(
            separationPath(scenario.batchId(), recordId),
            HttpMethod.POST,
            scenario.owner().token,
            scenario.houseId(),
            obj("allocations", List.of(allocation(targetCage, 4)), "requestId", requestId("capacity_separate")),
            400,
            "容量不足"
        );

        Assertions.assertEquals(4, count("select waiting_count from weaning_records where id = ?", recordId));
        Assertions.assertEquals(0, count("select count(*) from rabbits where birth_cycle_id = ?", scenario.cycleIds().get(0)));
        assertCageCountMatchesReality(targetCage, 8);
    }

    @Test
    void concurrentSeparationsAndRepeatedRequestIdNeverDuplicateInventory() throws Exception {
        Scenario scenario = nursingScenario("race", 2, 6);
        long firstRecord = wean(
            scenario, scenario.cycleIds().get(0), 6, 3, 3, scenario.spareCage(), "race_wean_one"
        ).get("weaningRecordId").asLong();
        long secondRecord = wean(
            scenario, scenario.cycleIds().get(1), 6, 3, 3, scenario.spareCage(), "race_wean_two"
        ).get("weaningRecordId").asLong();

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> jobs = List.of(
            separationJob(scenario, firstRecord, "race_first", start),
            separationJob(scenario, secondRecord, "race_second", start)
        );
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> job : jobs) {
            futures.add(pool.submit(job));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> future : futures) {
            if (future.get(60, TimeUnit.SECONDS) == 0) {
                succeeded++;
            }
        }
        pool.shutdown();

        Assertions.assertEquals(1, succeeded, "容量 10 时两次各 6 只只能成功一次");
        assertCageCountMatchesReality(scenario.spareCage(), 6);

        long successfulRecord = count("select waiting_count from weaning_records where id = ?", firstRecord) == 0
            ? firstRecord
            : secondRecord;
        String duplicateRequestId = requestId("separation_idempotent");
        Scenario idempotent = nursingScenario("idempotent", 1, 2);
        long idempotentRecord = wean(
            idempotent, idempotent.cycleIds().get(0), 2, 1, 1, idempotent.spareCage(), "idempotent_wean"
        ).get("weaningRecordId").asLong();
        JsonNode first = api.postOk(
            separationPath(idempotent.batchId(), idempotentRecord),
            idempotent.owner().token,
            idempotent.houseId(),
            obj("allocations", List.of(allocation(idempotent.spareCage(), 2)), "requestId", duplicateRequestId)
        );
        JsonNode replay = api.postOk(
            separationPath(idempotent.batchId(), idempotentRecord),
            idempotent.owner().token,
            idempotent.houseId(),
            obj("allocations", List.of(allocation(idempotent.spareCage(), 2)), "requestId", duplicateRequestId)
        );
        Assertions.assertEquals(2, first.get("separatedCount").asInt());
        Assertions.assertTrue(replay.get("replayed").asBoolean());
        Assertions.assertEquals(2, count(
            "select count(*) from rabbits where birth_cycle_id = ?", idempotent.cycleIds().get(0)
        ));
        Assertions.assertEquals(0, count("select waiting_count from weaning_records where id = ?", idempotentRecord));
        Assertions.assertTrue(successfulRecord == firstRecord || successfulRecord == secondRecord);
    }

    @Test
    void separationCannotCrossHouseBoundaryAndZeroWeaningRemainsHistorical() {
        Scenario first = nursingScenario("scope_first", 1, 3);
        Scenario second = nursingScenario("scope_second", 1, 3);
        long recordId = wean(first, first.cycleIds().get(0), 3, 1, 2, first.spareCage(), "scope_wean")
            .get("weaningRecordId").asLong();

        api.expectError(
            separationPath(first.batchId(), recordId),
            HttpMethod.POST,
            second.owner().token,
            second.houseId(),
            obj("allocations", List.of(allocation(second.spareCage(), 1)), "requestId", requestId("scope_cross")),
            403,
            "权限"
        );

        Scenario zero = nursingScenario("zero", 1, 0);
        JsonNode result = wean(zero, zero.cycleIds().get(0), 0, 0, 0, zero.spareCage(), "zero_wean");
        Assertions.assertEquals(0, result.get("waitingCount").asInt());
        Assertions.assertEquals(1, count(
            "select count(*) from weaning_records where breeding_cycle_id = ? and weaning_count = 0 and waiting_count = 0",
            zero.cycleIds().get(0)
        ));
        Assertions.assertEquals(0, count("select count(*) from rabbits where birth_cycle_id = ?", zero.cycleIds().get(0)));
    }

    private Callable<Integer> separationJob(
        Scenario scenario,
        long recordId,
        String prefix,
        CountDownLatch start
    ) {
        return () -> {
            start.await();
            return api.postResponse(
                separationPath(scenario.batchId(), recordId),
                scenario.owner().token,
                scenario.houseId(),
                obj("allocations", List.of(allocation(scenario.spareCage(), 6)), "requestId", requestId(prefix))
            ).get("code").asInt();
        };
    }

    private JsonNode wean(
        Scenario scenario,
        long cycleId,
        int count,
        int maleCount,
        int femaleCount,
        long ignoredTargetCage,
        String prefix
    ) {
        return api.postOk(
            "/api/repro/cycles/" + cycleId + "/actions",
            scenario.owner().token,
            scenario.houseId(),
            obj(
                "action", "WEANING",
                "occurredAt", now(),
                "weanedCount", count,
                "maleCount", maleCount,
                "femaleCount", femaleCount,
                "targetCageId", ignoredTargetCage,
                "avgWeaningWeight", 1.1,
                "requestId", requestId(prefix)
            )
        );
    }

    private JsonNode separate(
        Scenario scenario,
        long recordId,
        List<java.util.Map<String, Object>> allocations,
        String prefix
    ) {
        return api.postOk(
            separationPath(scenario.batchId(), recordId),
            scenario.owner().token,
            scenario.houseId(),
            obj("allocations", allocations, "requestId", requestId(prefix))
        );
    }

    private java.util.Map<String, Object> allocation(long cageId, int count) {
        return obj("cageId", cageId, "count", count);
    }

    private String separationPath(long batchId, long recordId) {
        return "/api/batches/" + batchId + "/weaning-records/" + recordId + "/separation";
    }

    private void assertCageCountMatchesReality(long cageId, int expected) {
        Assertions.assertEquals(expected, count("select rabbit_count from cages where id = ?", cageId));
        Assertions.assertEquals(expected, count(
            "select count(*) from rabbits where cage_id = ? and is_active = 1", cageId
        ));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Scenario nursingScenario(String prefix, int doeCount, int kitsPerDoe) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, doeCount * 4 + 4, 1);
        List<Long> cages = cageIds(owner, houseId);

        List<Long> does = new ArrayList<>();
        for (int index = 0; index < doeCount; index++) {
            does.add(createRabbit(owner, houseId, cages.get(index), "0", "0", prefix + "_doe" + index));
        }
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "WP-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", does,
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();

        List<Long> cycles = new ArrayList<>();
        for (int index = 0; index < doeCount; index++) {
            cycles.add(api.postOk("/api/repro/cycles", owner.token, houseId, obj(
                "motherRabbitId", does.get(index),
                "batchId", batchId,
                "stage", "AWAIT_WEANING",
                "occurredAt", now(),
                "birthDate", now() - 25L * 24 * 3600 * 1000,
                "totalKits", kitsPerDoe,
                "liveKits", kitsPerDoe,
                "requestId", requestId(prefix + "_cycle" + index)
            )).get("cycleId").asLong());
        }
        return new Scenario(owner, houseId, batchId, does, cycles, cages.get(cages.size() - 1));
    }

    private record Scenario(
        UserSession owner,
        long houseId,
        long batchId,
        List<Long> doeIds,
        List<Long> cycleIds,
        long spareCage
    ) {
    }
}
