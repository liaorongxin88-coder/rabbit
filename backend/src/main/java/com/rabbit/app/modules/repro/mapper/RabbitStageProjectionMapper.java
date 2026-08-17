package com.rabbit.app.modules.repro.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * rabbits 投影列的唯一写入口（设计 §4.6）。
 *
 * <p>刻意做成只含投影列的窄接口，而不是复用 RabbitMapper 的全量 update：
 * 「谁能改 current_stage」这个问题必须只有一个答案，否则又会退回旧模型里
 * 三处写点各自漂移的老路。
 */
@Mapper
public interface RabbitStageProjectionMapper {

    int projectStage(
        @Param("houseId") Long houseId,
        @Param("rabbitId") Long rabbitId,
        @Param("currentStage") String currentStage,
        @Param("currentCycleId") Long currentCycleId,
        @Param("stageEnteredAt") Date stageEnteredAt,
        @Param("updateBy") String updateBy
    );

    /** 种公兔的最近配种日；与母兔阶段投影分开，避免一次写入牵扯两种语义。 */
    int touchLastMatingDate(
        @Param("houseId") Long houseId,
        @Param("rabbitId") Long rabbitId,
        @Param("lastMatingDate") Date lastMatingDate,
        @Param("updateBy") String updateBy
    );
}
