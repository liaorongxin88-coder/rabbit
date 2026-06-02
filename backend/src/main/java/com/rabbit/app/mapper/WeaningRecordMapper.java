package com.rabbit.app.mapper;

import com.rabbit.app.model.WeaningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WeaningRecordMapper {
    int insert(WeaningRecord record);

    List<WeaningRecord> selectByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("limit") int limit);

    List<WeaningRecord> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);
}
