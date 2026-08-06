package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class BatchOutboundIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitService rabbitService;

    @Test
    void legacyBatchSaleCannotBypassOutboundEligibility() {
        UserSession owner = register("legacy_sale_guard");
        long houseId = createHouse(owner, "legacy_sale_guard_house", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long normalRabbit = createRabbit(owner, houseId, cages.get(0), "2", "0", "normal");
        long earlyRabbit = createRabbit(owner, houseId, cages.get(1), "2", "1", "early");
        long quarantinedRabbit = createRabbit(owner, houseId, cages.get(2), "2", "0", "quarantined");
        long normalBatch = attachSaleStage(houseId, normalRabbit, -1);
        long earlyBatch = attachSaleStage(houseId, earlyRabbit, 7);
        long quarantinedBatch = attachSaleStage(houseId, quarantinedRabbit, -1);
        jdbc.update("update rabbits set is_quarantined = true where id = ?", quarantinedRabbit);

        api.expectError("/api/batches/" + earlyBatch + "/sale", HttpMethod.POST, owner.token, houseId, obj(
                "rabbitIds", List.of(earlyRabbit),
                "saleDate", LocalDate.now().toString(),
                "requestId", requestId("legacy_early_sale")
        ), 409, "请使用安全出库流程");
        api.expectError("/api/batches/" + quarantinedBatch + "/sale", HttpMethod.POST, owner.token, houseId, obj(
                "rabbitIds", List.of(quarantinedRabbit),
                "saleDate", LocalDate.now().toString(),
                "requestId", requestId("legacy_quarantined_sale")
        ), 409, "请使用安全出库流程");

        api.postOk("/api/batches/" + normalBatch + "/sale", owner.token, houseId, obj(
                "rabbitIds", List.of(normalRabbit),
                "saleDate", LocalDate.now().toString(),
                "requestId", requestId("legacy_normal_sale")
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from rabbits where id = ? and is_active = true", Integer.class, normalRabbit));
        Assertions.assertEquals(2, jdbc.queryForObject(
                "select count(*) from rabbits where id in (?, ?) and is_active = true", Integer.class,
                earlyRabbit, quarantinedRabbit));
    }

    @Test
    void mixedEligibilitySubmitRetryAndPayloadMismatch() {
        UserSession owner = register("outbound_owner");
        long houseId = createHouse(owner, "outbound_house", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long normalRabbit = createRabbit(owner, houseId, cages.get(0), "2", "0", "normal");
        long earlyRabbit = createRabbit(owner, houseId, cages.get(1), "2", "1", "early");
        attachSaleStage(houseId, normalRabbit, -1);
        attachSaleStage(houseId, earlyRabbit, 7);

        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", true
        ));
        Assertions.assertEquals(1, task.get("summary").get("normal").asInt());
        Assertions.assertEquals(1, task.get("summary").get("earlySale").asInt());
        Assertions.assertEquals(1, task.get("selectedItems").size());

        long normalVersion = version(task, normalRabbit);
        long earlyVersion = version(task, earlyRabbit);
        JsonNode frozen = api.putOk("/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId, obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(
                        obj("rabbitId", normalRabbit, "stateVersion", normalVersion, "selectionType", "NORMAL"),
                        obj("rabbitId", earlyRabbit, "stateVersion", earlyVersion, "selectionType", "EARLY_SALE", "earlySaleReason", "客户提前采购")
                ),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 7.2,
                "unitPrice", 18.0,
                "customer", "e2e客户"
        ));
        Assertions.assertEquals("WAITING_CONFIRMATION", frozen.get("status").asText());

        String requestId = UUID.randomUUID().toString();
        Object submitBody = obj(
                "rabbitIds", List.of(normalRabbit, earlyRabbit),
                "stateVersions", obj(String.valueOf(normalRabbit), normalVersion, String.valueOf(earlyRabbit), earlyVersion),
                "earlySaleReasons", obj(String.valueOf(earlyRabbit), "客户提前采购"),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 7.2,
                "unitPrice", 18.0,
                "customer", "e2e客户",
                "requestId", requestId
        );
        JsonNode first = api.postOk("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit", owner.token, houseId, submitBody);
        JsonNode retry = api.postOk("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit", owner.token, houseId, submitBody);
        Assertions.assertEquals("COMPLETED", first.get("status").asText());
        Assertions.assertEquals(first.get("saleOrderId").asLong(), retry.get("saleOrderId").asLong());
        Assertions.assertEquals(2, first.get("rabbitCount").asInt());

        String secondRequestId = UUID.randomUUID().toString();
        JsonNode secondRequest = api.postOk(
                "/api/outbound/tasks/" + task.get("taskId").asText() + "/submit",
                owner.token, houseId, obj(
                        "rabbitIds", List.of(normalRabbit, earlyRabbit),
                        "stateVersions", obj(String.valueOf(normalRabbit), normalVersion,
                                String.valueOf(earlyRabbit), earlyVersion),
                        "earlySaleReasons", obj(String.valueOf(earlyRabbit), "客户提前采购"),
                        "saleTime", LocalDate.now().toString(),
                        "totalWeight", 7.2,
                        "unitPrice", 18.0,
                        "customer", "e2e客户",
                        "requestId", secondRequestId
                ));
        Assertions.assertEquals("FAILED", secondRequest.get("status").asText());
        Assertions.assertEquals("OUTBOUND_TASK_COMPLETED_USE_ORIGINAL_REQUEST",
                secondRequest.get("errorCode").asText());

        api.expectError("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit", HttpMethod.POST, owner.token, houseId, obj(
                "rabbitIds", List.of(normalRabbit, earlyRabbit),
                "stateVersions", obj(String.valueOf(normalRabbit), normalVersion, String.valueOf(earlyRabbit), earlyVersion),
                "earlySaleReasons", obj(String.valueOf(earlyRabbit), "客户提前采购"),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 8.8,
                "requestId", requestId
        ), 409, "REQUEST_ID_PAYLOAD_MISMATCH");

        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from sale_orders where house_id = ?", Integer.class, houseId));
        Assertions.assertEquals(2, jdbc.queryForObject("select count(*) from sale_order_items where sale_order_id = ?", Integer.class, first.get("saleOrderId").asLong()));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from rabbits where id in (?, ?) and is_active = true", Integer.class, normalRabbit, earlyRabbit));
        Assertions.assertEquals(2, jdbc.queryForObject("select count(*) from sale_order_items where sale_order_id = ? and cage_id_snapshot is not null and state_version_snapshot is not null and parallel_status_snapshot is not null", Integer.class, first.get("saleOrderId").asLong()));
    }

    @Test
    void goldenHouseScenarioClassifiesBlockersFreezesScopeAndCommitsAcrossBatches() {
        UserSession owner = register("outbound_golden");
        long houseId = createHouse(owner, "outbound_golden_house", 2, 6, 1);
        List<Long> cages = cageIds(owner, houseId);

        long normalRowOne = createRabbit(owner, houseId, cages.get(0), "2", "0", "normal_row_one");
        long earlySale = createRabbit(owner, houseId, cages.get(1), "2", "1", "early_sale");
        long quarantined = createRabbit(owner, houseId, cages.get(2), "2", "0", "quarantined");
        long inTreatment = createRabbit(owner, houseId, cages.get(3), "2", "1", "treatment");
        long unresolvedAbnormal = createRabbit(owner, houseId, cages.get(4), "2", "0", "abnormal");
        long nonCommodity = createRabbit(owner, houseId, cages.get(5), "0", "1", "breeding");
        long normalRowTwo = createRabbit(owner, houseId, cages.get(6), "2", "1", "normal_row_two");
        long missingStage = createRabbit(owner, houseId, cages.get(8), "2", "0", "missing_stage");
        long disabledCageRabbit = createRabbit(owner, houseId, cages.get(9), "2", "1", "disabled_cage");

        attachSaleStage(houseId, normalRowOne, -1);
        attachSaleStage(houseId, earlySale, 7);
        attachSaleStage(houseId, quarantined, -1);
        attachSaleStage(houseId, inTreatment, -1);
        attachSaleStage(houseId, unresolvedAbnormal, -1);
        attachSaleStage(houseId, normalRowTwo, -1);
        attachSaleStage(houseId, disabledCageRabbit, -1);
        jdbc.update("update rabbits set is_quarantined = true, state_version = state_version + 1 where id = ?",
                quarantined);
        jdbc.update("insert into treatment_records (house_id, rabbit_id, start_date, status, request_id, create_by, update_by) values (?, ?, now(), 'OPEN', ?, 'e2e', 'e2e')",
                houseId, inTreatment, requestId("golden_treatment"));
        jdbc.update("insert into rabbit_abnormal_conditions (rabbit_id, house_id, warning_status, warning_time, is_deal, create_by, update_by) values (?, ?, '待处理', now(), false, 'e2e', 'e2e')",
                unresolvedAbnormal, houseId);
        jdbc.update("update cages set is_enabled = false where id = ?", cages.get(9));

        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", true
        ));
        Assertions.assertEquals(2, task.get("summary").get("normal").asInt());
        Assertions.assertEquals(1, task.get("summary").get("earlySale").asInt());
        Assertions.assertEquals(1, task.get("summary").get("needsAction").asInt());
        Assertions.assertEquals(5, task.get("summary").get("blocked").asInt());
        Assertions.assertEquals(2, task.get("selectedItems").size());
        Assertions.assertTrue(selectedContains(task, normalRowOne));
        Assertions.assertTrue(selectedContains(task, normalRowTwo));
        Assertions.assertFalse(selectedContains(task, earlySale));
        Assertions.assertEquals("EARLY_SALE_CONFIRMATION_REQUIRED", reasonCode(task, earlySale));
        Assertions.assertEquals("RABBIT_QUARANTINED", reasonCode(task, quarantined));
        Assertions.assertEquals("RABBIT_IN_TREATMENT", reasonCode(task, inTreatment));
        Assertions.assertEquals("RABBIT_ABNORMAL_UNRESOLVED", reasonCode(task, unresolvedAbnormal));
        Assertions.assertEquals("RABBIT_NOT_COMMODITY", reasonCode(task, nonCommodity));
        Assertions.assertEquals("COMMODITY_STAGE_MISSING", reasonCode(task, missingStage));
        Assertions.assertEquals("CAGE_DISABLED", reasonCode(task, disabledCageRabbit));

        long firstVersion = version(task, normalRowOne);
        long earlyVersion = version(task, earlySale);
        long secondVersion = version(task, normalRowTwo);
        JsonNode frozen = api.putOk("/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId, obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(
                        obj("rabbitId", normalRowOne, "stateVersion", firstVersion, "selectionType", "NORMAL"),
                        obj("rabbitId", earlySale, "stateVersion", earlyVersion, "selectionType", "EARLY_SALE",
                                "earlySaleReason", "客户提前采购"),
                        obj("rabbitId", normalRowTwo, "stateVersion", secondVersion, "selectionType", "NORMAL")
                ),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 9.6,
                "unitPrice", 18.0,
                "customer", "黄金场景客户",
                "remark", "冻结后新增兔不得自动加入"
        ));
        Assertions.assertEquals("WAITING_CONFIRMATION", frozen.get("status").asText());
        Assertions.assertEquals(3, frozen.get("selectedItems").size());

        long arrivedAfterFreeze = createRabbit(owner, houseId, cages.get(10), "2", "0", "after_freeze");
        attachSaleStage(houseId, arrivedAfterFreeze, -1);
        JsonNode afterArrival = api.getOk(
                "/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId);
        Assertions.assertEquals(3, afterArrival.get("summary").get("normal").asInt());
        Assertions.assertEquals(3, afterArrival.get("selectedItems").size());
        Assertions.assertFalse(selectedContains(afterArrival, arrivedAfterFreeze));

        String requestId = UUID.randomUUID().toString();
        Object submitBody = obj(
                "rabbitIds", List.of(normalRowOne, earlySale, normalRowTwo),
                "stateVersions", obj(
                        String.valueOf(normalRowOne), firstVersion,
                        String.valueOf(earlySale), earlyVersion,
                        String.valueOf(normalRowTwo), secondVersion),
                "earlySaleReasons", obj(String.valueOf(earlySale), "客户提前采购"),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 9.6,
                "unitPrice", 18.0,
                "customer", "黄金场景客户",
                "remark", "冻结后新增兔不得自动加入",
                "requestId", requestId
        );
        JsonNode result = api.postOk(
                "/api/outbound/tasks/" + task.get("taskId").asText() + "/submit",
                owner.token, houseId, submitBody);
        Assertions.assertEquals("COMPLETED", result.get("status").asText());
        Assertions.assertEquals(3, result.get("rabbitCount").asInt());
        Assertions.assertEquals(3, result.get("cageCount").asInt());
        Assertions.assertEquals(2, result.get("rowCount").asInt());
        Assertions.assertEquals(172.8, result.get("totalAmount").asDouble(), 0.001);

        long saleOrderId = result.get("saleOrderId").asLong();
        Assertions.assertEquals(3, jdbc.queryForObject(
                "select count(*) from sale_order_items where sale_order_id = ?", Integer.class, saleOrderId));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from sale_order_items where sale_order_id = ? and early_sale = true and early_sale_reason = '客户提前采购'",
                Integer.class, saleOrderId));
        Assertions.assertEquals(3, jdbc.queryForObject(
                "select count(*) from batches b join sale_order_items soi on soi.batch_id_snapshot = b.id where soi.sale_order_id = ? and b.status = '已完成'",
                Integer.class, saleOrderId));
        Assertions.assertEquals(3, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where rabbit_id in (?, ?, ?) and departure_type = 'sale'",
                Integer.class, normalRowOne, earlySale, normalRowTwo));
        Assertions.assertEquals(3, jdbc.queryForObject(
                "select count(*) from rabbit_status_history where rabbit_id in (?, ?, ?) and to_status = '出售出栏'",
                Integer.class, normalRowOne, earlySale, normalRowTwo));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbits where id = ? and is_active = true", Integer.class, arrivedAfterFreeze));
        Assertions.assertEquals("COMPLETED", jdbc.queryForObject(
                "select status from outbound_tasks where task_id = ?", String.class, task.get("taskId").asText()));
    }

    @Test
    void stateVersionConflictRollsBackWholeOrder() {
        UserSession owner = register("outbound_conflict");
        long houseId = createHouse(owner, "conflict_house", 1, 1, 1);
        long rabbitId = createRabbit(owner, houseId, cageIds(owner, houseId).get(0), "2", "0", "conflict");
        attachSaleStage(houseId, rabbitId, -1);

        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj("entryType", "RABBIT", "rabbitId", rabbitId));
        long version = version(task, rabbitId);
        api.putOk("/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId, obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(obj("rabbitId", rabbitId, "stateVersion", version, "selectionType", "NORMAL")),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.5
        ));
        jdbc.update("update rabbits set state_version = state_version + 1, is_quarantined = true where id = ?", rabbitId);

        String requestId = UUID.randomUUID().toString();
        Object submitBody = obj(
                "rabbitIds", List.of(rabbitId),
                "stateVersions", obj(String.valueOf(rabbitId), version),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.5,
                "requestId", requestId
        );
        JsonNode first = api.postOk("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit", owner.token, houseId, submitBody);
        JsonNode retry = api.postOk("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit", owner.token, houseId, submitBody);
        JsonNode status = api.getOk("/api/outbound/requests/" + requestId, owner.token, houseId);
        Assertions.assertEquals("CONFLICT", first.get("status").asText());
        Assertions.assertEquals(1, first.get("conflicts").size());
        Assertions.assertEquals(first.get("conflicts"), retry.get("conflicts"));
        Assertions.assertEquals(first.get("conflicts"), status.get("conflicts"));
        Assertions.assertEquals(first.get("message"), retry.get("message"));
        Assertions.assertEquals(first.get("message"), status.get("message"));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from sale_orders where house_id = ?", Integer.class, houseId));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from rabbits where id = ? and is_active = true", Integer.class, rabbitId));

        api.expectError("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit", HttpMethod.POST,
                owner.token, houseId, obj(
                        "rabbitIds", List.of(rabbitId),
                        "stateVersions", obj(String.valueOf(rabbitId), version),
                        "saleTime", LocalDate.now().toString(),
                        "totalWeight", 3.5,
                        "requestId", "not-a-canonical-uuid"
                ), 400, "规范UUID");
    }

    @Test
    void earlySaleAndReplacementRequireControlAndSubmitRechecksFrozenItems() {
        UserSession owner = register("outbound_auth_owner");
        UserSession member = createMerchantAccount(owner, "outbound_auth_member");
        long houseId = createHouse(owner, "outbound_auth_house", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long normalRabbit = createRabbit(owner, houseId, cages.get(0), "2", "0", "normal_auth");
        long earlyRabbit = createRabbit(owner, houseId, cages.get(1), "2", "1", "early_auth");
        attachSaleStage(houseId, normalRabbit, -1);
        attachSaleStage(houseId, earlyRabbit, 7);
        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", member.userName,
                "perms", "edit",
                "isAdmin", false,
                "requestId", requestId("outbound_member_edit")
        ));

        JsonNode ordinaryTask = api.postOk("/api/outbound/tasks", member.token, houseId,
                obj("entryType", "RABBIT", "rabbitId", normalRabbit));
        long normalVersion = version(ordinaryTask, normalRabbit);
        api.putOk("/api/outbound/tasks/" + ordinaryTask.get("taskId").asText(), member.token, houseId, obj(
                "revision", ordinaryTask.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(obj("rabbitId", normalRabbit, "stateVersion", normalVersion, "selectionType", "NORMAL")),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.2
        ));
        JsonNode ordinaryResult = api.postOk("/api/outbound/tasks/" + ordinaryTask.get("taskId").asText() + "/submit",
                member.token, houseId, obj(
                        "rabbitIds", List.of(normalRabbit),
                        "stateVersions", obj(String.valueOf(normalRabbit), normalVersion),
                        "saleTime", LocalDate.now().toString(),
                        "totalWeight", 3.2,
                        "requestId", UUID.randomUUID().toString()
                ));
        Assertions.assertEquals("COMPLETED", ordinaryResult.get("status").asText());

        JsonNode earlyTask = api.postOk("/api/outbound/tasks", member.token, houseId,
                obj("entryType", "RABBIT", "rabbitId", earlyRabbit));
        long earlyVersion = version(earlyTask, earlyRabbit);
        Object earlyDraft = obj(
                "revision", earlyTask.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(obj("rabbitId", earlyRabbit, "stateVersion", earlyVersion,
                        "selectionType", "EARLY_SALE", "earlySaleReason", "提前采购")),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.2
        );
        api.expectError("/api/outbound/tasks/" + earlyTask.get("taskId").asText(), HttpMethod.PUT,
                member.token, houseId, earlyDraft, 403, "权限不足");

        setMemberPermission(owner, member, houseId, "control");
        api.putOk("/api/outbound/tasks/" + earlyTask.get("taskId").asText(), member.token, houseId, earlyDraft);
        setMemberPermission(owner, member, houseId, "edit");
        String earlyRequestId = UUID.randomUUID().toString();
        Object earlySubmit = obj(
                "rabbitIds", List.of(earlyRabbit),
                "stateVersions", obj(String.valueOf(earlyRabbit), earlyVersion),
                "earlySaleReasons", obj(String.valueOf(earlyRabbit), "提前采购"),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.2,
                "requestId", earlyRequestId
        );
        api.expectError("/api/outbound/tasks/" + earlyTask.get("taskId").asText() + "/submit", HttpMethod.POST,
                member.token, houseId, earlySubmit, 403, "权限不足");
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbits where id = ? and is_active = true", Integer.class, earlyRabbit));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from outbound_requests where request_id = ?", Integer.class, earlyRequestId));

        setMemberPermission(owner, member, houseId, "control");
        JsonNode earlyResult = api.postOk("/api/outbound/tasks/" + earlyTask.get("taskId").asText() + "/submit",
                member.token, houseId, earlySubmit);
        Assertions.assertEquals("COMPLETED", earlyResult.get("status").asText());

        long replacementRabbit = createRabbit(owner, houseId, cages.get(2), "2", "0", "replacement_auth");
        setMemberPermission(owner, member, houseId, "edit");
        String replacementRequestId = requestId("replacement_control");
        Object replacement = obj(
                "rabbitIds", List.of(replacementRabbit),
                "forceExitBatch", false,
                "requestId", replacementRequestId
        );
        BizException directDenied = Assertions.assertThrows(BizException.class,
                () -> rabbitService.convertToReplacement(member.userId, houseId,
                        List.of(replacementRabbit), false, null,
                        replacementRequestId));
        Assertions.assertEquals(403, directDenied.getCode());
        api.expectError("/api/rabbits/replacement", HttpMethod.POST, member.token, houseId,
                replacement, 403, "权限不足");
        setMemberPermission(owner, member, houseId, "control");
        api.postOk("/api/rabbits/replacement", member.token, houseId, replacement);
        Assertions.assertEquals("1", jdbc.queryForObject(
                "select type from rabbits where id = ?", String.class, replacementRabbit));
    }

    @Test
    void inconsistentCageCountRollsBackSaleRabbitAndBatchChanges() {
        UserSession owner = register("outbound_count");
        long houseId = createHouse(owner, "outbound_count_house", 1, 1, 1);
        long cageId = cageIds(owner, houseId).get(0);
        long rabbitId = createRabbit(owner, houseId, cageId, "2", "0", "count_corruption");
        attachSaleStage(houseId, rabbitId, -1);

        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId,
                obj("entryType", "RABBIT", "rabbitId", rabbitId));
        long version = version(task, rabbitId);
        api.putOk("/api/outbound/tasks/" + task.get("taskId").asText(), owner.token, houseId, obj(
                "revision", task.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", List.of(obj("rabbitId", rabbitId, "stateVersion", version, "selectionType", "NORMAL")),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.2
        ));
        jdbc.update("update cages set rabbit_count = 0 where id = ?", cageId);
        String requestId = UUID.randomUUID().toString();

        Object submitBody = obj(
                "rabbitIds", List.of(rabbitId),
                "stateVersions", obj(String.valueOf(rabbitId), version),
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 3.2,
                "requestId", requestId
        );
        JsonNode first = api.postOk("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit",
                owner.token, houseId, submitBody);
        JsonNode retry = api.postOk("/api/outbound/tasks/" + task.get("taskId").asText() + "/submit",
                owner.token, houseId, submitBody);
        JsonNode status = api.getOk("/api/outbound/requests/" + requestId, owner.token, houseId);
        Assertions.assertEquals("FAILED", first.get("status").asText());
        Assertions.assertEquals("BUSINESS_409", first.get("errorCode").asText());
        Assertions.assertEquals(first, retry);
        Assertions.assertEquals(first, status);

        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from sale_orders where house_id = ?", Integer.class, houseId));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbits where id = ? and is_active = true", Integer.class, rabbitId));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from batch_rabbits where rabbit_id = ? and is_active = true", Integer.class, rabbitId));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where rabbit_id = ?", Integer.class, rabbitId));
        Assertions.assertEquals("WAITING_CONFIRMATION", jdbc.queryForObject(
                "select status from outbound_tasks where task_id = ?", String.class, task.get("taskId").asText()));
        Assertions.assertEquals("FAILED", jdbc.queryForObject(
                "select status from outbound_requests where request_id = ?", String.class, requestId));
        Assertions.assertEquals("BUSINESS_409", jdbc.queryForObject(
                "select error_code from outbound_requests where request_id = ?", String.class, requestId));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from outbound_requests where request_id = ? and error_message like '%笼位状态已变化%'",
                Integer.class, requestId));
    }

    @Test
    void replacementTargetCapacityAndInsufficientSourceCountRollBack() {
        UserSession owner = register("replacement_counts");
        long houseId = createHouse(owner, "replacement_counts_house", 1, 5, 1);
        List<Long> cages = cageIds(owner, houseId);
        long firstRabbit = createRabbit(owner, houseId, cages.get(0), "2", "0", "replacement_one");
        long secondRabbit = createRabbit(owner, houseId, cages.get(1), "2", "1", "replacement_two");
        Object overCapacity = obj(
                "rabbitIds", List.of(firstRabbit, secondRabbit),
                "forceExitBatch", false,
                "targetCageId", cages.get(2),
                "requestId", requestId("replacement_capacity")
        );

        api.expectError("/api/rabbits/replacement", HttpMethod.POST, owner.token, houseId,
                overCapacity, 400, "容量不足");
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select rabbit_count from cages where id = ?", Integer.class, cages.get(2)));
        Assertions.assertEquals(2, jdbc.queryForObject(
                "select count(*) from rabbits where id in (?, ?) and type = '2'",
                Integer.class, firstRabbit, secondRabbit));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select rabbit_count from cages where id = ?", Integer.class, cages.get(0)));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select rabbit_count from cages where id = ?", Integer.class, cages.get(1)));

        jdbc.update("update cages set rabbit_count = 0 where id = ?", cages.get(0));
        Object insufficientSource = obj(
                "rabbitIds", List.of(firstRabbit),
                "forceExitBatch", false,
                "targetCageId", cages.get(3),
                "requestId", requestId("replacement_source_count")
        );
        api.expectError("/api/rabbits/replacement", HttpMethod.POST, owner.token, houseId,
                insufficientSource, 409, "在栏数量不足");

        Assertions.assertEquals("2", jdbc.queryForObject(
                "select type from rabbits where id = ?", String.class, firstRabbit));
        Assertions.assertEquals(cages.get(0), jdbc.queryForObject(
                "select cage_id from rabbits where id = ?", Long.class, firstRabbit));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select rabbit_count from cages where id = ?", Integer.class, cages.get(3)));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from replacement_records where rabbit_id = ?", Integer.class, firstRabbit));
    }

    private void setMemberPermission(UserSession owner, UserSession member, long houseId, String permission) {
        api.putOk("/api/house-members/" + member.userId, owner.token, houseId, obj(
                "perms", permission,
                "isAdmin", false,
                "requestId", requestId("outbound_member_" + permission)
        ));
    }

    private long attachSaleStage(long houseId, long rabbitId, int daysFromNow) {
        String requestId = requestId("batch");
        jdbc.update("insert into batches (house_id, batch_code, status, start_date, request_id, create_by, update_by) values (?, ?, '进行中', now(), ?, 'e2e', 'e2e')",
                houseId, "B-" + rabbitId, requestId);
        long batchId = jdbc.queryForObject("select id from batches where house_id = ? and request_id = ?", Long.class, houseId, requestId);
        jdbc.update("insert into batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, current_status, next_event_date, next_event_type, is_active, join_date, create_by, update_by) values (?, ?, '断奶', 'fattening', '成长期', timestampadd(day, ?, now()), '出售', true, now(), 'e2e', 'e2e')",
                batchId, rabbitId, daysFromNow);
        return batchId;
    }

    private long version(JsonNode task, long rabbitId) {
        for (JsonNode rabbit : task.get("rabbits")) {
            if (rabbit.get("rabbitId").asLong() == rabbitId) return rabbit.get("stateVersion").asLong();
        }
        throw new AssertionError("rabbit missing from precheck: " + rabbitId);
    }

    private String reasonCode(JsonNode task, long rabbitId) {
        for (JsonNode rabbit : task.get("rabbits")) {
            if (rabbit.get("rabbitId").asLong() == rabbitId) return rabbit.get("reasonCode").asText();
        }
        throw new AssertionError("rabbit missing from precheck: " + rabbitId);
    }

    private boolean selectedContains(JsonNode task, long rabbitId) {
        for (JsonNode item : task.get("selectedItems")) {
            if (item.get("rabbitId").asLong() == rabbitId) return true;
        }
        return false;
    }
}
