package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RabbitStagesAndCageConcurrencyIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsPersistValidatedStagesAndExposeActualBreedingGender() {
        UserSession owner = register("rabbit_stage");
        long houseId = createHouse(owner, "阶段录入兔舍", 1, 2, 1);
        long cageId = cageIds(owner, houseId).get(0);

        JsonNode rabbit = api.postOk("/api/rabbits", owner.token, houseId, obj(
                "cageId", cageId,
                "type", "0",
                "gender", "0",
                "growthStage", "MATURE",
                // doe-breeding-v2：种母兔的阶段改由生产流程维护，录入时给的是生产阶段。
                "reproStage", "AWAIT_PALPATION",
                "matingDate", now(),
                "arrivalMethod", "1",
                "arrivalDate", now(),
                "requestId", requestId("stage_create")
        ));

        Assertions.assertEquals("MATURE", rabbit.get("growthStage").asText());
        // 旧的繁殖阶段字段不再被写入：这只母兔的阶段以生产流程为准。
        Assertions.assertTrue(
                rabbit.get("reproductiveStage") == null || rabbit.get("reproductiveStage").isNull(),
                "种母兔不应再写入旧的繁殖阶段字段"
        );
        long doeId = rabbit.get("id").asLong();
        // 入轨与录入必须同事务：兔子在栏就一定有周期和待办，不存在“录进来却进不了流程”。
        Assertions.assertEquals(
                "AWAIT_PALPATION",
                jdbc.queryForObject(
                        "select stage from breeding_cycles where house_id = ? and mother_rabbit_id = ? and lifecycle = 'OPEN'",
                        String.class, houseId, doeId
                )
        );
        Assertions.assertEquals(
                "AWAIT_PALPATION",
                jdbc.queryForObject(
                        "select current_stage from rabbits where id = ?", String.class, doeId
                )
        );
        Assertions.assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from work_tasks where house_id = ? and rabbit_id = ? and status = 'PENDING'",
                        Integer.class, houseId, doeId
                )
        );
        JsonNode cages = api.getOk("/api/cages", owner.token, houseId);
        Assertions.assertEquals(
                "0",
                cageById(cages, cageId).get("breedingOccupantGender").asText()
        );
        Assertions.assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from rabbit_status_history "
                                + "where house_id = ? and rabbit_id = ? and reason like '录入兔子%'",
                        Integer.class,
                        houseId,
                        rabbit.get("id").asLong()
                )
        );

        // 手工改种母兔的繁殖阶段会被拒：否则人手写一个、状态机写另一个，两套阶段并存，
        // 正是 recvsrp9E2dqvB「阶段与批次不对应」的复发路径。
        api.expectError("/api/rabbits/" + doeId, org.springframework.http.HttpMethod.PUT,
                owner.token, houseId, obj(
                "growthStage", "MATURE",
                "reproductiveStage", "EMPTY",
                "requestId", requestId("stage_update")
        ), 400, "种母兔的繁育阶段由生产流程维护");

        api.expectError("/api/rabbits", org.springframework.http.HttpMethod.POST, owner.token, houseId, obj(
                "cageId", cageIds(owner, houseId).get(1),
                "type", "2",
                "gender", "0",
                "growthStage", "GROWING",
                "reproductiveStage", "PREGNANT",
                "requestId", requestId("commodity_stage")
        ), 400, "商品兔不能录入繁殖阶段");
    }

    @Test
    void concurrentCreatesKeepSingleBreedingCageAndCountConsistent() throws Exception {
        UserSession owner = register("rabbit_create_concurrent");
        long houseId = createHouse(owner, "并发入笼兔舍", 1, 2, 1);
        long cageId = cageIds(owner, houseId).get(0);

        List<Integer> codes = concurrently(
                () -> createRabbitWhenReleased(owner, houseId, cageId, "0", "create_a"),
                () -> createRabbitWhenReleased(owner, houseId, cageId, "0", "create_b")
        );

        Assertions.assertEquals(List.of(0, 409), codes);
        assertCageMatchesActiveRows(houseId, cageId, 1);
    }

    @Test
    void concurrentMovesKeepSingleBreedingCageAndAllCountsConsistent() throws Exception {
        UserSession owner = register("rabbit_move_concurrent");
        long houseId = createHouse(owner, "并发换笼兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long targetCageId = cages.get(0);
        long firstRabbitId = createRabbit(owner, houseId, cages.get(1), "0", "0", "move_doe");
        long secondRabbitId = createRabbit(owner, houseId, cages.get(2), "0", "1", "move_buck");

        List<Integer> codes = concurrently(
                () -> moveRabbitWhenReleased(owner, houseId, firstRabbitId, targetCageId, "move_a"),
                () -> moveRabbitWhenReleased(owner, houseId, secondRabbitId, targetCageId, "move_b")
        );

        Assertions.assertEquals(List.of(0, 409), codes);
        assertCageMatchesActiveRows(houseId, targetCageId, 1);
        int zeroCountCages = jdbc.queryForObject(
                "select count(*) from cages where house_id = ? and id in (?, ?) and rabbit_count = 0",
                Integer.class,
                houseId,
                cages.get(1),
                cages.get(2)
        );
        Assertions.assertEquals(1, zeroCountCages);
        int oneCountCages = jdbc.queryForObject(
                "select count(*) from cages where house_id = ? and id in (?, ?) and rabbit_count = 1",
                Integer.class,
                houseId,
                cages.get(1),
                cages.get(2)
        );
        Assertions.assertEquals(1, oneCountCages);
    }

    private List<Integer> concurrently(ConcurrentRequest first, ConcurrentRequest second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> awaitThenExecute(first, ready, start)));
            futures.add(executor.submit(() -> awaitThenExecute(second, ready, start)));
            Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                codes.add(future.get(30, TimeUnit.SECONDS).path("code").asInt());
            }
            codes.sort(Integer::compareTo);
            return codes;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private JsonNode awaitThenExecute(ConcurrentRequest request, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("concurrent rabbit request start timed out");
        }
        return request.execute();
    }

    private JsonNode createRabbitWhenReleased(
            UserSession owner,
            long houseId,
            long cageId,
            String gender,
            String suffix
    ) {
        return api.postResponse("/api/rabbits", owner.token, houseId, obj(
                "cageId", cageId,
                "type", "0",
                "gender", gender,
                "growthStage", "MATURE",
                // 本用例只关心笼位并发，不再携带旧的繁殖阶段字段。
                "arrivalMethod", "1",
                "arrivalDate", now(),
                "breed", suffix,
                "requestId", requestId(suffix)
        ));
    }

    private JsonNode moveRabbitWhenReleased(
            UserSession owner,
            long houseId,
            long rabbitId,
            long targetCageId,
            String suffix
    ) {
        return api.putResponse("/api/rabbits/" + rabbitId, owner.token, houseId, obj(
                "cageId", targetCageId,
                "requestId", requestId(suffix)
        ));
    }

    private void assertCageMatchesActiveRows(long houseId, long cageId, int expected) {
        Assertions.assertEquals(
                expected,
                jdbc.queryForObject(
                        "select rabbit_count from cages where house_id = ? and id = ?",
                        Integer.class,
                        houseId,
                        cageId
                )
        );
        Assertions.assertEquals(
                expected,
                jdbc.queryForObject(
                        "select count(*) from rabbits where house_id = ? and cage_id = ? and is_active = true",
                        Integer.class,
                        houseId,
                        cageId
                )
        );
    }

    private JsonNode cageById(JsonNode cages, long cageId) {
        for (JsonNode cage : cages) {
            if (cage.path("id").asLong() == cageId) {
                return cage;
            }
        }
        throw new AssertionError("cage not found: " + cageId);
    }

    @FunctionalInterface
    private interface ConcurrentRequest {
        JsonNode execute() throws Exception;
    }
}
