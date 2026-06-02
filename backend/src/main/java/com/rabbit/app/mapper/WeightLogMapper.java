package com.rabbit.app.mapper;

import com.rabbit.app.model.WeightLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WeightLogMapper {
    int insert(WeightLog r);

    WeightLog selectByReq(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("requestId") String requestId);

    List<WeightLog> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);
}

