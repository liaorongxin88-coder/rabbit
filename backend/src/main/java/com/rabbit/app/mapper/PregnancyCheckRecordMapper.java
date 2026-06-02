package com.rabbit.app.mapper;

import com.rabbit.app.model.PregnancyCheckRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PregnancyCheckRecordMapper {
    int insert(PregnancyCheckRecord record);

    List<PregnancyCheckRecord> selectByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("limit") int limit);

    List<PregnancyCheckRecord> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);
}
