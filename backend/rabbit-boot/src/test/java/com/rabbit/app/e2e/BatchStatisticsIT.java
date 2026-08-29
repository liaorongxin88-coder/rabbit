package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchStatisticsIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private long nextFixtureCycleId = 1L;

    @Test
    void returnsOnlyTheFourBatchScopedCounts() {
        UserSession owner = register("batch_statistics");
        long houseId = createHouse(owner, "批次统计兔舍", 1, 1, 1);
        long rabbitId = createRabbit(
            owner, houseId, cageIds(owner, houseId).get(0), "0", "0", "stats_doe"
        );
        long targetBatchId = createBatch(owner, houseId, "STATS-A");
        long otherBatchId = createBatch(owner, houseId, "STATS-B");

        // SQL fixture: two weaned litters belong to batch A.
        insertLitter(houseId, targetBatchId, rabbitId, 8, 7, 6);
        insertLitter(houseId, targetBatchId, rabbitId, 5, 4, 4);
        // The same mother has a litter in batch B. It must not affect batch A.
        insertLitter(houseId, otherBatchId, rabbitId, 99, 98, 88);

        JsonNode statistics = api.getOk(
            "/api/batches/" + targetBatchId + "/statistics", owner.token, houseId
        );

        assertEquals(4, statistics.size());
        assertTrue(statistics.has("totalLitters"));
        assertTrue(statistics.has("totalKits"));
        assertTrue(statistics.has("totalLiveKits"));
        assertTrue(statistics.has("totalWeaned"));
        assertStatistics(statistics, 2, 13, 11, 10);
    }

    @Test
    void returnsZeroCountsWhenTheBatchHasNoProductionRecords() {
        UserSession owner = register("batch_statistics_empty");
        long houseId = createHouse(owner, "空批次统计兔舍", 1, 1, 1);
        long batchId = createBatch(owner, houseId, "STATS-EMPTY");

        JsonNode statistics = api.getOk(
            "/api/batches/" + batchId + "/statistics", owner.token, houseId
        );

        assertEquals(4, statistics.size());
        assertStatistics(statistics, 0, 0, 0, 0);
    }

    @Test
    void requiresBothHouseAccessAndBatchOwnership() {
        UserSession owner = register("batch_statistics_owner");
        UserSession outsider = register("batch_statistics_outsider");
        long sourceHouseId = createHouse(owner, "统计来源兔舍", 1, 1, 1);
        long otherHouseId = createHouse(owner, "统计目标兔舍", 1, 1, 1);
        long batchId = createBatch(owner, sourceHouseId, "STATS-ISOLATED");

        api.expectError(
            "/api/batches/" + batchId + "/statistics",
            HttpMethod.GET,
            owner.token,
            otherHouseId,
            null,
            404,
            "批次不存在"
        );
        api.expectError(
            "/api/batches/" + batchId + "/statistics",
            HttpMethod.GET,
            outsider.token,
            sourceHouseId,
            null,
            403,
            "无兔场权限"
        );
    }

    private long createBatch(UserSession owner, long houseId, String batchCode) {
        return api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", batchCode,
            "femaleRabbitIds", java.util.List.of(),
            "requestId", requestId("batch_statistics")
        )).get("id").asLong();
    }

    private void insertLitter(
        long houseId,
        long batchId,
        long rabbitId,
        int totalKits,
        int liveKits,
        int weanedCount
    ) {
        jdbc.update(
            "insert into litters (house_id, cycle_id, batch_id, mother_rabbit_id, birth_date, "
                + "total_kits, live_kits, kept_kits, current_nursing, status, weaning_date, "
                + "weaned_count, request_id, create_by, update_by) "
                + "values (?, ?, ?, ?, now(), ?, ?, ?, 0, 'WEANED', now(), ?, ?, 'test', 'test')",
            houseId,
            nextFixtureCycleId++,
            batchId,
            rabbitId,
            totalKits,
            liveKits,
            liveKits,
            weanedCount,
            requestId("batch_statistics_litter")
        );
    }

    private void assertStatistics(
        JsonNode statistics,
        int totalLitters,
        int totalKits,
        int totalLiveKits,
        int totalWeaned
    ) {
        assertEquals(totalLitters, statistics.get("totalLitters").asInt());
        assertEquals(totalKits, statistics.get("totalKits").asInt());
        assertEquals(totalLiveKits, statistics.get("totalLiveKits").asInt());
        assertEquals(totalWeaned, statistics.get("totalWeaned").asInt());
        assertFalse(statistics.get("totalLitters").isNull());
        assertFalse(statistics.get("totalKits").isNull());
        assertFalse(statistics.get("totalLiveKits").isNull());
        assertFalse(statistics.get("totalWeaned").isNull());
    }
}
