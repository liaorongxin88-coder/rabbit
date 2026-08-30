package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据驱动的状态机转换表（设计 §3.2 T2–T9、T11）。
 *
 * <p>这是全系统唯一的「什么阶段能做什么操作」判据。旧实现把它拆散在 BatchService 的六个
 * 千行方法里各写一遍 if，于是同一条规则在不同入口有不同写法——飞书 recvsrp9E2dqvB
 * （阶段与批次不对应）本质就是这种重复的产物。
 *
 * <p>刻意不含 T1 START_CYCLE：开周期不是「已有周期上的转换」，它的入轨阶段与补录事实
 * 另有一张锚点表（{@link EntryPoint}），两者语义不同不应挤进同一个 Map。
 *
 * <p>扩展点：新增阶段 / 操作 = 往这张表加数据 + 加枚举项，不动表结构、不动服务代码。
 */
public final class TransitionTable {
    private static final Map<String, Transition> TABLE = build();

    private TransitionTable() {
    }

    /**
     * 查转换；非法组合抛 409，让客户端知道是「状态已变化」而不是参数错误。
     */
    public static Transition require(ReproStage from, ReproAction action, String outcome) {
        Transition transition = find(from, action, outcome);
        if (transition == null) {
            throw new BizException(
                409,
                "当前阶段【" + from.label() + "】不允许执行【" + action.label() + "】"
            );
        }
        return transition;
    }

    public static Transition find(ReproStage from, ReproAction action, String outcome) {
        if (from == null || action == null) {
            return null;
        }
        return TABLE.get(key(from, action, outcome));
    }

    /** 供测试与文档生成：全部合法转换。 */
    public static List<Transition> all() {
        return List.copyOf(TABLE.values());
    }

    /**
     * 某阶段当下允许的全部动作，按枚举声明顺序返回。
     *
     * <p>存在的意义是让客户端不再自己维护一张「哪个阶段能做什么」的表。
     * 流产就是典型例子：它只在孕期三个阶段成立，若客户端把这个判断拄写一份，
     * 日后转移表改了而界面没改，用户就会看到一个点下去必定 409 的按钮——
     * 旧实现里 App 与后端各存一张映射表并最终漂移，就是这么发生的。
     */
    public static List<ReproAction> actionsFrom(ReproStage from) {
        if (from == null) {
            return List.of();
        }
        return java.util.Arrays.stream(ReproAction.values())
            .filter(action -> TABLE.keySet().stream()
                .anyMatch(key -> key.startsWith(from.name() + '|' + action.name() + '|')))
            .toList();
    }

    private static String key(ReproStage from, ReproAction action, String outcome) {
        return from.name() + '|' + action.name() + '|' + (outcome == null ? "" : outcome);
    }

    private static Map<String, Transition> build() {
        List<Transition> rows = new ArrayList<>();

        // T1 休养结束：无批次继续进入待催情，批次直到配种时才绑定。
        rows.add(advance(
            ReproStage.READY, ReproAction.START_CYCLE, ReproStage.AWAIT_ESTRUS,
            DueAnchor.IMMEDIATE, ReproEventType.RECOVERY_DONE
        ));

        // T2 催情：待催情 → 待配种，到期 = 操作日 + estrus_duration_days
        rows.add(advance(
            ReproStage.AWAIT_ESTRUS, ReproAction.ESTRUS, ReproStage.AWAIT_MATING,
            DueAnchor.ESTRUS_DURATION, ReproEventType.ESTRUS_DONE
        ));

        // T3 配种：待配种 → 待摸胎，到期 = 配种日 + palpation_wait_days
        rows.add(advance(
            ReproStage.AWAIT_MATING, ReproAction.MATING, ReproStage.AWAIT_PALPATION,
            DueAnchor.PALPATION_WAIT, ReproEventType.MATING_DONE
        ));

        // T4a 摸胎-怀孕：→ 待备产，到期 = 摸胎确认日 + prepartum_days
        rows.add(new Transition(
            ReproStage.AWAIT_PALPATION, ReproAction.PALPATION, PalpationResult.PREGNANT.name(),
            ReproStage.AWAIT_PREPARTUM, false, null, null,
            DueAnchor.PREPARTUM_DURATION, ReproEventType.PALPATION_PREGNANT
        ));

        // T4b 摸胎-空怀：关闭原批次周期，进入无批次休养期。
        rows.add(new Transition(
            ReproStage.AWAIT_PALPATION, ReproAction.PALPATION, PalpationResult.EMPTY.name(),
            null, true, CycleResult.EMPTY, ReproStage.READY,
            DueAnchor.POSTPARTUM_RECOVERY, ReproEventType.PALPATION_EMPTY
        ));

        // T4c 摸胎-不确定：阶段不变，按用户选的复查日期再提醒
        rows.add(new Transition(
            ReproStage.AWAIT_PALPATION, ReproAction.PALPATION, PalpationResult.UNSURE.name(),
            ReproStage.AWAIT_PALPATION, false, null, null,
            DueAnchor.USER_SPECIFIED, ReproEventType.PALPATION_UNSURE
        ));

        // T5 备产：→ 待分娩，完成备产当天即提醒接产
        rows.add(advance(
            ReproStage.AWAIT_PREPARTUM, ReproAction.PREPARTUM, ReproStage.AWAIT_DELIVERY,
            DueAnchor.SAME_DAY, ReproEventType.PREPARTUM_DONE
        ));

        // T6 接产-产仔：原周期进入待分笼并建窝；服务层同时建立无批次产后恢复周期。
        rows.add(new Transition(
            ReproStage.AWAIT_DELIVERY, ReproAction.DELIVERY, DeliveryOutcome.BORN.name(),
            ReproStage.AWAIT_WEANING, false, null, null,
            DueAnchor.WEANING_DUE, ReproEventType.DELIVERY_DONE
        ));

        // T6x 接产-分娩失败：关闭原批次周期，进入无批次休养期。
        rows.add(new Transition(
            ReproStage.AWAIT_DELIVERY, ReproAction.DELIVERY, DeliveryOutcome.FAILED.name(),
            null, true, CycleResult.FAILED, ReproStage.READY,
            DueAnchor.POSTPARTUM_RECOVERY, ReproEventType.DELIVERY_FAILED
        ));

        // T7 分笼：关闭原批次周期并进入无批次休养期。
        // 若母兔已有下一轮管线周期（血配），服务层只关闭本轮哺乳周期。
        rows.add(new Transition(
            ReproStage.AWAIT_WEANING, ReproAction.WEANING, null,
            null, true, CycleResult.WEANED, ReproStage.READY,
            DueAnchor.POSTPARTUM_RECOVERY, ReproEventType.WEANING_DONE
        ));

        // T8 流产：怀孕段三个阶段都可能发生，一律关周期后复旧再催情
        for (ReproStage from : List.of(
            ReproStage.AWAIT_PALPATION, ReproStage.AWAIT_PREPARTUM, ReproStage.AWAIT_DELIVERY
        )) {
            rows.add(new Transition(
                from, ReproAction.ABORTION, null,
                null, true, CycleResult.ABORTED, ReproStage.READY,
                DueAnchor.POSTPARTUM_RECOVERY, ReproEventType.ABORTION
            ));
        }

        // T9 推迟：休养期和任意等待态都可推迟，阶段不变，只顺延到期时间
        for (ReproStage from : ReproStage.values()) {
            if (from == ReproStage.READY || from.isAwaiting()) {
                rows.add(new Transition(
                    from, ReproAction.POSTPONE, null,
                    from, false, null, null,
                    DueAnchor.USER_SPECIFIED, ReproEventType.POSTPONE
                ));
            }
        }

        // T11 离场：任何还开着周期的阶段都能离场，关周期且不接续
        for (ReproStage from : ReproStage.values()) {
            if (from.isAwaiting() || from == ReproStage.READY) {
                rows.add(new Transition(
                    from, ReproAction.RETIRE, null,
                    null, true, CycleResult.REMOVED, null,
                    DueAnchor.NONE, ReproEventType.DEPARTURE
                ));
            }
        }

        Map<String, Transition> table = new HashMap<>();
        for (Transition row : rows) {
            String key = key(row.fromStage(), row.action(), row.outcome());
            if (table.put(key, row) != null) {
                throw new IllegalStateException("转换表存在重复定义: " + key);
            }
        }
        return Collections.unmodifiableMap(table);
    }

    private static Transition advance(
        ReproStage from,
        ReproAction action,
        ReproStage to,
        DueAnchor anchor,
        ReproEventType eventType
    ) {
        return new Transition(from, action, null, to, false, null, null, anchor, eventType);
    }
}
