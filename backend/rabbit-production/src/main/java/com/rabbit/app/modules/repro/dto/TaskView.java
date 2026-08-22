package com.rabbit.app.modules.repro.dto;

import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.entity.WorkTask;
import java.util.Date;

/**
 * 待办列表项。
 *
 * <p>带上 {@code taskLabel} 和 {@code action}，让客户端不必再维护一份
 * 「任务类型 → 按钮文案 → 该调哪个动作」的映射表。旧实现里这份映射在
 * App 和后端各存一份，改一处忘一处正是提醒与实际状态对不上的来源之一。
 */
public record TaskView(
    Long id,
    String taskType,
    String taskLabel,
    String action,
    String subjectType,
    Long subjectId,
    Long cycleId,
    Long rabbitId,
    Long batchId,
    Long cageId,
    Date dueDate,
    Date dueTime,
    String status,
    Integer snoozeCount,
    boolean overdue,
    String remark
) {

    public static TaskView of(WorkTask task, Date today) {
        TaskType type = TaskType.parse(task.getTaskType());
        return new TaskView(
            task.getId(),
            task.getTaskType(),
            type.label(),
            type.action() == null ? null : type.action().name(),
            task.getSubjectType(),
            task.getSubjectId(),
            task.getCycleId(),
            task.getRabbitId(),
            task.getBatchId(),
            task.getCageId(),
            task.getDueDate(),
            task.getDueTime(),
            task.getStatus(),
            task.getSnoozeCount(),
            task.getDueDate() != null && task.getDueDate().before(today),
            task.getRemark()
        );
    }
}
