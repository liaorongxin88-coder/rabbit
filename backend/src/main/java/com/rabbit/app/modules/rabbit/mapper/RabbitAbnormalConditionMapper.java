package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RabbitAbnormalConditionMapper {
    int insert(RabbitAbnormalCondition condition);

    List<RabbitAbnormalCondition> selectByHouse(@Param("houseId") Long houseId, @Param("isDeal") Boolean isDeal);

    int markDeal(@Param("houseId") Long houseId, @Param("id") Long id, @Param("deal") boolean deal, @Param("updateBy") String updateBy);

    int countUndealByCage(@Param("houseId") Long houseId, @Param("cageId") Long cageId);

    RabbitAbnormalCondition selectLatestByCage(@Param("houseId") Long houseId, @Param("cageId") Long cageId);
}
