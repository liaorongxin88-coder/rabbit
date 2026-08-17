package com.rabbit.app.modules.repro.domain;

/** 周期生命周期（{@code breeding_cycles.lifecycle}），并发守卫生成列只认这一列。 */
public enum CycleLifecycle {
    OPEN("进行中"),
    CLOSED("已结束");

    private final String label;

    CycleLifecycle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
