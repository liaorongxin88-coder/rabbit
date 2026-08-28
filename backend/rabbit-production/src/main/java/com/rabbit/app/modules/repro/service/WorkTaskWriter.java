package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.TaskStatus;
import com.rabbit.app.modules.repro.domain.TaskSubjectType;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import com.rabbit.app.util.DateUtil;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * work_tasks 的写入封装。
 *
 * <p>抽出来只为一件事：保住「每个 OPEN 周期恰有 1 条关联 PENDING 任务」这条不变式。
 * 任务的建立、完成、作废若散落在状态机的各个分支里，这条不变式迟早破——
 * 旧实现的提醒之所以能和实际状态对不上，正是因为提醒散布在 next_event_date、
 * batch_rabbits.next_event_type 和夜间扫表三处，谁都不为一致性负责。
 */
@Component
public class WorkTaskWriter {
    private final WorkTaskMapper workTaskMapper;

    public WorkTaskWriter(WorkTaskMapper workTaskMapper) {
        this.workTaskMapper = workTaskMapper;
    }

    /**
     * 建立或改期一条待办。
     *
     * <p>主体归属决定 dedup_key：管线任务挂周期，分笼任务挂窝。血配时同一母兔因此
     * 能同时持有两条 PENDING 任务而不互相顶掉——它们的 dedup_key 前缀不同。
     */
    public WorkTask schedule(TaskScheduleRequest request) {
        TaskSubjectType subjectType = subjectTypeOf(request.taskType());
        Long subjectId = subjectType == TaskSubjectType.LITTER ? request.litterId() : request.cycleId();
        if (subjectId == null) {
            throw new IllegalArgumentException("任务主体 id 不能为空: " + request.taskType());
        }

        WorkTask task = new WorkTask();
        task.setHouseId(request.houseId());
        task.setTaskType(request.taskType().name());
        task.setSubjectType(subjectType.name());
        task.setSubjectId(subjectId);
        task.setCycleId(request.cycleId());
        task.setRabbitId(request.rabbitId());
        task.setBatchId(request.batchId());
        task.setCageId(request.cageId());
        task.setDueDate(request.dueTime());
        task.setDueTime(request.dueTime());
        task.setStatus(TaskStatus.PENDING.name());
        task.setDedupKey(dedupKey(subjectType, subjectId, request.taskType()));
        workTaskMapper.upsert(task);
        return task;
    }

    /**
     * 建立兔只级待办，例如商品兔待出售、后备兔成熟。
     *
     * <p>这类待办没有生产周期，不能伪造 cycleId。独立入口也防止以后新增非繁育任务时
     * 再次被 {@link #subjectTypeOf(TaskType)} 默认归到 CYCLE。
     */
    public WorkTask scheduleForRabbit(RabbitTaskScheduleRequest request) {
        return scheduleRabbitTask(
            request,
            dedupKey(TaskSubjectType.RABBIT, request.rabbitId(), request.taskType())
        );
    }

    /**
     * 每日任务的去重键带上业务自然日。同一只兔在同一天重复跑调度只更新同一条待办，
     * 次日则建立新的待办，不会覆盖尚未处理的前一日记录。
     */
    public WorkTask scheduleDailyForRabbit(RabbitTaskScheduleRequest request) {
        LocalDate taskDate = DateUtil.localDate(request.dueTime());
        if (taskDate == null) {
            throw new IllegalArgumentException("每日兔只任务的到期时间不能为空: " + request.taskType());
        }
        return scheduleRabbitTask(
            request,
            dedupKey(TaskSubjectType.RABBIT, request.rabbitId(), request.taskType()) + ':' + taskDate
        );
    }

    public void cancelCommodityDailyCareForRabbit(Long houseId, Long rabbitId, String operator) {
        cancelCommodityDailyCareForRabbitExcept(houseId, rabbitId, null, operator);
    }

    /** 保留当前阶段的每日任务，作废其它阶段尚未处理的日常任务。 */
    public void cancelCommodityDailyCareForRabbitExcept(
        Long houseId,
        Long rabbitId,
        TaskType retainedType,
        String operator
    ) {
        for (WorkTask task : pendingBySubject(houseId, TaskSubjectType.RABBIT, rabbitId)) {
            if (TaskType.isCommodityDailyCare(task.getTaskType())
                && (retainedType == null || !retainedType.name().equals(task.getTaskType()))) {
                workTaskMapper.cancel(houseId, task.getId(), operator);
            }
        }
    }

    /** 完成任务并回链事件。返回 false 表示任务已被他人处理（并发或重复提交）。 */
    public boolean complete(Long houseId, Long taskId, Long eventId, String operator) {
        return workTaskMapper.complete(houseId, taskId, eventId, operator) > 0;
    }

    /**
     * 完成某主体上的当前待办。
     *
     * <p>刻意按主体而非任务 id 查找：客户端从「今日待办」点进来时带任务 id，
     * 但从兔卡片直接操作时并没有任务 id，两条路径必须收敛到同一处完成逻辑。
     */
    public void completeBySubject(
        Long houseId,
        TaskSubjectType subjectType,
        Long subjectId,
        Long eventId,
        String operator
    ) {
        for (WorkTask task : workTaskMapper.selectPendingBySubject(houseId, subjectType.name(), subjectId)) {
            workTaskMapper.complete(houseId, task.getId(), eventId, operator);
        }
    }

    public List<WorkTask> pendingBySubject(Long houseId, TaskSubjectType subjectType, Long subjectId) {
        return workTaskMapper.selectPendingBySubject(houseId, subjectType.name(), subjectId);
    }

    public int postpone(Long houseId, Long taskId, Date dueTime, String operator) {
        return workTaskMapper.postpone(houseId, taskId, dueTime, dueTime, operator);
    }

    public void cancelBySubject(Long houseId, TaskSubjectType subjectType, Long subjectId, String operator) {
        workTaskMapper.cancelPendingBySubject(houseId, subjectType.name(), subjectId, operator);
    }

    /** 母兔离场：连并行的哺乳任务一起作废，避免离场后仍在待办里冒出来。 */
    public void cancelAllForRabbit(Long houseId, Long rabbitId, String operator) {
        workTaskMapper.cancelPendingByRabbit(houseId, rabbitId, operator);
    }

    /** 将已有开放周期归入批次时，同步其待办的冗余批次定位列。 */
    public void assignPendingCycleTasksToBatch(
        Long houseId,
        Long cycleId,
        Long batchId,
        String operator
    ) {
        workTaskMapper.assignPendingCycleTasksToBatch(
            houseId, cycleId, batchId, operator
        );
    }

    /** 完成兔只名下指定类型的待办；幂等重放时已完成任务不会再次更新。 */
    public void completeForRabbit(
        Long houseId,
        Long rabbitId,
        TaskType taskType,
        String operator
    ) {
        for (WorkTask task : pendingBySubject(houseId, TaskSubjectType.RABBIT, rabbitId)) {
            if (taskType.name().equals(task.getTaskType())) {
                workTaskMapper.complete(houseId, task.getId(), null, operator);
            }
        }
    }

    public void cancelForRabbit(
        Long houseId,
        Long rabbitId,
        TaskType taskType,
        String operator
    ) {
        for (WorkTask task : pendingBySubject(houseId, TaskSubjectType.RABBIT, rabbitId)) {
            if (taskType.name().equals(task.getTaskType())) {
                workTaskMapper.cancel(houseId, task.getId(), operator);
            }
        }
    }

    private WorkTask scheduleRabbitTask(RabbitTaskScheduleRequest request, String dedupKey) {
        if (request.rabbitId() == null) {
            throw new IllegalArgumentException("兔只任务的 rabbitId 不能为空: " + request.taskType());
        }
        WorkTask task = new WorkTask();
        task.setHouseId(request.houseId());
        task.setTaskType(request.taskType().name());
        task.setSubjectType(TaskSubjectType.RABBIT.name());
        task.setSubjectId(request.rabbitId());
        task.setRabbitId(request.rabbitId());
        task.setBatchId(request.batchId());
        task.setCageId(request.cageId());
        task.setDueDate(request.dueTime());
        task.setDueTime(request.dueTime());
        task.setStatus(TaskStatus.PENDING.name());
        task.setDedupKey(dedupKey);
        task.setRemark(request.remark());
        workTaskMapper.upsert(task);
        return task;
    }

    private static TaskSubjectType subjectTypeOf(TaskType taskType) {
        return taskType == TaskType.WEANING ? TaskSubjectType.LITTER : TaskSubjectType.CYCLE;
    }

    private static String dedupKey(TaskSubjectType subjectType, Long subjectId, TaskType taskType) {
        return subjectType.name().toLowerCase() + ':' + subjectId + ':' + taskType.name();
    }

    /**
     * @param litterId 仅分笼任务需要
     * @param cageId   供 NFC 碰笼直查待办；未知时为空不影响主流程
     */
    public record TaskScheduleRequest(
        Long houseId,
        TaskType taskType,
        Long cycleId,
        Long litterId,
        Long rabbitId,
        Long batchId,
        Long cageId,
        Date dueTime,
        String operator
    ) {
    }

    public record RabbitTaskScheduleRequest(
        Long houseId,
        TaskType taskType,
        Long rabbitId,
        Long batchId,
        Long cageId,
        Date dueTime,
        String remark,
        String operator
    ) {
    }
}
