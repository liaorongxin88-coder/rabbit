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

        Assertions.assertEquals(
            30,
            daysBetween(
                dateOf(opened.cycleId(), "mating_date"),
                dateOf(opened.cycleId(), "expected_birth_date")
            ),
            "预产期参考值应固定为配种日后 30 天"
        );

        ReproResult palpation = apply(fixture, opened.cycleId(), ReproAction.PALPATION, "full_palpation",
            b -> b.outcome(PalpationResult.PREGNANT.name()).palpationResult(PalpationResult.PREGNANT));
        Assertions.assertEquals(ReproStage.AWAIT_PREPARTUM, palpation.stage());

        ReproResult prepartum = apply(fixture, opened.cycleId(), ReproAction.PREPARTUM, "full_prepartum", b -> b);
        Assertions.assertEquals(ReproStage.AWAIT_DELIVERY, prepartum.stage());

        jdbc.update(
            "update global_setting set postpartum_days = 6 where house_id = ?",
            fixture.houseId
        );
        Date deliveryAt = new Date();
        ReproResult delivery = apply(fixture, opened.cycleId(), ReproAction.DELIVERY, "full_delivery",
            b -> b.occurredAt(deliveryAt)
                .outcome(DeliveryOutcome.BORN.name()).totalKits(9).liveKits(8).keptKits(8));
        Assertions.assertEquals(ReproStage.READY, delivery.stage());
        Assertions.assertNotNull(delivery.currentCycleId(), "接产后应立即创建休养周期");
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), delivery.lifecycle());
        Assertions.assertEquals(
            ReproStage.AWAIT_WEANING.name(),
            jdbc.queryForObject(
                "select stage from breeding_cycles where id = ?",
                String.class,
                opened.cycleId()
            ),
            "旧窝周期仍须保持待分笼"
        );
        assertProjection(fixture, "READY", delivery.currentCycleId());
        assertSinglePendingTask(fixture.houseId, delivery.currentCycleId(), "RECOVERY");
        Date recoveryDueTime = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and cycle_id = ?"
                + " and task_type = 'RECOVERY' and status = 'PENDING'",
            Date.class,
            fixture.houseId,
            delivery.currentCycleId()
        );
        Assertions.assertEquals(
            6,
            daysBetween(deliveryAt, recoveryDueTime),
            "休养到期日应从接产日期按兔舍配置计算"
        );
        Assertions.assertNotNull(delivery.litterId(), "接产必须建窝");
        // 分笼任务挂在窝上而不是周期上——血配时母兔要能同时持有两条互不干扰的待办。
        Assertions.assertEquals(
            "LITTER",
            jdbc.queryForObject(
                "select subject_type from work_tasks where house_id = ? and task_type = 'WEANING' and status = 'PENDING'",
                String.class, fixture.houseId
            )
        );

        Date weaningAt = new Date();
        ReproResult weaning = apply(fixture, opened.cycleId(), ReproAction.WEANING, "full_weaning",
            b -> b.occurredAt(weaningAt).weanedCount(8).avgWeaningWeight(0.6));
        Assertions.assertEquals(opened.cycleId(), weaning.cycleId(), "动作周期仍应是刚完成的断奶周期");
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), weaning.lifecycle());
        Assertions.assertEquals(ReproStage.READY, weaning.stage());
        Assertions.assertEquals(
            CycleResult.WEANED.name(),
            jdbc.queryForObject(
                "select result from breeding_cycles where id = ?", String.class, opened.cycleId()
            )
        );
        // 接产时已开始休养，断奶只结束原哺乳周期，不得重复创建休养周期。
        Assertions.assertNull(weaning.followUpCycleId());
        Assertions.assertEquals(delivery.currentCycleId(), weaning.currentCycleId());
        assertProjection(fixture, "READY", delivery.currentCycleId());
        assertSinglePendingTask(fixture.houseId, delivery.currentCycleId(), "RECOVERY");
        Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and is_active = true",
            Integer.class,
            fixture.batchId,
            fixture.doeId
        ));

        ReproResult recovered = apply(
            fixture,
            delivery.currentCycleId(),
            ReproAction.START_CYCLE,
            "full_recovery_done",
            b -> b
        );
        Assertions.assertEquals(ReproStage.AWAIT_ESTRUS, recovered.stage());
        assertSinglePendingTask(fixture.houseId, recovered.cycleId(), "ESTRUS");
        Assertions.assertNull(jdbc.queryForObject(
            "select batch_id from breeding_cycles where id = ?",
            Long.class,
            recovered.cycleId()
        ));
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
    void everyOperationUsesTheCurrentHouseCycleForItsNextReminder() {
        Fixture fixture = fixture("repro_time_sync");
        api.putOk("/api/house-settings", fixture.owner.token, fixture.houseId, obj(
            "aphrodisiacDays", 4,
            "palpationDays", 5,
            "prepartumDays", 6,
            "weaningDays", 7,
            "postpartumDays", 8,
            "saleDays", 33,
            "replacementDays", 90,
            "remark", "五段提醒验收配置",
            "requestId", requestId("time_sync_house_setting")
        ));
        ReproResult opened = openAtEstrus(fixture, "time_sync_open");

        Date estrusAt = new Date();
        ReproResult estrus = apply(
            fixture, opened.cycleId(), ReproAction.ESTRUS, "time_sync_estrus",
            b -> b.occurredAt(estrusAt)
        );
        assertTransitionTiming(opened.cycleId(), estrusAt, estrus, 4);

        Date matingAt = new Date();
        ReproResult mating = apply(
            fixture, opened.cycleId(), ReproAction.MATING, "time_sync_mating",
            b -> b.occurredAt(matingAt)
                .maleRabbitId(fixture.sireId)
                .matingMethod(MatingMethod.NATURAL)
        );
        assertTransitionTiming(opened.cycleId(), matingAt, mating, 5);

        Date palpationAt = new Date();
        ReproResult palpation = apply(
            fixture, opened.cycleId(), ReproAction.PALPATION, "time_sync_palpation",
            b -> b.occurredAt(palpationAt)
                .outcome(PalpationResult.PREGNANT.name())
                .palpationResult(PalpationResult.PREGNANT)
        );
        assertTransitionTiming(opened.cycleId(), palpationAt, palpation, 6);

        Date prepartumAt = new Date();
        ReproResult prepartum = apply(
            fixture, opened.cycleId(), ReproAction.PREPARTUM, "time_sync_prepartum",
            b -> b.occurredAt(prepartumAt)
        );
        assertTransitionTiming(opened.cycleId(), prepartumAt, prepartum, 0);

        Date deliveryAt = new Date();
        ReproResult delivery = apply(
            fixture, opened.cycleId(), ReproAction.DELIVERY, "time_sync_delivery",
            b -> b.occurredAt(deliveryAt)
                .outcome(DeliveryOutcome.BORN.name())
                .totalKits(8)
                .liveKits(7)
                .keptKits(7)
        );
        assertTransitionTiming(opened.cycleId(), deliveryAt, delivery, 7);
        Date recoveryDue = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and cycle_id = ?"
                + " and task_type = 'RECOVERY' and status = 'PENDING'",
            Date.class,
            fixture.houseId,
            delivery.currentCycleId()
        );
        Assertions.assertEquals(8, daysBetween(deliveryAt, recoveryDue));

        Date weaningAt = new Date();
        ReproResult weaning = apply(
            fixture, opened.cycleId(), ReproAction.WEANING, "time_sync_weaning",
            b -> b.occurredAt(weaningAt).weanedCount(7)
        );
        Assertions.assertNull(weaning.followUpCycleId());
        Assertions.assertEquals(delivery.currentCycleId(), weaning.currentCycleId());
        assertSinglePendingTask(fixture.houseId, delivery.currentCycleId(), "RECOVERY");
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
    void matingRejectsInactiveBatchAndRestoresRemovedPlannedMembership() {
        Fixture inactiveBatch = fixture("repro_inactive_batch");
        ReproResult inactiveCycle = openAtEstrus(inactiveBatch, "inactive_open");
        apply(inactiveBatch, inactiveCycle.cycleId(), ReproAction.ESTRUS, "inactive_estrus", b -> b);
        jdbc.update(
            "update batches set status = '已完成', end_date = now() where id = ?",
            inactiveBatch.batchId
        );

        BizException inactiveError = Assertions.assertThrows(BizException.class, () ->
            stateMachine.apply(command(
                inactiveBatch,
                inactiveCycle.cycleId(),
                ReproAction.MATING,
                requestId("inactive_action")
            ).batchId(inactiveBatch.batchId)
                .maleRabbitId(inactiveBatch.sireId)
                .matingMethod(MatingMethod.NATURAL)
                .build())
        );
        Assertions.assertEquals(409, inactiveError.getCode());
        Assertions.assertTrue(inactiveError.getMessage().contains("不在进行中"));

        Fixture removedPlan = fixture("repro_removed_plan");
        ReproResult removedPlanCycle = openAtEstrus(removedPlan, "removed_plan_open");
        apply(removedPlan, removedPlanCycle.cycleId(), ReproAction.ESTRUS,
            "removed_plan_estrus", b -> b);
        jdbc.update(
            "update batch_rabbits set is_active = false, exit_date = now()"
                + " where batch_id = ? and rabbit_id = ? and is_active = true",
            removedPlan.batchId,
            removedPlan.doeId
        );

        ReproResult mating = apply(
            removedPlan,
            removedPlanCycle.cycleId(),
            ReproAction.MATING,
            "removed_plan_mating",
            b -> b.batchId(removedPlan.batchId)
                .maleRabbitId(removedPlan.sireId)
                .matingMethod(MatingMethod.NATURAL)
        );
        Assertions.assertEquals(ReproStage.AWAIT_PALPATION, mating.stage());
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and batch_role = 'breeding' and is_active = true",
            Integer.class,
            removedPlan.batchId,
            removedPlan.doeId
        ));
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
    void deliveryRunsLactationAndRecoveryInParallel() {
        Fixture fixture = fixture("repro_recovery_parallel");
        ReproResult opened = openAtEstrus(fixture, "recovery_parallel_open");
        advanceToDelivery(fixture, opened.cycleId(), "recovery_parallel");

        Long recoveryCycleId = jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN' and stage = 'READY'",
            Long.class,
            fixture.houseId,
            fixture.doeId
        );
        Assertions.assertNotNull(recoveryCycleId);
        Assertions.assertEquals(
            2,
            (int) jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ? and lifecycle = 'OPEN'",
                Integer.class,
                fixture.houseId,
                fixture.doeId
            ),
            "接产后应并行保留哺乳和休养两个周期"
        );
        assertSinglePendingTask(fixture.houseId, recoveryCycleId, "RECOVERY");

        long bloodBatchId = emptyBatch(fixture, "recovery_parallel_batch");
        BizException blockedMating = Assertions.assertThrows(
            BizException.class,
            () -> stateMachine.openCycleAt(new OpenCycleCommand(
                fixture.houseId, fixture.userId, "tester", fixture.doeId, bloodBatchId,
                ReproStage.AWAIT_MATING, new Date(), new Date(), null, null, null,
                null, null, null, null, null, null, requestId("recovery_parallel_mating")
            ))
        );
        Assertions.assertEquals(409, blockedMating.getCode());
        Assertions.assertTrue(blockedMating.getMessage().contains("进行中"), blockedMating.getMessage());

        Date ignoredOverride = new Date(System.currentTimeMillis() + 4L * 24 * 3600 * 1000);
        BizException ignored = Assertions.assertThrows(BizException.class, () -> apply(
            fixture, opened.cycleId(), ReproAction.WEANING, "recovery_parallel_weaning_override",
            b -> b.weanedCount(7).nextRemindAt(ignoredOverride)
        ));
        Assertions.assertEquals(400, ignored.getCode());
        Assertions.assertTrue(ignored.getMessage().contains("不会生成后续待办"), ignored.getMessage());
        Assertions.assertEquals(
            CycleLifecycle.OPEN.name(),
            jdbc.queryForObject("select lifecycle from breeding_cycles where id = ?", String.class, opened.cycleId()),
            "被拒绝的自定义日期不得顺带完成分笼"
        );

        ReproResult weaning = apply(
            fixture,
            opened.cycleId(),
            ReproAction.WEANING,
            "recovery_parallel_weaning",
            b -> b.weanedCount(7)
        );
        Assertions.assertEquals(opened.cycleId(), weaning.cycleId());
        Assertions.assertNull(weaning.followUpCycleId(), "接产时已有休养周期，不得在断奶时重复创建");
        Assertions.assertEquals(recoveryCycleId, weaning.currentCycleId());
        Assertions.assertEquals(ReproStage.READY, weaning.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), weaning.lifecycle());
        Assertions.assertEquals(
            1,
            (int) jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ? and lifecycle = 'OPEN'",
                Integer.class,
                fixture.houseId,
                fixture.doeId
            )
        );
        assertProjection(fixture, "READY", recoveryCycleId);
    }

    @Test
    void emptyPalpationClosesCycleReleasesBatchAndStartsRecovery() {
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
        Assertions.assertNotNull(empty.followUpCycleId(), "空怀应开启休养周期");
        Assertions.assertEquals(empty.followUpCycleId(), empty.currentCycleId());
        Assertions.assertEquals(ReproStage.READY, empty.stage());
        assertSinglePendingTask(fixture.houseId, empty.followUpCycleId(), "RECOVERY");
        Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and is_active = true",
            Integer.class,
            fixture.batchId,
            fixture.doeId
        ));

        ReproResult replayed = stateMachine.apply(emptyCommand);
        Assertions.assertTrue(replayed.replayed());
        Assertions.assertEquals(opened.cycleId(), replayed.cycleId());
        Assertions.assertEquals(empty.currentCycleId(), replayed.currentCycleId());
        Assertions.assertEquals(ReproStage.READY, replayed.stage());
        Assertions.assertEquals(CycleLifecycle.OPEN.name(), replayed.lifecycle());
        Integer recoveryDays = jdbc.queryForObject(
            "select postpartum_days from global_setting where house_id = ?",
            Integer.class,
            fixture.houseId
        );
        Assertions.assertEquals(
            recoveryDays.intValue(),
            daysBetween(new Date(), empty.nextDueTime()),
            "空怀后应按兔舍配置完成休养，再进入待催情"
        );
        // 原周期关闭并建立无批次休养周期。
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

    /** 空批次避免建批自动入轨，由各用例显式选择起始阶段。 */
    private record Fixture(
        UserSession owner, long userId, long houseId, long doeId, long sireId, Long batchId
    ) {
    }

    @Test
    void directEntryCreatesMatingAndDeliveryOperationEvents() {
        Fixture matingFixture = fixture("repro_entry_mating");
        Date matingAt = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);
        ReproResult palpation = stateMachine.openCycleAt(new OpenCycleCommand(
            matingFixture.houseId,
            matingFixture.userId,
            "tester",
            matingFixture.doeId,
            matingFixture.batchId,
            ReproStage.AWAIT_PALPATION,
            matingAt,
            matingAt,
            null,
            null,
            null,
            null,
            null,
            null,
            matingFixture.sireId,
            MatingMethod.NATURAL,
            null,
            null,
            requestId("entry_mating")
        ));
        long storedMatingSecond = dateOf(
            palpation.cycleId(), "mating_date"
        ).getTime() / 1000;
        Assertions.assertTrue(
            Math.abs(matingAt.getTime() / 1000 - storedMatingSecond) <= 1,
            "配种操作时间应使用入轨日期"
        );
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from repro_events where cycle_id = ?"
                + " and event_type = 'MATING_DONE' and batch_id = ?"
                + " and payload->>'$.maleRabbitId' = ?"
                + " and payload->>'$.matingMethod' = 'NATURAL'",
            Integer.class,
            palpation.cycleId(),
            matingFixture.batchId,
            String.valueOf(matingFixture.sireId)
        ));

        Fixture deliveryFixture = fixture("repro_entry_delivery");
        Date deliveryAt = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);
        ReproResult weaning = stateMachine.openCycleAt(new OpenCycleCommand(
            deliveryFixture.houseId,
            deliveryFixture.userId,
            "tester",
            deliveryFixture.doeId,
            deliveryFixture.batchId,
            ReproStage.AWAIT_WEANING,
            deliveryAt,
            deliveryAt,
            null,
            null,
            null,
            8,
            7,
            6,
            null,
            null,
            null,
            null,
            requestId("entry_delivery")
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from repro_events where cycle_id = ?"
                + " and event_type = 'DELIVERY_DONE' and batch_id = ?"
                + " and payload->>'$.totalKits' = '8'"
                + " and payload->>'$.liveKits' = '7'"
                + " and payload->>'$.keptKits' = '6'",
            Integer.class,
            weaning.cycleId(),
            deliveryFixture.batchId
        ));
        Assertions.assertEquals(6, jdbc.queryForObject(
            "select kept_kits from litters where cycle_id = ?",
            Integer.class,
            weaning.cycleId()
        ));
    }

    private Fixture fixture(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long sireId = createRabbit(owner, houseId, cages.get(1), "0", "1", prefix + "_sire");
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "RSM-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "femaleRabbitIds", List.of(),
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();
        return new Fixture(owner, owner.userId, houseId, doeId, sireId, batchId);
    }

    /** 再拉一个空批次。血配的第二条并行周期需要它来安置。 */
    private long emptyBatch(Fixture fixture, String prefix) {
        return api.postOk("/api/batches", fixture.owner.token, fixture.houseId, obj(
            "batchCode", "RSM-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "femaleRabbitIds", List.of(),
            "requestId", requestId(prefix)
        )).get("id").asLong();
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

    private void assertTransitionTiming(
        Long cycleId,
        Date occurredAt,
        ReproResult result,
        int expectedDueDays
    ) {
        Date stageEnteredAt = dateOf(cycleId, "stage_entered_at");
        Assertions.assertTrue(
            Math.abs(occurredAt.getTime() - stageEnteredAt.getTime()) < 1000,
            "阶段时间必须同步为操作时间"
        );
        Assertions.assertEquals(expectedDueDays, daysBetween(occurredAt, result.nextDueTime()));
        Date taskDue = jdbc.queryForObject(
            "select due_time from work_tasks where id = ?",
            Date.class, result.nextTaskId()
        );
        Assertions.assertTrue(
            Math.abs(result.nextDueTime().getTime() - taskDue.getTime()) < 1000,
            "返回的提醒时间必须与待办落库时间一致"
        );
    }

    private static int daysBetween(Date from, Date to) {
        return com.rabbit.app.util.DateUtil.daysBetween(from, to);
    }
}
