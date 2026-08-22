package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exercises the final transactional submit for a production-sized whole house. */
public class LargeHouseOutboundSubmitScaleIT extends E2eTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeHouseOutboundSubmitScaleIT.class);
    private static final int CAGE_COUNT = 700;
    private static final int RABBITS_PER_CAGE = 10;
    private static final int RABBIT_COUNT = CAGE_COUNT * RABBITS_PER_CAGE;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${app.mybatis.write-guard.max-affected-rows}")
    private int maxAffectedRows;

    @Test
    void wholeHouseSubmitCompletesSevenThousandRabbitsAtomicallyAndIdempotently(TestReporter reporter) {
        Assertions.assertTrue(RABBIT_COUNT > maxAffectedRows);
        UserSession owner = register("large_outbound_submit");
        ScaleFixture fixture = insertSaleReadyHouse(owner);

        long startedAt = System.nanoTime();
        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, fixture.houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", false
        ));
        long taskCreatedAt = System.nanoTime();
        String taskId = task.get("taskId").asText();
        Assertions.assertEquals(RABBIT_COUNT, task.get("summary").get("normal").asInt());
        Assertions.assertEquals(RABBIT_COUNT, task.get("selectedItems").size());

        List<Long> rabbitIds = new ArrayList<>(RABBIT_COUNT);
        Map<String, Long> stateVersions = new LinkedHashMap<>();
        List<Map<String, Object>> selected = new ArrayList<>(RABBIT_COUNT);
        for (JsonNode item : task.get("selectedItems")) {
            long rabbitId = item.get("rabbitId").asLong();
            long stateVersion = item.get("stateVersion").asLong();
            rabbitIds.add(rabbitId);
            stateVersions.put(String.valueOf(rabbitId), stateVersion);
            selected.add(obj(
                    "rabbitId", rabbitId,
                    "stateVersion", stateVersion,
                    "selectionType", "NORMAL"
            ));
        }

        JsonNode frozen = api.putOk("/api/outbound/tasks/" + taskId, owner.token, fixture.houseId, obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selected,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", RABBIT_COUNT * 2.6,
                "unitPrice", 18.0,
                "customer", "large house submit customer",
                "remark", "submit every sale-ready rabbit in one house"
        ));
        long taskFrozenAt = System.nanoTime();
        Assertions.assertEquals("WAITING_CONFIRMATION", frozen.get("status").asText());
        Assertions.assertEquals(RABBIT_COUNT, frozen.get("selectedItems").size());
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from outbound_task_items where task_id = ?",
                taskId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(distinct rabbit_id) from outbound_task_items where task_id = ?",
                taskId
        ));

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> submitBody = obj(
                "rabbitIds", rabbitIds,
                "stateVersions", stateVersions,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", RABBIT_COUNT * 2.6,
                "unitPrice", 18.0,
                "customer", "large house submit customer",
                "remark", "submit every sale-ready rabbit in one house",
                "requestId", requestId
        );

        long otherHouseId = createHouse(owner, "large_outbound_submit_isolation", 1, 1, 1);
        api.expectError(
                "/api/outbound/tasks/" + taskId + "/submit",
                HttpMethod.POST,
                owner.token,
                otherHouseId,
                submitBody,
                404,
                "OUTBOUND_TASK_NOT_FOUND"
        );
        Assertions.assertEquals(0, count(
                "select count(*) from outbound_requests where house_id = ? and request_id = ?",
                otherHouseId,
                requestId
        ));
        long isolationCheckedAt = System.nanoTime();

        JsonNode completed = Assertions.assertTimeout(
                Duration.ofMinutes(3),
                () -> api.postOk(
                        "/api/outbound/tasks/" + taskId + "/submit",
                        owner.token,
                        fixture.houseId,
                        submitBody
                )
        );
        long submittedAt = System.nanoTime();
        Assertions.assertEquals("COMPLETED", completed.get("status").asText());
        Assertions.assertEquals(RABBIT_COUNT, completed.get("rabbitCount").asInt());
        Assertions.assertEquals(CAGE_COUNT, completed.get("cageCount").asInt());
        Assertions.assertEquals(fixture.rowCount, completed.get("rowCount").asInt());
        long saleOrderId = completed.get("saleOrderId").asLong();

        JsonNode retry = api.postOk(
                "/api/outbound/tasks/" + taskId + "/submit",
                owner.token,
                fixture.houseId,
                submitBody
        );
        long retryCompletedAt = System.nanoTime();
        Assertions.assertEquals("COMPLETED", retry.get("status").asText());
        Assertions.assertEquals(saleOrderId, retry.get("saleOrderId").asLong());
        Assertions.assertEquals(RABBIT_COUNT, retry.get("rabbitCount").asInt());

        assertCommittedState(fixture, taskId, requestId, saleOrderId);
        long verifiedAt = System.nanoTime();

        Map<String, String> metrics = Map.of(
                "rabbits", String.valueOf(RABBIT_COUNT),
                "cages", String.valueOf(CAGE_COUNT),
                "sqlGuardRows", String.valueOf(maxAffectedRows),
                "createTaskMs", elapsedMillis(startedAt, taskCreatedAt),
                "freezeTaskMs", elapsedMillis(taskCreatedAt, taskFrozenAt),
                "isolationMs", elapsedMillis(taskFrozenAt, isolationCheckedAt),
                "submitMs", elapsedMillis(isolationCheckedAt, submittedAt),
                "idempotentRetryMs", elapsedMillis(submittedAt, retryCompletedAt),
                "verificationMs", elapsedMillis(retryCompletedAt, verifiedAt),
                "totalMs", elapsedMillis(startedAt, verifiedAt)
        );
        reporter.publishEntry(metrics);
        LOGGER.info("Large house outbound submit scale: {}", metrics);
    }

    private ScaleFixture insertSaleReadyHouse(UserSession owner) {
        int rowCount = 20;
        long houseId = createHouse(owner, "large_outbound_submit_house", rowCount, 35, 1);
        List<Long> cageIds = jdbc.queryForList(
                "select id from cages where house_id = ? order by id",
                Long.class,
                houseId
        );
        Assertions.assertEquals(CAGE_COUNT, cageIds.size());

        List<Object[]> rabbitRows = new ArrayList<>(RABBIT_COUNT);
        for (int index = 0; index < RABBIT_COUNT; index++) {
            rabbitRows.add(new Object[]{
                    houseId,
                    cageIds.get(index / RABBITS_PER_CAGE),
                    index % 2 == 0 ? "0" : "1",
                    "large_outbound_submit_commodity",
                    "large-outbound-submit-rabbit-" + String.format("%05d", index),
                    String.valueOf(owner.userId),
                    String.valueOf(owner.userId)
            });
        }
        jdbc.batchUpdate(
                "insert into rabbits (house_id, cage_id, type, gender, breed, arrival_method, arrival_date, "
                        + "weight, state_version, is_active, is_quarantined, request_id, create_by, update_by) "
                        + "values (?, ?, '2', ?, ?, '2', now(), 2.6, 0, true, false, ?, ?, ?)",
                rabbitRows
        );
        jdbc.update(
                "update cages set status = '1', rabbit_count = ? where house_id = ?",
                RABBITS_PER_CAGE,
                houseId
        );

        String batchCode = "OUTBOUND-SUBMIT-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update(
                "insert into batches (house_id, batch_code, status, start_date, request_id, create_by, update_by) "
                        + "values (?, ?, '进行中', now(), ?, ?, ?)",
                houseId,
                batchCode,
                "large-outbound-submit-batch-" + UUID.randomUUID(),
                String.valueOf(owner.userId),
                String.valueOf(owner.userId)
        );
        Long batchId = jdbc.queryForObject(
                "select id from batches where house_id = ? and batch_code = ?",
                Long.class,
                houseId,
                batchCode
        );
        Assertions.assertNotNull(batchId);

        List<Long> rabbitIds = jdbc.queryForList(
                "select id from rabbits where house_id = ? order by request_id",
                Long.class,
                houseId
        );
        List<Object[]> batchRabbitRows = new ArrayList<>(RABBIT_COUNT);
        for (Long rabbitId : rabbitIds) {
            batchRabbitRows.add(new Object[]{
                    batchId,
                    rabbitId,
                    String.valueOf(owner.userId),
                    String.valueOf(owner.userId)
            });
        }
        jdbc.batchUpdate(
                "insert into batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, current_status, "
                        + "last_event_date, next_event_date, next_event_type, is_active, join_date, create_by, update_by) "
                        + "values (?, ?, '断奶转入', 'fattening', '可出售', now(), now(), '出售', true, now(), ?, ?)",
                batchRabbitRows
        );
        return new ScaleFixture(houseId, batchId, rowCount);
    }

    private void assertCommittedState(ScaleFixture fixture, String taskId, String requestId, long saleOrderId) {
        Assertions.assertEquals(1, count(
                "select count(*) from sale_orders where id = ? and house_id = ? and request_id = ?",
                saleOrderId,
                fixture.houseId,
                requestId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from sale_order_items where sale_order_id = ?",
                saleOrderId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(distinct rabbit_id) from sale_order_items where sale_order_id = ?",
                saleOrderId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from rabbits where house_id = ? and is_active = false "
                        + "and departure_reason = 'sale' and departure_date is not null",
                fixture.houseId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from rabbits where house_id = ? and is_active = false "
                        + "and departure_reason = 'sale' and state_version = 2",
                fixture.houseId
        ), "sale departure and active batch exit must each bump state_version once");
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'sale' "
                        + "and request_id like concat(?, '-%')",
                fixture.houseId,
                requestId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from rabbit_status_history h "
                        + "inner join rabbit_departure_records d on d.id = h.related_record_id "
                        + "where d.house_id = ? and d.request_id like concat(?, '-%') "
                        + "and h.to_status = '出售出栏' and h.related_record_table = 'rabbit_departure_records'",
                fixture.houseId,
                requestId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
                fixture.batchId
        ));
        Assertions.assertEquals("已完成", jdbc.queryForObject(
                "select status from batches where id = ? and house_id = ?",
                String.class,
                fixture.batchId,
                fixture.houseId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from cages where house_id = ? and (rabbit_count <> 0 or status <> '0')",
                fixture.houseId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from cages c where c.house_id = ? and c.rabbit_count <> ("
                        + "select count(*) from rabbits r where r.house_id = c.house_id "
                        + "and r.cage_id = c.id and r.is_active = true)",
                fixture.houseId
        ));
        Assertions.assertEquals("COMPLETED", jdbc.queryForObject(
                "select status from outbound_tasks where task_id = ? and house_id = ?",
                String.class,
                taskId,
                fixture.houseId
        ));
        Assertions.assertEquals(1, count(
                "select count(*) from outbound_requests where house_id = ? and request_id = ? "
                        + "and status = 'COMPLETED' and sale_order_id = ?",
                fixture.houseId,
                requestId,
                saleOrderId
        ));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String elapsedMillis(long startedAt, long endedAt) {
        return String.valueOf(TimeUnit.NANOSECONDS.toMillis(endedAt - startedAt));
    }

    private record ScaleFixture(long houseId, long batchId, int rowCount) {}
}
