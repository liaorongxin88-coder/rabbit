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
 * The mother row lock in BatchService must serialize overlapping batch creates.
 * Historical (inactive) batch_rabbits rows remain valid and are not constrained.
 */
public class BatchConcurrentCreateIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentCreatesCannotPutOneMotherInTwoActiveBatches() throws Exception {
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
            Assertions.assertEquals(List.of(0, 400), codes);

            Assertions.assertEquals(
                1,
                jdbc.queryForObject(
                    "select count(*) from batch_rabbits where rabbit_id = ? and is_active = true",
                    Integer.class,
                    motherId
                )
            );
            Assertions.assertEquals(
                1,
                jdbc.queryForObject(
                    "select count(distinct br.batch_id) from batch_rabbits br "
                        + "where br.rabbit_id = ? and br.is_active = true",
                    Integer.class,
                    motherId
                )
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
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
