package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchStatisticsIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private long nextFixtureCycleId = 1L;

    @Test
    void returnsTheOrderedMetricContractAndLegacyBatchCounts() {
        UserSession owner = register("batch_statistics");
        String houseName = "批次统计兔舍";
        String batchCode = "STATS-A";
        long houseId = createHouse(owner, houseName, 1, 1, 1);
        long rabbitId = createRabbit(
            owner, houseId, cageIds(owner, houseId).get(0), "0", "0", "stats_doe"
        );
        long targetBatchId = createBatch(owner, houseId, batchCode);
        long otherBatchId = createBatch(owner, houseId, "STATS-B");

        // SQL fixture: two weaned litters belong to batch A.
        insertLitter(houseId, targetBatchId, rabbitId, 8, 7, 6);
        insertLitter(houseId, targetBatchId, rabbitId, 5, 4, 4);
        // The same mother has a litter in batch B. It must not affect batch A.
        insertLitter(houseId, otherBatchId, rabbitId, 99, 98, 88);

        JsonNode statistics = api.getOk(
            "/api/batches/" + targetBatchId + "/statistics", owner.token, houseId
        );

        assertEquals(10, statistics.size());
        assertEquals(1, statistics.get("schemaVersion").asInt());
        assertEquals(targetBatchId, statistics.get("batchId").asLong());
        assertEquals(houseName, statistics.get("houseName").asText());
        assertEquals(batchCode, statistics.get("batchCode").asText());
        assertFalse(statistics.get("calculatedAt").asText().isBlank());
        assertStatistics(statistics, 2, 13, 11, 10);
        assertEquals(28, statistics.get("metrics").size());
        assertEquals(
            List.of(
                "MATING_DATE", "MATED_DOE_COUNT", "CONCEPTION_RATE", "DOE_BUCK_RATIO",
                "PREGNANT_DOE_COUNT", "ABORTION_RATE", "DELIVERED_LITTER_COUNT",
                "TOTAL_KIT_COUNT", "AVERAGE_KITS_PER_LITTER", "LIVE_KIT_COUNT",
                "LIVE_BIRTH_RATE", "KEPT_LITTER_COUNT", "KEPT_KIT_COUNT",
                "KEPT_LIVE_RATE", "AVERAGE_KEPT_PER_LITTER", "WEANED_KIT_COUNT",
                "AVERAGE_WEANING_WEIGHT", "WEANING_SURVIVAL_RATE", "SOLD_RABBIT_COUNT",
                "OUTBOUND_SURVIVAL_RATE", "SOLD_WEIGHT", "AVERAGE_SOLD_WEIGHT",
                "TOTAL_SALES_AMOUNT", "SALES_PRICE_PER_KG", "SALES_PRICE_PER_RABBIT",
                "FULL_FEED_CONVERSION_RATIO", "FATTENING_FEED_CONVERSION_RATIO",
                "CARCASS_YIELD_RATE"
            ),
            java.util.stream.StreamSupport.stream(
                statistics.get("metrics").spliterator(), false
            ).map(metric -> metric.get("code").asText()).toList()
        );
        assertMetric(statistics, "DELIVERED_LITTER_COUNT", "AVAILABLE", "2");
        assertMetric(statistics, "AVERAGE_WEANING_WEIGHT", "DATA_MISSING", null);
        assertMetric(statistics, "CARCASS_YIELD_RATE", "NOT_RECORDED", null);
    }

    @Test
    void returnsZeroCountsAndExplicitStatusesWhenTheBatchIsEmpty() {
        UserSession owner = register("batch_statistics_empty");
        String houseName = "空批次统计兔舍";
        String batchCode = "STATS-EMPTY";
        long houseId = createHouse(owner, houseName, 1, 1, 1);
        long batchId = createBatch(owner, houseId, batchCode);

        JsonNode statistics = api.getOk(
            "/api/batches/" + batchId + "/statistics", owner.token, houseId
        );

        assertEquals(10, statistics.size());
        assertEquals(houseName, statistics.get("houseName").asText());
        assertEquals(batchCode, statistics.get("batchCode").asText());
        assertStatistics(statistics, 0, 0, 0, 0);
        assertEquals(28, statistics.get("metrics").size());
        assertMetric(statistics, "MATED_DOE_COUNT", "AVAILABLE", "0");
        assertMetric(statistics, "MATING_DATE", "NOT_RECORDED", null);
        assertMetric(statistics, "CONCEPTION_RATE", "NOT_APPLICABLE", null);
        assertEquals(
            "ZERO_DENOMINATOR",
            metric(statistics, "CONCEPTION_RATE").get("missingCauses").get(0).get("code").asText()
        );
    }

    @Test
    void appliesTheSqlMetricGrainAndBusinessDayFeedWindow() {
        UserSession owner = register("batch_statistics_sql");
        long houseId = createHouse(owner, "SQL口径兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long motherId = createRabbit(owner, houseId, cages.get(0), "0", "0", "sql_mother");
        long batchId = createBatch(owner, houseId, "STATS-SQL");
        long firstCycleId = insertPregnantCycle(
            houseId, batchId, motherId, 1, "2024-04-22 12:00:00"
        );
        insertPregnantCycle(houseId, batchId, motherId, 2, "2024-04-23 09:00:00");
        jdbc.update(
            "insert into repro_events (house_id, cycle_id, mother_rabbit_id, batch_id, "
                + "operation_code, target_type, target_id, event_type, occurred_at, payload, "
                + "operator_id, operator_name, request_id) "
                + "values (?, ?, ?, ?, 'repro:state-machine', 'RABBIT', ?, 'ABORTION', "
                + "'2024-05-01 09:00:00', '{}', 1, 'test', ?)",
            houseId,
            firstCycleId,
            motherId,
            batchId,
            motherId,
            requestId("batch_statistics_abortion")
        );

        jdbc.update(
            "insert into feed_logs (house_id, feeding_rabbits, feed_time, unit, request_id, "
                + "amount, create_by, update_by) values (?, ?, '2024-04-22 08:00:00', "
                + "'kg', ?, 5.00, 'test', 'test')",
            houseId,
            String.valueOf(motherId),
            requestId("batch_statistics_feed_window")
        );
        long feedLogId = jdbc.queryForObject(
            "select id from feed_logs where house_id = ? and request_id like "
                + "'batch_statistics_feed_window%' order by id desc limit 1",
            Long.class,
            houseId
        );
        jdbc.update(
            "insert into feed_log_batch_allocations "
                + "(feed_log_id, house_id, batch_id, phase, amount_kg) "
                + "values (?, ?, ?, 'BREEDING', 5.00)",
            feedLogId,
            houseId,
            batchId
        );
        long saleOrderId = insertSaleOrder(houseId, "batch_statistics_window_sale", 10.0);
        jdbc.update(
            "insert into sale_order_batch_allocations "
                + "(sale_order_id, house_id, batch_id, rabbit_count, actual_weight_kg, "
                + "unit_price_per_kg, amount) values (?, ?, ?, 1, 10.000, 12.00, 120.00)",
            saleOrderId,
            houseId,
            batchId
        );

        JsonNode statistics = api.getOk(
            "/api/batches/" + batchId + "/statistics", owner.token, houseId
        );

        assertMetric(statistics, "MATED_DOE_COUNT", "AVAILABLE", "1");
        assertMetric(statistics, "PREGNANT_DOE_COUNT", "AVAILABLE", "1");
        assertMetric(statistics, "CONCEPTION_RATE", "AVAILABLE", "1");
        assertMetric(statistics, "ABORTION_RATE", "AVAILABLE", "0.5");
        assertMetric(statistics, "FULL_FEED_CONVERSION_RATIO", "AVAILABLE", "0.5");
    }

    @Test
    void propagatesPersistedLegacyGapsAndNonKgFeedCauses() {
        UserSession owner = register("batch_statistics_gaps");
        long houseId = createHouse(owner, "缺口传播兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long motherId = createRabbit(owner, houseId, cages.get(0), "0", "0", "gap_mother");
        long commodityId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", "gap_commodity"
        );
        long batchId = createBatch(owner, houseId, "STATS-GAPS");
        insertPregnantCycle(houseId, batchId, motherId, 1, "2024-04-22 09:00:00");
        jdbc.update(
            "update rabbits set birth_batch_id = ?, arrival_date = '2024-04-22 10:00:00' "
                + "where house_id = ? and id = ?",
            batchId,
            houseId,
            commodityId
        );
        jdbc.update(
            "insert into feed_logs (house_id, feeding_rabbits, feed_time, unit, request_id, "
                + "amount, create_by, update_by) values (?, ?, '2024-04-22 13:00:00', "
                + "'g', ?, 100.00, 'test', 'test')",
            houseId,
            String.valueOf(commodityId),
            requestId("batch_statistics_legacy_feed")
        );
        long feedLogId = jdbc.queryForObject(
            "select id from feed_logs where house_id = ? and request_id like "
                + "'batch_statistics_legacy_feed%' order by id desc limit 1",
            Long.class,
            houseId
        );
        jdbc.update(
            "insert into feed_log_rabbits (house_id, feed_log_id, rabbit_id, cage_id) "
                + "values (?, ?, ?, ?)",
            houseId,
            feedLogId,
            commodityId,
            cages.get(1)
        );
        long saleOrderId = insertSaleOrder(houseId, "batch_statistics_gap_sale", 2.0);
        jdbc.update(
            "insert into sale_order_items "
                + "(sale_order_id, rabbit_id, batch_id_snapshot, create_by, update_by) "
                + "values (?, ?, ?, 'test', 'test')",
            saleOrderId,
            commodityId,
            batchId
        );
        insertGapEvent(
            houseId, batchId, "sale:create", "LEGACY_SALE_PRICE_GAP", "gap_sale_price"
        );
        insertGapEvent(
            houseId,
            batchId,
            "rabbit.toReplacement",
            "LEGACY_REPLACEMENT_WEIGHT_GAP",
            "gap_replacement"
        );

        assertThrows(DataAccessException.class, () -> jdbc.update(
            "insert into sale_order_batch_allocations "
                + "(sale_order_id, house_id, batch_id, rabbit_count, actual_weight_kg, "
                + "unit_price_per_kg, amount) values (?, ?, ?, 1, 1.000, 12.00, null)",
            saleOrderId,
            houseId,
            batchId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
            "insert into litters (house_id, cycle_id, batch_id, mother_rabbit_id, birth_date, "
                + "total_kits, live_kits, kept_kits, current_nursing, status, weaning_date, "
                + "weaned_count, weaning_total_weight_kg, request_id, create_by, update_by) "
                + "values (?, ?, ?, ?, now(), 1, 1, 1, 0, 'WEANED', now(), 1, -1.000, "
                + "?, 'test', 'test')",
            houseId,
            nextFixtureCycleId++,
            batchId,
            motherId,
            requestId("batch_statistics_invalid_litter")
        ));

        JsonNode statistics = api.getOk(
            "/api/batches/" + batchId + "/statistics", owner.token, houseId
        );

        assertCauseCodes(
            metric(statistics, "TOTAL_SALES_AMOUNT"),
            "MISSING_BATCH_SALE_ALLOCATION",
            "MISSING_SALE_UNIT_PRICE"
        );
        assertCauseCodes(
            metric(statistics, "FULL_FEED_CONVERSION_RATIO"),
            "MISSING_BATCH_SALE_ALLOCATION",
            "MISSING_FEED_ALLOCATION",
            "MISSING_FEED_UNIT",
            "MISSING_REPLACEMENT_WEIGHT"
        );
    }

    @Test
    void marksHistoricalMembershipOnlySaleAndReplacementSnapshotsAsMissing() {
        UserSession owner = register("batch_statistics_historical_membership_gaps");
        long houseId = createHouse(owner, "历史成员缺口兔舍", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long saleRabbitId = createRabbit(
            owner, houseId, cages.get(0), "2", "0", "historical_sale"
        );
        long replacementRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", "historical_replacement"
        );
        long batchId = createBatch(owner, houseId, "STATS-HISTORICAL-MEMBERSHIP");
        jdbc.update(
            "insert into batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, "
                + "current_status, is_active, join_date, exit_date, create_by, update_by) "
                + "values (?, ?, '断奶', 'fattening', '成长期', false, "
                + "'2024-04-01 00:00:00', '2024-08-01 12:00:00', 'test', 'test'), "
                + "(?, ?, '断奶', 'fattening', '成长期', false, "
                + "'2024-04-01 00:00:00', '2024-08-01 12:00:00', 'test', 'test')",
            batchId,
            saleRabbitId,
            batchId,
            replacementRabbitId
        );

        long saleOrderId = insertSaleOrder(
            houseId,
            "batch_statistics_historical_membership_sale",
            2.0
        );
        jdbc.update(
            "insert into sale_order_items "
                + "(sale_order_id, rabbit_id, batch_id_snapshot, create_by, update_by) "
                + "values (?, ?, null, 'test', 'test')",
            saleOrderId,
            saleRabbitId
        );
        jdbc.update(
            "insert into replacement_records "
                + "(house_id, rabbit_id, request_id, original_type, replacement_date, "
                + "expected_mature_date, status, create_by, update_by) "
                + "values (?, ?, ?, '2', '2024-08-01 12:00:00', "
                + "'2024-10-01 12:00:00', 'PENDING', 'test', 'test')",
            houseId,
            replacementRabbitId,
            requestId("batch_statistics_historical_replacement")
        );

        JsonNode statistics = api.getOk(
            "/api/batches/" + batchId + "/statistics", owner.token, houseId
        );

        assertCauseCodes(
            metric(statistics, "SOLD_RABBIT_COUNT"),
            "MISSING_BATCH_ATTRIBUTION"
        );
        assertCauseCodes(
            metric(statistics, "SOLD_WEIGHT"),
            "MISSING_BATCH_SALE_ALLOCATION"
        );
        assertCauseCodes(
            metric(statistics, "FULL_FEED_CONVERSION_RATIO"),
            "MISSING_BATCH_SALE_ALLOCATION",
            "MISSING_REPLACEMENT_WEIGHT"
        );
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

    private long insertPregnantCycle(
        long houseId,
        long batchId,
        long motherId,
        int cycleNo,
        String matingTime
    ) {
        String requestId = requestId("batch_statistics_cycle");
        jdbc.update(
            "insert into breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, "
                + "stage, stage_entered_at, lifecycle, mating_method, mating_date, "
                + "pregnancy_result, request_id, create_by, update_by) "
                + "values (?, ?, ?, ?, 'AWAIT_PALPATION', ?, 'CLOSED', 'AI', ?, "
                + "'怀孕', ?, 'test', 'test')",
            houseId,
            batchId,
            motherId,
            cycleNo,
            matingTime,
            matingTime,
            requestId
        );
        return jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and request_id = ?",
            Long.class,
            houseId,
            requestId
        );
    }

    private long insertSaleOrder(long houseId, String requestPrefix, double totalWeight) {
        String requestId = requestId(requestPrefix);
        jdbc.update(
            "insert into sale_orders (house_id, sale_time, total_weight, request_id, "
                + "create_by, update_by) values (?, '2024-08-01 09:00:00', ?, ?, "
                + "'test', 'test')",
            houseId,
            totalWeight,
            requestId
        );
        return jdbc.queryForObject(
            "select id from sale_orders where house_id = ? and request_id = ?",
            Long.class,
            houseId,
            requestId
        );
    }

    private void insertGapEvent(
        long houseId,
        long batchId,
        String operationCode,
        String eventType,
        String requestPrefix
    ) {
        jdbc.update(
            "insert into repro_events (house_id, batch_id, operation_code, target_type, "
                + "target_id, event_type, occurred_at, payload, operator_id, operator_name, "
                + "request_id) values (?, ?, ?, 'BATCH', ?, ?, now(), "
                + "'{\"clientBuild\":\"UNKNOWN\"}', 1, 'test', ?)",
            houseId,
            batchId,
            operationCode,
            batchId,
            eventType,
            requestId(requestPrefix)
        );
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

    private void assertMetric(
        JsonNode statistics,
        String code,
        String status,
        String numericValue
    ) {
        JsonNode metric = metric(statistics, code);
        assertEquals(status, metric.get("status").asText());
        if (numericValue == null) {
            assertTrue(metric.get("numericValue").isNull());
            assertTrue(metric.get("displayValue").isNull());
        } else {
            assertEquals(0, new java.math.BigDecimal(numericValue).compareTo(
                metric.get("numericValue").decimalValue()
            ));
            assertFalse(metric.get("displayValue").isNull());
        }
    }

    private void assertCauseCodes(JsonNode metric, String... expectedCodes) {
        assertEquals("DATA_MISSING", metric.get("status").asText());
        assertEquals(
            List.of(expectedCodes),
            java.util.stream.StreamSupport.stream(
                metric.get("missingCauses").spliterator(), false
            ).map(cause -> cause.get("code").asText()).toList()
        );
    }

    private JsonNode metric(JsonNode statistics, String code) {
        for (JsonNode metric : statistics.get("metrics")) {
            if (code.equals(metric.get("code").asText())) {
                return metric;
            }
        }
        throw new AssertionError("Missing metric " + code);
    }
}
