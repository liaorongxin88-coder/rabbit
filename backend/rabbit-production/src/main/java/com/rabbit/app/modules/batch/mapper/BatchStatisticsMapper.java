package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.dto.BatchStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchStatisticsMapper {
    BatchStatistics selectByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );
}
