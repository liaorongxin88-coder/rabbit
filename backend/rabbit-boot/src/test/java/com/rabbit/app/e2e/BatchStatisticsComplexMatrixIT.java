package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.security.JwtUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchStatisticsComplexMatrixIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void verifiesExactMetricsBoundariesIsolationAndPayloadSensitiveReplay() {
        BatchStatisticsComplexMatrixFixture.Fixture fixture =
                BatchStatisticsComplexMatrixFixture.load(jdbc);
        try {
            assertEquals(
                    List.of(
                            "complex-available",
                            "mixed-data-quality",
                            "mixed-batch-rounding",
                            "time-and-cycle-boundaries",
                            "security-and-retry",
                            "mixed-batch-rounding-support"
                    ),
                    List.copyOf(fixture.scenarios().keySet())
            );
            String ownerToken = jwtUtil.generateToken(fixture.owner().userId());
            String readOnlyToken = jwtUtil.generateToken(fixture.readOnly().userId());
            String outsiderToken = jwtUtil.generateToken(fixture.outsider().userId());
            BatchStatisticsComplexMatrixFixture.Scenario security =
                    fixture.scenario("security-and-retry");
            String requestId = "bsx-carcass-" + fixture.runId() + "-security-api";
            Object firstPayload = carcassPayload(requestId, "0.580000");
            String carcassEndpoint = "/api/batches/" + security.batchId() + "/carcass-yields";

            JsonNode created = api.postOk(
                    carcassEndpoint,
                    ownerToken,
                    fixture.houseId(),
                    firstPayload
            );
            JsonNode replayed = api.postOk(
                    carcassEndpoint,
                    ownerToken,
                    fixture.houseId(),
                    firstPayload
            );
            assertEquals(created.path("id").asLong(), replayed.path("id").asLong());
            api.expectError(
                    carcassEndpoint,
                    HttpMethod.POST,
                    ownerToken,
                    fixture.houseId(),
                    carcassPayload(requestId, "0.590000"),
                    409,
                    "requestId已用于不同的出肉率记录"
            );

            for (BatchStatisticsComplexMatrixFixture.Scenario scenario
                    : fixture.scenarios().values()) {
                JsonNode statistics = api.getOk(
                        statisticsEndpoint(scenario.batchId()),
                        ownerToken,
                        fixture.houseId()
                );
                BatchStatisticsComplexMatrixFixture.assertStatistics(
                        statistics,
                        fixture,
                        scenario
                );
            }

            BatchStatisticsComplexMatrixFixture.Scenario timeBoundary =
                    fixture.scenario("time-and-cycle-boundaries");
            JsonNode timeBoundaryStatistics = api.getOk(
                    statisticsEndpoint(timeBoundary.batchId()),
                    ownerToken,
                    fixture.houseId()
            );
            JsonNode fullFeedConversion = metricByCode(
                    timeBoundaryStatistics,
                    "FULL_FEED_CONVERSION_RATIO"
            );
            assertEquals(
                    0,
                    new BigDecimal("0.5").compareTo(
                            fullFeedConversion.path("numericValue").decimalValue()
                    ),
                    "end-day feed must be included and next-day midnight feed excluded"
            );
            assertEquals("0.50", fullFeedConversion.path("displayValue").asText());

            JsonNode readOnlyStatistics = api.getOk(
                    statisticsEndpoint(security.batchId()),
                    readOnlyToken,
                    fixture.houseId()
            );
            BatchStatisticsComplexMatrixFixture.assertStatistics(
                    readOnlyStatistics,
                    fixture,
                    security
            );
            api.expectError(
                    carcassEndpoint,
                    HttpMethod.POST,
                    readOnlyToken,
                    fixture.houseId(),
                    carcassPayload("bsx-readonly-" + fixture.runId(), "0.600000"),
                    403,
                    null
            );
            api.expectError(
                    carcassEndpoint,
                    HttpMethod.GET,
                    readOnlyToken,
                    fixture.houseId(),
                    null,
                    403,
                    null
            );
            api.expectError(
                    statisticsEndpoint(security.batchId()),
                    HttpMethod.GET,
                    outsiderToken,
                    fixture.isolationHouseId(),
                    null,
                    404,
                    "批次不存在"
            );

            JsonNode history = api.getOk(
                    carcassEndpoint + "?page=1&pageSize=20",
                    ownerToken,
                    fixture.houseId()
            );
            assertEquals(1, history.path("total").asInt());
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from batch_carcass_yield_versions "
                            + "where house_id = ? and batch_id = ? and request_id = ?",
                    Integer.class,
                    fixture.houseId(),
                    security.batchId(),
                    requestId
            ));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from request_dedup "
                            + "where house_id = ? and user_id = ? and api = ? and request_id = ? "
                            + "and status = 'DONE'",
                    Integer.class,
                    fixture.houseId(),
                    fixture.owner().userId(),
                    "batch:carcass-yield",
                    requestId
            ));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from repro_events "
                            + "where house_id = ? and target_type = 'BATCH' and target_id = ? "
                            + "and operation_code = ? and event_type = 'CARCASS_YIELD_RECORDED'",
                    Integer.class,
                    fixture.houseId(),
                    security.batchId(),
                    "batch:carcass-yield"
            ));

            assertEquals(new BigDecimal("18.03"), jdbc.queryForObject(
                    "select total_amount from sale_orders where house_id = ? and request_id = ?",
                    BigDecimal.class,
                    fixture.houseId(),
                    "bsx-sale-" + fixture.runId() + "-round"
            ));
            assertEquals("6.00,6.01,6.02", jdbc.queryForObject(
                    "select group_concat(format(amount, 2) order by "
                            + "case when batch_id = ? then 0 when batch_id = ? then 1 else 2 end "
                            + "separator ',') from sale_order_batch_allocations "
                            + "where house_id = ? and sale_order_id = "
                            + "(select id from sale_orders where house_id = ? and request_id = ?)",
                    String.class,
                    fixture.scenario("mixed-batch-rounding").batchId(),
                    fixture.scenario("mixed-batch-rounding-support").batchId(),
                    fixture.houseId(),
                    fixture.houseId(),
                    "bsx-sale-" + fixture.runId() + "-round"
            ));
        } finally {
            BatchStatisticsComplexMatrixFixture.cleanup(jdbc, fixture);
        }
    }

    @Test
    void cleanupPreservesCollidingRowsOwnedByAnotherActor() {
        BatchStatisticsComplexMatrixFixture.Fixture fixture =
                BatchStatisticsComplexMatrixFixture.load(jdbc);
        Long decoyUserId = null;
        Long decoyHouseId = null;
        boolean fixtureCleaned = false;
        String decoyUser = "bsx_decoy_" + fixture.runId();
        String collidingHouseRequest = "bsx-house-" + fixture.runId() + "-target";
        String collidingRuntimeRequest = "bsx-gap-" + fixture.runId() + "-collision";
        try {
            jdbc.update(
                    "insert into sys_user (user_name, user_code, password, status) "
                            + "values (?, concat('Y', upper(substring(sha2(?, 256), 1, 10))), "
                            + "'$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6', "
                            + "'ENABLED')",
                    decoyUser,
                    decoyUser
            );
            decoyUserId = jdbc.queryForObject(
                    "select user_id from sys_user where user_name = ?",
                    Long.class,
                    decoyUser
            );
            jdbc.update(
                    "insert into rabbit_houses "
                            + "(name, status, layout_rows, layout_cols, layout_layers, "
                            + "request_id, create_by, update_by) "
                            + "values (?, 'ENABLED', 1, 1, 1, ?, ?, ?)",
                    "cleanup collision " + fixture.runId(),
                    collidingHouseRequest,
                    String.valueOf(decoyUserId),
                    String.valueOf(decoyUserId)
            );
            decoyHouseId = jdbc.queryForObject(
                    "select id from rabbit_houses where request_id = ? and create_by = ?",
                    Long.class,
                    collidingHouseRequest,
                    String.valueOf(decoyUserId)
            );
            jdbc.update(
                    "insert into repro_events "
                            + "(house_id, operation_code, target_type, target_id, event_type, "
                            + "occurred_at, operator_id, operator_name, request_id) "
                            + "values (?, 'test:cleanup', 'HOUSE', ?, 'CLEANUP_COLLISION', "
                            + "now(), ?, ?, ?)",
                    decoyHouseId,
                    decoyHouseId,
                    decoyUserId,
                    decoyUser,
                    collidingRuntimeRequest
            );
            jdbc.update(
                    "insert into request_dedup "
                            + "(house_id, user_id, api, request_id, status) "
                            + "values (?, ?, 'test:cleanup', ?, 'DONE')",
                    decoyHouseId,
                    decoyUserId,
                    collidingRuntimeRequest
            );

            BatchStatisticsComplexMatrixFixture.cleanup(jdbc, fixture);
            fixtureCleaned = true;

            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from rabbit_houses where id = ? and create_by = ?",
                    Integer.class,
                    decoyHouseId,
                    String.valueOf(decoyUserId)
            ));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from repro_events where house_id = ? and operator_id = ? "
                            + "and request_id = ?",
                    Integer.class,
                    decoyHouseId,
                    decoyUserId,
                    collidingRuntimeRequest
            ));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from request_dedup where house_id = ? and user_id = ? "
                            + "and request_id = ?",
                    Integer.class,
                    decoyHouseId,
                    decoyUserId,
                    collidingRuntimeRequest
            ));
        } finally {
            if (decoyHouseId != null) {
                jdbc.update("delete from request_dedup where house_id = ?", decoyHouseId);
                jdbc.update("delete from repro_events where house_id = ?", decoyHouseId);
                jdbc.update("delete from rabbit_houses where id = ?", decoyHouseId);
            }
            if (decoyUserId != null) {
                jdbc.update("delete from sys_user where user_id = ?", decoyUserId);
            }
            if (!fixtureCleaned) {
                BatchStatisticsComplexMatrixFixture.cleanup(jdbc, fixture);
            }
        }
    }

    private static JsonNode metricByCode(JsonNode statistics, String code) {
        for (JsonNode metric : statistics.path("metrics")) {
            if (code.equals(metric.path("code").asText())) {
                return metric;
            }
        }
        throw new AssertionError("Missing metric " + code);
    }

    private Object carcassPayload(String requestId, String rate) {
        return obj(
                "yieldRate", new BigDecimal(rate),
                "sourceUnit", "复杂矩阵测试场",
                "measuredDate", LocalDate.of(2024, 8, 1).toString(),
                "changeReason", "首次录入",
                "requestId", requestId
        );
    }

    private static String statisticsEndpoint(long batchId) {
        return "/api/batches/" + batchId + "/statistics";
    }
}
