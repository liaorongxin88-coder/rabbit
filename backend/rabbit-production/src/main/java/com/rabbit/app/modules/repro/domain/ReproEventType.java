package com.rabbit.app.modules.repro.domain;

/**
 * {@code repro_events.event_type}——append-only 事件流的事件种类（设计 §4.1）。
 *
 * <p>事件流有三重职责，所以「每个操作一种事件」比复用动作枚举更合适：
 * 留痕（谁在什么时候对哪只母兔做了什么）、幂等（唯一键冲突即回查首次结果）、
 * 可重放（投影损坏时由事件流重建 rabbits.current_stage 与统计）。
 */
public enum ReproEventType {
    CYCLE_START("开始周期"),
    RECOVERY_DONE("结束休养"),
    ESTRUS_DONE("催情"),
    MATING_DONE("配种"),
    PALPATION_PREGNANT("摸胎-怀孕"),
    PALPATION_EMPTY("摸胎-空怀"),
    PALPATION_UNSURE("摸胎-不确定"),
    PREPARTUM_DONE("备产"),
    DELIVERY_DONE("接产"),
    DELIVERY_FAILED("难产"),
    WEANING_DONE("分笼"),
    ABORTION("流产"),
    POSTPONE("推迟"),
    CYCLE_CLOSE("周期结束"),
    /** 改批次标签留痕（设计 §4.5）。 */
    BATCH_RETAG("批次改标"),
    /** 母兔离场，与 departure 记录联动。 */
    DEPARTURE("离场"),
    /**
     * 异期复孕等异常补产的兜底事件（设计 §3.4）。刻意不开第二条管线周期：
     * 生产管理上这属于应预防的事故，用异常事件 + 窝计数修正记录，而不是把它建成常规路径。
     */
    DELIVERY_EXTRA("异常补产"),
    /** 哺乳期留崽数调整；阶段与待办不变，只更新窝计数并追加审计事件。 */
    KEPT_KITS_ADJUSTED("留崽数调整");

    private final String label;

    ReproEventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
