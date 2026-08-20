package com.rabbit.app.e2e;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.domain.CycleLifecycle;
import com.rabbit.app.modules.repro.domain.CycleResult;
import com.rabbit.app.modules.repro.domain.DeliveryOutcome;
import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.service.OpenCycleCommand;
import com.rabbit.app.modules.repro.service.ReproCommand;
import com.rabbit.app.modules.repro.service.ReproResult;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 状态机写路径的集成验收（施工计划 P2 出口条件）。
 *
 * <p>刻意直接调服务而不是走 HTTP：本期尚无新接口，且要验的是事务、锁与幂等这些
 * 只有在真库上才会暴露的行为，MyBatis XML 的错误同样只在这里才会现形。
 */
public class ReproStateMachineIT extends E2eTestSupport {
    @Autowired
    private ReproStateMachineService stateMachine;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void fullPipelineAdvancesStageTaskAndProjectionAtEveryStep() {
        Fixture fixture = fixture("repro_full");

        ReproResult opened = openAtEstrus(fixture, "full_open");
        Assertions.assertEquals(opened.cycleId(), opened.currentCycleId());
        Assertions.assertEquals(ReproStage.AWAIT_ESTRUS, opened.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), opened.lifecycle());
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "ESTRUS");
        assertProjection(fixture, "AWAIT_ESTRUS", opened.cycleId());

        ReproResult estrus = apply(fixture, opened.cycleId(), ReproAction.ESTRUS, "full_estrus", b -> b);
        Assertions.assertEquals(opened.cycleId(), estrus.cycleId());
        Assertions.assertEquals(opened.cycleId(), estrus.currentCycleId());
        Assertions.assertEquals(ReproStage.AWAIT_MATING, estrus.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), estrus.lifecycle());
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "MATING");

        ReproResult mating = apply(fixture, opened.cycleId(), ReproAction.MATING, "full_mating",
            b -> b.maleRabbitId(fixture.sireId).matingMethod(MatingMethod.NATURAL));
        Assertions.assertEquals(ReproStage.AWAIT_PALPATION, mating.stage());
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "PALPATION");

        // 预产期必须由 gestation_days 推出，而不是旧实现里硬编码的 30 天。
        Integer gestation = jdbc.queryForObject(
            "select gestation_days from global_setting where user_id = ? order by house_id is null desc limit 1",
            Integer.class, fixture.userId
        );
        Assertions.assertNotNull(gestation);
        Assertions.assertEquals(
            gestation.intValue(),
            daysBetween(
                dateOf(opened.cycleId(), "mating_date"),
                dateOf(opened.cycleId(), "expected_birth_date")
            )
        );

        ReproResult palpation = apply(fixture, opened.cycleId(), ReproAction.PALPATION, "full_palpation",
            b -> b.outcome(PalpationResult.PREGNANT.name()).palpationResult(PalpationResult.PREGNANT));
        Assertions.assertEquals(ReproStage.AWAIT_PREPARTUM, palpation.stage());

        ReproResult prepartum = apply(fixture, opened.cycleId(), ReproAction.PREPARTUM, "full_prepartum", b -> b);
        Assertions.assertEquals(ReproStage.AWAIT_DELIVERY, prepartum.stage());

        ReproResult delivery = apply(fixture, opened.cycleId(), ReproAction.DELIVERY, "full_delivery",
            b -> b.outcome(DeliveryOutcome.BORN.name()).totalKits(9).liveKits(8).keptKits(8));
        Assertions.assertEquals(ReproStage.READY, delivery.stage());
        Assertions.assertNull(delivery.currentCycleId(), "分娩后母兔应退出繁育管线并回到准备态");
        Assertions.assertEquals(CycleLifecycle.CLOSED.name(), delivery.lifecycle());
        Assertions.assertEquals(
            ReproStage.AWAIT_WEANING.name(),
            jdbc.queryForObject(
                "select stage from breeding_cycles where id = ?",
                String.class,
                opened.cycleId()
            ),
            "旧窝周期仍须保持待分笼"
        );
        assertProjection(fixture, "READY", null);
        Assertions.assertNotNull(delivery.litterId(), "接产必须建窝");
        // 分笼任务挂在窝上而不是周期上——血配时母兔要能同时持有两条互不干扰的待办。
        Assertions.assertEquals(
            "LITTER",
            jdbc.queryForObject(
                "select subject_type from work_tasks where house_id = ? and task_type = 'WEANING' and status = 'PENDING'",
                String.class, fixture.houseId
            )
        );

        ReproResult weaning = apply(fixture, opened.cycleId(), ReproAction.WEANING, "full_weaning",
            b -> b.weanedCount(8).avgWeaningWeight(0.6));
        Assertions.assertEquals(opened.cycleId(), weaning.cycleId(), "动作周期仍应是刚完成的断奶周期");
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), weaning.lifecycle());
        Assertions.assertEquals(ReproStage.AWAIT_ESTRUS, weaning.stage());
        Assertions.assertEquals(
            CycleResult.WEANED.name(),
            jdbc.queryForObject(
                "select result from breeding_cycles where id = ?", String.class, opened.cycleId()
            )
        );
        // 断奶后自动接续下一轮：母兔不会掉出流程。
        Assertions.assertNotNull(weaning.followUpCycleId(), "断奶应自动开启下一轮周期");
        Assertions.assertEquals(weaning.followUpCycleId(), weaning.currentCycleId());
        assertProjection(fixture, "AWAIT_ESTRUS", weaning.followUpCycleId());
    }

    @Test
    void normalTransitionsUseConfiguredDueDateAndAllowReminderOverride() {
        Fixture fixture = fixture("repro_due_override");
        ReproResult opened = openAtEstrus(fixture, "due_override_open");

        Date estrusAt = new Date();
        ReproResult estrus = stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.ESTRUS, requestId("due_default"))
                .occurredAt(estrusAt)
                .build()
        );
        Integer configuredDays = jdbc.queryForObject(
            "select aphrodisiac_days from global_setting where user_id = ? order by house_id is null desc limit 1",
            Integer.class, fixture.userId
        );
        Assertions.assertNotNull(configuredDays);
        Assertions.assertEquals(
            configuredDays.intValue(),
            daysBetween(estrusAt, estrus.nextDueTime()),
            "未指定日期时应继续使用兔场配置"
        );

        Date future = new Date(System.currentTimeMillis() + 6L * 24 * 3600 * 1000);
        ReproResult mating = apply(fixture, opened.cycleId(), ReproAction.MATING, "due_future",
            b -> b.maleRabbitId(fixture.sireId)
                .matingMethod(MatingMethod.NATURAL)
                .nextRemindAt(future));
        Assertions.assertEquals(6, daysBetween(new Date(), mating.nextDueTime()));

        Date today = new Date();
        ReproResult palpation = apply(fixture, opened.cycleId(), ReproAction.PALPATION, "due_today",
            b -> b.outcome(PalpationResult.PREGNANT.name())
                .palpationResult(PalpationResult.PREGNANT)
                .nextRemindAt(today));
        Assertions.assertEquals(0, daysBetween(new Date(), palpation.nextDueTime()));
    }

    @Test
    void pastReminderOverrideIsRejectedWithoutAdvancing() {
        Fixture fixture = fixture("repro_due_past");
        ReproResult opened = openAtEstrus(fixture, "due_past_open");
        Date yesterday = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);

        BizException error = Assertions.assertThrows(BizException.class, () -> stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.ESTRUS, requestId("due_past"))
                .nextRemindAt(yesterday)
                .build()
        ));

        Assertions.assertEquals(400, error.getCode());
        Assertions.assertTrue(error.getMessage().contains("不能早于今天"), error.getMessage());
        Assertions.assertEquals(
            ReproStage.AWAIT_ESTRUS.name(),
            jdbc.queryForObject("select stage from breeding_cycles where id = ?", String.class, opened.cycleId())
        );
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "ESTRUS");
    }

    @Test
    void reminderOverrideIsRejectedWhenActionCreatesNoNextTask() {
        Fixture fixture = fixture("repro_due_none");
        ReproResult opened = openAtEstrus(fixture, "due_none_open");
        Date tomorrow = new Date(System.currentTimeMillis() + 24L * 3600 * 1000);

        BizException error = Assertions.assertThrows(BizException.class, () -> stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.RETIRE, requestId("due_none_retire"))
                .reason("淘汰")
                .nextRemindAt(tomorrow)
                .build()
        ));

        Assertions.assertEquals(400, error.getCode());
        Assertions.assertTrue(error.getMessage().contains("不会生成后续待办"), error.getMessage());
        Assertions.assertEquals(
            CycleLifecycle.OPEN.name(),
            jdbc.queryForObject("select lifecycle from breeding_cycles where id = ?", String.class, opened.cycleId())
        );
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "ESTRUS");
    }

    @Test
    void repeatedRequestIdReplaysInsteadOfAdvancingTwice() {
        Fixture fixture = fixture("repro_idem");
        ReproResult opened = openAtEstrus(fixture, "idem_open");

        String requestId = requestId("idem_estrus");
        Date override = new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000);
        ReproResult first = stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.ESTRUS, requestId)
                .nextRemindAt(override)
                .build()
        );
        ReproResult second = stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.ESTRUS, requestId)
                .nextRemindAt(override)
                .build()
        );

        Assertions.assertFalse(first.replayed());
        Assertions.assertTrue(second.replayed(), "同 requestId 重复提交应回放而非二次推进");
        Assertions.assertEquals(first.eventId(), second.eventId());
        Assertions.assertEquals(opened.cycleId(), second.currentCycleId());
        Assertions.assertEquals(ReproStage.AWAIT_MATING, second.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), second.lifecycle());
        Date authoritativeDueTime = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and id = ?",
            Date.class, fixture.houseId, first.nextTaskId()
        );
        Assertions.assertEquals(authoritativeDueTime.getTime(), second.nextDueTime().getTime());
        Assertions.assertEquals(7, daysBetween(new Date(), second.nextDueTime()));
        Assertions.assertEquals(
            1,
            (int) jdbc.queryForObject(
                "select count(*) from repro_events where house_id = ? and cycle_id = ? and event_type = 'ESTRUS_DONE'",
                Integer.class, fixture.houseId, opened.cycleId()
            )
        );
        // 阶段只前进了一步，没有被推到 AWAIT_PALPATION。
        Assertions.assertEquals(
            ReproStage.AWAIT_MATING.name(),
            jdbc.queryForObject("select stage from breeding_cycles where id = ?", String.class, opened.cycleId())
        );
    }

    @Test
    void concurrentSameTransitionLetsExactlyOneWin() throws Exception {
        Fixture fixture = fixture("repro_race");
        ReproResult opened = openAtEstrus(fixture, "race_open");

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        try {
            List<Callable<Void>> jobs = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                // 不同 requestId：绕开幂等回放，真正去撞并发控制。
                String requestId = requestId("race_estrus_" + i);
                jobs.add(() -> {
                    try {
                        stateMachine.apply(
                            command(fixture, opened.cycleId(), ReproAction.ESTRUS, requestId).build()
                        );
                        ok.incrementAndGet();
                    } catch (BizException e) {
                        Assertions.assertEquals(409, e.getCode(), "并发失败必须是 409：" + e.getMessage());
                        conflict.incrementAndGet();
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(jobs)) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Assertions.assertEquals(1, ok.get(), "只应有一个线程推进成功");
        Assertions.assertEquals(threads - 1, conflict.get(), "其余线程必须得到 409 而非静默覆盖");
        Assertions.assertEquals(
            1,
            (int) jdbc.queryForObject(
                "select count(*) from repro_events where cycle_id = ? and event_type = 'ESTRUS_DONE'",
                Integer.class, opened.cycleId()
            )
        );
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "MATING");
    }

    @Test
    void illegalTransitionIsRejectedWithConflict() {
        Fixture fixture = fixture("repro_illegal");
        ReproResult opened = openAtEstrus(fixture, "illegal_open");

        // 待催情直接接产：跳过了配种、摸胎、备产。
        BizException error = Assertions.assertThrows(BizException.class, () -> stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.DELIVERY, requestId("illegal_delivery"))
                .outcome(DeliveryOutcome.BORN.name()).totalKits(8).liveKits(8).keptKits(8).build()
        ));

        Assertions.assertEquals(409, error.getCode());
        Assertions.assertEquals(
            ReproStage.AWAIT_ESTRUS.name(),
            jdbc.queryForObject("select stage from breeding_cycles where id = ?", String.class, opened.cycleId())
        );
        Assertions.assertEquals(
            0,
            (int) jdbc.queryForObject(
                "select count(*) from repro_events where cycle_id = ? and event_type = 'DELIVERY_DONE'",
                Integer.class, opened.cycleId()
            ),
            "被拒绝的操作不得留下事件"
        );
    }

    @Test
    void doeCannotOccupyTwoPipelineCyclesAtOnce() {
        Fixture fixture = fixture("repro_pipeline");
        openAtEstrus(fixture, "pipeline_first");

        BizException error = Assertions.assertThrows(
            BizException.class, () -> openAtEstrus(fixture, "pipeline_second")
        );

        Assertions.assertEquals(409, error.getCode());
        Assertions.assertTrue(error.getMessage().contains("进行中"), error.getMessage());
    }

    @Test
    void bloodMatingRunsLactationAndNextPregnancyInParallel() {
        Fixture fixture = fixture("repro_blood");
        ReproResult opened = openAtEstrus(fixture, "blood_open");
        advanceToDelivery(fixture, opened.cycleId(), "blood");

        // 哺乳段不占管线，因此可以在带崽期间重新开启一轮配种周期。
        ReproResult second = stateMachine.openCycleAt(new OpenCycleCommand(
            fixture.houseId, fixture.userId, "tester", fixture.doeId, fixture.batchId,
            ReproStage.AWAIT_MATING, new Date(), new Date(), null, null, null,
            null, null, null, null, null, null, requestId("blood_second")
        ));
        Assertions.assertEquals(ReproStage.AWAIT_MATING, second.stage());

        Assertions.assertEquals(
            2,
            (int) jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ? and lifecycle = 'OPEN'",
                Integer.class, fixture.houseId, fixture.doeId
            ),
            "血配期间应有哺乳周期与管线周期各一条"
        );

        Date ignoredOverride = new Date(System.currentTimeMillis() + 4L * 24 * 3600 * 1000);
        BizException ignored = Assertions.assertThrows(BizException.class, () -> apply(
            fixture, opened.cycleId(), ReproAction.WEANING, "blood_weaning_override",
            b -> b.weanedCount(7).nextRemindAt(ignoredOverride)
        ));
        Assertions.assertEquals(400, ignored.getCode());
        Assertions.assertTrue(ignored.getMessage().contains("不会生成后续待办"), ignored.getMessage());
        Assertions.assertEquals(
            CycleLifecycle.OPEN.name(),
            jdbc.queryForObject("select lifecycle from breeding_cycles where id = ?", String.class, opened.cycleId()),
            "被拒绝的自定义日期不得顺带完成分笼"
        );

        ReproResult weaning = apply(fixture, opened.cycleId(), ReproAction.WEANING, "blood_weaning",
            b -> b.weanedCount(7));

        // 关键：已有管线周期在跑时不再自动接续，否则同一母兔会出现两条管线周期，
        // V27 的 uk_bc_pipeline 会直接拒绝写入。
        Assertions.assertEquals(opened.cycleId(), weaning.cycleId());
        Assertions.assertNull(weaning.followUpCycleId(), "已在管线上时不应再自动开新周期");
        Assertions.assertEquals(second.cycleId(), weaning.currentCycleId(), "应返回既有血配管线周期");
        Assertions.assertEquals(ReproStage.AWAIT_MATING, weaning.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), weaning.lifecycle());
        Assertions.assertEquals(
            1,
            (int) jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ? and lifecycle = 'OPEN'",
                Integer.class, fixture.houseId, fixture.doeId
            )
        );
        assertProjection(fixture, "AWAIT_MATING", second.cycleId());
    }

    @Test
    void emptyPalpationClosesCycleAndReopensForEstrusImmediately() {
        Fixture fixture = fixture("repro_empty");
        ReproResult opened = openAtEstrus(fixture, "empty_open");
        apply(fixture, opened.cycleId(), ReproAction.ESTRUS, "empty_estrus", b -> b);
        apply(fixture, opened.cycleId(), ReproAction.MATING, "empty_mating",
            b -> b.maleRabbitId(fixture.sireId).matingMethod(MatingMethod.NATURAL));

        String emptyRequestId = requestId("empty_palpation");
        ReproCommand emptyCommand = command(
            fixture, opened.cycleId(), ReproAction.PALPATION, emptyRequestId
        ).outcome(PalpationResult.EMPTY.name()).palpationResult(PalpationResult.EMPTY).build();
        ReproResult empty = stateMachine.apply(emptyCommand);

        Assertions.assertEquals(opened.cycleId(), empty.cycleId());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), empty.lifecycle());
        Assertions.assertNotNull(empty.followUpCycleId(), "空怀应立即接续下一轮");
        Assertions.assertEquals(empty.followUpCycleId(), empty.currentCycleId());
        Assertions.assertEquals(ReproStage.AWAIT_ESTRUS, empty.stage());

        ReproResult replayed = stateMachine.apply(emptyCommand);
        Assertions.assertTrue(replayed.replayed());
        Assertions.assertEquals(opened.cycleId(), replayed.cycleId());
        Assertions.assertEquals(empty.currentCycleId(), replayed.currentCycleId());
        Assertions.assertEquals(ReproStage.AWAIT_ESTRUS, replayed.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), replayed.lifecycle());
        // 空怀是立即重新催情，不等子宫复旧期。
        Assertions.assertEquals(
            0,
            daysBetween(new Date(), empty.nextDueTime()),
            "空怀后的催情任务应当天到期"
        );
        // 同一母兔在同一批次重开周期：唯一性只在 OPEN 周期间成立。
        Assertions.assertEquals(
            2,
            (int) jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ?",
                Integer.class, fixture.houseId, fixture.doeId
            )
        );
    }

    @Test
    void unsurePalpationKeepsStageAndRequiresRecheckDate() {
        Fixture fixture = fixture("repro_unsure");
        ReproResult opened = openAtEstrus(fixture, "unsure_open");
        apply(fixture, opened.cycleId(), ReproAction.ESTRUS, "unsure_estrus", b -> b);
        apply(fixture, opened.cycleId(), ReproAction.MATING, "unsure_mating",
            b -> b.maleRabbitId(fixture.sireId).matingMethod(MatingMethod.NATURAL));

        // 不给复查日必须被拒：否则这只兔子会停在待摸胎且再无提醒，
        // 正是旧实现里「兔子消失在流程中」的成因。
        BizException missing = Assertions.assertThrows(BizException.class, () -> stateMachine.apply(
            command(fixture, opened.cycleId(), ReproAction.PALPATION, requestId("unsure_missing"))
                .outcome(PalpationResult.UNSURE.name()).palpationResult(PalpationResult.UNSURE).build()
        ));
        Assertions.assertEquals(400, missing.getCode());

        Date recheck = new Date(System.currentTimeMillis() + 5L * 24 * 3600 * 1000);
        ReproResult unsure = apply(fixture, opened.cycleId(), ReproAction.PALPATION, "unsure_ok",
            b -> b.outcome(PalpationResult.UNSURE.name())
                .palpationResult(PalpationResult.UNSURE)
                .nextRemindAt(recheck));

        Assertions.assertEquals(ReproStage.AWAIT_PALPATION, unsure.stage(), "不确定应停留在待摸胎");
        Assertions.assertEquals("OPEN", unsure.lifecycle());
        assertSinglePendingTask(fixture.houseId, opened.cycleId(), "PALPATION");
        Assertions.assertEquals(5, daysBetween(new Date(), unsure.nextDueTime()));
    }

    @Test
    void retireCancelsEveryPendingTaskIncludingLactation() {
        Fixture fixture = fixture("repro_retire");
        ReproResult opened = openAtEstrus(fixture, "retire_open");
        advanceToDelivery(fixture, opened.cycleId(), "retire");

        ReproResult retired = apply(fixture, opened.cycleId(), ReproAction.RETIRE, "retire_do",
            b -> b.reason("淘汰"));

        Assertions.assertNull(retired.currentCycleId());
        Assertions.assertEquals(ReproStage.RETIRED, retired.stage());
        Assertions.assertEquals(CycleLifecycle.CLOSED.name(), retired.lifecycle());
        Assertions.assertEquals(
            0,
            (int) jdbc.queryForObject(
                "select count(*) from work_tasks where house_id = ? and rabbit_id = ? and status = 'PENDING'",
                Integer.class, fixture.houseId, fixture.doeId
            ),
            "离场后不应残留任何待办"
        );
        assertProjection(fixture, "RETIRED", null);
    }

    @Test
    void backdatedEntryPullsOverdueTaskToToday() {
        Fixture fixture = fixture("repro_backdate");
        Date twentyDaysAgo = new Date(System.currentTimeMillis() - 20L * 24 * 3600 * 1000);

        // 补录 20 天前的配种：摸胎日（+12 天）早已过期。
        ReproResult opened = stateMachine.openCycleAt(new OpenCycleCommand(
            fixture.houseId, fixture.userId, "tester", fixture.doeId, fixture.batchId,
            ReproStage.AWAIT_PALPATION, twentyDaysAgo, twentyDaysAgo, twentyDaysAgo, null, null,
            null, null, fixture.sireId, MatingMethod.NATURAL, null, null, requestId("backdate_open")
        ));

        Assertions.assertEquals(
            0,
            daysBetween(new Date(), opened.nextDueTime()),
            "逾期任务的到期日必须拉平到当天，否则永远不会出现在今日待办里"
        );
    }

    // ------------------------------------------------------------------ 夹具

    /** batchId 留空：散养母兔同样要能跑完整流程（2026-08-16 业务裁定 batch_id 可空）。 */
    private record Fixture(long userId, long houseId, long doeId, long sireId, Long batchId) {
    }

    private Fixture fixture(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long sireId = createRabbit(owner, houseId, cages.get(1), "0", "1", prefix + "_sire");
        return new Fixture(owner.userId, houseId, doeId, sireId, null);
    }

    private ReproResult openAtEstrus(Fixture fixture, String prefix) {
        return stateMachine.openCycleAt(new OpenCycleCommand(
            fixture.houseId, fixture.userId, "tester", fixture.doeId, fixture.batchId,
            ReproStage.AWAIT_ESTRUS, new Date(), new Date(), null, null, null,
            null, null, null, null, null, null, requestId(prefix)
        ));
    }

    private ReproCommand.Builder command(Fixture fixture, Long cycleId, ReproAction action, String requestId) {
        return ReproCommand.builder()
            .houseId(fixture.houseId)
            .userId(fixture.userId)
            .operatorName("tester")
            .cycleId(cycleId)
            .motherRabbitId(fixture.doeId)
            .action(action)
            .occurredAt(new Date())
            .requestId(requestId);
    }

    private ReproResult apply(
        Fixture fixture,
        Long cycleId,
        ReproAction action,
        String requestPrefix,
        java.util.function.UnaryOperator<ReproCommand.Builder> customizer
    ) {
        return stateMachine.apply(
            customizer.apply(command(fixture, cycleId, action, requestId(requestPrefix))).build()
        );
    }

    private void advanceToDelivery(Fixture fixture, Long cycleId, String prefix) {
        apply(fixture, cycleId, ReproAction.ESTRUS, prefix + "_estrus", b -> b);
        apply(fixture, cycleId, ReproAction.MATING, prefix + "_mating",
            b -> b.maleRabbitId(fixture.sireId).matingMethod(MatingMethod.NATURAL));
        apply(fixture, cycleId, ReproAction.PALPATION, prefix + "_palpation",
            b -> b.outcome(PalpationResult.PREGNANT.name()).palpationResult(PalpationResult.PREGNANT));
        apply(fixture, cycleId, ReproAction.PREPARTUM, prefix + "_prepartum", b -> b);
        apply(fixture, cycleId, ReproAction.DELIVERY, prefix + "_delivery",
            b -> b.outcome(DeliveryOutcome.BORN.name()).totalKits(8).liveKits(7).keptKits(7));
    }

    private void assertSinglePendingTask(long houseId, Long cycleId, String taskType) {
        Assertions.assertEquals(
            1,
            (int) jdbc.queryForObject(
                "select count(*) from work_tasks where house_id = ? and cycle_id = ? and status = 'PENDING'",
                Integer.class, houseId, cycleId
            ),
            "每个进行中周期恰有一条待办"
        );
        Assertions.assertEquals(
            taskType,
            jdbc.queryForObject(
                "select task_type from work_tasks where house_id = ? and cycle_id = ? and status = 'PENDING'",
                String.class, houseId, cycleId
            )
        );
    }

    private void assertProjection(Fixture fixture, String stage, Long cycleId) {
        Assertions.assertEquals(
            stage,
            jdbc.queryForObject(
                "select current_stage from rabbits where id = ?", String.class, fixture.doeId
            ),
            "rabbits 投影必须与周期同事务更新"
        );
        Assertions.assertEquals(
            cycleId,
            jdbc.queryForObject("select current_cycle_id from rabbits where id = ?", Long.class, fixture.doeId)
        );
    }

    private Date dateOf(Long cycleId, String column) {
        return jdbc.queryForObject(
            "select " + column + " from breeding_cycles where id = ?", Date.class, cycleId
        );
    }

    private static int daysBetween(Date from, Date to) {
        return com.rabbit.app.util.DateUtil.daysBetween(from, to);
    }
}
