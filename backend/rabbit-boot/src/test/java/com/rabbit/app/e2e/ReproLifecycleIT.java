package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 整轮生产在新 API 下的验收，接替被删除的遗留生命周期套件。
 *
 * <p>被它取代的是 {@code BreedingReminderIT}、{@code WholeHouseBatchLifecycleIT}、
 * {@code LargeWholeHouseBatchLifecycleIT}、{@code LargeHouseBatchScaleIT}、
 * {@code BreedingCycleAcrossBatchIT} 与 {@code BreedingCycleTerminationIT}。
 * 它们各自的阶段推进、血配、空怀重开等断言已由 {@code ReproStateMachineIT} 覆盖；
 * 本类只补齐那批套件里<b>真正独有</b>的几条不变式，不做 1:1 搬运。
 */
public class ReproLifecycleIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 接替 {@code BreedingCycleAcrossBatchIT}：母兔换批次后周期编号如何延续。
     *
     * <p>编号按 (house, batch, mother) 计。自动接续进入无批次作用域，显式换到新批次
     * 也进入新的编号作用域，因此两者都从 1 起算。
     */
    @Test
    void cycleNumberRestartsPerBatchButIncrementsWithinOne() {
        Fixture f = fixture("across", 1);
        long doe = f.doeIds.get(0);

        long first = advanceToMating(f, doe, "b1c1");
        Assertions.assertEquals(1, intOf("select cycle_no from breeding_cycles where id = ?", first));
        // 空怀关掉第一轮，自动接续进入无批次作用域。
        act(f, first, "b1c1_mate", obj("action", "MATING", "occurredAt", oneMinuteAgo(),
            "maleRabbitId", f.buckId, "matingMethod", "NATURAL"));
        act(f, first, "b1c1_empty", obj("action", "PALPATION", "occurredAt", oneMinuteAgo(),
            "palpationResult", "EMPTY"));

        Long reopened = jdbc.queryForObject(
            "select id from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'",
            Long.class, doe);
        Assertions.assertEquals(1, intOf("select cycle_no from breeding_cycles where id = ?", reopened),
            "无批次接续应在自己的编号作用域从 1 开始");
        Assertions.assertNull(jdbc.queryForObject(
            "select batch_id from breeding_cycles where id = ?", Long.class, reopened
        ));

        // 换到新批次：编号重新从 1 起算。
        //
        // 新批次用另一头母兔建，而不是把本头写进去：旧的 batch_rabbits 会以
        // 「母兔已在活跃批次中」拒绝。而新模型里批次归属本就由 breeding_cycles.batch_id
        // 推导（batch_rabbits 待 V28 退役），所以直接拿新 batchId 开周期才是新路径的真实用法。
        long spare = createRabbit(f.owner, f.houseId,
            cageIds(f.owner, f.houseId).get(2), "0", "0", "across_spare");
        long otherBatch = createBatch(f, List.of(spare), "across_b2");
        act(f, reopened, "b1c2_retire", obj("action", "RETIRE", "occurredAt", oneMinuteAgo(),
            "reason", "转批次"));
        long moved = openAt(f, doe, otherBatch, "AWAIT_MATING", "b2c1");
        Assertions.assertEquals(1, intOf("select cycle_no from breeding_cycles where id = ?", moved),
            "换批次后应重新从 1 起算");
    }

    /**
     * 接替 {@code BreedingCycleTerminationIT.culledMotherClosesHerOpenCycleBeforeBatchAutoCompletion}：
     * 母兔离场必须同时结清她所有在跑的东西，一个都不能漏。
     */
    @Test
    void retiringADoeClosesEveryOpenCycleAndTask() {
        Fixture f = fixture("cull", 1);
        long doe = f.doeIds.get(0);

        // 血配：一个哺乳周期 + 一个管线周期，两个都 OPEN。
        long nursing = openAt(f, doe, f.batchId, "AWAIT_WEANING", "cull_nursing",
            obj("birthDate", oneMinuteAgo(), "totalKits", 7, "liveKits", 6));
        long pipeline = advanceToMating(f, doe, "cull_pipeline");
        Assertions.assertEquals(2, intOf(
            "select count(*) from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'", doe));

        act(f, pipeline, "cull_do", obj("action", "RETIRE", "occurredAt", oneMinuteAgo(),
            "reason", "淘汰"));

        Assertions.assertEquals(0, intOf(
            "select count(*) from work_tasks where rabbit_id = ? and status = 'PENDING'", doe),
            "离场后不得残留待办：否则这只兔子会一直出现在今日清单里");
        Assertions.assertEquals("RETIRED", strOf(
            "select current_stage from rabbits where id = ?", doe));
        Assertions.assertEquals("CLOSED", strOf(
            "select lifecycle from breeding_cycles where id = ?", pipeline));
        // 哺乳窝不会因母兔离场而凭空消失，但它也不该再产生待办。
        Assertions.assertEquals(0, intOf(
            "select count(*) from work_tasks where cycle_id = ? and status = 'PENDING'", nursing));
    }

    /**
     * 接替 {@code BreedingCycleTerminationIT.forcedBatchCompletionClosesOpenCyclesAndStopsReminders}，
     * 但<b>断言的是相反的行为</b>。
     *
     * <p>旧实现允许 force=true 直接把批次下所有周期 UPDATE 成「已终止」。那条 SQL 不认识
     * lifecycle/stage，会造成「旧视角已终止、新视角仍 OPEN」的分裂状态，母兔从此被
     * uk_bc_pipeline 卡死。更根本的是：批次现在只是标签，归档标签不该终止母兔的生理过程。
     * 所以现在是拒绝，并告诉操作者去生产流程里处理。
     */
    @Test
    void batchCannotBeCompletedWhileCyclesAreStillRunning() {
        Fixture f = fixture("complete", 1);
        long doe = f.doeIds.get(0);
        advanceToMating(f, doe, "complete_open");

        api.expectError("/api/batches/" + f.batchId + "/complete", HttpMethod.POST,
            f.owner.token, f.houseId, obj(
                "force", true,
                "endDate", now(),
                "requestId", requestId("force_complete")
            ), 409, "未结束的生产周期");

        Assertions.assertEquals("OPEN", strOf(
            "select lifecycle from breeding_cycles where mother_rabbit_id = ?", doe),
            "被拒绝后周期必须原样保留");
    }

    /**
     * 接替 {@code LargeHouseBatchScaleIT}：规模下的正确性与租户隔离。
     *
     * <p>原测试用一千只母兔，跑一次要两分钟。这里压到 120 只，它验证的是
     * 批量路径不会串舍、不会漏项、编号不冲突，这些在 120 只上同样能暴露；
     * 一千只多出来的只是等待时间，不是新的失败模式。
     */
    @Test
    void bulkRoundStaysInsideItsOwnHouse() {
        Fixture mine = fixture("scale_a", 120);
        Fixture other = fixture("scale_b", 3);

        JsonNode res = api.postOk("/api/repro/tasks/bulk-actions", mine.owner.token, mine.houseId, obj(
            "requestId", requestId("scale_round"),
            "action", "ESTRUS",
            "occurredAt", oneMinuteAgo(),
            "filter", obj("batchId", mine.batchId, "taskType", "ESTRUS")
        ));
        Assertions.assertEquals(120, res.get("succeeded").asInt());

        Assertions.assertEquals(120, intOf(
            "select count(*) from breeding_cycles where house_id = ? and stage = 'AWAIT_MATING'",
            mine.houseId));
        // 隔离：另一个兔舍一头都不该被推进。
        Assertions.assertEquals(3, intOf(
            "select count(*) from breeding_cycles where house_id = ? and stage = 'AWAIT_ESTRUS'",
            other.houseId), "批量操作串到了别的兔舍");
        Assertions.assertEquals(0, intOf(
            "select count(*) from breeding_cycles where house_id = ? and stage = 'AWAIT_MATING'",
            other.houseId));
    }

    // ---------------------------------------------------------------- helpers

    private int intOf(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    private String strOf(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    /**
     * 建繁殖批次必须把母兔送进生产流水线。
     *
     * <p>这条盯的是一个真实出现过的阱阻缺口：旧模型里「加入批次」只写
     * {@code batch_rabbits.current_status = 待催情}，那个字段本身就是状态；
     * doe-breeding-v2 把状态搬到了 breeding_cycles 上，批次只剩标签。如果建批时
     * 不开周期，母兔就既无阶段也无待办——界面上看到一批无法操作的母兔，
     * 整条生产流程根本无法开始。
     *
     * <p>并且开周期必须与建批同事务：否则部分失败会留下一半母兔入轨、一半没入轨。
     */
    @Test
    void creatingABreedingBatchPutsEveryDoeIntoThePipeline() {
        Fixture f = fixture("intake", 3);

        Assertions.assertEquals(3, intOf(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ?"
                + " and lifecycle = 'OPEN' and stage = 'AWAIT_ESTRUS'", f.houseId, f.batchId),
            "每头母兔都应有一条待催情的周期");
        Assertions.assertEquals(3, intOf(
            "select count(*) from work_tasks where house_id = ? and batch_id = ?"
                + " and task_type = 'ESTRUS' and status = 'PENDING'", f.houseId, f.batchId),
            "待办与周期同事务生成，不靠夜间扫表");
        for (long doe : f.doeIds) {
            Assertions.assertEquals("AWAIT_ESTRUS", strOf(
                "select current_stage from rabbits where id = ?", doe),
                "母兔投影列要同步，否则笼位与兔卡看不到阶段");
        }

        // 已有流水线周期的母兔入批时不得被再开一条。
        //
        // 用散养母兔（batch_id 为空）构造这个场景：她不在任何活跃批次里，
        // 因此能绕过旧的 batch_rabbits 「母兔已在活跃批次中」拦截，真正走到入轨判断。
        // 这也是真实用法：先录入存栏母兔，之后再组织进某个繁殖批次。
        long freeRange = createRabbit(f.owner, f.houseId,
            cageIds(f.owner, f.houseId).get(4), "0", "0", "intake_free");
        api.postOk("/api/repro/cycles", f.owner.token, f.houseId, obj(
            "motherRabbitId", freeRange,
            "stage", "AWAIT_MATING",
            "occurredAt", oneMinuteAgo(),
            "requestId", requestId("intake_free_open")
        ));
        createBatch(f, List.of(freeRange), "intake_b2");
        Assertions.assertEquals(1, intOf(
            "select count(*) from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'",
            freeRange),
            "已在流水线上的母兔入批时不得凭空多出一条周期");
        Assertions.assertEquals("AWAIT_MATING", strOf(
            "select stage from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'",
            freeRange),
            "入批不应把她从待配种打回待催情");
    }

    /**
     * 兔子离场（淘汰/死亡）必须一并结清她的生产周期与待办。
     *
     * <p>这里盯的是与「结束批次」同一家族的静默错误：遗留的 closeOpenByMother
     * 只写 status/closed_at，不认识 lifecycle/stage，也不取消待办。那样母兔已经离场，
     * 周期在新视角仍是 OPEN——她永久占着 uk_bc_pipeline，待办也永远停在 PENDING，
     * 今日清单里会一直有一只已不存在的兔子，而且没任何报错。
     *
     * <p>用血配场景（两个开放周期）构造，因为对单个周期的 RETIRE 只关它自己，
     * 只关一个就会漏掉另一个。
     */
    @Test
    void cullingADoeThroughTheRabbitEndpointClosesHerCyclesAndTasks() {
        Fixture f = fixture("depart", 1);
        long doe = f.doeIds.get(0);

        long nursing = openAt(f, doe, f.batchId, "AWAIT_WEANING", "depart_nursing",
            obj("birthDate", oneMinuteAgo(), "totalKits", 6, "liveKits", 5));
        advanceToMating(f, doe, "depart_pipeline");
        Assertions.assertEquals(2, intOf(
            "select count(*) from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'",
            doe), "前置：血配下应有两个开放周期");

        api.postOk("/api/rabbits/events", f.owner.token, f.houseId, obj(
            "rabbitId", doe,
            "eventType", "cull",
            "actionDate", oneMinuteAgo(),
            "reason", "繁殖性能下降",
            "forceExitBatch", true,
            "requestId", requestId("depart_cull")
        ));

        Assertions.assertEquals(0, intOf(
            "select count(*) from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'",
            doe), "离场后不得残留开放周期，否则 uk_bc_pipeline 被永久占用");
        Assertions.assertEquals(0, intOf(
            "select count(*) from work_tasks where rabbit_id = ? and status = 'PENDING'", doe),
            "离场后不得残留待办");
        Assertions.assertEquals("RETIRED", strOf(
            "select current_stage from rabbits where id = ?", doe));
        Assertions.assertEquals("CLOSED", strOf(
            "select lifecycle from breeding_cycles where id = ?", nursing),
            "哺乳周期也要关，不能只关流水线那一个");
    }

    @Test
    void departingACommodityRabbitRemovesEveryReminder() {
        UserSession owner = register("depart_reminder");
        long houseId = createHouse(owner, "离场提醒兔舍", 1, 2, 1);
        long rabbitId = createRabbit(
            owner,
            houseId,
            cageIds(owner, houseId).get(0),
            "2",
            "0",
            "离场提醒商品兔"
        );

        jdbc.update(
            "update rabbits set growth_stage = 'MATURE' where house_id = ? and id = ?",
            houseId,
            rabbitId
        );
        jdbc.update(
            "update work_tasks set due_date = date_sub(curdate(), interval 1 day),"
                + " due_time = date_sub(now(), interval 1 day)"
                + " where house_id = ? and rabbit_id = ? and task_type = 'SALE_READY'",
            houseId,
            rabbitId
        );
        api.postOk("/api/treatments", owner.token, houseId, obj(
            "rabbitId", rabbitId,
            "startDate", oneMinuteAgo(),
            "nextReviewDate", oneMinuteAgo(),
            "diagnosis", "离场前观察",
            "requestId", requestId("depart_treatment")
        ));
        Assertions.assertEquals(2, reminderCount(owner, houseId, rabbitId),
            "前置：商品出售和治疗复查都应进入首页提醒");

        api.postOk("/api/rabbits/events", owner.token, houseId, obj(
            "rabbitId", rabbitId,
            "eventType", "cull",
            "actionDate", oneMinuteAgo(),
            "reason", "离场提醒回归",
            "forceExitBatch", false,
            "requestId", requestId("depart_reminder_cull")
        ));

        Assertions.assertEquals(0, intOf(
            "select count(*) from work_tasks where house_id = ? and rabbit_id = ?"
                + " and status = 'PENDING'",
            houseId,
            rabbitId
        ), "离场事务必须取消兔只名下全部待办");
        Assertions.assertEquals(0, reminderCount(owner, houseId, rabbitId),
            "离场兔不能继续出现在首页提醒");

        // 查询端也要兜底。即使历史数据或并发写入重新造出 PENDING 行，离场兔仍不可见。
        jdbc.update(
            "update work_tasks set status = 'PENDING' where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY'",
            houseId,
            rabbitId
        );
        JsonNode tasks = api.getOk(
            "/api/tasks?includeFuture=true&rabbitId=" + rabbitId,
            owner.token,
            houseId
        );
        Assertions.assertEquals(0, tasks.get("total").asInt(),
            "待办查询不得返回离场兔的残留任务");
        Assertions.assertEquals(0, reminderCount(owner, houseId, rabbitId),
            "首页查询不得返回离场兔的残留任务或治疗复查");
        Assertions.assertEquals(1, intOf(
            "select count(*) from treatment_records where house_id = ? and rabbit_id = ?"
                + " and status = 'OPEN'",
            houseId,
            rabbitId
        ), "治疗记录保留原始状态供追溯，但不再生成提醒");
    }

    private int reminderCount(UserSession owner, long houseId, long rabbitId) {
        int count = 0;
        for (JsonNode event : api.getOk("/api/events", owner.token, houseId)) {
            if (event.path("rabbitId").asLong() == rabbitId) {
                count++;
            }
        }
        return count;
    }

    private long openAt(Fixture f, long doe, long batchId, String stage, String prefix) {
        return openAt(f, doe, batchId, stage, prefix, obj());
    }

    /**
     * 把建批次时自动开出的待催情周期推到待配种。
     *
     * <p>不能直接再开一个待配种周期：那会撞上「一头母兔仅一条流水线周期」不变式。
     * 实际用法也是如此——母兔入批即入轨，之后靠动作逐步推进。
     */
    private long advanceToMating(Fixture f, long doe, String prefix) {
        Long cycleId = jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN' and stage = 'AWAIT_ESTRUS' order by id desc limit 1",
            Long.class, f.houseId, doe);
        act(f, cycleId, prefix + "_estrus", obj(
            "action", "ESTRUS", "occurredAt", oneMinuteAgo()));
        return cycleId;
    }

    private long openAt(
        Fixture f, long doe, long batchId, String stage, String prefix, Map<String, Object> extra
    ) {
        Map<String, Object> body = obj(
            "motherRabbitId", doe,
            "batchId", batchId,
            "stage", stage,
            "occurredAt", oneMinuteAgo(),
            "requestId", requestId(prefix)
        );
        body.putAll(extra);
        return api.postOk("/api/repro/cycles", f.owner.token, f.houseId, body)
            .get("cycleId").asLong();
    }

    private void act(Fixture f, long cycleId, String prefix, Map<String, Object> body) {
        body.put("requestId", requestId(prefix));
        api.postOk("/api/repro/cycles/" + cycleId + "/actions", f.owner.token, f.houseId, body);
    }

    private long createBatch(Fixture f, List<Long> does, String prefix) {
        // 用完整随机后缀：requestId(prefix) 的前八位是 prefix 本身，
        // 两个同前缀的批次（across_b2 / across_batch）会撞出重复编号。
        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);
        return api.postOk("/api/batches", f.owner.token, f.houseId, obj(
            "batchCode", "RL-" + unique,
            "femaleRabbitIds", does,
            "requestId", requestId(prefix + "_create")
        )).get("id").asLong();
    }

    private Fixture fixture(String prefix, int doeCount) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, doeCount + 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long buckId = createRabbit(owner, houseId, cages.get(0), "0", "1", prefix + "_buck");
        List<Long> does = new ArrayList<>();
        for (int i = 0; i < doeCount; i++) {
            does.add(createRabbit(owner, houseId, cages.get(i + 1), "0", "0", prefix + "_doe" + i));
        }
        Fixture f = new Fixture(owner, houseId, 0, buckId, does);
        long batchId = createBatch(f, does, prefix + "_batch");
        return new Fixture(owner, houseId, batchId, buckId, does);
    }

    private record Fixture(
        UserSession owner, long houseId, long batchId, long buckId, List<Long> doeIds
    ) {
    }

    /**
     * 批次新口径：可先建空壳，母兔陆续追加，追加即入轨。
     *
     * <p>旧实现强制建批时凑齐母兔，而现场是先拉一个批次、母兔到期了再放进去。
     */
    @Test
    void batchCanStartEmptyAndTakeMembersLater() {
        UserSession owner = register("batch_empty");
        long houseId = createHouse(owner, "空批兔舍", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", "empty_doe");

        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "B-EMPTY-" + java.util.UUID.randomUUID().toString().substring(0, 6),
                "requestId", requestId("empty_create")
        ));
        long batchId = batch.get("id").asLong();
        Assertions.assertEquals(0,
                (int) jdbc.queryForObject(
                        "select count(*) from breeding_cycles where batch_id = ?",
                        Integer.class, batchId),
                "空批次不应凭空产生生产周期");
        // 空批次一建出来就已无在册母兔，因此立刻带上「可结束」提示。
        Assertions.assertTrue(
                api.getOk("/api/batches/" + batchId, owner.token, houseId)
                        .get("pendingCompletion").asBoolean(),
                "无成员的批次应提示可结束");

        api.postOk("/api/batches/" + batchId + "/members", owner.token, houseId, obj(
                "femaleRabbitIds", List.of(doeId),
                "requestId", requestId("empty_add")
        ));

        Assertions.assertEquals("AWAIT_ESTRUS",
                jdbc.queryForObject(
                        "select stage from breeding_cycles where batch_id = ? and mother_rabbit_id = ?",
                        String.class, batchId, doeId),
                "追加的母兔必须当场入轨");
        Assertions.assertEquals(1,
                (int) jdbc.queryForObject(
                        "select count(*) from work_tasks where house_id = ? and rabbit_id = ? and status = 'PENDING'",
                        Integer.class, houseId, doeId));
        Assertions.assertFalse(
                api.getOk("/api/batches/" + batchId, owner.token, houseId)
                        .get("pendingCompletion").asBoolean(),
                "已有在册母兔就不该再提示结束");
    }

    /**
     * 成员全部离场后，批次只提示、不自动结束。
     *
     * <p>「这一轮算不算完」是业务判断：母兔可能只是暂时清空，用户还想继续往里补兔。
     */
    @Test
    void emptyingABatchOnlyPromptsInsteadOfClosingIt() {
        UserSession owner = register("batch_prompt");
        long houseId = createHouse(owner, "提示兔舍", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", "prompt_doe");
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "B-PROMPT-" + java.util.UUID.randomUUID().toString().substring(0, 6),
                "femaleRabbitIds", List.of(doeId),
                "requestId", requestId("prompt_create")
        ));
        long batchId = batch.get("id").asLong();

        api.postOk("/api/rabbits/events", owner.token, houseId, obj(
                "rabbitId", doeId, "eventType", "cull", "actionDate", new Date().getTime(),
                // 母兔还在活跃批次里，离场需显式确认退出批次。
                "forceExitBatch", true,
                "reason", "淘汰", "requestId", requestId("prompt_cull")
        ));

        JsonNode after = api.getOk("/api/batches/" + batchId, owner.token, houseId);
        Assertions.assertNotEquals("已完成", after.get("status").asText(),
                "不应再自动结束批次");
        Assertions.assertTrue(after.get("pendingCompletion").asBoolean(),
                "应提示用户去结束批次");
    }
}
