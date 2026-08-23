package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.WeaningRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WeaningRecordMapper {
    int insert(WeaningRecord record);

    List<WeaningRecord> selectByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("limit") int limit);

    List<WeaningRecord> selectPendingByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("limit") int limit);

    WeaningRecord selectById(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("id") Long id);

    WeaningRecord selectByIdForUpdate(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("id") Long id);

    int decrementWaitingCount(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("id") Long id, @Param("count") int count, @Param("updateBy") String updateBy);

    List<WeaningRecord> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);
}
