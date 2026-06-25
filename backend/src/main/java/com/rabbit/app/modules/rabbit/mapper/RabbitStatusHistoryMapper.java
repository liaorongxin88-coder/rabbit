package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RabbitStatusHistoryMapper {
    int insert(RabbitStatusHistory history);

    List<RabbitStatusHistory> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId);
}
