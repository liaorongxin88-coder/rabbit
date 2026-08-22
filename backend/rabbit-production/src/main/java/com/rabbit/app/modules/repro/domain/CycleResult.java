package com.rabbit.app.modules.repro.domain;

/**
 * 周期关闭结果（{@code breeding_cycles.result}，仅 lifecycle=CLOSED 时非空）。
 *
 * <p>与 {@link CycleLifecycle} 分成两列而不是压进一个 status 字段，是为了让
 * 「周期是否还占管线」（lifecycle）和「这一轮的业务结局」（result）各自独立可查：
 * 报表按 result 统计繁殖成绩，并发守卫只看 lifecycle。
 */
public enum CycleResult {
    /** 正常走完：已分笼断奶。 */
    WEANED("已断奶"),
    /** 摸胎空怀。 */
    EMPTY("空怀"),
    /** 流产。 */
    ABORTED("流产"),
    /** 分娩失败。 */
    FAILED("分娩失败"),
    /** 母兔离场（死亡 / 淘汰 / 出售）导致周期中止。 */
    REMOVED("已离场");

    private final String label;

    CycleResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
