package com.rabbit.app.modules.repro.mapper;

import com.rabbit.app.modules.repro.entity.ReproCycle;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * breeding_cycles 的 V2 写入口。与 batch 模块的 BreedingCycleMapper 并存（见 {@link ReproCycle}）。
 *
 * <p>所有方法一律带 houseId：租户隔离靠 SQL 条件强制，不依赖调用方自觉。
 */
@Mapper
public interface ReproCycleMapper {

    int insert(ReproCycle cycle);

    ReproCycle selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    ReproCycle selectByIdForUpdate(@Param("houseId") Long houseId, @Param("id") Long id);

    /**
     * 锁定该母兔当前占用管线的 OPEN 周期。
     *
     * <p>管线段 = pipeline_guard 生成列认定的五个阶段，刻意不含 AWAIT_WEANING——
     * 哺乳与下一轮怀孕可并行（血配），这是「同一母兔仅一条管线周期」不变式的准确边界。
     */
    ReproCycle selectOpenPipelineForUpdate(
        @Param("houseId") Long houseId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    /**
     * 锁定该母兔在指定批次内的 OPEN 周期（含哺乳段）。
     *
     * <p>对应 V44 的 uk_bc_batch_member：一只母兔在同一批次内至多一条未结束周期。
     * 与 {@link #selectOpenPipelineForUpdate} 的区别是它<b>不排除</b> AWAIT_WEANING——
     * 哺乳周期不占管线，但它占批次，血配的下一轮因此必须开在别的批次上。
     */
    ReproCycle selectOpenByBatchAndMotherForUpdate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    /** 该母兔全部未结束周期（含哺乳段），用于血配场景下的并行判定。 */
    List<ReproCycle> selectOpenByMother(
        @Param("houseId") Long houseId,
        @Param("motherRabbitId") Long motherRabbitId
    );

    /** 将锁定的无批次周期归入新批次，不覆盖已有批次归属。 */
    int assignBatchIfUnbound(
        @Param("houseId") Long houseId,
        @Param("id") Long id,
        @Param("batchId") Long batchId,
        @Param("updateBy") String updateBy
    );

    /**
     * 应用一次状态转换。
     *
     * <p>where 带 state_version 比对：并发下第二个写入者影响 0 行，服务层据此抛 409，
     * 而不是让后写者静默覆盖先写者。
     *
     * @return 受影响行数；0 表示版本已变化
     */
    int applyTransition(
        @Param("cycle") ReproCycle cycle,
        @Param("expectedVersion") Long expectedVersion
    );

    /** 批次内该母兔已用的最大周期号；空怀/流产后允许重开，故按 OPEN+CLOSED 全量取最大值。 */
    Integer selectMaxCycleNo(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId
    );
}
