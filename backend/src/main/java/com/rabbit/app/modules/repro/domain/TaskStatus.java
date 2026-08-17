package com.rabbit.app.modules.repro.domain;

/**
 * {@code work_tasks.status}（设计 §4.4）。
 *
 * <p>刻意没有「已读 / 已 ack」态：旧模型用 event_acks + is_event_notified 表达
 * 「看过但没做」，结果提醒会从首页消失而事情没做。新模型里「未执行」= 推迟（改 due_time
 * 并累加 snooze_count），任务始终 PENDING，直到真的做完才 DONE。
 */
public enum TaskStatus {
    PENDING("待办"),
    DONE("已完成"),
    /** 母兔离场等原因级联取消。 */
    CANCELLED("已取消"),
    /** 预留：周期被更晚的事实证伪时作废（例如回填后发现该窝早已断奶）。 */
    EXPIRED("已失效");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
