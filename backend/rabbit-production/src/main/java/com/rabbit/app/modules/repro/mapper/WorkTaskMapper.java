package com.rabbit.app.modules.repro.mapper;

import com.rabbit.app.modules.repro.entity.WorkTask;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * work_tasks 的读写口（设计 §4.4）。
 *
 * <p>取代夜间扫表：任务在业务事务里同步生成，首页读的是索引直查而不是扫描结果。
 */
@Mapper
public interface WorkTaskMapper {

    /**
     * 按 uk_wt_dedup 落库；已存在则复活并改期。
     *
     * <p>用 upsert 而不是 select-then-insert：并发下后者会撞唯一键，而复活语义
     * （空怀重开周期时旧的催情任务还在）本来就等价于「写入或改期」。
     */
    int upsert(WorkTask task);

    WorkTask selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    WorkTask selectByIdForUpdate(@Param("houseId") Long houseId, @Param("id") Long id);

    WorkTask selectByDedupKey(@Param("houseId") Long houseId, @Param("dedupKey") String dedupKey);

    /** 完成任务：仅 PENDING 可完成，回链事件 id。返回 0 表示已被他人处理。 */
    int complete(
        @Param("houseId") Long houseId,
        @Param("id") Long id,
        @Param("completedEventId") Long completedEventId,
        @Param("updateBy") String updateBy
    );

    int cancel(
        @Param("houseId") Long houseId,
        @Param("id") Long id,
        @Param("updateBy") String updateBy
    );

    /** 推迟：状态保持 PENDING，只改到期时间并累加 snoozeCount。 */
    int postpone(
        @Param("houseId") Long houseId,
        @Param("id") Long id,
        @Param("dueDate") Date dueDate,
        @Param("dueTime") Date dueTime,
        @Param("updateBy") String updateBy
    );

    /** 关闭主体（周期/窝）时批量作废其待办。 */
    int cancelPendingBySubject(
        @Param("houseId") Long houseId,
        @Param("subjectType") String subjectType,
        @Param("subjectId") Long subjectId,
        @Param("updateBy") String updateBy
    );

    /** 母兔离场：其名下所有待办一并作废，含并行的哺乳任务。 */
    int cancelPendingByRabbit(
        @Param("houseId") Long houseId,
        @Param("rabbitId") Long rabbitId,
        @Param("updateBy") String updateBy
    );

    /** 将无批次周期尚未完成的待办归入新批次。 */
    int assignPendingCycleTasksToBatch(
        @Param("houseId") Long houseId,
        @Param("cycleId") Long cycleId,
        @Param("batchId") Long batchId,
        @Param("updateBy") String updateBy
    );

    List<WorkTask> selectPendingBySubject(
        @Param("houseId") Long houseId,
        @Param("subjectType") String subjectType,
        @Param("subjectId") Long subjectId
    );

    /**
     * 待办列表主查询，走 idx_wt_due。
     *
     * @param dueBefore 含当日；为空时不应用到期日上限
     */
    List<WorkTask> selectPendingDue(
        @Param("houseId") Long houseId,
        @Param("dueBefore") Date dueBefore,
        @Param("taskType") String taskType,
        @Param("batchId") Long batchId,
        @Param("cageId") Long cageId,
        @Param("rabbitId") Long rabbitId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    /**
     * 批量操作的目标解析：不看到期日，取该条件下全部 PENDING 任务。
     *
     * <p>与 {@link #selectPendingDue} 分开：待办列表答的是「今天该干什么」，而批量
     * 操作答的是「这批还没干完的有哪些」——把未到期的任务排除在外会让
     * 「批量提前配种」这类合理操作静默漏掉一部分母兔。
     */
    List<WorkTask> selectPendingByFilter(
        @Param("houseId") Long houseId,
        @Param("taskType") String taskType,
        @Param("batchId") Long batchId,
        @Param("cageId") Long cageId,
        @Param("limit") int limit
    );

    /** 统计待办；dueBefore 为空时不应用到期日上限。 */
    long countPendingDue(
        @Param("houseId") Long houseId,
        @Param("dueBefore") Date dueBefore,
        @Param("taskType") String taskType,
        @Param("batchId") Long batchId,
        @Param("cageId") Long cageId,
        @Param("rabbitId") Long rabbitId
    );
}
