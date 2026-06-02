package com.rabbit.app.mapper;

import com.rabbit.app.model.ParturitionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ParturitionRecordMapper {
    int insert(ParturitionRecord record);

    List<ParturitionRecord> selectByBatch(@Param("houseId") Long houseId, @Param("batchId") Long batchId, @Param("limit") int limit);

    List<ParturitionRecord> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);
}
