package com.rabbit.app.modules.weight.mapper;

import com.rabbit.app.modules.weight.entity.WeightLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WeightLogMapper {
    int insert(WeightLog r);

    WeightLog selectByReq(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("requestId") String requestId);

    List<WeightLog> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);
}

