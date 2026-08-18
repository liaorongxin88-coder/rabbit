package com.rabbit.app.modules.repro.domain;

/**
 * {@code work_tasks.subject_type}——任务挂载的主体（设计 §4.4）。
 *
 * <p>关键不变式：每个 OPEN 周期恰有 1 条关联 PENDING 任务。管线段（催情→接产）挂 CYCLE，
 * 哺乳段的分笼任务挂 LITTER。正因为分笼任务挂在窝上而不是周期上，血配时同一母兔可以
 * 同时看到两条任务——新周期的管线任务 + 旧窝的分笼任务——互不干扰。
 *
 * <p>刻意没有 BATCH：批次退化为纯标签，本身无状态也无提醒，空批次角标由查询派生。
 */
public enum TaskSubjectType {
    CYCLE("周期"),
    LITTER("窝"),
    RABBIT("兔子"),
    CAGE("笼位");

    private final String label;

    TaskSubjectType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
