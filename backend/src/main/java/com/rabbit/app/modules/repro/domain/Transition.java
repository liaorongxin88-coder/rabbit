package com.rabbit.app.modules.repro.domain;

/**
 * 一条状态机转换（设计 §3.2 转换表的一行）。
 *
 * <p>语义约定，三者互斥且完备：
 * <ul>
 *   <li><b>推进</b>：{@code closesCycle=false}，当前周期 stage 改为 {@link #toStage()}。</li>
 *   <li><b>关闭并接续</b>：{@code closesCycle=true} 且 {@link #followUpStage()} 非空——
 *       当前周期落 {@link #result()} 后关闭，同事务在 followUpStage 开新周期
 *       （空怀 / 分娩失败 / 流产 / 分笼后的下一轮）。</li>
 *   <li><b>关闭并终止</b>：{@code closesCycle=true} 且 followUpStage 为空——离场。</li>
 * </ul>
 *
 * <p>关闭周期时刻意不改写 {@code stage}：保留「这一轮停在哪个阶段」的事实，
 * 流产统计要按发生阶段分组，而并发守卫只看 lifecycle，不受影响。
 *
 * @param toStage         推进后的阶段；关闭类转换为 null
 * @param followUpStage   新周期的入轨阶段；不接续时为 null
 * @param dueAnchor       接下来那条待办的到期日锚点
 */
public record Transition(
    ReproStage fromStage,
    ReproAction action,
    String outcome,
    ReproStage toStage,
    boolean closesCycle,
    CycleResult result,
    ReproStage followUpStage,
    DueAnchor dueAnchor,
    ReproEventType eventType
) {
    public Transition {
        if (closesCycle && result == null) {
            throw new IllegalArgumentException("关闭类转换必须给出 result: " + fromStage + "/" + action);
        }
        if (!closesCycle && result != null) {
            throw new IllegalArgumentException("非关闭类转换不应有 result: " + fromStage + "/" + action);
        }
        if (closesCycle && toStage != null) {
            throw new IllegalArgumentException("关闭类转换不应改写 toStage: " + fromStage + "/" + action);
        }
        if (!closesCycle && toStage == null) {
            throw new IllegalArgumentException("推进类转换必须给出 toStage: " + fromStage + "/" + action);
        }
    }

    /** 母兔在这次转换后应当投影出的阶段（{@code rabbits.current_stage} 的单一来源）。 */
    public ReproStage projectedMotherStage() {
        if (!closesCycle) {
            return toStage;
        }
        if (followUpStage != null) {
            return followUpStage;
        }
        return result == CycleResult.REMOVED ? ReproStage.RETIRED : ReproStage.READY;
    }

    /** 是否需要同事务建窝（只有正常接产会）。 */
    public boolean createsLitter() {
        return action == ReproAction.DELIVERY && DeliveryOutcome.BORN.name().equals(outcome);
    }

    /** 是否需要级联取消该母兔全部 PENDING 任务（离场）。 */
    public boolean cancelsAllTasks() {
        return action == ReproAction.RETIRE;
    }
}
