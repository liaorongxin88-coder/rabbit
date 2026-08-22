package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.BreedingCycle;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BreedingCycleMapper {




    List<BreedingCycle> selectByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("activeOnly") Boolean activeOnly
    );











    /**
     * 按权威列 lifecycle 统计未结束周期。
     *
     * <p>与 {@link #countOpenByBatch} 的区别在于判据：后者看 closed_at，而 doe-breeding-v2
     * 之后判定周期是否进行中的唯一依据是 lifecycle（并发守卫也只读它）。
     * 两个列在旧路径退休前可能不一致，批次结束守门必须以 lifecycle 为准。
     */
    int countOpenLifecycleByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId);




}
