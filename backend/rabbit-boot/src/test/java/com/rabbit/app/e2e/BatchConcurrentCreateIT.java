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

/**
 * The mother row lock in BatchService serializes overlapping tag writes.
 * One rabbit may carry several active batch tags without duplicating its pipeline.
 */
public class BatchConcurrentCreateIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentCreatesCanTagOneMotherInTwoActiveBatches() throws Exception {
        UserSession owner = register("batch_concurrent_create");
        long houseId = createHouse(owner, "并发建批次兔舍", 1, 2, 1);
        long motherId = createRabbit(
            owner,
            houseId,
            cageIds(owner, houseId).get(0),
            "0",
            "0",
            "concurrent_create_mother"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<JsonNode>> futures = new ArrayList<Future<JsonNode>>();
            futures.add(executor.submit(() -> createBatchWhenReleased(
                owner,
                houseId,
                motherId,
                "CONCURRENT-A",
                ready,
                start
            )));
            futures.add(executor.submit(() -> createBatchWhenReleased(
                owner,
                houseId,
                motherId,
                "CONCURRENT-B",
                ready,
                start
            )));

            Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> codes = new ArrayList<Integer>(2);
            for (Future<JsonNode> future : futures) {
                codes.add(future.get(30, TimeUnit.SECONDS).path("code").asInt());
            }
            codes.sort(Integer::compareTo);
            Assertions.assertEquals(List.of(0, 0), codes);

            Assertions.assertEquals(
                2,
                jdbc.queryForObject(
                    "select count(*) from batch_rabbits where rabbit_id = ? and is_active = true",
                    Integer.class,
                    motherId
                )
            );
            Assertions.assertEquals(
                2,
                jdbc.queryForObject(
                    "select count(distinct br.batch_id) from batch_rabbits br "
                        + "where br.rabbit_id = ? and br.is_active = true",
                    Integer.class,
                    motherId
                )
            );
            Assertions.assertEquals(
                1,
                jdbc.queryForObject(
                    "select count(*) from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN' "
                        + "and stage <> 'AWAIT_WEANING'",
                    Integer.class,
                    motherId
                )
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void secondaryBatchTagsCanBeRemovedAndAddedAgain() {
        UserSession owner = register("batch_tag_remove");
        long houseId = createHouse(owner, "批次标签增删兔舍", 1, 2, 1);
        long rabbitId = createRabbit(
            owner,
            houseId,
            cageIds(owner, houseId).get(0),
            "0",
            "0",
            "batch_tag_mother"
        );
        long batchA = createEmptyBatch(owner, houseId, "TAG-A");
        long batchB = createEmptyBatch(owner, houseId, "TAG-B");

        addRabbitTag(owner, houseId, batchA, rabbitId, "add-a-1");
        addRabbitTag(owner, houseId, batchB, rabbitId, "add-b-1");
        Assertions.assertEquals(2, activeTagCount(rabbitId));

        String removeRequestId = requestId("remove-b");
        api.deleteOk(
            "/api/batches/" + batchB + "/members/" + rabbitId
                + "?requestId=" + removeRequestId,
            owner.token,
            houseId
        );
        api.deleteOk(
            "/api/batches/" + batchB + "/members/" + rabbitId
                + "?requestId=" + removeRequestId,
            owner.token,
            houseId
        );
        Assertions.assertEquals(1, activeTagCount(rabbitId));

        addRabbitTag(owner, houseId, batchB, rabbitId, "add-b-2");
        Assertions.assertEquals(2, activeTagCount(rabbitId));
        Assertions.assertEquals(
            3,
            jdbc.queryForObject(
                "select count(*) from batch_rabbits where rabbit_id = ?",
                Integer.class,
                rabbitId
            )
        );
    }

    private long createEmptyBatch(UserSession owner, long houseId, String code) {
        return api.postOk(
            "/api/batches",
            owner.token,
            houseId,
            obj(
                "batchCode", code + "-" + requestId("code").substring(0, 12),
                "femaleRabbitIds", List.of(),
                "requestId", requestId("batch-" + code)
            )
        ).path("id").asLong();
    }

    private void addRabbitTag(
        UserSession owner,
        long houseId,
        long batchId,
        long rabbitId,
        String requestSuffix
    ) {
        api.postOk(
            "/api/batches/" + batchId + "/members",
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(rabbitId),
                "requestId", requestId(requestSuffix)
            )
        );
    }

    private int activeTagCount(long rabbitId) {
        return jdbc.queryForObject(
            "select count(*) from batch_rabbits where rabbit_id = ? and is_active = true",
            Integer.class,
            rabbitId
        );
    }

    private JsonNode createBatchWhenReleased(
        UserSession owner,
        long houseId,
        long motherId,
        String suffix,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent batch create start timed out");
        }
        return api.postResponse(
            "/api/batches",
            owner.token,
            houseId,
            obj(
                "batchCode", suffix + "-" + requestId("code").substring(0, 12),
                "femaleRabbitIds", List.of(motherId),
                "requestId", requestId("batch_" + suffix)
            )
        );
    }
}
