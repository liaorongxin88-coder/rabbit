package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
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

/**
 * Verifies that a whole-house outbound draft can replace more task items than
 * the SQL write guard permits in a single DELETE statement.
 */
public class LargeHouseOutboundTaskScaleIT extends E2eTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeHouseOutboundTaskScaleIT.class);
    private static final int CAGE_COUNT = 700;
    private static final int RABBITS_PER_CAGE = 10;
    private static final int RABBIT_COUNT = CAGE_COUNT * RABBITS_PER_CAGE;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${app.mybatis.write-guard.max-affected-rows}")
    private int maxAffectedRows;

    @Test
    void wholeHouseDraftReplacesSevenThousandItemsBelowTheSqlGuardLimit(TestReporter reporter) {
        Assertions.assertTrue(RABBIT_COUNT > maxAffectedRows);
        UserSession owner = register("large_outbound_task");
        ScaleFixture fixture = insertSaleReadyHouse(owner);

        long createStartedAt = System.nanoTime();
        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, fixture.houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", false
        ));
        long taskCreatedAt = System.nanoTime();
        String taskId = task.get("taskId").asText();
        Assertions.assertEquals(RABBIT_COUNT, task.get("summary").get("normal").asInt());
        Assertions.assertEquals(RABBIT_COUNT, task.get("selectedItems").size());

        List<Map<String, Object>> selected = selectedInputs(task.get("selectedItems"));
        Map<String, Object> saveBody = obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selected,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", RABBIT_COUNT * 2.6,
                "unitPrice", 18.0,
                "customer", "large house scale customer",
                "remark", "freeze all sale-ready rabbits in one house"
        );

        JsonNode frozen = api.putOk(
                "/api/outbound/tasks/" + taskId,
                owner.token,
                fixture.houseId,
                saveBody
        );
        long taskSavedAt = System.nanoTime();
        Assertions.assertEquals("WAITING_CONFIRMATION", frozen.get("status").asText());
        Assertions.assertEquals(task.get("revision").asLong() + 1, frozen.get("revision").asLong());
        Assertions.assertEquals(RABBIT_COUNT, frozen.get("selectedItems").size());

        assertFrozenSnapshot(taskId, fixture.houseId);

        api.expectError(
                "/api/outbound/tasks/" + taskId,
                HttpMethod.PUT,
                owner.token,
                fixture.houseId,
                saveBody,
                409,
                "OUTBOUND_REVISION_CONFLICT"
        );
        long staleRevisionCheckedAt = System.nanoTime();
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from outbound_task_items where task_id = ?",
                taskId
        ));
        Assertions.assertEquals("WAITING_CONFIRMATION", jdbc.queryForObject(
                "select status from outbound_tasks where task_id = ?",
                String.class,
                taskId
        ));

        JsonNode resumed = api.postOk("/api/outbound/tasks", owner.token, fixture.houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", true
        ));
        long resumedAt = System.nanoTime();
        Assertions.assertEquals(taskId, resumed.get("taskId").asText());
        Assertions.assertTrue(resumed.get("resumed").asBoolean());
        Assertions.assertEquals(frozen.get("revision").asLong(), resumed.get("revision").asLong());
        Assertions.assertEquals(RABBIT_COUNT, resumed.get("selectedItems").size());
        Assertions.assertEquals(1, count(
                "select count(*) from outbound_tasks where house_id = ? and operator_id = ?",
                fixture.houseId,
                owner.userId
        ));

        long otherHouseId = createHouse(owner, "large_outbound_isolation_house", 1, 1, 1);
        api.expectError(
                "/api/outbound/tasks/" + taskId,
                HttpMethod.GET,
                owner.token,
                otherHouseId,
                null,
                404,
                "OUTBOUND_TASK_NOT_FOUND"
        );
        Assertions.assertEquals(0, count(
                "select count(*) from outbound_task_items oti "
                        + "join rabbits r on r.id = oti.rabbit_id "
                        + "where oti.task_id = ? and r.house_id <> ?",
                taskId,
                fixture.houseId
        ));
        long verifiedAt = System.nanoTime();

        Map<String, String> metrics = Map.of(
                "rabbits", String.valueOf(RABBIT_COUNT),
                "cages", String.valueOf(CAGE_COUNT),
                "sqlGuardRows", String.valueOf(maxAffectedRows),
                "createTaskMs", elapsedMillis(createStartedAt, taskCreatedAt),
                "saveTaskMs", elapsedMillis(taskCreatedAt, taskSavedAt),
                "staleRevisionMs", elapsedMillis(taskSavedAt, staleRevisionCheckedAt),
                "resumeMs", elapsedMillis(staleRevisionCheckedAt, resumedAt),
                "verificationMs", elapsedMillis(resumedAt, verifiedAt),
                "totalMs", elapsedMillis(createStartedAt, verifiedAt)
        );
        reporter.publishEntry(metrics);
        LOGGER.info("Large house outbound task scale baseline: {}", metrics);
    }

    private ScaleFixture insertSaleReadyHouse(UserSession owner) {
        long houseId = createHouse(owner, "large_outbound_task_house", 20, 35, 1);
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
                    "large_outbound_commodity",
                    "large-outbound-rabbit-" + String.format("%05d", index),
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

        String batchCode = "OUTBOUND-SCALE-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update(
                "insert into batches (house_id, batch_code, status, start_date, request_id, create_by, update_by) "
                        + "values (?, ?, '进行中', now(), ?, ?, ?)",
                houseId,
                batchCode,
                "large-outbound-batch-" + UUID.randomUUID(),
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
        List<Object[]> batchRabbitRows = new ArrayList<>(rabbitIds.size());
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

        Assertions.assertEquals(0, count(
                "select count(*) from cages c where c.house_id = ? and "
                        + "c.rabbit_count <> (select count(*) from rabbits r "
                        + "where r.house_id = c.house_id and r.cage_id = c.id and r.is_active = true)",
                houseId
        ));
        return new ScaleFixture(houseId);
    }

    private List<Map<String, Object>> selectedInputs(JsonNode selectedItems) {
        List<Map<String, Object>> inputs = new ArrayList<>(selectedItems.size());
        for (JsonNode item : selectedItems) {
            inputs.add(obj(
                    "rabbitId", item.get("rabbitId").asLong(),
                    "stateVersion", item.get("stateVersion").asLong(),
                    "selectionType", "NORMAL"
            ));
        }
        return inputs;
    }

    private void assertFrozenSnapshot(String taskId, long houseId) {
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from outbound_task_items where task_id = ?",
                taskId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(distinct rabbit_id) from outbound_task_items where task_id = ?",
                taskId
        ));
        Assertions.assertEquals(RABBIT_COUNT, count(
                "select count(*) from outbound_task_items where task_id = ? and selection_type = 'NORMAL'",
                taskId
        ));
        Assertions.assertEquals(CAGE_COUNT, count(
                "select count(distinct cage_id_snapshot) from outbound_task_items where task_id = ?",
                taskId
        ));
        Assertions.assertEquals(RABBITS_PER_CAGE, count(
                "select max(item_count) from (select count(*) item_count from outbound_task_items "
                        + "where task_id = ? group by cage_id_snapshot) counts",
                taskId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from outbound_task_items oti "
                        + "join rabbits r on r.id = oti.rabbit_id "
                        + "where oti.task_id = ? and r.house_id <> ?",
                taskId,
                houseId
        ));
    }

    private int count(String sql, Object... parameters) {
        Integer value = jdbc.queryForObject(sql, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private String elapsedMillis(long startedAt, long finishedAt) {
        return String.valueOf(TimeUnit.NANOSECONDS.toMillis(finishedAt - startedAt));
    }

    private static final class ScaleFixture {
        private final long houseId;

        private ScaleFixture(long houseId) {
            this.houseId = houseId;
        }
    }
}
