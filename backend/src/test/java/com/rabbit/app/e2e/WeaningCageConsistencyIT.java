package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class WeaningCageConsistencyIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void specifiedCageOverCapacityRollsBackEveryWeaningSideEffect() {
        NursingScenario scenario = prepareNursingScenario("weaning_capacity", 1, 3, 6);
        long motherId = scenario.motherIds.get(0);
        long targetCageId = scenario.cageIds.get(2);
        for (int i = 0; i < 8; i++) {
            createRabbit(
                scenario.owner,
                scenario.houseId,
                targetCageId,
                "2",
                i % 2 == 0 ? "0" : "1",
                "existing_commodity_" + i
            );
        }

        api.expectError(
            "/api/batches/" + scenario.batchId + "/weaning",
            HttpMethod.POST,
            scenario.owner.token,
            scenario.houseId,
            obj(
                "rabbitId", motherId,
                "weaningDate", oneMinuteAgo(),
                "weaningCount", 3,
                "maleCount", 2,
                "femaleCount", 1,
                "targetCageId", targetCageId,
                "avgWeight", 1.1,
                "requestId", requestId("over_capacity")
            ),
            400,
            "容量不足"
        );

        assertCageMatchesActiveRabbits(targetCageId, 8);
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from weaning_records where batch_id = ? and rabbit_id = ?",
                Integer.class,
                scenario.batchId,
                motherId
            )
        );
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from rabbits where mother_id = ?",
                Integer.class,
                motherId
            )
        );
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from weaning_record_allocations a " +
                "join weaning_records w on w.id = a.weaning_record_id where w.batch_id = ?",
                Integer.class,
                scenario.batchId
            )
        );
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'fattening'",
                Integer.class,
                scenario.batchId
            )
        );
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from rabbit_status_history where batch_id = ? and reason = '断奶生成仔兔'",
                Integer.class,
                scenario.batchId
            )
        );
        Assertions.assertEquals(
            1,
            jdbc.queryForObject(
                "select count(*) from breeding_cycles where batch_id = ? and mother_rabbit_id = ? " +
                "and status = '哺乳中' and current_nursing_kits = 3 and closed_at is null",
                Integer.class,
                scenario.batchId,
                motherId
            )
        );
        Assertions.assertEquals(
            0,
            jdbc.queryForObject(
                "select coalesce(max(total_weaned), 0) from breeding_performance where house_id = ? and rabbit_id = ?",
                Integer.class,
                scenario.houseId,
                motherId
            )
        );
    }

    @Test
    void concurrentWeaningsToSameCagePreserveCapacityAndExactCount() throws Exception {
        NursingScenario scenario = prepareNursingScenario("weaning_same_cage", 2, 7, 6);
        long targetCageId = scenario.cageIds.get(3);

        List<JsonNode> responses = postConcurrentWeanings(
            scenario,
            7,
            targetCageId,
            List.of(targetCageId)
        );

        int successCount = 0;
        int capacityFailureCount = 0;
        for (JsonNode response : responses) {
            if (response.path("code").asInt() == 0) {
                successCount++;
            } else if (
                response.path("code").asInt() == 400 &&
                response.path("message").asText().contains("容量不足")
            ) {
                capacityFailureCount++;
            } else {
                Assertions.fail("unexpected response: " + response);
            }
        }
        Assertions.assertEquals(1, successCount);
        Assertions.assertEquals(1, capacityFailureCount);
        assertCageMatchesActiveRabbits(targetCageId, 7);
        assertSuccessfulWeaningTotals(scenario, 1, 7);
        Assertions.assertEquals(
            1,
            jdbc.queryForObject(
                "select count(*) from breeding_cycles where batch_id = ? and status = '哺乳中' " +
                "and current_nursing_kits = 7 and closed_at is null",
                Integer.class,
                scenario.batchId
            )
        );
    }

    @Test
    void concurrentAutomaticAllocationKeepsEveryCageWithinCapacity() throws Exception {
        NursingScenario scenario = prepareNursingScenario("weaning_auto", 2, 8, 5);
        List<Long> commodityCageIds = List.of(
            scenario.cageIds.get(3),
            scenario.cageIds.get(4)
        );

        List<JsonNode> responses = postConcurrentWeanings(
            scenario,
            8,
            null,
            commodityCageIds
        );

        for (JsonNode response : responses) {
            Assertions.assertEquals(0, response.path("code").asInt(), response.toString());
        }
        int totalCageCount = 0;
        for (Long cageId : commodityCageIds) {
            int cageCount = jdbc.queryForObject(
                "select rabbit_count from cages where id = ?",
                Integer.class,
                cageId
            );
            Assertions.assertTrue(cageCount <= 10, "commodity cage must not exceed capacity");
            assertCageMatchesActiveRabbits(cageId, cageCount);
            totalCageCount += cageCount;
        }
        Assertions.assertEquals(16, totalCageCount);
        assertSuccessfulWeaningTotals(scenario, 2, 16);
    }

    private NursingScenario prepareNursingScenario(
        String prefix,
        int motherCount,
        int liveKits,
        int cageCount
    ) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, cageCount, 1);
        List<Long> cageIds = cageIds(owner, houseId);
        api.putOk(
            "/api/settings",
            owner.token,
            null,
            obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 0,
                "replacementDays", 0,
                "remark", "weaning cage consistency",
                "requestId", requestId(prefix + "_settings")
            )
        );

        List<Long> motherIds = new ArrayList<Long>();
        for (int i = 0; i < motherCount; i++) {
            motherIds.add(
                createRabbit(owner, houseId, cageIds.get(i), "0", "0", prefix + "_mother_" + i)
            );
        }
        long fatherId = createRabbit(
            owner,
            houseId,
            cageIds.get(motherCount),
            "0",
            "1",
            prefix + "_father"
        );
        JsonNode batch = api.postOk(
            "/api/batches",
            owner.token,
            houseId,
            obj(
                "batchCode", "W" + requestId(prefix).substring(0, 8),
                "femaleRabbitIds", motherIds,
                "remark", "weaning cage consistency",
                "requestId", requestId(prefix + "_batch")
            )
        );
        long batchId = batch.get("id").asLong();
        api.postOk(
            "/api/batches/" + batchId + "/aphrodisiac/start",
            owner.token,
            houseId,
            obj("rabbitIds", motherIds, "requestId", requestId(prefix + "_aph_start"))
        );
        api.postOk(
            "/api/batches/" + batchId + "/aphrodisiac/finish",
            owner.token,
            houseId,
            obj("rabbitIds", motherIds, "requestId", requestId(prefix + "_aph_finish"))
        );
        for (Long motherId : motherIds) {
            api.postOk(
                "/api/batches/" + batchId + "/mating",
                owner.token,
                houseId,
                obj(
                    "femaleRabbitId", motherId,
                    "maleRabbitId", fatherId,
                    "matingDate", oneMinuteAgo(),
                    "requestId", requestId(prefix + "_mating")
                )
            );
            api.postOk(
                "/api/batches/" + batchId + "/pregnancy-check",
                owner.token,
                houseId,
                obj(
                    "rabbitId", motherId,
                    "checkDate", oneMinuteAgo(),
                    "result", "怀孕",
                    "requestId", requestId(prefix + "_pregnancy")
                )
            );
            api.postOk(
                "/api/batches/" + batchId + "/prepartum/finish",
                owner.token,
                houseId,
                obj(
                    "rabbitId", motherId,
                    "actionDate", oneMinuteAgo(),
                    "requestId", requestId(prefix + "_prepartum")
                )
            );
            api.postOk(
                "/api/batches/" + batchId + "/parturition",
                owner.token,
                houseId,
                obj(
                    "rabbitId", motherId,
                    "birthDate", oneMinuteAgo(),
                    "totalKits", liveKits,
                    "liveKits", liveKits,
                    "failed", false,
                    "requestId", requestId(prefix + "_parturition")
                )
            );
        }
        return new NursingScenario(owner, houseId, batchId, cageIds, motherIds);
    }

    private List<JsonNode> postConcurrentWeanings(
        NursingScenario scenario,
        int weaningCount,
        Long targetCageId,
        List<Long> cageIdsToLock
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (
            Connection blocker = dataSource.getConnection();
            PreparedStatement statement = blocker.prepareStatement(
                "select id from cages where id in (" +
                String.join(",", Collections.nCopies(cageIdsToLock.size(), "?")) +
                ") order by id for update"
            )
        ) {
            blocker.setAutoCommit(false);
            for (int i = 0; i < cageIdsToLock.size(); i++) {
                statement.setLong(i + 1, cageIdsToLock.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    // Consume every locked row before starting the competing requests.
                }
            }

            List<Future<JsonNode>> futures = new ArrayList<Future<JsonNode>>();
            for (Long motherId : scenario.motherIds) {
                futures.add(
                    executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("concurrent weaning start timed out");
                        }
                        java.util.Map<String, Object> body = obj(
                            "rabbitId", motherId,
                            "weaningDate", oneMinuteAgo(),
                            "weaningCount", weaningCount,
                            "maleCount", weaningCount / 2,
                            "femaleCount", weaningCount - weaningCount / 2,
                            "avgWeight", 1.1,
                            "requestId", requestId("parallel_weaning")
                        );
                        if (targetCageId != null) {
                            body.put("targetCageId", targetCageId);
                        }
                        return api.postResponse(
                            "/api/batches/" + scenario.batchId + "/weaning",
                            scenario.owner.token,
                            scenario.houseId,
                            body
                        );
                    })
                );
            }
            Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            Thread.sleep(500L);
            blocker.commit();

            List<JsonNode> responses = new ArrayList<JsonNode>();
            for (Future<JsonNode> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void assertCageMatchesActiveRabbits(long cageId, int expectedCount) {
        Assertions.assertEquals(
            expectedCount,
            jdbc.queryForObject(
                "select rabbit_count from cages where id = ?",
                Integer.class,
                cageId
            )
        );
        Assertions.assertEquals(
            expectedCount,
            jdbc.queryForObject(
                "select count(*) from rabbits where cage_id = ? and is_active = true",
                Integer.class,
                cageId
            )
        );
    }

    private void assertSuccessfulWeaningTotals(
        NursingScenario scenario,
        int expectedRecords,
        int expectedKits
    ) {
        Assertions.assertEquals(
            expectedRecords,
            jdbc.queryForObject(
                "select count(*) from weaning_records where batch_id = ?",
                Integer.class,
                scenario.batchId
            )
        );
        Assertions.assertEquals(
            expectedKits,
            jdbc.queryForObject(
                "select count(*) from rabbits where birth_batch_id = ? and is_active = true",
                Integer.class,
                scenario.batchId
            )
        );
        Assertions.assertEquals(
            expectedKits,
            jdbc.queryForObject(
                "select coalesce(sum(a.alloc_count), 0) from weaning_record_allocations a " +
                "join weaning_records w on w.id = a.weaning_record_id where w.batch_id = ?",
                Integer.class,
                scenario.batchId
            )
        );
        Assertions.assertEquals(
            expectedKits,
            jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'fattening' and is_active = true",
                Integer.class,
                scenario.batchId
            )
        );
    }

    private static class NursingScenario {
        private final UserSession owner;
        private final long houseId;
        private final long batchId;
        private final List<Long> cageIds;
        private final List<Long> motherIds;

        private NursingScenario(
            UserSession owner,
            long houseId,
            long batchId,
            List<Long> cageIds,
            List<Long> motherIds
        ) {
            this.owner = owner;
            this.houseId = houseId;
            this.batchId = batchId;
            this.cageIds = cageIds;
            this.motherIds = motherIds;
        }
    }
}
