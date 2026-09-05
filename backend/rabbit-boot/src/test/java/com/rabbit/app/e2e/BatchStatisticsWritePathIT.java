package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchStatisticsWritePathIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsFeedSaleAndReplacementSnapshotsWithStableRequestIds() {
        UserSession owner = register("batch_write_owner");
        long houseId = createHouse(owner, "批次写入验收兔舍", 1, 5, 1);
        List<Long> cages = cageIds(owner, houseId);
        long batchId = createBatch(owner, houseId, "WRITE-A");
        long saleRabbitId = createRabbit(owner, houseId, cages.get(0), "2", "0", "sale");
        long replacementRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", "replacement"
        );
        jdbc.update(
            "update rabbits set birth_batch_id = ?, arrival_date = date_sub(now(), interval 1 day) "
                + "where house_id = ? and id in (?, ?)",
            batchId,
            houseId,
            saleRabbitId,
            replacementRabbitId
        );

        String feedRequestId = requestId("batch_feed");
        long feedTime = now();
        Object feedBody = obj(
            "rabbitIds", List.of(saleRabbitId),
            "feedTime", feedTime,
            "amount", new BigDecimal("2.50"),
            "unit", "kg",
            "allocations", List.of(obj(
                "batchId", batchId,
                "phase", "FATTENING",
                "amountKg", new BigDecimal("2.50")
            )),
            "requestId", feedRequestId
        );
        api.postOk("/api/feed-logs", owner.token, houseId, feedBody);
        api.postOk("/api/feed-logs", owner.token, houseId, feedBody);
        assertEquals(new BigDecimal("2.50"), jdbc.queryForObject(
            "select amount_kg from feed_log_batch_allocations where house_id = ? and batch_id = ?",
            BigDecimal.class,
            houseId,
            batchId
        ));
        assertEquals(1, count(
            "select count(*) from feed_log_batch_allocations where house_id = ?", houseId
        ));
        api.expectError(
            "/api/feed-logs",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(saleRabbitId),
                "feedTime", feedTime,
                "amount", new BigDecimal("2.60"),
                "unit", "kg",
                "allocations", List.of(obj(
                    "batchId", batchId,
                    "phase", "FATTENING",
                    "amountKg", new BigDecimal("2.60")
                )),
                "requestId", feedRequestId
            ),
            409,
            "requestId已用于不同的请求载荷"
        );

        String saleRequestId = requestId("batch_sale");
        Object saleBody = obj(
            "rabbitIds", List.of(saleRabbitId),
            "saleTime", now(),
            "totalWeight", 2.500,
            "unitPricePerKg", new BigDecimal("12.00"),
            "batchAllocations", List.of(obj(
                "batchId", batchId,
                "actualWeightKg", new BigDecimal("2.500")
            )),
            "requestId", saleRequestId
        );
        JsonNode sale = api.postOk("/api/sales", owner.token, houseId, saleBody);
        assertEquals(sale.get("id").asLong(),
            api.postOk("/api/sales", owner.token, houseId, saleBody).get("id").asLong());
        assertEquals(new BigDecimal("30.00"), jdbc.queryForObject(
            "select amount from sale_order_batch_allocations where house_id = ? and batch_id = ?",
            BigDecimal.class,
            houseId,
            batchId
        ));
        assertEquals(1, count(
            "select rabbit_count from sale_order_batch_allocations where house_id = ?", houseId
        ));

        String replacementRequestId = requestId("batch_replacement");
        Object replacementBody = obj(
            "rabbitIds", List.of(replacementRabbitId),
            "forceExitBatch", false,
            "batchAllocations", List.of(obj(
                "batchId", batchId,
                "rabbitCount", 1,
                "totalWeightKg", new BigDecimal("2.200")
            )),
            "requestId", replacementRequestId
        );
        api.postOk("/api/rabbits/replacement", owner.token, houseId, replacementBody);
        api.postOk("/api/rabbits/replacement", owner.token, houseId, replacementBody);
        assertEquals(new BigDecimal("2.200"), jdbc.queryForObject(
            "select total_weight_kg from replacement_batch_allocations "
                + "where house_id = ? and source_batch_id = ?",
            BigDecimal.class,
            houseId,
            batchId
        ));
        assertEquals(1, count(
            "select count(*) from replacement_batch_allocations where house_id = ?", houseId
        ));
        assertEquals(0, count(
            "select count(*) from repro_events where house_id = ? and event_type in ("
                + "'LEGACY_FEED_ALLOCATION_GAP', 'LEGACY_WEANING_WEIGHT_GAP', "
                + "'LEGACY_SALE_ALLOCATION_GAP', 'LEGACY_SALE_PRICE_GAP', "
                + "'LEGACY_REPLACEMENT_WEIGHT_GAP')",
            houseId
        ));
    }

    @Test
    void rejectsCrossHouseSnapshotReferencesAtTheDatabaseBoundary() {
        UserSession owner = register("batch_snapshot_tenant_constraints");
        long sourceHouseId = createHouse(owner, "快照来源兔舍", 1, 1, 1);
        long otherHouseId = createHouse(owner, "快照目标兔舍", 1, 1, 1);
        long sourceBatchId = createBatch(owner, sourceHouseId, "SNAPSHOT-SOURCE");
        long otherBatchId = createBatch(owner, otherHouseId, "SNAPSHOT-OTHER");

        String feedRequestId = requestId("cross_house_feed_parent");
        jdbc.update(
            "insert into feed_logs (house_id, feed_time, unit, request_id, amount, "
                + "create_by, update_by) values (?, now(), 'kg', ?, 1.00, 'test', 'test')",
            sourceHouseId,
            feedRequestId
        );
        long feedLogId = jdbc.queryForObject(
            "select id from feed_logs where house_id = ? and request_id = ?",
            Long.class,
            sourceHouseId,
            feedRequestId
        );
        assertThrows(DataAccessException.class, () -> jdbc.update(
            "insert into feed_log_batch_allocations "
                + "(feed_log_id, house_id, batch_id, phase, amount_kg) "
                + "values (?, ?, ?, 'FATTENING', 1.00)",
            feedLogId,
            otherHouseId,
            otherBatchId
        ));

        String saleRequestId = requestId("cross_house_sale_parent");
        jdbc.update(
            "insert into sale_orders (house_id, sale_time, total_weight, request_id, "
                + "create_by, update_by) values (?, now(), 1.0, ?, 'test', 'test')",
            sourceHouseId,
            saleRequestId
        );
        long saleOrderId = jdbc.queryForObject(
            "select id from sale_orders where house_id = ? and request_id = ?",
            Long.class,
            sourceHouseId,
            saleRequestId
        );
        assertThrows(DataAccessException.class, () -> jdbc.update(
            "insert into sale_order_batch_allocations "
                + "(sale_order_id, house_id, batch_id, rabbit_count, actual_weight_kg, "
                + "unit_price_per_kg, amount) values (?, ?, ?, 1, 1.000, 12.00, 12.00)",
            saleOrderId,
            otherHouseId,
            otherBatchId
        ));

        assertThrows(DataAccessException.class, () -> jdbc.update(
            "insert into replacement_batch_allocations "
                + "(house_id, request_id, source_batch_id, rabbit_count, total_weight_kg, "
                + "created_by) values (?, ?, ?, 1, 1.000, 1)",
            sourceHouseId,
            requestId("cross_house_replacement"),
            otherBatchId
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
            "insert into batch_carcass_yield_versions "
                + "(house_id, batch_id, yield_rate, source_unit, measured_date, "
                + "change_reason, request_id, payload_hash, created_by) "
                + "values (?, ?, 0.500000, '测试单位', current_date, '测试', ?, "
                + "repeat('0', 64), 1)",
            sourceHouseId,
            otherBatchId,
            requestId("cross_house_carcass")
        ));

        assertEquals(sourceBatchId, jdbc.queryForObject(
            "select id from batches where house_id = ? and id = ?",
            Long.class,
            sourceHouseId,
            sourceBatchId
        ));
    }

    private long createBatch(UserSession owner, long houseId, String batchCode) {
        return api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", batchCode,
            "femaleRabbitIds", List.of(),
            "requestId", requestId("batch_write")
        )).get("id").asLong();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
