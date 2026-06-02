package com.rabbit.app.mapper;

import com.rabbit.app.model.RabbitStatusHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RabbitStatusHistoryMapper {
    int insert(RabbitStatusHistory history);

    List<RabbitStatusHistory> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId);
}
