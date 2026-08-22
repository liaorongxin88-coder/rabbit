package com.rabbit.app.modules.repro.domain;

/** 接产结果分流（设计 §3.2 T6 / T6x）。 */
public enum DeliveryOutcome {
    /** T6 正常产仔：建窝进入哺乳段。 */
    BORN("产仔"),
    /** T6x 分娩失败：关闭周期，待子宫复旧后重新催情。 */
    FAILED("分娩失败");

    private final String label;

    DeliveryOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
