package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.batch-statistics.legacy-write-enabled=false")
class BatchStatisticsLegacyWriteDisabledIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rejectsLegacyWritesBeforeCreatingParentOrGapRows() {
        UserSession owner = register("legacy_write_disabled");
        long houseId = createHouse(owner, "关闭兼容兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long saleRabbitId = createRabbit(
            owner, houseId, cages.get(0), "2", "0", "disabled_sale"
        );
        long replacementRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", "disabled_replacement"
        );

        api.expectError(
            "/api/feed-logs",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(saleRabbitId),
                "feedTime", now(),
                "amount", new BigDecimal("1.00"),
                "unit", "kg",
                "requestId", requestId("disabled_feed")
            ),
            409,
            "当前版本过低，请升级应用后重试"
        );
        api.expectError(
            "/api/sales",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(saleRabbitId),
                "saleTime", now(),
                "totalWeight", 2.0,
                "requestId", requestId("disabled_sale")
            ),
            409,
            "当前版本过低，请升级应用后重试"
        );
        api.expectError(
            "/api/rabbits/replacement",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(replacementRabbitId),
                "forceExitBatch", false,
                "requestId", requestId("disabled_replacement")
            ),
            409,
            "当前版本过低，请升级应用后重试"
        );

        assertEquals(0, count("select count(*) from feed_logs where house_id = ?", houseId));
        assertEquals(0, count("select count(*) from sale_orders where house_id = ?", houseId));
        assertEquals(
            0,
            count("select count(*) from replacement_records where house_id = ?", houseId)
        );
        assertEquals(
            0,
            count(
                "select count(*) from repro_events where house_id = ? and event_type in ("
                    + "'LEGACY_FEED_ALLOCATION_GAP', 'LEGACY_WEANING_WEIGHT_GAP', "
                    + "'LEGACY_SALE_ALLOCATION_GAP', 'LEGACY_SALE_PRICE_GAP', "
                    + "'LEGACY_REPLACEMENT_WEIGHT_GAP')",
                houseId
            )
        );
        assertEquals(
            2,
            count(
                "select count(*) from rabbits where house_id = ? and id in (?, ?) "
                    + "and is_active = true and type = '2'",
                houseId,
                saleRabbitId,
                replacementRabbitId
            )
        );
    }

    @Test
    void rejectsIncompleteLegacyOutboundBeforeClaimWithoutBusinessWrites() {
        UserSession owner = register("legacy_outbound_disabled");
        long houseId = createHouse(owner, "关闭旧出库兔舍", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long assignedRabbitId = createRabbit(
            owner, houseId, cages.get(0), "2", "0", "disabled-outbound-assigned"
        );
        long unassignedRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", "disabled-outbound-unassigned"
        );
        long batchId = attachSaleStage(houseId, assignedRabbitId);
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE' where house_id = ? and id = ?",
            houseId,
            unassignedRabbitId
        );
        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
            "entryType", "HOUSE",
            "resumeExisting", false
        ));
        JsonNode saved = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(
                    obj(
                        "rabbitId", assignedRabbitId,
                        "stateVersion", version(task, assignedRabbitId),
                        "selectionType", "NORMAL"
                    ),
                    obj(
                        "rabbitId", unassignedRabbitId,
                        "stateVersion", version(task, unassignedRabbitId),
                        "selectionType", "NORMAL"
                    )
                ),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("4.000"),
                "unitPricePerKg", new BigDecimal("12.00"),
                "batchAllocations", List.of(
                    obj("batchId", batchId, "actualWeightKg", new BigDecimal("2.000")),
                    obj("batchId", null, "actualWeightKg", new BigDecimal("2.000"))
                )
            )
        );
        JsonNode reloaded = api.getOk(
            "/api/outbound/tasks/" + saved.get("taskId").asText(), owner.token, houseId
        );
        String requestId = UUID.randomUUID().toString();
        List<Long> rabbitIds = reloaded.get("selectedItems").findValuesAsText("rabbitId")
            .stream().map(Long::valueOf).toList();
        Map<String, Long> stateVersions = new LinkedHashMap<>();
        for (JsonNode item : reloaded.get("selectedItems")) {
            stateVersions.put(
                item.get("rabbitId").asText(), item.get("stateVersion").asLong()
            );
        }

        api.expectError(
            "/api/outbound/tasks/" + saved.get("taskId").asText() + "/submit",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "rabbitIds", rabbitIds,
                "stateVersions", stateVersions,
                "earlySaleReasons", Map.of(),
                "saleTime", reloaded.get("saleTime").asText(),
                "totalWeight", reloaded.get("totalWeight").decimalValue(),
                "requestId", requestId
            ),
            409,
            "当前版本过低，请升级应用后重试"
        );

        assertEquals(0, count(
            "select count(*) from outbound_requests where house_id = ? and request_id = ?",
            houseId,
            requestId
        ));
        assertEquals(0, count(
            "select count(*) from sale_orders where house_id = ? and request_id = ?",
            houseId,
            requestId
        ));
        assertEquals(0, count(
            "select count(*) from sale_order_batch_allocations where house_id = ?",
            houseId
        ));
        assertEquals(0, count(
            "select count(*) from rabbit_departure_records where house_id = ? "
                + "and rabbit_id in (?, ?)",
            houseId,
            assignedRabbitId,
            unassignedRabbitId
        ));
        assertEquals(0, count(
            "select count(*) from rabbit_status_history where house_id = ? "
                + "and rabbit_id in (?, ?) and to_status = '出售出栏'",
            houseId,
            assignedRabbitId,
            unassignedRabbitId
        ));
        assertEquals(0, count(
            "select count(*) from repro_events where house_id = ? and request_id = ?",
            houseId,
            requestId
        ));
        assertEquals(2, count(
            "select count(*) from rabbits where house_id = ? and id in (?, ?) "
                + "and is_active = true and departure_date is null",
            houseId,
            assignedRabbitId,
            unassignedRabbitId
        ));
        assertEquals(1, count(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ? "
                + "and is_active = true",
            batchId,
            assignedRabbitId
        ));
        assertEquals(1, count(
            "select count(*) from outbound_tasks where house_id = ? and task_id = ? "
                + "and status = 'WAITING_CONFIRMATION' and request_id is null",
            houseId,
            saved.get("taskId").asText()
        ));
    }

    private long attachSaleStage(long houseId, long rabbitId) {
        String batchRequestId = requestId("disabled_outbound_batch");
        jdbc.update(
            "insert into batches (house_id, batch_code, status, start_date, request_id, "
                + "create_by, update_by) values (?, ?, '进行中', now(), ?, 'e2e', 'e2e')",
            houseId,
            "DISABLED-" + rabbitId,
            batchRequestId
        );
        long batchId = jdbc.queryForObject(
            "select id from batches where house_id = ? and request_id = ?",
            Long.class,
            houseId,
            batchRequestId
        );
        jdbc.update(
            "insert into batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, "
                + "current_status, next_event_date, next_event_type, is_active, join_date, "
                + "create_by, update_by) values (?, ?, '断奶', 'fattening', '成长期', "
                + "now(), '出售', true, now(), 'e2e', 'e2e')",
            batchId,
            rabbitId
        );
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE' where house_id = ? and id = ?",
            houseId,
            rabbitId
        );
        return batchId;
    }

    private long version(JsonNode task, long rabbitId) {
        for (JsonNode rabbit : task.get("rabbits")) {
            if (rabbit.get("rabbitId").asLong() == rabbitId) {
                return rabbit.get("stateVersion").asLong();
            }
        }
        throw new AssertionError("rabbit missing from outbound task: " + rabbitId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
