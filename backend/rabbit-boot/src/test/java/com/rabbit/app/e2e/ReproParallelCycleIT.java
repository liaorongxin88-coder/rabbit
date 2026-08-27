package com.rabbit.app.e2e;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.domain.DeliveryOutcome;
import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.service.OpenCycleCommand;
import com.rabbit.app.modules.repro.service.ReproActionService;
import com.rabbit.app.modules.repro.service.ReproCommand;
import com.rabbit.app.modules.repro.service.ReproResult;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 一只母兔同时持有两条周期时的状态机验收。
 *
 * <p>血配把「一兔一周期」变成了「一兔两周期」：哺乳段（待分笼）不占管线，
 * 所以母兔可以一边带崽一边跑下一轮怀孕。两条周期各自有阶段、有待办、有窝，
 * 而母兔身上只有一个 current_stage 投影列——冲突只会在这里出现。
 *
 * <p>本套件专门盯「操作 A 周期时会不会伤到 B 周期」：历史上真出现过给 A 分笼
 * 把 B 的阶段覆盖掉的漂移缺陷，只断言当事周期是发现不了的。
 *
 * <p><b>V44 改了两条并行周期的批次归属。</b>它们不再共用一个批次，而是各占一个：
 * 飞书 recvqh3EJXzmO1 定义「每只母兔在同一 Batch 内只有一个繁育周期，当母兔同时
 * 位于两个繁殖周期时它也同时处于两个批次之中」。血配本身仍然合法，只是第二条
 * 周期必须显式选定另一个批次（新建或现有都行），服务端不自动建批。
 * 因此本类的夹具带两个批次，并行周期一律走 {@code openBloodMatingAt}。
 */
public class ReproParallelCycleIT extends E2eTestSupport {
    @Autowired
    private ReproStateMachineService stateMachine;

    @Autowired
    private ReproActionService actionService;

    @Autowired
    private com.rabbit.app.modules.repro.service.WorkTaskService workTaskService;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 待分笼 + 待催情 并存，且互不干扰。
     *
     * <p>这个组合不是假想：母兔产仔进入待分笼后被血配，若这一轮摸胎空怀，
     * T4b 会立刻关掉怀孕周期并接续一条<b>待催情</b>周期，于是她同时挂着
     * 「待分笼」和「待催情」两条 OPEN 周期。两者的待办主体还不同——
     * 分笼挂在窝上，催情挂在周期上——正是最容易互相误伤的形状。
     */
    @Test
    void lactationAndNextEstrusCoexistWithoutInterference() {
        Fixture f = fixture("par_coexist");
        ReproResult nursing = openAtEstrus(f, "coexist_open");
        advanceToDelivery(f, nursing.cycleId(), "coexist");
        Long litterId = litterIdOf(nursing.cycleId());
        assertProjection(f, ReproStage.READY, null);

        // 分笼前开启下一轮（落到血配批次），再摸出空怀 —— 由此产生「待分笼 + 待催情」
        ReproResult bloodMating = openBloodMatingAt(f, ReproStage.AWAIT_MATING, "coexist_blood");
        apply(f, bloodMating.cycleId(), ReproAction.MATING, "coexist_mate",
            b -> b.maleRabbitId(f.sireId).matingMethod(MatingMethod.NATURAL));
        ReproResult empty = apply(f, bloodMating.cycleId(), ReproAction.PALPATION, "coexist_empty",
            b -> b.outcome(PalpationResult.EMPTY.name()).palpationResult(PalpationResult.EMPTY));

        Long estrusCycle = empty.followUpCycleId();
        Assertions.assertNotNull(estrusCycle, "空怀应接续一条新的待催情周期");

        assertStage(nursing.cycleId(), ReproStage.AWAIT_WEANING, "OPEN");
        assertStage(estrusCycle, ReproStage.AWAIT_ESTRUS, "OPEN");
        Assertions.assertEquals(2, openCycles(f), "此时应恰有哺乳与待催情两条周期");
        // 两条待办并存，且主体不同：分笼挂窝、催情挂周期。
        Assertions.assertEquals("WEANING", pendingTaskTypeOnLitter(litterId));
        Assertions.assertEquals("ESTRUS", pendingTaskTypeOnCycle(estrusCycle));
        // 管线周期优先：母兔卡片显示待催情，而不是待分笼。
        assertProjection(f, ReproStage.AWAIT_ESTRUS, estrusCycle);

        // ① 推进待催情周期，哺乳周期与它的分笼待办必须纹丝不动
        apply(f, estrusCycle, ReproAction.ESTRUS, "coexist_estrus", b -> b);
        assertStage(nursing.cycleId(), ReproStage.AWAIT_WEANING, "OPEN");
        Assertions.assertEquals("WEANING", pendingTaskTypeOnLitter(litterId),
            "推进待催情周期不应动到窝上的分笼待办");
        assertStage(estrusCycle, ReproStage.AWAIT_MATING, "OPEN");

        // ② 给哺乳周期分笼，待配种周期必须纹丝不动，且不得再接续一条新周期
        ReproResult weaned = actionService.apply(
            command(f, nursing.cycleId(), ReproAction.WEANING, requestId("coexist_wean"))
                .weanedCount(7).build(),
            new ReproActionService.PlacementInput(null, 3, 4)
        );
        Assertions.assertNull(weaned.followUpCycleId(),
            "管线上已有周期在跑，分笼不得再自动接续，否则违反 uk_bc_pipeline");
        assertStage(nursing.cycleId(), null, "CLOSED");
        assertStage(estrusCycle, ReproStage.AWAIT_MATING, "OPEN");
        Assertions.assertEquals(1, openCycles(f));
        // 投影跟随仍在跑的那条，而不是被刚关掉的哺乳周期拖回去。
        assertProjection(f, ReproStage.AWAIT_MATING, estrusCycle);
    }

    /** 推迟只改当事周期的到期时间，另一条周期的待办不受影响。 */
    @Test
    void postponingOneCycleLeavesTheOtherTaskUntouched() {
        Fixture f = fixture("par_postpone");
        ReproResult nursing = openAtEstrus(f, "pp_open");
        advanceToDelivery(f, nursing.cycleId(), "pp");
        Long litterId = litterIdOf(nursing.cycleId());
        ReproResult mating = openBloodMatingAt(f, ReproStage.AWAIT_MATING, "pp_blood");

        Date weaningDueBefore = taskDueOnLitter(litterId);
        Date matingDueBefore = taskDueOnCycle(mating.cycleId());

        // 推迟管线上的配种待办
        Date later = new Date(System.currentTimeMillis() + 7L * 86400_000L);
        apply(f, mating.cycleId(), ReproAction.POSTPONE, "pp_delay", b -> b.nextRemindAt(later));

        Assertions.assertTrue(taskDueOnCycle(mating.cycleId()).after(matingDueBefore),
            "被推迟的待办应顺延");
        Assertions.assertEquals(weaningDueBefore, taskDueOnLitter(litterId),
            "推迟配种不得顺带改掉窝上的分笼待办");
        Assertions.assertEquals(1, snoozeCountOnCycle(mating.cycleId()), "推迟次数应留痕");
        // 推迟不改阶段，两条周期都还开着。
        assertStage(nursing.cycleId(), ReproStage.AWAIT_WEANING, "OPEN");
        assertStage(mating.cycleId(), ReproStage.AWAIT_MATING, "OPEN");
        Assertions.assertEquals(2, openCycles(f));
    }

    /**
     * 对单条周期发离场，不能把母兔的另一条周期晾成「开着但没有待办」的孤儿。
     *
     * <p>离场转移的语义是「这只母兔走了」而不是「这条周期结束了」，所以它按兔取消
     * 全部待办。若只关掉当事周期，另一条周期就会 OPEN 着却从待办中心消失：
     * 崽子永远等不到分笼提醒，批次也会被这条看不见的周期永久卡住无法结束。
     */
    @Test
    void retiringOneCycleMustNotStrandTheOther() {
        Fixture f = fixture("par_retire");
        ReproResult nursing = openAtEstrus(f, "rt_open");
        advanceToDelivery(f, nursing.cycleId(), "rt");
        ReproResult mating = openBloodMatingAt(f, ReproStage.AWAIT_MATING, "rt_blood");
        Assertions.assertEquals(2, openCycles(f));

        // 只对管线周期发离场
        actionService.apply(
            command(f, mating.cycleId(), ReproAction.RETIRE, requestId("rt_retire")).build(),
            ReproActionService.PlacementInput.empty()
        );

        Assertions.assertEquals(0, openCycles(f),
            "离场后母兔不得残留任何开着的周期，否则是待办中心里看不见的孤儿");
        Assertions.assertEquals(0, pendingTasks(f), "离场后不得残留待办");
        assertStage(nursing.cycleId(), null, "CLOSED");
        assertStage(mating.cycleId(), null, "CLOSED");
        Assertions.assertEquals("RETIRED", projectedStage(f));
    }

    /** 自动接续的新周期继续归属同一生产批次。 */
    @Test
    void followUpCycleInheritsTheClosedBatchBinding() {
        Fixture f = batchFixture("par_batch");
        Long firstCycle = openCycleIdInBatch(f);
        Assertions.assertEquals(f.batchId, batchIdOf(firstCycle), "建批入轨的周期应绑在该批次上");

        // 走一轮空怀，触发接续
        apply(f, firstCycle, ReproAction.ESTRUS, "batch_estrus", b -> b);
        apply(f, firstCycle, ReproAction.MATING, "batch_mating",
            b -> b.maleRabbitId(f.sireId).matingMethod(MatingMethod.NATURAL));
        ReproResult empty = apply(f, firstCycle, ReproAction.PALPATION, "batch_empty",
            b -> b.outcome(PalpationResult.EMPTY.name()).palpationResult(PalpationResult.EMPTY));

        Long followUp = empty.followUpCycleId();
        Assertions.assertNotNull(followUp);
        Assertions.assertEquals(f.batchId, batchIdOf(followUp),
            "接续周期必须继承已关闭周期的生产批次");
        Assertions.assertEquals(1, openCycles(f));
    }

    /**
     * 两条并行周期落在两个不同批次，且各自挡住自己那个批次的结束。
     *
     * <p><b>这条用例的语义在 V44 被整个换掉了。</b>原来它叫
     * {@code bothParallelCyclesBlockBatchCompletion}，断言两条周期留在<b>同一个</b>
     * 批次里（注释写的是「两条并行周期都应计入批次的未结束周期」）。飞书
     * recvqh3EJXzmO1 的新定义把这件事反过来，于是断言也反过来。
     *
     * <p>换语义不等于放松保护。「批次还有未结束周期就不许结束」这条既有守卫必须
     * 原样活着，只是现在要<b>逐个批次</b>验证：以前一个批次挡两条周期，现在是两个
     * 批次各挡一条。任何一边漏掉，被漏掉的那条周期就会随批次归档一起从视野里消失，
     * 而母兔仍被 uk_bc_pipeline 占着。
     */
    @Test
    void parallelCyclesLandInSeparateBatchesAndEachBlocksItsOwn() {
        Fixture f = batchFixture("par_complete");
        Long nursingCycle = openCycleIdInBatch(f);
        advanceToDelivery(f, nursingCycle, "complete");
        // 血配的第二轮：必须显式选另一个批次，服务端不会替你新建。
        ReproResult bloodCycle = openBloodMatingAt(f, ReproStage.AWAIT_MATING, "complete_blood");

        // ① 一兔两周期 == 一兔两批次
        Assertions.assertEquals(2, openCycles(f), "血配下母兔仍应有两条未结束周期");
        Assertions.assertEquals(f.batchId, batchIdOf(nursingCycle), "哺乳周期留在原批次");
        Assertions.assertEquals(
            f.bloodBatchId, batchIdOf(bloodCycle.cycleId()), "并行周期必须落到另一个批次"
        );
        Assertions.assertEquals(1, openCyclesInBatch(f, f.batchId),
            "同一 (母兔, 批次) 至多一条未结束周期");
        Assertions.assertEquals(1, openCyclesInBatch(f, f.bloodBatchId),
            "同一 (母兔, 批次) 至多一条未结束周期");

        // ② 成员关系由生产周期派生，所以母兔应同时是两个批次的活跃繁殖成员
        Assertions.assertEquals(1, activeBreedingMembers(f, f.batchId));
        Assertions.assertEquals(1, activeBreedingMembers(f, f.bloodBatchId));

        // ③ 同批次内不得再开第二条：这是硬约束，不是「先到先得」。
        // 用待分笼入轨并补齐它的必录事实：它不占管线，所以报错只能来自批次约束；
        // 事实缺一个都会先拿到 400，那就测不到想测的那条规则了。
        BizException duplicate = Assertions.assertThrows(
            BizException.class,
            () -> stateMachine.openCycleAt(new OpenCycleCommand(
                f.houseId, f.userId, "tester", f.doeId, f.bloodBatchId,
                ReproStage.AWAIT_WEANING, new Date(), new Date(), null, null, new Date(),
                6, 5, null, null, null, null, requestId("complete_dup")
            ))
        );
        Assertions.assertEquals(409, duplicate.getCode());
        Assertions.assertTrue(duplicate.getMessage().contains("其他批次"), duplicate.getMessage());

        // ④ 既有保护原样保留：两个批次各自都因为自己那条未结束周期而不能结束
        assertCompletionBlocked(f, f.batchId);
        assertCompletionBlocked(f, f.bloodBatchId);

        // ⑤ 离场是按兔结清的，两条周期一起关掉，两个批次随之都可以结束
        actionService.apply(
            command(f, bloodCycle.cycleId(), ReproAction.RETIRE, requestId("complete_retire"))
                .build(),
            ReproActionService.PlacementInput.empty()
        );
        Assertions.assertEquals(0, openCycles(f), "离场后不得残留开着的周期");
        completeBatch(f, f.bloodBatchId, "complete_ok_blood");
        completeBatch(f, f.batchId, "complete_ok_nursing");
    }

    /** 结束批次必须被拒，且周期原样保留——拒绝不能是「先改了再报错」。 */
    private void assertCompletionBlocked(Fixture f, Long batchId) {
        int before = openCyclesInBatch(f, batchId);
        api.expectError(
            "/api/batches/" + batchId + "/complete",
            org.springframework.http.HttpMethod.POST,
            f.owner.token, f.houseId,
            obj("force", true, "endDate", now(),
                "requestId", requestId("blk" + batchId)),
            409, "未结束的生产周期"
        );
        Assertions.assertEquals(before, openCyclesInBatch(f, batchId),
            "被拒绝后该批次的未结束周期数必须原样保留");
        Assertions.assertEquals("进行中", jdbc.queryForObject(
            "select status from batches where id = ?", String.class, batchId
        ));
    }

    private void completeBatch(Fixture f, Long batchId, String prefix) {
        api.postOk("/api/batches/" + batchId + "/complete", f.owner.token, f.houseId, obj(
            "force", true, "endDate", now(), "requestId", requestId(prefix)
        ));
        Assertions.assertEquals("已完成", jdbc.queryForObject(
            "select status from batches where id = ?", String.class, batchId
        ));
    }

    /**
     * 流产（T8）在孕期三个阶段都能发生，一律关周期、记 ABORTED、复旧后重新催情。
     *
     * <p>三个阶段分开跑：旧实现把这段判断散在多个方法里，它们曾经各自漂移过。
     */
    @Test
    void abortionFromEveryGestationStageClosesAndReopens() {
        for (ReproStage stage : List.of(
            ReproStage.AWAIT_PALPATION, ReproStage.AWAIT_PREPARTUM, ReproStage.AWAIT_DELIVERY
        )) {
            String tag = "ab_" + stage.name().toLowerCase();
            Fixture f = fixture(tag);
            ReproResult cycle = openAtEstrus(f, tag + "_open");
            advanceTo(f, cycle.cycleId(), stage, tag);
            assertStage(cycle.cycleId(), stage, "OPEN");
            String imageId = uploadTestImage(f.owner, f.houseId, tag + "_abortion");

            BizException missingCount = Assertions.assertThrows(
                BizException.class,
                () -> apply(
                    f,
                    cycle.cycleId(),
                    ReproAction.ABORTION,
                    tag + "_missing_count",
                    b -> b.remark("流产详情").attachmentFileIds(List.of(imageId))
                )
            );
            Assertions.assertEquals(400, missingCount.getCode());
            Assertions.assertTrue(missingCount.getMessage().contains("流产死胎数"));
            assertStage(cycle.cycleId(), stage, "OPEN");

            ReproResult aborted = apply(f, cycle.cycleId(), ReproAction.ABORTION, tag + "_abort",
                b -> b.stillbirthCount(2)
                    .remark("流产详情")
                    .attachmentFileIds(List.of(imageId)));
            // 死胎数是设计 §5.2 明列的字段，不能在写入时静默丢失。
            // 用 JSON 取值而不是子串匹配：MySQL 的 JSON 列会重排版（冒号后补空格）。
            Assertions.assertEquals("2", eventPayloadField(cycle.cycleId(), "ABORTION", "stillbirthCount"),
                "流产事件应留下死胎数");

            assertStage(cycle.cycleId(), stage, "CLOSED");
            Assertions.assertEquals("ABORTED", resultOf(cycle.cycleId()),
                "流产结果要可统计，否则算不出流产率");
            // 关周期不改 stage：报表靠它回答「在哪一步流的」。
            Assertions.assertNotNull(aborted.followUpCycleId(), "流产后应接续新一轮");
            assertStage(aborted.followUpCycleId(), ReproStage.AWAIT_ESTRUS, "OPEN");
            Assertions.assertEquals(1, openCycles(f));
            Assertions.assertEquals("ESTRUS", pendingTaskTypeOnCycle(aborted.followUpCycleId()));
        }
    }

    @Test
    void emptyAbortionAndWeaningFollowUpsInheritTheClosedBatch() {
        Fixture emptyFixture = batchFixture("batch_empty_close");
        Long emptyCycle = openCycleIdInBatch(emptyFixture);
        apply(emptyFixture, emptyCycle, ReproAction.ESTRUS, "batch_empty_estrus", b -> b);
        apply(emptyFixture, emptyCycle, ReproAction.MATING, "batch_empty_mating",
            b -> b.maleRabbitId(emptyFixture.sireId).matingMethod(MatingMethod.NATURAL));
        ReproResult empty = apply(
            emptyFixture,
            emptyCycle,
            ReproAction.PALPATION,
            "batch_empty",
            b -> b.outcome(PalpationResult.EMPTY.name()).palpationResult(PalpationResult.EMPTY)
        );
        assertFollowUpInheritedBatch(emptyFixture, empty.followUpCycleId());

        Fixture abortionFixture = batchFixture("batch_abort_close");
        Long abortionCycle = openCycleIdInBatch(abortionFixture);
        advanceTo(abortionFixture, abortionCycle, ReproStage.AWAIT_PREPARTUM, "batch_abort");
        String abortionImage = uploadTestImage(
            abortionFixture.owner, abortionFixture.houseId, "batch_abort"
        );
        ReproResult aborted = apply(
            abortionFixture,
            abortionCycle,
            ReproAction.ABORTION,
            "batch_abort_do",
            b -> b.stillbirthCount(2)
                .remark("流产详情")
                .attachmentFileIds(List.of(abortionImage))
        );
        assertFollowUpInheritedBatch(abortionFixture, aborted.followUpCycleId());

        Fixture weaningFixture = batchFixture("batch_weaning_close");
        Long weaningCycle = openCycleIdInBatch(weaningFixture);
        advanceToDelivery(weaningFixture, weaningCycle, "batch_weaning");
        ReproResult weaned = apply(
            weaningFixture,
            weaningCycle,
            ReproAction.WEANING,
            "batch_weaning_do",
            b -> b.weanedCount(7)
        );
        assertFollowUpInheritedBatch(weaningFixture, weaned.followUpCycleId());
    }

    private void assertFollowUpInheritedBatch(Fixture fixture, Long followUpCycleId) {
        Assertions.assertNotNull(followUpCycleId);
        Assertions.assertEquals(1, (int) jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ?"
                + " and id = ? and lifecycle = 'OPEN'",
            Integer.class, fixture.houseId, fixture.batchId, followUpCycleId
        ));

        JsonNode item = api.getOk(
            "/api/batches/" + fixture.batchId + "/batch-rabbits",
            fixture.owner.token,
            fixture.houseId
        ).get(0);
        Assertions.assertEquals(followUpCycleId, item.get("currentCycleId").asLong());
        Assertions.assertEquals("AWAIT_ESTRUS", item.get("currentStage").asText());
        Assertions.assertEquals(1, api.getOk(
            "/api/tasks?batchId=" + fixture.batchId + "&includeFuture=true",
            fixture.owner.token,
            fixture.houseId
        ).get("total").asInt());
    }

    /** 哺乳段不是孕期，对待分笼周期发流产必须被拒。 */
    @Test
    void abortionIsRejectedOnANursingCycle() {
        Fixture f = fixture("par_abort_nursing");
        ReproResult nursing = openAtEstrus(f, "an_open");
        advanceToDelivery(f, nursing.cycleId(), "an");

        com.rabbit.app.common.BizException error = Assertions.assertThrows(
            com.rabbit.app.common.BizException.class,
            () -> apply(f, nursing.cycleId(), ReproAction.ABORTION, "an_abort", b -> b.stillbirthCount(1))
            // 哺乳段已经生完了，“流产”在这里没有业务含义
        );
        Assertions.assertEquals(409, error.getCode());
        assertStage(nursing.cycleId(), ReproStage.AWAIT_WEANING, "OPEN");
    }

    /**
     * 批量离场同样不得留下孤儿周期。
     *
     * <p>批量路径是更常见的触发方式（在待办列表里刃选一批淘汰），而它只能
     * 看到待办对应的那一条周期，所以必须单独钉住。
     */
    @Test
    void bulkRetireAlsoSettlesTheParallelCycle() {
        Fixture f = fixture("par_bulk_retire");
        ReproResult nursing = openAtEstrus(f, "br_open");
        advanceToDelivery(f, nursing.cycleId(), "br");
        ReproResult mating = openBloodMatingAt(f, ReproStage.AWAIT_MATING, "br_blood");
        Assertions.assertEquals(2, openCycles(f));

        Long matingTaskId = taskField(mating.cycleId(), "CYCLE", "id", Long.class);
        com.rabbit.app.modules.repro.dto.BulkActionRequest bulk =
            new com.rabbit.app.modules.repro.dto.BulkActionRequest();
        bulk.setRequestId(requestId("br_bulk"));
        bulk.setAction(ReproAction.RETIRE.name());
        bulk.setOccurredAt(new Date());
        bulk.setTaskIds(List.of(matingTaskId));
        workTaskService.bulkApply(f.houseId, f.userId, "tester", bulk);

        Assertions.assertEquals(0, openCycles(f), "批量离场同样不得留下开着的周期");
        Assertions.assertEquals(0, pendingTasks(f));
        Assertions.assertEquals("RETIRED", projectedStage(f));
    }

    /**
     * 阶段字典端点：客户端靠它决定流产入口的显隐。
     *
     * <p>走 HTTP 而不是直接调 {@code TransitionTable}：要验的正是客户端真实拿到的
     * 那份数据，包括序列化后的形状。
     */
    @Test
    void stageActionDictionaryTellsClientsWhenAbortionIsOffered() {
        UserSession owner = register("par_dict");
        long houseId = createHouse(owner, "par_dict_house", 1, 2, 1);
        com.fasterxml.jackson.databind.JsonNode rows =
            api.getOk("/api/repro/stage-actions", owner.token, houseId);

        java.util.List<String> allowing = new java.util.ArrayList<>();
        java.util.List<String> stages = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode row : rows) {
            stages.add(row.get("stage").asText());
            Assertions.assertFalse(row.get("stageLabel").asText().isBlank(), "阶段中文名不得为空");
            for (com.fasterxml.jackson.databind.JsonNode action : row.get("actions")) {
                if ("ABORTION".equals(action.get("action").asText())) {
                    allowing.add(row.get("stage").asText());
                    Assertions.assertEquals("流产", action.get("label").asText());
                }
            }
        }

        Assertions.assertTrue(stages.contains("AWAIT_WEANING"), "字典应覆盖全部阶段");
        Assertions.assertEquals(
            List.of("AWAIT_PALPATION", "AWAIT_PREPARTUM", "AWAIT_DELIVERY"), allowing,
            "流产只应在孕期三个阶段提供入口"
        );
    }

    /**
     * 入轨阶段字典：录入表单靠它决定「选了这个阶段要多填哪几个日期」。
     *
     * <p>客户端拄一份就会漂移，用户会遇到「填完才 400」（飞书 recvsrnEJ8bKrk）。
     */
    @Test
    void entryPointDictionaryTellsClientsWhichFactsAreRequired() {
        UserSession owner = register("par_entry");
        long houseId = createHouse(owner, "par_entry_house", 1, 2, 1);
        com.fasterxml.jackson.databind.JsonNode rows =
            api.getOk("/api/repro/entry-points", owner.token, houseId);

        java.util.Map<String, java.util.List<String>> factsByStage =
            new java.util.LinkedHashMap<>();
        for (com.fasterxml.jackson.databind.JsonNode row : rows) {
            Assertions.assertFalse(row.get("stageLabel").asText().isBlank(), "阶段中文名不得为空");
            java.util.List<String> facts = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode fact : row.get("requiredFacts")) {
                Assertions.assertFalse(fact.get("label").asText().isBlank(), "必填项中文名不得为空");
                facts.add(fact.get("fact").asText());
            }
            factsByStage.put(row.get("stage").asText(), facts);
        }

        // 只列可入轨的阶段；准备/暂停/离场不是入轨点。
        Assertions.assertEquals(
            List.of(
                "AWAIT_ESTRUS", "AWAIT_MATING", "AWAIT_PALPATION",
                "AWAIT_PREPARTUM", "AWAIT_DELIVERY", "AWAIT_WEANING"
            ),
            List.copyOf(factsByStage.keySet())
        );
        Assertions.assertEquals(List.of("STAGE_ENTERED_AT"), factsByStage.get("AWAIT_ESTRUS"));
        Assertions.assertEquals(List.of("MATING_DATE"), factsByStage.get("AWAIT_PALPATION"));
        Assertions.assertEquals(List.of("STAGE_ENTERED_AT"), factsByStage.get("AWAIT_PREPARTUM"));
        Assertions.assertEquals(List.of("STAGE_ENTERED_AT"), factsByStage.get("AWAIT_DELIVERY"));
        Assertions.assertEquals(
            List.of("BIRTH_DATE", "LIVE_KITS"), factsByStage.get("AWAIT_WEANING")
        );
    }

    /**
     * 字典说不允许的阶段，服务端必须真的拒绝。
     *
     * <p>否则字典只是个建议，绕过界面直接调接口就能写出非法状态。
     */
    @Test
    void abortionOverHttpIsRejectedOutsideGestation() {
        Fixture f = fixture("par_http_abort");
        ReproResult nursing = openAtEstrus(f, "ha_open");
        advanceToDelivery(f, nursing.cycleId(), "ha");

        api.expectError(
            "/api/repro/cycles/" + nursing.cycleId() + "/actions",
            org.springframework.http.HttpMethod.POST,
            f.owner.token, f.houseId,
            obj("action", "ABORTION", "occurredAt", new Date().getTime(),
                "stillbirthCount", 1, "requestId", requestId("ha_reject")),
            409, "不允许执行"
        );
    }

    /**
     * 历史记录查询必须能看到刚发生的事实。
     *
     * <p>删除旧写入路径后，pregnancy_check_records / parturition_records 两张表无人再写，
     * 而查询接口照旧返回——线上表现是“摸胎、分娩都做了，记录里什么都没有”。
     * 本用例直接走接口确认事件库回放正确，不查旧表，以免它又惄惄停更而无人发现。
     */
    @Test
    void palpationAndDeliveryHistoryStayQueryableAfterTheRewrite() {
        Fixture f = fixture("par_hist");
        ReproResult cycle = openAtEstrus(f, "hist_open");
        advanceToDelivery(f, cycle.cycleId(), "hist");

        JsonNode palpations = api.getOk(
            "/api/pregnancy-check-records?rabbitId=" + f.doeId, f.owner.token, f.houseId);
        Assertions.assertEquals(1, palpations.size(), "摸胎历史应能查到");
        Assertions.assertEquals("怀孕确认", palpations.get(0).get("result").asText());
        Assertions.assertEquals(f.doeId, palpations.get(0).get("rabbitId").asLong());
        Assertions.assertEquals(
            cycle.cycleId(), palpations.get(0).get("breedingCycleId").asLong());

        JsonNode births = api.getOk(
            "/api/parturition-records?rabbitId=" + f.doeId, f.owner.token, f.houseId);
        Assertions.assertEquals(1, births.size(), "分娩历史应能查到");
        Assertions.assertEquals(8, births.get(0).get("totalKits").asInt());
        Assertions.assertEquals(7, births.get(0).get("liveKits").asInt());
        Assertions.assertFalse(
            births.get(0).get("createBy").asText().isBlank(), "应保留操作人");
    }

    // ---------- 脚手架 ----------

    /**
     * @param batchId      主批次，第一条（哺乳）周期归它
     * @param bloodBatchId 血配批次，V44 起第二条并行周期必须归另一个批次
     */
    private record Fixture(
        UserSession owner, long userId, long houseId, long doeId, long sireId,
        Long batchId, Long bloodBatchId
    ) {
    }

    private Fixture fixture(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long sireId = createRabbit(owner, houseId, cages.get(1), "0", "1", prefix + "_sire");
        long batchId = emptyBatch(owner, houseId, prefix + "_empty_batch");
        long bloodBatchId = emptyBatch(owner, houseId, prefix + "_blood_batch");
        return new Fixture(owner, owner.userId, houseId, doeId, sireId, batchId, bloodBatchId);
    }

    /** 建批会自动给每头母兔入轨，所以批次场景不再另行开周期。 */
    private Fixture batchFixture(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long sireId = createRabbit(owner, houseId, cages.get(1), "0", "1", prefix + "_sire");
        // 批次编号用完整随机后缀：requestId(prefix) 前八位就是 prefix，同前缀会撞号。
        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "RP-" + unique,
            "femaleRabbitIds", List.of(doeId),
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();
        long bloodBatchId = emptyBatch(owner, houseId, prefix + "_blood_batch");
        return new Fixture(owner, owner.userId, houseId, doeId, sireId, batchId, bloodBatchId);
    }

    /**
     * 空批次——建批时不选母兔，新口径明确允许。
     *
     * <p>血配批次用它而不是建批时就把母兔放进去：建批带母兔会立刻自动入轨一条
     * 待催情周期，那就不是血配了。
     */
    private long emptyBatch(UserSession owner, long houseId, String requestPrefix) {
        return api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "RP-EMPTY-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "femaleRabbitIds", List.of(),
            "requestId", requestId(requestPrefix)
        )).get("id").asLong();
    }

    private Long openCycleIdInBatch(Fixture f) {
        return jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN' order by id desc limit 1",
            Long.class, f.houseId, f.doeId
        );
    }

    private ReproResult openAtEstrus(Fixture f, String prefix) {
        return openAt(f, ReproStage.AWAIT_ESTRUS, prefix);
    }

    private ReproResult openAt(Fixture f, ReproStage stage, String prefix) {
        return openAt(f, f.batchId, stage, prefix);
    }

    /**
     * 开一条并行（血配）周期。
     *
     * <p>它不能再用 {@code f.batchId}：V44 起同一 (母兔, 批次) 至多一条未结束周期，
     * 同批次里再开一条会被 409 拒掉。现实里的操作也是这样：先拉一个新批次（或选一个
     * 现有的），再把带崽的母兔配进去。
     */
    private ReproResult openBloodMatingAt(Fixture f, ReproStage stage, String prefix) {
        return openAt(f, f.bloodBatchId, stage, prefix);
    }

    private ReproResult openAt(Fixture f, Long batchId, ReproStage stage, String prefix) {
        return stateMachine.openCycleAt(new OpenCycleCommand(
            f.houseId, f.userId, "tester", f.doeId, batchId,
            stage, new Date(), new Date(), null, null, null,
            null, null, null, null, null, null, requestId(prefix)
        ));
    }

    private ReproCommand.Builder command(Fixture f, Long cycleId, ReproAction action, String requestId) {
        return ReproCommand.builder()
            .houseId(f.houseId)
            .userId(f.userId)
            .operatorName("tester")
            .cycleId(cycleId)
            .motherRabbitId(f.doeId)
            .action(action)
            .occurredAt(new Date())
            .requestId(requestId);
    }

    private ReproResult apply(
        Fixture f, Long cycleId, ReproAction action, String requestPrefix,
        java.util.function.UnaryOperator<ReproCommand.Builder> customizer
    ) {
        return stateMachine.apply(
            customizer.apply(command(f, cycleId, action, requestId(requestPrefix))).build()
        );
    }

    /** 推到指定孕期阶段为止。 */
    private void advanceTo(Fixture f, Long cycleId, ReproStage target, String prefix) {
        apply(f, cycleId, ReproAction.ESTRUS, prefix + "_estrus", b -> b);
        apply(f, cycleId, ReproAction.MATING, prefix + "_mating",
            b -> b.maleRabbitId(f.sireId).matingMethod(MatingMethod.NATURAL));
        if (target == ReproStage.AWAIT_PALPATION) {
            return;
        }
        apply(f, cycleId, ReproAction.PALPATION, prefix + "_palpation",
            b -> b.outcome(PalpationResult.PREGNANT.name()).palpationResult(PalpationResult.PREGNANT));
        if (target == ReproStage.AWAIT_PREPARTUM) {
            return;
        }
        apply(f, cycleId, ReproAction.PREPARTUM, prefix + "_prepartum", b -> b);
    }

    private String eventPayloadField(Long cycleId, String eventType, String field) {
        return jdbc.queryForObject(
            "select payload->>'$." + field + "' from repro_events"
                + " where cycle_id = ? and event_type = ?",
            String.class, cycleId, eventType
        );
    }

    private String resultOf(Long cycleId) {
        return jdbc.queryForObject(
            "select result from breeding_cycles where id = ?", String.class, cycleId
        );
    }

    private void advanceToDelivery(Fixture f, Long cycleId, String prefix) {
        apply(f, cycleId, ReproAction.ESTRUS, prefix + "_estrus", b -> b);
        apply(f, cycleId, ReproAction.MATING, prefix + "_mating",
            b -> b.maleRabbitId(f.sireId).matingMethod(MatingMethod.NATURAL));
        apply(f, cycleId, ReproAction.PALPATION, prefix + "_palpation",
            b -> b.outcome(PalpationResult.PREGNANT.name()).palpationResult(PalpationResult.PREGNANT));
        apply(f, cycleId, ReproAction.PREPARTUM, prefix + "_prepartum", b -> b);
        apply(f, cycleId, ReproAction.DELIVERY, prefix + "_delivery",
            b -> b.outcome(DeliveryOutcome.BORN.name()).totalKits(8).liveKits(7).keptKits(7));
    }

    // ---------- 断言辅助 ----------

    private void assertStage(Long cycleId, ReproStage stage, String lifecycle) {
        Map<String, Object> row = jdbc.queryForMap(
            "select stage, lifecycle from breeding_cycles where id = ?", cycleId
        );
        Assertions.assertEquals(lifecycle, row.get("lifecycle"), "周期 " + cycleId + " 的 lifecycle");
        if (stage != null) {
            Assertions.assertEquals(stage.name(), row.get("stage"), "周期 " + cycleId + " 的 stage");
        }
    }

    private int openCycles(Fixture f) {
        return jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN'",
            Integer.class, f.houseId, f.doeId
        );
    }

    /** 不限定母兔：要看的就是「这个批次还挡着几条周期」。 */
    private int openCyclesInBatch(Fixture f, Long batchId) {
        return jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ?"
                + " and lifecycle = 'OPEN'",
            Integer.class, f.houseId, batchId
        );
    }

    private int activeBreedingMembers(Fixture f, Long batchId) {
        return jdbc.queryForObject(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and batch_role = 'breeding' and is_active = true",
            Integer.class, batchId, f.doeId
        );
    }

    private int pendingTasks(Fixture f) {
        return jdbc.queryForObject(
            "select count(*) from work_tasks where house_id = ? and rabbit_id = ? and status = 'PENDING'",
            Integer.class, f.houseId, f.doeId
        );
    }

    private Long litterIdOf(Long cycleId) {
        return jdbc.queryForObject("select id from litters where cycle_id = ?", Long.class, cycleId);
    }

    private Long batchIdOf(Long cycleId) {
        return jdbc.queryForObject(
            "select batch_id from breeding_cycles where id = ?", Long.class, cycleId
        );
    }

    private String pendingTaskTypeOnLitter(Long litterId) {
        return taskField(litterId, "LITTER", "task_type", String.class);
    }

    private String pendingTaskTypeOnCycle(Long cycleId) {
        return taskField(cycleId, "CYCLE", "task_type", String.class);
    }

    private Date taskDueOnLitter(Long litterId) {
        return taskField(litterId, "LITTER", "due_time", Date.class);
    }

    private Date taskDueOnCycle(Long cycleId) {
        return taskField(cycleId, "CYCLE", "due_time", Date.class);
    }

    private int snoozeCountOnCycle(Long cycleId) {
        return taskField(cycleId, "CYCLE", "snooze_count", Integer.class);
    }

    private <T> T taskField(Long subjectId, String subjectType, String column, Class<T> type) {
        return jdbc.queryForObject(
            "select " + column + " from work_tasks where subject_type = ? and subject_id = ?"
                + " and status = 'PENDING'",
            type, subjectType, subjectId
        );
    }

    private String projectedStage(Fixture f) {
        return jdbc.queryForObject(
            "select current_stage from rabbits where id = ?", String.class, f.doeId
        );
    }

    private void assertProjection(Fixture f, ReproStage stage, Long cycleId) {
        Map<String, Object> row = jdbc.queryForMap(
            "select current_stage, current_cycle_id from rabbits where id = ?", f.doeId
        );
        Assertions.assertEquals(stage.name(), row.get("current_stage"), "母兔投影阶段");
        Object projectedCycleId = row.get("current_cycle_id");
        if (cycleId == null) {
            Assertions.assertNull(projectedCycleId, "准备态不应指向哺乳周期");
        } else {
            Assertions.assertEquals(
                cycleId,
                ((Number) projectedCycleId).longValue(),
                "母兔投影指向的周期"
            );
        }
    }
}
