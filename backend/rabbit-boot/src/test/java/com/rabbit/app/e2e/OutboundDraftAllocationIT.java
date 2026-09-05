package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.outbound.entity.OutboundTaskBatchAllocation;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskBatchAllocationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboundDraftAllocationIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboundTaskBatchAllocationMapper allocationMapper;

    @Test
    void savesReloadsAndSubmitsAssignedAndUnassignedDraftAllocations() {
        UserSession owner = register("outbound_draft_allocations");
        long houseId = createHouse(owner, "出库草稿兔舍", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long assignedRabbitId = createRabbit(
            owner, houseId, cages.get(0), "2", "0", "assigned"
        );
        long unassignedRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", "unassigned"
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
        long assignedVersion = version(task, assignedRabbitId);
        long unassignedVersion = version(task, unassignedRabbitId);
        List<Object> selectedItems = List.of(
            obj(
                "rabbitId", assignedRabbitId,
                "stateVersion", assignedVersion,
                "selectionType", "NORMAL"
            ),
            obj(
                "rabbitId", unassignedRabbitId,
                "stateVersion", unassignedVersion,
                "selectionType", "NORMAL"
            )
        );
        List<Object> allocations = List.of(
            obj("batchId", batchId, "actualWeightKg", new BigDecimal("2.500")),
            obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
        );

        JsonNode initiallyWaiting = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selectedItems,
                "batchAllocations", List.of()
            )
        );
        Assertions.assertTrue(initiallyWaiting.get("batchAllocations").isEmpty());

        JsonNode partial = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", initiallyWaiting.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selectedItems,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("4.000"),
                "unitPricePerKg", new BigDecimal("12.00"),
                "batchAllocations", List.of(allocations.getFirst()),
                "customer", "收购商甲",
                "remark", "同一快照"
            )
        );
        Assertions.assertEquals(1, partial.get("batchAllocations").size());
        JsonNode partialReloaded = api.getOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId
        );
        Assertions.assertEquals(1, partialReloaded.get("batchAllocations").size());
        Assertions.assertEquals(batchId, partialReloaded.get("batchAllocations")
            .get(0).get("batchId").asLong());
        Assertions.assertEquals(1, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            task.get("taskId").asText()
        ));

        JsonNode cleared = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", partial.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selectedItems,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("4.000"),
                "unitPricePerKg", new BigDecimal("12.00"),
                "batchAllocations", List.of(),
                "customer", "收购商甲",
                "remark", "同一快照"
            )
        );
        Assertions.assertTrue(cleared.get("batchAllocations").isEmpty());
        Assertions.assertEquals(0, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            task.get("taskId").asText()
        ));

        JsonNode saved = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", cleared.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selectedItems,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("4.000"),
                "unitPricePerKg", new BigDecimal("12.00"),
                "batchAllocations", allocations,
                "customer", "收购商甲",
                "remark", "同一快照"
            )
        );

        assertDraftSnapshot(saved, batchId);
        String taskId = saved.get("taskId").asText();
        JsonNode reloaded = api.getOk("/api/outbound/tasks/" + taskId, owner.token, houseId);
        assertDraftSnapshot(reloaded, batchId);
        JsonNode resumed = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
            "entryType", "HOUSE",
            "resumeExisting", true
        ));
        Assertions.assertEquals(taskId, resumed.get("taskId").asText());
        Assertions.assertTrue(resumed.get("resumed").asBoolean());
        assertDraftSnapshot(resumed, batchId);

        List<OutboundTaskBatchAllocation> persisted = allocationMapper.selectByTask(
            houseId, taskId
        );
        Assertions.assertEquals(2, persisted.size());
        Assertions.assertEquals(new BigDecimal("2.500"), persisted.get(0).getActualWeightKg());
        Assertions.assertEquals(new BigDecimal("1.500"), persisted.get(1).getActualWeightKg());

        long otherHouseId = createHouse(owner, "出库草稿隔离兔舍", 1, 1, 1);
        api.expectError(
            "/api/outbound/tasks/" + taskId,
            HttpMethod.GET,
            owner.token,
            otherHouseId,
            null,
            404,
            "OUTBOUND_TASK_NOT_FOUND"
        );
        Assertions.assertTrue(allocationMapper.selectByTask(otherHouseId, taskId).isEmpty());
        api.expectError(
            "/api/outbound/tasks/" + taskId + "/submit",
            HttpMethod.POST,
            owner.token,
            otherHouseId,
            submitBodyFromTask(reloaded, UUID.randomUUID().toString()),
            404,
            "OUTBOUND_TASK_NOT_FOUND"
        );

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> submitBody = submitBodyFromTask(reloaded, requestId);
        JsonNode submitted = api.postOk(
            "/api/outbound/tasks/" + taskId + "/submit",
            owner.token,
            houseId,
            submitBody
        );
        Assertions.assertEquals(1, jdbc.update(
            "update outbound_requests set status = 'FAILED', sale_order_id = null, "
                + "error_code = 'STALE_STATUS', error_message = '模拟提交结果回写歧义' "
                + "where house_id = ? and request_id = ? and status = 'COMPLETED'",
            houseId,
            requestId
        ));
        Map<String, Object> equivalentRetry = new LinkedHashMap<>(submitBody);
        equivalentRetry.put("unitPrice", new BigDecimal("12.0"));
        equivalentRetry.put("batchAllocations", List.of(
            obj("batchId", null, "actualWeightKg", new BigDecimal("1.500")),
            obj("batchId", batchId, "actualWeightKg", new BigDecimal("2.500"))
        ));
        JsonNode retried = api.postOk(
            "/api/outbound/tasks/" + taskId + "/submit",
            owner.token,
            houseId,
            equivalentRetry
        );

        Assertions.assertEquals("COMPLETED", submitted.get("status").asText());
        Assertions.assertEquals(
            submitted.get("saleOrderId").asLong(), retried.get("saleOrderId").asLong()
        );
        Assertions.assertEquals("COMPLETED", jdbc.queryForObject(
            "select status from outbound_requests where house_id = ? and request_id = ?",
            String.class,
            houseId,
            requestId
        ));
        long saleOrderId = submitted.get("saleOrderId").asLong();
        Assertions.assertEquals(2, count(
            "select count(*) from sale_order_batch_allocations where sale_order_id = ?",
            saleOrderId
        ));
        Assertions.assertEquals(new BigDecimal("2.500"), jdbc.queryForObject(
            "select actual_weight_kg from sale_order_batch_allocations "
                + "where sale_order_id = ? and batch_id = ?",
            BigDecimal.class,
            saleOrderId,
            batchId
        ));
        Assertions.assertEquals(new BigDecimal("1.500"), jdbc.queryForObject(
            "select actual_weight_kg from sale_order_batch_allocations "
                + "where sale_order_id = ? and batch_id is null",
            BigDecimal.class,
            saleOrderId
        ));
        Assertions.assertEquals(new BigDecimal("48.00"), jdbc.queryForObject(
            "select sum(amount) from sale_order_batch_allocations where sale_order_id = ?",
            BigDecimal.class,
            saleOrderId
        ));
        Assertions.assertEquals(2, count(
            "select sum(rabbit_count) from sale_order_batch_allocations where sale_order_id = ?",
            saleOrderId
        ));
        Assertions.assertEquals(new BigDecimal("4.000"), jdbc.queryForObject(
            "select cast(total_weight as decimal(12,3)) from sale_orders where id = ?",
            BigDecimal.class,
            saleOrderId
        ));
        Assertions.assertEquals(new BigDecimal("12.00"), jdbc.queryForObject(
            "select unit_price from sale_orders where id = ?",
            BigDecimal.class,
            saleOrderId
        ));
        Assertions.assertEquals("收购商甲", jdbc.queryForObject(
            "select customer from sale_orders where id = ?", String.class, saleOrderId
        ));
        Assertions.assertEquals("同一快照", jdbc.queryForObject(
            "select remark from sale_orders where id = ?", String.class, saleOrderId
        ));
        Assertions.assertEquals(LocalDate.now().toString(), jdbc.queryForObject(
            "select date_format(sale_time, '%Y-%m-%d') from sale_orders where id = ?",
            String.class,
            saleOrderId
        ));
    }

    @Test
    void nullDraftItemReturnsValidationErrorInsteadOfServerFailure() {
        UserSession owner = register("outbound_null_draft_item");
        long houseId = createHouse(owner, "空草稿项兔舍", 1, 1, 1);

        api.expectError(
            "/api/outbound/tasks/not-claimed",
            HttpMethod.PUT,
            owner.token,
            houseId,
            obj(
                "revision", 0,
                "status", "SELECTING",
                "items", Collections.singletonList(null)
            ),
            400,
            "items不能包含空项"
        );
    }

    @Test
    void omittedAllocationsPreserveOnlyAnEquivalentRabbitSnapshot() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_omitted_allocations",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        JsonNode original = fixture.task();
        Map<String, Object> equivalent = obj(
            "revision", original.get("revision").asLong(),
            "status", "WAITING_CONFIRMATION",
            "items", original.get("selectedItems"),
            "saleTime", original.get("saleTime").asText(),
            "totalWeight", original.get("totalWeight").decimalValue(),
            "unitPricePerKg", original.get("unitPricePerKg").decimalValue(),
            "customer", original.get("customer").asText(),
            "remark", original.get("remark").asText()
        );

        JsonNode preserved = api.putOk(
            "/api/outbound/tasks/" + fixture.taskId(),
            fixture.owner().token,
            fixture.houseId(),
            equivalent
        );

        assertDraftSnapshot(preserved, fixture.batchId());
        Assertions.assertEquals(2, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            fixture.taskId()
        ));

        Map<String, Object> changed = obj(
            "revision", preserved.get("revision").asLong(),
            "status", "WAITING_CONFIRMATION",
            "items", List.of(preserved.get("selectedItems").get(0)),
            "saleTime", preserved.get("saleTime").asText(),
            "totalWeight", preserved.get("totalWeight").decimalValue(),
            "unitPricePerKg", preserved.get("unitPricePerKg").decimalValue(),
            "customer", preserved.get("customer").asText(),
            "remark", preserved.get("remark").asText()
        );

        JsonNode cleared = api.putOk(
            "/api/outbound/tasks/" + fixture.taskId(),
            fixture.owner().token,
            fixture.houseId(),
            changed
        );

        Assertions.assertTrue(cleared.get("batchAllocations").isEmpty());
        Assertions.assertEquals(0, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            fixture.taskId()
        ));
        JsonNode reloaded = api.getOk(
            "/api/outbound/tasks/" + fixture.taskId(),
            fixture.owner().token,
            fixture.houseId()
        );
        Assertions.assertTrue(reloaded.get("batchAllocations").isEmpty());
        Assertions.assertEquals(1, reloaded.get("selectedItems").size());
        Assertions.assertEquals(
            fixture.assignedRabbitId(),
            reloaded.get("selectedItems").get(0).get("rabbitId").asLong()
        );
    }

    @Test
    void runtimeRollbackAfterClaimCanRetryWithTheSameRequestId() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_runtime_retry",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = submitBodyFromTask(fixture.task(), requestId);
        jdbc.update(
            "insert into sale_orders (house_id, sale_time, total_weight, unit_price, "
                + "total_amount, request_id, create_by, update_by) "
                + "values (?, now(), 1.000, 1.00, 1.00, ?, 'e2e-blocker', 'e2e-blocker')",
            fixture.houseId(),
            requestId
        );
        long blockerId = jdbc.queryForObject(
            "select id from sale_orders where house_id = ? and request_id = ?",
            Long.class,
            fixture.houseId(),
            requestId
        );

        api.expectError(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            HttpMethod.POST,
            fixture.owner().token,
            fixture.houseId(),
            body,
            500,
            "系统异常，请稍后重试"
        );

        Assertions.assertEquals("FAILED", jdbc.queryForObject(
            "select status from outbound_requests where house_id = ? and request_id = ?",
            String.class,
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals("INTERNAL_ERROR", jdbc.queryForObject(
            "select error_code from outbound_requests where house_id = ? and request_id = ?",
            String.class,
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from sale_order_items where sale_order_id = ?",
            blockerId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from sale_order_batch_allocations where sale_order_id = ?",
            blockerId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbit_departure_records where house_id = ? "
                + "and rabbit_id in (?, ?)",
            fixture.houseId(),
            fixture.assignedRabbitId(),
            fixture.unassignedRabbitId()
        ));
        Map<String, Object> rolledBackTask = jdbc.queryForMap(
            "select status, request_id, sale_order_id from outbound_tasks "
                + "where house_id = ? and task_id = ?",
            fixture.houseId(),
            fixture.taskId()
        );
        Assertions.assertEquals("WAITING_CONFIRMATION", rolledBackTask.get("status"));
        Assertions.assertEquals(null, rolledBackTask.get("request_id"));
        Assertions.assertEquals(null, rolledBackTask.get("sale_order_id"));

        jdbc.update(
            "delete from sale_orders where house_id = ? and id = ?",
            fixture.houseId(),
            blockerId
        );
        JsonNode retried = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            body
        );

        Assertions.assertEquals("COMPLETED", retried.get("status").asText());
        Assertions.assertEquals("COMPLETED", jdbc.queryForObject(
            "select status from outbound_requests where house_id = ? and request_id = ?",
            String.class,
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from sale_orders where house_id = ? and request_id = ?",
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(2, count(
            "select count(*) from sale_order_items where sale_order_id = ?",
            retried.get("saleOrderId").asLong()
        ));
        Assertions.assertEquals(2, count(
            "select count(*) from sale_order_batch_allocations where sale_order_id = ?",
            retried.get("saleOrderId").asLong()
        ));
        Assertions.assertEquals(2, count(
            "select count(*) from rabbit_departure_records where house_id = ? "
                + "and rabbit_id in (?, ?)",
            fixture.houseId(),
            fixture.assignedRabbitId(),
            fixture.unassignedRabbitId()
        ));
    }

    @Test
    void finalSubmitRejectsChangedConfirmedFieldsWithoutPartialWrites() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_tamper",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        assertSnapshotRejected(fixture, body -> body.put("totalWeight", new BigDecimal("5.000")));
        assertSnapshotRejected(fixture, body -> body.put("unitPricePerKg", new BigDecimal("13.00")));
        assertSnapshotRejected(fixture, body -> body.put("saleTime", LocalDate.now().minusDays(1).toString()));
        assertSnapshotRejected(fixture, body -> body.put("customer", "另一个客户"));
        assertSnapshotRejected(fixture, body -> body.put("remark", "修改后的备注"));
        assertSnapshotRejected(fixture, body -> body.put("batchAllocations", List.of(
            obj("batchId", fixture.batchId(), "actualWeightKg", new BigDecimal("2.400")),
            obj("batchId", null, "actualWeightKg", new BigDecimal("1.600"))
        )));
    }

    @Test
    void finalSubmitRejectsAnIncompletePersistedSnapshotWithoutPartialWrites() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_incomplete",
            List.of(obj(
                "batchId", 0L,
                "actualWeightKg", new BigDecimal("2.500")
            ))
        );
        String requestId = UUID.randomUUID().toString();

        JsonNode result = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            submitBodyFromTask(fixture.task(), requestId)
        );

        Assertions.assertEquals("FAILED", result.get("status").asText());
        Assertions.assertTrue(result.get("message").asText().contains("必须覆盖全部批次"));
        assertNoPartialWrites(fixture, requestId, true);
    }

    @Test
    void batchMembershipChangeAfterConfirmationIsAConflictWithoutPartialWrites() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_batch_change",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        Long beforeVersion = jdbc.queryForObject(
            "select state_version from rabbits where house_id = ? and id = ?",
            Long.class,
            fixture.houseId(),
            fixture.assignedRabbitId()
        );
        jdbc.update(
            "update batch_rabbits set is_active = false where batch_id = ? and rabbit_id = ?",
            fixture.batchId(),
            fixture.assignedRabbitId()
        );
        Assertions.assertEquals(beforeVersion, jdbc.queryForObject(
            "select state_version from rabbits where house_id = ? and id = ?",
            Long.class,
            fixture.houseId(),
            fixture.assignedRabbitId()
        ));
        String requestId = UUID.randomUUID().toString();

        JsonNode result = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            submitBodyFromTask(fixture.task(), requestId)
        );

        Assertions.assertEquals("CONFLICT", result.get("status").asText());
        Assertions.assertTrue(result.get("conflicts").findValuesAsText("errorCode")
            .contains("RABBIT_BATCH_CHANGED"));
        assertNoPartialWrites(fixture, requestId, false);
    }

    @Test
    void failedBusinessTransactionCanBeRetriedWithTheSameRequestId() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_retry",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        Long cageId = jdbc.queryForObject(
            "select cage_id from rabbits where house_id = ? and id = ?",
            Long.class,
            fixture.houseId(),
            fixture.assignedRabbitId()
        );
        jdbc.update("update cages set rabbit_count = 0 where house_id = ? and id = ?",
            fixture.houseId(), cageId);
        String requestId = UUID.randomUUID().toString();
        Object body = submitBodyFromTask(fixture.task(), requestId);

        JsonNode failed = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            body
        );
        Assertions.assertEquals("FAILED", failed.get("status").asText());
        assertNoPartialWrites(fixture, requestId, true);
        jdbc.update("update cages set rabbit_count = 1 where house_id = ? and id = ?",
            fixture.houseId(), cageId);

        JsonNode retried = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            body
        );

        Assertions.assertEquals("COMPLETED", retried.get("status").asText());
        Assertions.assertEquals("COMPLETED", jdbc.queryForObject(
            "select status from outbound_requests where house_id = ? and request_id = ?",
            String.class,
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from sale_orders where house_id = ? and request_id = ?",
            fixture.houseId(),
            requestId
        ));
    }

    @Test
    void surplusMapKeysFailBeforeClaimAndCorrectedPayloadCanUseTheRequestId() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_shape",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = submitBodyFromTask(fixture.task(), requestId);
        Map<String, Long> surplusVersions = stateVersionsFromTask(fixture.task());
        surplusVersions.put("999999", 0L);
        body.put("stateVersions", surplusVersions);

        api.expectError(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            HttpMethod.POST,
            fixture.owner().token,
            fixture.houseId(),
            body,
            400,
            "stateVersions必须与rabbitIds完全一致"
        );
        Assertions.assertEquals(0, count(
            "select count(*) from outbound_requests where house_id = ? and request_id = ?",
            fixture.houseId(),
            requestId
        ));

        body.put("stateVersions", stateVersionsFromTask(fixture.task()));
        body.put("earlySaleReasons", Map.of("999999", "未选择兔只"));
        api.expectError(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            HttpMethod.POST,
            fixture.owner().token,
            fixture.houseId(),
            body,
            400,
            "earlySaleReasons包含未选择的兔只"
        );
        Assertions.assertEquals(0, count(
            "select count(*) from outbound_requests where house_id = ? and request_id = ?",
            fixture.houseId(),
            requestId
        ));

        body.put("earlySaleReasons", Map.of());
        JsonNode corrected = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            body
        );
        Assertions.assertEquals("COMPLETED", corrected.get("status").asText());
        Assertions.assertEquals(1, count(
            "select count(*) from outbound_requests where house_id = ? and request_id = ? "
                + "and status = 'COMPLETED'",
            fixture.houseId(),
            requestId
        ));
    }

    @Test
    void legacyMixedSubmissionRecordsAllocationAndPriceGapsWhenEnabled() {
        DraftFixture fixture = createLegacyMixedDraft("outbound_legacy_gaps", null);
        JsonNode reloaded = api.getOk(
            "/api/outbound/tasks/" + fixture.taskId(),
            fixture.owner().token,
            fixture.houseId()
        );
        String requestId = UUID.randomUUID().toString();

        JsonNode result = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            legacySubmitBodyFromTask(reloaded, requestId)
        );

        Assertions.assertEquals("COMPLETED", result.get("status").asText());
        long saleOrderId = result.get("saleOrderId").asLong();
        Assertions.assertEquals(0, count(
            "select count(*) from sale_order_batch_allocations where sale_order_id = ?",
            saleOrderId
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from sale_orders where id = ? and unit_price is null "
                + "and total_amount is null",
            saleOrderId
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from repro_events where house_id = ? and request_id = ? "
                + "and batch_id = ? and event_type = 'LEGACY_SALE_ALLOCATION_GAP'",
            fixture.houseId(),
            requestId,
            fixture.batchId()
        ));
        Assertions.assertEquals(1, count(
            "select count(*) from repro_events where house_id = ? and request_id = ? "
                + "and batch_id = ? and event_type = 'LEGACY_SALE_PRICE_GAP'",
            fixture.houseId(),
            requestId,
            fixture.batchId()
        ));
    }

    @Test
    void legacySingleBatchSubmissionAutoAllocatesWithoutGapWhenEnabled() {
        UserSession owner = register("outbound_legacy_single");
        long houseId = createHouse(owner, "旧单批出库兔舍", 1, 1, 1);
        long rabbitId = createRabbit(
            owner, houseId, cageIds(owner, houseId).getFirst(), "2", "0", "legacy-single"
        );
        long batchId = attachSaleStage(houseId, rabbitId);
        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
            "entryType", "RABBIT",
            "rabbitId", rabbitId,
            "resumeExisting", false
        ));
        JsonNode saved = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(obj(
                    "rabbitId", rabbitId,
                    "stateVersion", version(task, rabbitId),
                    "selectionType", "NORMAL"
                )),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("2.500"),
                "unitPrice", new BigDecimal("12.00")
            )
        );
        JsonNode reloaded = api.getOk(
            "/api/outbound/tasks/" + saved.get("taskId").asText(), owner.token, houseId
        );
        String requestId = UUID.randomUUID().toString();

        JsonNode result = api.postOk(
            "/api/outbound/tasks/" + saved.get("taskId").asText() + "/submit",
            owner.token,
            houseId,
            legacySubmitBodyFromTask(reloaded, requestId)
        );

        Assertions.assertEquals("COMPLETED", result.get("status").asText());
        long saleOrderId = result.get("saleOrderId").asLong();
        Assertions.assertEquals(1, count(
            "select count(*) from sale_order_batch_allocations where sale_order_id = ? "
                + "and house_id = ? and batch_id = ? and actual_weight_kg = 2.500 "
                + "and unit_price_per_kg = 12.00 and amount = 30.00",
            saleOrderId,
            houseId,
            batchId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from repro_events where house_id = ? and request_id = ? "
                + "and event_type in ('LEGACY_SALE_ALLOCATION_GAP', "
                + "'LEGACY_SALE_PRICE_GAP')",
            houseId,
            requestId
        ));
    }

    @Test
    void draftAllocationSchemaEnforcesTenantScopeUniquenessAndTaskCascade() {
        DraftFixture fixture = createMixedDraft(
            "outbound_draft_schema",
            List.of(
                obj("batchId", 0L, "actualWeightKg", new BigDecimal("2.500")),
                obj("batchId", null, "actualWeightKg", new BigDecimal("1.500"))
            )
        );
        long otherHouseId = createHouse(fixture.owner(), "草稿约束异舍", 1, 1, 1);
        long otherRabbitId = createRabbit(
            fixture.owner(), otherHouseId, cageIds(fixture.owner(), otherHouseId).getFirst(),
            "2", "0", "other-house"
        );
        long otherBatchId = attachSaleStage(otherHouseId, otherRabbitId);

        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "insert into outbound_task_batch_allocations "
                + "(task_id, house_id, batch_id, actual_weight_kg) values (?, ?, null, 1.000)",
            fixture.taskId(),
            otherHouseId
        ));
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "insert into outbound_task_batch_allocations "
                + "(task_id, house_id, batch_id, actual_weight_kg) values (?, ?, ?, 1.000)",
            fixture.taskId(),
            fixture.houseId(),
            otherBatchId
        ));
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "insert into outbound_task_batch_allocations "
                + "(task_id, house_id, batch_id, actual_weight_kg) values (?, ?, null, 1.000)",
            fixture.taskId(),
            fixture.houseId()
        ));
        Assertions.assertEquals(0, allocationMapper.deleteByTaskLimited(
            otherHouseId,
            fixture.taskId(),
            1_000
        ));
        Assertions.assertEquals(2, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            fixture.taskId()
        ));

        jdbc.update("delete from outbound_task_items where task_id = ?", fixture.taskId());
        jdbc.update(
            "delete from outbound_tasks where house_id = ? and task_id = ?",
            fixture.houseId(),
            fixture.taskId()
        );
        Assertions.assertEquals(0, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            fixture.taskId()
        ));
    }

    @Test
    void legacyDraftWithoutAllocationsRemainsReadable() {
        UserSession owner = register("outbound_legacy_draft");
        long houseId = createHouse(owner, "旧出库草稿兔舍", 1, 1, 1);
        long rabbitId = createRabbit(
            owner, houseId, cageIds(owner, houseId).getFirst(), "2", "0", "legacy"
        );
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE' where house_id = ? and id = ?",
            houseId,
            rabbitId
        );
        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
            "entryType", "RABBIT",
            "rabbitId", rabbitId,
            "resumeExisting", false
        ));

        JsonNode saved = api.putOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(obj(
                    "rabbitId", rabbitId,
                    "stateVersion", version(task, rabbitId),
                    "selectionType", "NORMAL"
                )),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("2.500"),
                "unitPrice", new BigDecimal("10.00")
            )
        );
        JsonNode reloaded = api.getOk(
            "/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId
        );

        Assertions.assertTrue(saved.get("batchAllocations").isArray());
        Assertions.assertTrue(saved.get("batchAllocations").isEmpty());
        Assertions.assertEquals(
            0,
            new BigDecimal("10.00").compareTo(saved.get("unitPricePerKg").decimalValue())
        );
        Assertions.assertTrue(reloaded.get("batchAllocations").isEmpty());
        Assertions.assertEquals(0, count(
            "select count(*) from outbound_task_batch_allocations where task_id = ?",
            task.get("taskId").asText()
        ));
    }

    private DraftFixture createLegacyMixedDraft(String prefix, BigDecimal unitPrice) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, "旧混批出库-" + prefix, 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long assignedRabbitId = createRabbit(
            owner, houseId, cages.get(0), "2", "0", prefix + "-assigned"
        );
        long unassignedRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", prefix + "-unassigned"
        );
        long batchId = attachSaleStage(houseId, assignedRabbitId);
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE' where house_id = ? and id = ?",
            houseId,
            unassignedRabbitId
        );
        JsonNode created = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
            "entryType", "HOUSE",
            "resumeExisting", false
        ));
        Map<String, Object> draft = obj(
            "revision", created.get("revision").asLong(),
            "status", "WAITING_CONFIRMATION",
            "items", List.of(
                obj(
                    "rabbitId", assignedRabbitId,
                    "stateVersion", version(created, assignedRabbitId),
                    "selectionType", "NORMAL"
                ),
                obj(
                    "rabbitId", unassignedRabbitId,
                    "stateVersion", version(created, unassignedRabbitId),
                    "selectionType", "NORMAL"
                )
            ),
            "saleTime", LocalDate.now().toString(),
            "totalWeight", new BigDecimal("4.000"),
            "customer", "旧客户端客户",
            "remark", "旧客户端提交"
        );
        if (unitPrice != null) {
            draft.put("unitPrice", unitPrice);
        }
        JsonNode saved = api.putOk(
            "/api/outbound/tasks/" + created.get("taskId").asText(),
            owner.token,
            houseId,
            draft
        );
        return new DraftFixture(
            owner,
            houseId,
            batchId,
            assignedRabbitId,
            unassignedRabbitId,
            saved
        );
    }

    private DraftFixture createMixedDraft(String prefix, List<Object> allocationTemplates) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, "出库草稿-" + prefix, 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long assignedRabbitId = createRabbit(
            owner, houseId, cages.get(0), "2", "0", prefix + "-assigned"
        );
        long unassignedRabbitId = createRabbit(
            owner, houseId, cages.get(1), "2", "1", prefix + "-unassigned"
        );
        long batchId = attachSaleStage(houseId, assignedRabbitId);
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE' where house_id = ? and id = ?",
            houseId,
            unassignedRabbitId
        );
        JsonNode created = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
            "entryType", "HOUSE",
            "resumeExisting", false
        ));
        List<Object> selectedItems = List.of(
            obj(
                "rabbitId", assignedRabbitId,
                "stateVersion", version(created, assignedRabbitId),
                "selectionType", "NORMAL"
            ),
            obj(
                "rabbitId", unassignedRabbitId,
                "stateVersion", version(created, unassignedRabbitId),
                "selectionType", "NORMAL"
            )
        );
        List<Object> allocations = new ArrayList<>();
        for (Object template : allocationTemplates) {
            Map<?, ?> row = (Map<?, ?>) template;
            Object rawBatchId = row.get("batchId");
            Long resolvedBatchId = rawBatchId == null ? null : ((Number) rawBatchId).longValue();
            if (Long.valueOf(0L).equals(resolvedBatchId)) {
                resolvedBatchId = batchId;
            }
            allocations.add(obj(
                "batchId", resolvedBatchId,
                "actualWeightKg", row.get("actualWeightKg")
            ));
        }
        JsonNode saved = api.putOk(
            "/api/outbound/tasks/" + created.get("taskId").asText(),
            owner.token,
            houseId,
            obj(
                "revision", created.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selectedItems,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", new BigDecimal("4.000"),
                "unitPricePerKg", new BigDecimal("12.00"),
                "batchAllocations", allocations,
                "customer", "收购商甲",
                "remark", "同一快照"
            )
        );
        return new DraftFixture(
            owner,
            houseId,
            batchId,
            assignedRabbitId,
            unassignedRabbitId,
            saved
        );
    }

    private Map<String, Object> submitBodyFromTask(JsonNode task, String requestId) {
        List<Long> rabbitIds = new ArrayList<>();
        Map<String, Long> stateVersions = stateVersionsFromTask(task);
        Map<String, String> earlySaleReasons = new LinkedHashMap<>();
        for (JsonNode item : task.get("selectedItems")) {
            long rabbitId = item.get("rabbitId").asLong();
            rabbitIds.add(rabbitId);
            if (item.hasNonNull("earlySaleReason")) {
                earlySaleReasons.put(
                    String.valueOf(rabbitId),
                    item.get("earlySaleReason").asText()
                );
            }
        }
        return obj(
            "rabbitIds", rabbitIds,
            "stateVersions", stateVersions,
            "earlySaleReasons", earlySaleReasons,
            "saleTime", task.get("saleTime").asText(),
            "totalWeight", task.get("totalWeight").decimalValue(),
            "unitPricePerKg", task.get("unitPricePerKg").decimalValue(),
            "batchAllocations", task.get("batchAllocations"),
            "customer", task.hasNonNull("customer") ? task.get("customer").asText() : null,
            "remark", task.hasNonNull("remark") ? task.get("remark").asText() : null,
            "requestId", requestId
        );
    }

    private Map<String, Object> legacySubmitBodyFromTask(JsonNode task, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Long> rabbitIds = new ArrayList<>();
        Map<String, String> earlySaleReasons = new LinkedHashMap<>();
        for (JsonNode item : task.get("selectedItems")) {
            long rabbitId = item.get("rabbitId").asLong();
            rabbitIds.add(rabbitId);
            if (item.hasNonNull("earlySaleReason")) {
                earlySaleReasons.put(
                    String.valueOf(rabbitId),
                    item.get("earlySaleReason").asText()
                );
            }
        }
        body.put("rabbitIds", rabbitIds);
        body.put("stateVersions", stateVersionsFromTask(task));
        body.put("earlySaleReasons", earlySaleReasons);
        body.put("saleTime", task.get("saleTime").asText());
        body.put("totalWeight", task.get("totalWeight").decimalValue());
        if (task.hasNonNull("unitPrice")) {
            body.put("unitPrice", task.get("unitPrice").decimalValue());
        }
        body.put("customer", task.hasNonNull("customer")
            ? task.get("customer").asText() : null);
        body.put("remark", task.hasNonNull("remark") ? task.get("remark").asText() : null);
        body.put("requestId", requestId);
        return body;
    }

    private Map<String, Long> stateVersionsFromTask(JsonNode task) {
        Map<String, Long> stateVersions = new LinkedHashMap<>();
        for (JsonNode item : task.get("selectedItems")) {
            stateVersions.put(
                item.get("rabbitId").asText(),
                item.get("stateVersion").asLong()
            );
        }
        return stateVersions;
    }

    private void assertSnapshotRejected(
        DraftFixture fixture,
        Consumer<Map<String, Object>> mutation
    ) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = submitBodyFromTask(fixture.task(), requestId);
        mutation.accept(body);

        JsonNode result = api.postOk(
            "/api/outbound/tasks/" + fixture.taskId() + "/submit",
            fixture.owner().token,
            fixture.houseId(),
            body
        );

        Assertions.assertEquals("FAILED", result.get("status").asText());
        assertNoPartialWrites(fixture, requestId, true);
    }

    private void assertNoPartialWrites(
        DraftFixture fixture,
        String requestId,
        boolean batchMembershipRemainsActive
    ) {
        Assertions.assertEquals(0, count(
            "select count(*) from sale_orders where house_id = ? and request_id = ?",
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from sale_order_batch_allocations allocation "
                + "join sale_orders sale on sale.id = allocation.sale_order_id "
                + "where sale.house_id = ? and sale.request_id = ?",
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from repro_events where house_id = ? and request_id = ?",
            fixture.houseId(),
            requestId
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbit_departure_records where house_id = ? "
                + "and rabbit_id in (?, ?)",
            fixture.houseId(),
            fixture.assignedRabbitId(),
            fixture.unassignedRabbitId()
        ));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbit_status_history where house_id = ? "
                + "and rabbit_id in (?, ?) and to_status = '出售出栏'",
            fixture.houseId(),
            fixture.assignedRabbitId(),
            fixture.unassignedRabbitId()
        ));
        Assertions.assertEquals(2, count(
            "select count(*) from rabbits where house_id = ? and id in (?, ?) "
                + "and is_active = true and departure_date is null",
            fixture.houseId(),
            fixture.assignedRabbitId(),
            fixture.unassignedRabbitId()
        ));
        Assertions.assertEquals(batchMembershipRemainsActive ? 1 : 0, count(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ? "
                + "and is_active = true",
            fixture.batchId(),
            fixture.assignedRabbitId()
        ));
        Map<String, Object> taskState = jdbc.queryForMap(
            "select status, request_id from outbound_tasks where house_id = ? and task_id = ?",
            fixture.houseId(),
            fixture.taskId()
        );
        Assertions.assertEquals("WAITING_CONFIRMATION", taskState.get("status"));
        Assertions.assertEquals(null, taskState.get("request_id"));
    }

    private void assertDraftSnapshot(JsonNode task, long batchId) {
        Assertions.assertEquals(
            0,
            new BigDecimal("12.00").compareTo(task.get("unitPrice").decimalValue())
        );
        Assertions.assertEquals(
            task.get("unitPrice").decimalValue(), task.get("unitPricePerKg").decimalValue()
        );
        JsonNode allocations = task.get("batchAllocations");
        Assertions.assertEquals(2, allocations.size());
        Assertions.assertEquals(batchId, allocations.get(0).get("batchId").asLong());
        Assertions.assertEquals(
            0,
            new BigDecimal("2.500").compareTo(
                allocations.get(0).get("actualWeightKg").decimalValue()
            )
        );
        Assertions.assertTrue(allocations.get(1).get("batchId").isNull());
        Assertions.assertEquals(
            0,
            new BigDecimal("1.500").compareTo(
                allocations.get(1).get("actualWeightKg").decimalValue()
            )
        );
    }

    private long attachSaleStage(long houseId, long rabbitId) {
        String batchRequestId = requestId("outbound_draft_batch");
        jdbc.update(
            "insert into batches (house_id, batch_code, status, start_date, request_id, "
                + "create_by, update_by) values (?, ?, '进行中', now(), ?, 'e2e', 'e2e')",
            houseId,
            "DRAFT-" + rabbitId,
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

    private record DraftFixture(
        UserSession owner,
        long houseId,
        long batchId,
        long assignedRabbitId,
        long unassignedRabbitId,
        JsonNode task
    ) {
        private String taskId() {
            return task.get("taskId").asText();
        }
    }
}
