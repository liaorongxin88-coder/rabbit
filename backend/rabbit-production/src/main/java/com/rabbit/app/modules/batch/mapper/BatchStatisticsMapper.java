package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.dto.BatchStatisticsMatingDateRow;
import com.rabbit.app.modules.batch.dto.BatchStatisticsRawSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchStatisticsMapper {
    BatchStatisticsRawSnapshot selectBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectMatingAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    List<BatchStatisticsMatingDateRow> selectMatingDates(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectAbortionAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectLitterAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectSalesCountAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectSalesValueAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectFeedAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectReplacementAggregate(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );

    BatchStatisticsRawSnapshot selectLatestCarcassYield(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );
}
