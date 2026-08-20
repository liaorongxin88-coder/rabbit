package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 母兔生产流程 V2 的 HTTP 契约测试（开关打开）。
 *
 * <p>与 {@link ReproStateMachineIT} 分工：那边直接注入服务验证状态机不变式，
 * 这边验证端点契约——序列化形状、过滤条件、批量的部分成功语义与幂等。
 * 两层都要有，因为 MyBatis 的 XML 错误和 Jackson 的形状问题只在各自的层暴露。
 */
public class ReproApiIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void openCycleThenAdvanceThroughTaskCentre() {
        Fixture f = fixture("repro_api_flow", 1);
        long doeId = f.does.get(0);

        // 建批次时母兔已入轨（待催情），这里直接拿那个周期。
        long cycleId = openAtEstrus(f, doeId, "open");
        JsonNode opened = api.getOk("/api/repro/cycles/" + cycleId, f.token, f.houseId);
        Assertions.assertEquals("AWAIT_ESTRUS", opened.get("stage").asText());

        // 待办中心应当立刻出现一条催情待办 —— 任务与周期同事务生成，无需等夜间扫表。
        JsonNode tasks = api.getOk("/api/tasks", f.token, f.houseId);
        Assertions.assertEquals(1, tasks.get("total").asInt());
        JsonNode task = tasks.get("items").get(0);
        Assertions.assertEquals("ESTRUS", task.get("taskType").asText());
        Assertions.assertEquals("待催情", task.get("taskLabel").asText());
        Assertions.assertEquals("ESTRUS", task.get("action").asText(), "客户端据此决定按钮动作");
        Assertions.assertEquals(cycleId, task.get("cycleId").asLong());
        Assertions.assertEquals(doeId, task.get("rabbitId").asLong());

        String estrusRequestId = requestId("estrus");
        var estrusBody = obj(
            "action", "ESTRUS",
            "occurredAt", now(),
            "requestId", estrusRequestId
        );
        JsonNode advanced = api.postOk(
            "/api/repro/cycles/" + cycleId + "/actions", f.token, f.houseId, estrusBody
        );
        Assertions.assertEquals(cycleId, advanced.get("cycleId").asLong());
        Assertions.assertEquals(cycleId, advanced.get("currentCycleId").asLong());
        Assertions.assertEquals("AWAIT_MATING", advanced.get("stage").asText());
        Assertions.assertEquals("OPEN", advanced.get("lifecycle").asText());
        Assertions.assertFalse(advanced.get("replayed").asBoolean());

        JsonNode replayed = api.postOk(
            "/api/repro/cycles/" + cycleId + "/actions", f.token, f.houseId, estrusBody
        );
        Assertions.assertEquals(cycleId, replayed.get("cycleId").asLong());
        Assertions.assertEquals(cycleId, replayed.get("currentCycleId").asLong());
        Assertions.assertEquals("AWAIT_MATING", replayed.get("stage").asText());
        Assertions.assertEquals("OPEN", replayed.get("lifecycle").asText());
        Assertions.assertTrue(replayed.get("replayed").asBoolean());

        JsonNode cycle = api.getOk("/api/repro/cycles/" + cycleId, f.token, f.houseId);
        Assertions.assertEquals("AWAIT_MATING", cycle.get("stage").asText());
        Assertions.assertEquals("待配种", cycle.get("stageLabel").asText());
        Assertions.assertEquals("OPEN", cycle.get("lifecycle").asText());
        Assertions.assertFalse(cycle.has("status"), "兼容镜像列不得外泄给新客户端");

        // 催情后配种任务按 estrus_duration_days 顺延，因此「今日待办」应当是空的 ——
        // 提醒不该在等待期里持续冒泡，这正是把等待天数配置化的目的。
        Assertions.assertEquals(
            0, api.getOk("/api/tasks", f.token, f.houseId).get("total").asInt(),
            "配种任务尚未到期，不应出现在今日待办"
        );

        // 旧任务完成，新任务顶上：同一时刻母兔只有一条待办。
        JsonNode upcoming = upcoming(f);
        Assertions.assertEquals(1, upcoming.get("total").asInt());
        Assertions.assertEquals("MATING", upcoming.get("items").get(0).get("taskType").asText());
    }

    @Test
    void eventTrailRecordsWhoDidItByName() {
        Fixture f = fixture("repro_api_operator", 1);
        long cycleId = openAtEstrus(f, f.does.get(0), "operator");
        api.postOk("/api/repro/cycles/" + cycleId + "/actions", f.token, f.houseId, obj(
            "action", "ESTRUS",
            "occurredAt", now(),
            "requestId", requestId("operator_act")
        ));

        // 事件流是给人看的审计记录：事故复盘要靠它回答「谁做的」。
        // 存 userId 等于把这个问题推给每个读取方，而事后 join 出来的是
        // 「现在的名字」，不是「当时的名字」。
        List<String> operators = jdbc.queryForList(
            "select operator_name from repro_events where house_id = ? order by id",
            String.class, f.houseId
        );
        Assertions.assertEquals(2, operators.size(), "开启周期 + 催情共两条事件");
        for (String operator : operators) {
            Assertions.assertEquals(
                f.userName, operator,
                "operator_name 应当是展示名快照，而不是 userId 字符串"
            );
        }
    }

    /**
     * 提醒完全由待办中心承载：每推进一步，旧任务置 DONE、新任务同事务生成。
     *
     * <p>本用例原先盯的是 breeding_cycles 上的 next_event_date / next_event_type / status
     * 三个镜像列（为没有 OTA 的老 APK 而写）。产品确认仍在试点、客户端同步升级后，
     * 那三列已随 V28 删除，因此改为断言它们的替代物——也就是当初重构要达到的终态。
     */
    @Test
    void everyAdvanceRotatesTheTaskCentre() {
        Fixture f = fixture("repro_api_compat", 1);
        long cycleId = openAtEstrus(f, f.does.get(0), "compat");

        Assertions.assertEquals("ESTRUS", pendingTaskType(cycleId),
            "开周期必须同事务生成催情待办，否则这只母兔不会出现在任何清单里");

        api.postOk("/api/repro/cycles/" + cycleId + "/actions", f.token, f.houseId, obj(
            "action", "ESTRUS", "occurredAt", now(), "requestId", requestId("compat_estrus")));
        Assertions.assertEquals("MATING", pendingTaskType(cycleId));

        api.postOk("/api/repro/cycles/" + cycleId + "/actions", f.token, f.houseId, obj(
            "action", "MATING", "occurredAt", now(),
            "maleRabbitId", f.buckId,
            "requestId", requestId("compat_mating")));
        Assertions.assertEquals("PALPATION", pendingTaskType(cycleId));

        // 任何时刻一个开放周期只能挂一条未完成待办；旧任务必须被置 DONE
        // 而不是堆着，否则今日清单会重复列出同一头母兔。
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from work_tasks where cycle_id = ? and status = 'PENDING'",
            Integer.class, cycleId).intValue());
        Assertions.assertEquals("AWAIT_PALPATION", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, cycleId));
    }

    private String pendingTaskType(long cycleId) {
        return jdbc.queryForObject(
            "select task_type from work_tasks where cycle_id = ? and status = 'PENDING'"
                + " order by id desc limit 1", String.class, cycleId);
    }

    /** 存量录入：从待分笼入轨时，仔数与分笼待办必须同事务就位。 */
    @Test
    void enteringAtLactationPersistsKitCounts() {
        Fixture f = fixture("repro_api_lactation", 1);
        // 存量录入：母兔已在哺乳，直接从待分笼入轨。
        long cycleId = api.postOk("/api/repro/cycles", f.token, f.houseId, obj(
            "motherRabbitId", f.does.get(0),
            "batchId", f.batchId,
            "stage", "AWAIT_WEANING",
            "occurredAt", now(),
            "birthDate", now() - 5L * 24 * 3600 * 1000,
            "totalKits", 9,
            "liveKits", 7,
            "requestId", requestId("lactation")
        )).get("cycleId").asLong();

        // 仔数写在窝表，但必须同步回周期的兼容列，否则老客户端显示 0 只仔兔。
        Assertions.assertEquals(9, jdbc.queryForObject(
            "select total_kits from breeding_cycles where id = ?", Integer.class, cycleId).intValue());
        Assertions.assertEquals(7, jdbc.queryForObject(
            "select live_kits from breeding_cycles where id = ?", Integer.class, cycleId).intValue());
        Assertions.assertEquals(7, jdbc.queryForObject(
            "select current_nursing_kits from breeding_cycles where id = ?",
            Integer.class, cycleId).intValue());
        // 入轨必须同时建窝并挂上分笼待办（主体是窝，不是周期），
        // 否则这头母兔会停在一个永远不会被提醒的阶段。
        Assertions.assertEquals("WEANING", jdbc.queryForObject(
            "select task_type from work_tasks where cycle_id = ? and status = 'PENDING'",
            String.class, cycleId));
        Assertions.assertEquals("LITTER", jdbc.queryForObject(
            "select subject_type from work_tasks where cycle_id = ? and status = 'PENDING'",
            String.class, cycleId));
    }

    @Test
    void taskListFiltersByCageForNfcTap() {
        Fixture f = fixture("repro_api_nfc", 2);
        openAtEstrus(f, f.does.get(0), "nfc_a");
        openAtEstrus(f, f.does.get(1), "nfc_b");

        long cageOfFirst = api.getOk("/api/rabbits/" + f.does.get(0), f.token, f.houseId)
            .get("cageId").asLong();

        JsonNode all = api.getOk("/api/tasks", f.token, f.houseId);
        Assertions.assertEquals(2, all.get("total").asInt());

        JsonNode byCage = api.getOk("/api/tasks?cageId=" + cageOfFirst, f.token, f.houseId);
        Assertions.assertEquals(1, byCage.get("total").asInt());
        Assertions.assertEquals(
            f.does.get(0).longValue(), byCage.get("items").get(0).get("rabbitId").asLong()
        );
    }

    @Test
    void taskListCanIncludeAllFuturePendingWithoutLeakingOtherRabbitsOrStatuses() {
        Fixture f = fixture("repro_api_future_tasks", 4);
        long overdueRabbitId = f.does.get(0);
        long futureRabbitId = f.does.get(1);
        long doneRabbitId = f.does.get(2);
        long cancelledRabbitId = f.does.get(3);

        jdbc.update(
            "update work_tasks set due_date = date_sub(current_date, interval 1 day),"
                + " due_time = date_sub(now(), interval 1 day), status = 'PENDING'"
                + " where house_id = ? and rabbit_id = ?",
            f.houseId, overdueRabbitId
        );
        jdbc.update(
            "update work_tasks set due_date = date_add(current_date, interval 30 day),"
                + " due_time = date_add(now(), interval 30 day), status = 'PENDING'"
                + " where house_id = ? and rabbit_id = ?",
            f.houseId, futureRabbitId
        );
        jdbc.update(
            "update work_tasks set due_date = date_add(current_date, interval 31 day),"
                + " due_time = date_add(now(), interval 31 day), status = 'DONE'"
                + " where house_id = ? and rabbit_id = ?",
            f.houseId, doneRabbitId
        );
        jdbc.update(
            "update work_tasks set due_date = date_add(current_date, interval 32 day),"
                + " due_time = date_add(now(), interval 32 day), status = 'CANCELLED'"
                + " where house_id = ? and rabbit_id = ?",
            f.houseId, cancelledRabbitId
        );

        JsonNode dueOnly = api.getOk("/api/tasks?batchId=" + f.batchId, f.token, f.houseId);
        Assertions.assertEquals(1, dueOnly.get("total").asInt(),
            "默认查询仍应只返回今日及逾期待办");
        Assertions.assertEquals(overdueRabbitId,
            dueOnly.get("items").get(0).get("rabbitId").asLong());

        JsonNode allPending = api.getOk(
            "/api/tasks?includeFuture=true&dueBefore=0&batchId=" + f.batchId,
            f.token,
            f.houseId
        );
        Assertions.assertEquals(2, allPending.get("total").asInt(),
            "includeFuture 应忽略 dueBefore 上限，但仍只返回 PENDING");
        Assertions.assertEquals(overdueRabbitId,
            allPending.get("items").get(0).get("rabbitId").asLong());
        Assertions.assertEquals(futureRabbitId,
            allPending.get("items").get(1).get("rabbitId").asLong());
        for (JsonNode task : allPending.get("items")) {
            Assertions.assertEquals("PENDING", task.get("status").asText());
        }

        JsonNode byRabbit = api.getOk(
            "/api/tasks?includeFuture=true&rabbitId=" + futureRabbitId,
            f.token,
            f.houseId
        );
        Assertions.assertEquals(1, byRabbit.get("total").asInt());
        Assertions.assertEquals(futureRabbitId,
            byRabbit.get("items").get(0).get("rabbitId").asLong(),
            "rabbitId 过滤不得带回同舍其他兔的待办");
    }

    @Test
    void bulkActionByFilterAdvancesWholeBatch() {
        Fixture f = fixture("repro_api_bulk", 3);
        for (int i = 0; i < 3; i++) {
            openAtEstrus(f, f.does.get(i), "bulk_" + i);
        }

        JsonNode result = api.postOk("/api/repro/tasks/bulk-actions", f.token, f.houseId, obj(
            "requestId", requestId("bulk_estrus"),
            "action", "ESTRUS",
            "occurredAt", now(),
            "filter", obj("batchId", f.batchId, "taskType", "ESTRUS")
        ));

        Assertions.assertEquals(3, result.get("total").asInt());
        Assertions.assertEquals(3, result.get("succeeded").asInt());
        Assertions.assertEquals(0, result.get("failed").asInt());

        JsonNode tasks = upcoming(f);
        Assertions.assertEquals(3, tasks.get("total").asInt());
        for (JsonNode t : tasks.get("items")) {
            Assertions.assertEquals("MATING", t.get("taskType").asText(), "整批应推进到待配种");
        }
    }

    @Test
    void bulkReportsPerItemFailureWithoutAbortingTheRest() {
        Fixture f = fixture("repro_api_partial", 3);
        List<Long> cycleIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            cycleIds.add(openAtEstrus(f, f.does.get(i), "partial_" + i));
        }

        // 把第二只单独推进走，使其在批量执行时已不在待催情阶段。
        api.postOk("/api/repro/cycles/" + cycleIds.get(1) + "/actions", f.token, f.houseId, obj(
            "action", "ESTRUS",
            "occurredAt", now(),
            "requestId", requestId("out_of_band")
        ));

        // 取全部未来待办：被单独推进那只的配种任务要 2 天后才到期，
        // 但它同样应当出现在批量目标里并被判为「不支持该操作」。
        List<Long> taskIds = new ArrayList<>();
        for (JsonNode t : upcoming(f).get("items")) {
            taskIds.add(t.get("id").asLong());
        }
        Assertions.assertEquals(3, taskIds.size());

        JsonNode result = api.postOk("/api/repro/tasks/bulk-actions", f.token, f.houseId, obj(
            "requestId", requestId("bulk_partial"),
            "action", "ESTRUS",
            "occurredAt", now(),
            "taskIds", taskIds
        ));

        Assertions.assertEquals(3, result.get("total").asInt());
        Assertions.assertEquals(2, result.get("succeeded").asInt(), "另外两只不该被牵连回滚");
        Assertions.assertEquals(1, result.get("failed").asInt());

        JsonNode failed = null;
        for (JsonNode item : result.get("items")) {
            if (!item.get("ok").asBoolean()) {
                failed = item;
            }
        }
        Assertions.assertNotNull(failed);
        Assertions.assertEquals(400, failed.get("code").asInt());
        Assertions.assertTrue(
            failed.get("message").asText().contains("不支持操作"),
            "失败原因要可直接展示，实际: " + failed.get("message").asText()
        );
    }

    @Test
    void bulkRetryReplaysInsteadOfAdvancingTwice() {
        Fixture f = fixture("repro_api_replay", 2);
        for (int i = 0; i < 2; i++) {
            openAtEstrus(f, f.does.get(i), "replay_" + i);
        }
        String sharedRequestId = requestId("bulk_replay");

        JsonNode first = api.postOk("/api/repro/tasks/bulk-actions", f.token, f.houseId, obj(
            "requestId", sharedRequestId,
            "action", "ESTRUS",
            "occurredAt", now(),
            "filter", obj("batchId", f.batchId, "taskType", "ESTRUS")
        ));
        Assertions.assertEquals(2, first.get("succeeded").asInt());

        // 整批重试：逐项幂等键 requestId-taskId 已存在，应全部命中回放。
        JsonNode retry = api.postOk("/api/repro/tasks/bulk-actions", f.token, f.houseId, obj(
            "requestId", sharedRequestId,
            "action", "ESTRUS",
            "occurredAt", now(),
            "taskIds", taskIdsOf(f, "MATING")
        ));
        Assertions.assertEquals(0, retry.get("succeeded").asInt(), "重试不应把待配种再推一格");
        Assertions.assertEquals(2, retry.get("failed").asInt());

        for (JsonNode t : upcoming(f).get("items")) {
            Assertions.assertEquals("MATING", t.get("taskType").asText());
        }
    }

    @Test
    void bulkRejectsActionsThatCarryPerDoeData() {
        Fixture f = fixture("repro_api_reject", 1);
        openAtEstrus(f, f.does.get(0), "reject");

        api.expectError(
            "/api/repro/tasks/bulk-actions", HttpMethod.POST, f.token, f.houseId, obj(
                "requestId", requestId("bulk_delivery"),
                "action", "DELIVERY",
                "outcome", "BORN",
                "occurredAt", now(),
                "filter", obj("batchId", f.batchId)
            ), 400, "不支持批量");

        api.expectError(
            "/api/repro/tasks/bulk-actions", HttpMethod.POST, f.token, f.houseId, obj(
                "requestId", requestId("bulk_postpone"),
                "action", "POSTPONE",
                "occurredAt", now(),
                "filter", obj("batchId", f.batchId)
            ), 400, "下次提醒时间");

        api.expectError(
            "/api/repro/tasks/bulk-actions", HttpMethod.POST, f.token, f.houseId, obj(
                "requestId", requestId("bulk_both"),
                "action", "ESTRUS",
                "occurredAt", now(),
                "taskIds", taskIdsOf(f, "ESTRUS"),
                "filter", obj("batchId", f.batchId)
            ), 400, "二选一");
    }

    @Test
    void postponeKeepsTaskPendingAndRecordsSnooze() {
        Fixture f = fixture("repro_api_postpone", 1);
        openAtEstrus(f, f.does.get(0), "postpone");
        long threeDaysLater = now() + 3L * 24 * 3600 * 1000;

        JsonNode result = api.postOk("/api/repro/tasks/bulk-actions", f.token, f.houseId, obj(
            "requestId", requestId("bulk_snooze"),
            "action", "POSTPONE",
            "occurredAt", now(),
            "nextRemindAt", threeDaysLater,
            "filter", obj("batchId", f.batchId, "taskType", "ESTRUS")
        ));
        Assertions.assertEquals(1, result.get("succeeded").asInt());

        // 推迟后任务仍是 PENDING，只是到期日推后 —— 「提醒不消失」。
        JsonNode today = api.getOk("/api/tasks", f.token, f.houseId);
        Assertions.assertEquals(0, today.get("total").asInt(), "今日待办里不该再出现");

        JsonNode later = api.getOk("/api/tasks?dueBefore=" + (threeDaysLater + 86400000L), f.token, f.houseId);
        Assertions.assertEquals(1, later.get("total").asInt());
        JsonNode task = later.get("items").get(0);
        Assertions.assertEquals("ESTRUS", task.get("taskType").asText());
        Assertions.assertEquals("PENDING", task.get("status").asText());
        Assertions.assertEquals(1, task.get("snoozeCount").asInt(), "拖延次数要留痕");
    }

    private List<Long> taskIdsOf(Fixture f, String taskType) {
        List<Long> ids = new ArrayList<>();
        JsonNode page = api.getOk(
            "/api/tasks?type=" + taskType + "&dueBefore=" + farFuture(), f.token, f.houseId
        );
        for (JsonNode t : page.get("items")) {
            ids.add(t.get("id").asLong());
        }
        return ids;
    }

    /** 全部未来待办；默认的 /api/tasks 只返回今日及逾期。 */
    private JsonNode upcoming(Fixture f) {
        return api.getOk("/api/tasks?dueBefore=" + farFuture(), f.token, f.houseId);
    }

    private long farFuture() {
        return now() + 30L * 24 * 3600 * 1000;
    }

    /**
     * 取该母兔当前处于待催情的周期。
     *
     * <p>无需再调入轨接口：建批次时已经把成员送进了生产流水线。
     * 这里直接用那个周期，而不是自己另开一个——后者既会撞上
     * 「一头母兔仅一条流水线周期」不变式，也不是真实用法。
     */
    private long openAtEstrus(Fixture f, long doeId, String prefix) {
        return jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN' order by id desc limit 1",
            Long.class, f.houseId, doeId);
    }

    private Fixture fixture(String prefix, int doeCount) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, doeCount + 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        List<Long> does = new ArrayList<>();
        for (int i = 0; i < doeCount; i++) {
            does.add(createRabbit(owner, houseId, cages.get(i), "0", "0", prefix + "_doe" + i));
        }
        long buckId = createRabbit(
            owner, houseId, cages.get(doeCount), "0", "1", prefix + "_buck");
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "API-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", does,
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();
        return new Fixture(owner.token, houseId, does, batchId, owner.userName, buckId);
    }

    private record Fixture(
        String token, long houseId, List<Long> does, long batchId, String userName, long buckId
    ) {
    }
}
