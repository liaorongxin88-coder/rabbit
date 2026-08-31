package com.rabbit.app.modules.repro.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.dto.StageActionsView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 转换矩阵全覆盖：合法组合逐条对齐设计 §3.2 的表，其余组合一律拒绝。
 *
 * <p>这张测试是状态机的验收线。旧实现把同一条规则在六个方法里各写一遍 if，
 * 没有任何一处能回答「待备产能不能直接接产」；这里把答案钉死成数据，
 * 任何人改转换表都必须同步改这份期望清单。
 */
class TransitionTableTest {

    /** 设计 §3.2 全部合法转换的期望值，格式：from|action|outcome。 */
    private static final List<String> LEGAL_KEYS = List.of(
        "READY|START_CYCLE|",
        "READY|POSTPONE|",
        "AWAIT_ESTRUS|ESTRUS|",
        "AWAIT_MATING|MATING|",
        "AWAIT_PALPATION|PALPATION|PREGNANT",
        "AWAIT_PALPATION|PALPATION|EMPTY",
        "AWAIT_PALPATION|PALPATION|UNSURE",
        "AWAIT_PREPARTUM|PREPARTUM|",
        "AWAIT_DELIVERY|DELIVERY|BORN",
        "AWAIT_DELIVERY|DELIVERY|FAILED",
        "AWAIT_WEANING|WEANING|",
        "AWAIT_PALPATION|ABORTION|",
        "AWAIT_PREPARTUM|ABORTION|",
        "AWAIT_DELIVERY|ABORTION|",
        "AWAIT_ESTRUS|POSTPONE|",
        "AWAIT_MATING|POSTPONE|",
        "AWAIT_PALPATION|POSTPONE|",
        "AWAIT_PREPARTUM|POSTPONE|",
        "AWAIT_DELIVERY|POSTPONE|",
        "AWAIT_WEANING|POSTPONE|",
        "READY|RETIRE|",
        "AWAIT_ESTRUS|RETIRE|",
        "AWAIT_MATING|RETIRE|",
        "AWAIT_PALPATION|RETIRE|",
        "AWAIT_PREPARTUM|RETIRE|",
        "AWAIT_DELIVERY|RETIRE|",
        "AWAIT_WEANING|RETIRE|"
    );

    @Test
    void tableContainsExactlyTheDesignedTransitions() {
        Set<String> actual = new LinkedHashSet<>();
        for (Transition transition : TransitionTable.all()) {
            actual.add(
                transition.fromStage().name() + '|' + transition.action().name() + '|'
                    + (transition.outcome() == null ? "" : transition.outcome())
            );
        }
        assertEquals(new LinkedHashSet<>(LEGAL_KEYS), actual, "转换表与设计 §3.2 不一致");
    }

    @Test
    void everyCombinationOutsideTheTableIsRejected() {
        Set<String> legal = Set.copyOf(LEGAL_KEYS);
        List<String> wronglyAccepted = new ArrayList<>();

        for (ReproStage from : ReproStage.values()) {
            for (ReproAction action : ReproAction.values()) {
                for (String outcome : outcomesFor(action)) {
                    String key = from.name() + '|' + action.name() + '|' + (outcome == null ? "" : outcome);
                    if (legal.contains(key)) {
                        continue;
                    }
                    if (TransitionTable.find(from, action, outcome) != null) {
                        wronglyAccepted.add(key);
                    }
                }
            }
        }

        assertTrue(wronglyAccepted.isEmpty(), () -> "以下非法组合被接受了: " + wronglyAccepted);
    }

    @Test
    void illegalTransitionFailsWithConflictAndReadableMessage() {
        // 待备产不能直接接产：必须先执行备产。这类误操作要报 409「状态已变化」语义，
        // 而不是 400，客户端据此提示刷新而不是提示参数写错了。
        BizException error = assertThrows(
            BizException.class,
            () -> TransitionTable.require(ReproStage.AWAIT_PREPARTUM, ReproAction.DELIVERY, "BORN")
        );

        assertAll(
            () -> assertEquals(409, error.getCode()),
            () -> assertTrue(error.getMessage().contains("待备产"), error.getMessage()),
            () -> assertTrue(error.getMessage().contains("接产"), error.getMessage())
        );
    }

    @Test
    void pipelineStagesMatchTheGeneratedGuardColumn() {
        // 必须与 V26 breeding_cycles.pipeline_guard 生成列里的 stage 列表逐字一致，
        // 否则应用层放行的并发写会在数据库层被唯一键拒绝（或反过来漏防）。
        assertAll(
            () -> assertTrue(ReproStage.READY.isPipeline()),
            () -> assertTrue(ReproStage.AWAIT_ESTRUS.isPipeline()),
            () -> assertTrue(ReproStage.AWAIT_MATING.isPipeline()),
            () -> assertTrue(ReproStage.AWAIT_PALPATION.isPipeline()),
            () -> assertTrue(ReproStage.AWAIT_PREPARTUM.isPipeline()),
            () -> assertTrue(ReproStage.AWAIT_DELIVERY.isPipeline()),
            // 哺乳段不占管线：这正是血配（重叠哺乳配种）得以成立的前提。
            () -> assertTrue(!ReproStage.AWAIT_WEANING.isPipeline()),
            () -> assertTrue(!ReproStage.SUSPENDED.isPipeline()),
            () -> assertTrue(!ReproStage.RETIRED.isPipeline())
        );
    }

    @Test
    void closingTransitionsCarryResultAndFollowUp() {
        assertAll(
            () -> assertClose(
                ReproStage.AWAIT_PALPATION, ReproAction.PALPATION, "EMPTY",
                CycleResult.EMPTY, ReproStage.READY, DueAnchor.POSTPARTUM_RECOVERY
            ),
            () -> assertClose(
                ReproStage.AWAIT_DELIVERY, ReproAction.DELIVERY, "FAILED",
                CycleResult.FAILED, ReproStage.READY, DueAnchor.POSTPARTUM_RECOVERY
            ),
            () -> assertClose(
                ReproStage.AWAIT_WEANING, ReproAction.WEANING, null,
                CycleResult.WEANED, ReproStage.READY, DueAnchor.POSTPARTUM_RECOVERY
            ),
            () -> assertClose(
                ReproStage.AWAIT_PREPARTUM, ReproAction.ABORTION, null,
                CycleResult.ABORTED, ReproStage.READY, DueAnchor.POSTPARTUM_RECOVERY
            )
        );
    }

    @Test
    void recoveryCanBeCompletedOrPostponedManually() {
        Transition recovery = TransitionTable.require(
            ReproStage.READY, ReproAction.START_CYCLE, null
        );
        Transition postpone = TransitionTable.require(
            ReproStage.READY, ReproAction.POSTPONE, null
        );

        assertAll(
            () -> assertTrue(ReproAction.START_CYCLE.isPostponable()),
            () -> assertFalse(recovery.closesCycle()),
            () -> assertEquals(ReproStage.AWAIT_ESTRUS, recovery.toStage()),
            () -> assertEquals(DueAnchor.IMMEDIATE, recovery.dueAnchor()),
            () -> assertEquals(ReproEventType.RECOVERY_DONE, recovery.eventType()),
            () -> assertFalse(postpone.closesCycle()),
            () -> assertEquals(ReproStage.READY, postpone.toStage()),
            () -> assertEquals(DueAnchor.USER_SPECIFIED, postpone.dueAnchor()),
            () -> assertEquals(ReproEventType.POSTPONE, postpone.eventType())
        );
    }

    @Test
    void retireClosesWithoutFollowUpAndCancelsTasks() {
        Transition retire = TransitionTable.require(ReproStage.AWAIT_DELIVERY, ReproAction.RETIRE, null);

        assertAll(
            () -> assertTrue(retire.closesCycle()),
            () -> assertEquals(CycleResult.REMOVED, retire.result()),
            () -> assertNull(retire.followUpStage(), "离场不接续新周期"),
            () -> assertEquals(DueAnchor.NONE, retire.dueAnchor()),
            () -> assertEquals(ReproStage.RETIRED, retire.projectedMotherStage()),
            () -> assertTrue(retire.cancelsAllTasks())
        );
    }

    @Test
    void pregnancyFlowUsesTheMarkedBusinessTimeAnchors() {
        Transition palpation = TransitionTable.require(
            ReproStage.AWAIT_PALPATION, ReproAction.PALPATION, "PREGNANT"
        );
        Transition prepartum = TransitionTable.require(
            ReproStage.AWAIT_PREPARTUM, ReproAction.PREPARTUM, null
        );

        assertAll(
            () -> assertEquals(
                DueAnchor.PALPATION_TO_PREPARTUM, palpation.dueAnchor()
            ),
            () -> assertEquals(DueAnchor.SAME_DAY, prepartum.dueAnchor())
        );
    }

    @Test
    void postponeKeepsStageAndOnlyMovesTheReminder() {
        Transition postpone = TransitionTable.require(ReproStage.AWAIT_PALPATION, ReproAction.POSTPONE, null);

        assertAll(
            () -> assertTrue(!postpone.closesCycle()),
            () -> assertEquals(ReproStage.AWAIT_PALPATION, postpone.toStage(), "推迟不改阶段"),
            () -> assertEquals(DueAnchor.USER_SPECIFIED, postpone.dueAnchor()),
            () -> assertEquals(ReproEventType.POSTPONE, postpone.eventType())
        );
    }

    @Test
    void onlyNormalDeliveryCreatesALitter() {
        assertAll(
            () -> assertTrue(
                TransitionTable.require(ReproStage.AWAIT_DELIVERY, ReproAction.DELIVERY, "BORN").createsLitter()
            ),
            () -> assertTrue(
                !TransitionTable.require(ReproStage.AWAIT_DELIVERY, ReproAction.DELIVERY, "FAILED").createsLitter()
            ),
            () -> assertTrue(
                !TransitionTable.require(ReproStage.AWAIT_DELIVERY, ReproAction.ABORTION, null).createsLitter()
            )
        );
    }

    private static void assertClose(
        ReproStage from,
        ReproAction action,
        String outcome,
        CycleResult expectedResult,
        ReproStage expectedFollowUp,
        DueAnchor expectedAnchor
    ) {
        Transition transition = TransitionTable.require(from, action, outcome);
        assertNotNull(transition);
        assertTrue(transition.closesCycle(), from + "/" + action + " 应关闭周期");
        assertEquals(expectedResult, transition.result());
        assertEquals(expectedFollowUp, transition.followUpStage());
        assertEquals(expectedAnchor, transition.dueAnchor());
        assertEquals(expectedFollowUp, transition.projectedMotherStage());
    }

    /**
     * 阶段→可执行动作字典是客户端入口显隐的唯一依据，必须钉死。
     *
     * <p>流产只在孕期三个阶段成立：待催情/待配种时还没怀上，待分笼时已经生完了，
     * 这两处出现「流产」按钮都是荒谬的，点下去也必定 409。
     */
    @Test
    void abortionIsOfferedOnlyDuringGestation() {
        assertEquals(
            List.of("AWAIT_PALPATION", "AWAIT_PREPARTUM", "AWAIT_DELIVERY"),
            StageActionsView.stagesAllowing(ReproAction.ABORTION),
            "流产入口的可见阶段"
        );
    }

    /** 休养期和每个等待态都应能推迟与离场，否则待办会卡死在那一步。 */
    @Test
    void everyActionableStageCanPostponeAndRetire() {
        for (ReproStage stage : ReproStage.values()) {
            List<ReproAction> actions = TransitionTable.actionsFrom(stage);
            if (stage == ReproStage.READY || stage.isAwaiting()) {
                assertTrue(actions.contains(ReproAction.POSTPONE), stage + " 应可推迟");
                assertTrue(actions.contains(ReproAction.RETIRE), stage + " 应可离场");
            } else {
                assertFalse(actions.contains(ReproAction.POSTPONE), stage + " 不应可推迟");
            }
        }
    }

    /** 字典必须与 require() 完全一致，否则界面允许的和服务端允许的会分家。 */
    @Test
    void dictionaryMatchesWhatRequireActuallyAccepts() {
        for (ReproStage stage : ReproStage.values()) {
            List<ReproAction> offered = TransitionTable.actionsFrom(stage);
            for (ReproAction action : ReproAction.values()) {
                boolean accepted = outcomesFor(action).stream()
                    .anyMatch(outcome -> TransitionTable.find(stage, action, outcome) != null);
                assertEquals(accepted, offered.contains(action),
                    stage + "/" + action + "：字典与转换表不一致");
            }
        }
    }

    /** 带结果分流的动作要枚举全部分支，其余动作只有一个 null 分支。 */
    private static List<String> outcomesFor(ReproAction action) {
        return switch (action) {
            case PALPATION -> List.of("PREGNANT", "EMPTY", "UNSURE");
            case DELIVERY -> List.of("BORN", "FAILED");
            default -> java.util.Collections.singletonList(null);
        };
    }
}
