package com.rabbit.app.mapper;

import com.rabbit.app.model.RabbitHouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RabbitHouseMapper {
    int insert(RabbitHouse house);

    RabbitHouse selectById(@Param("houseId") Long houseId);

    List<RabbitHouse> selectByUserId(@Param("userId") Long userId);

    List<RabbitHouse> selectAllActive();

    RabbitHouse selectByCreatorAndRequestId(@Param("createBy") String createBy, @Param("requestId") String requestId);

    int updateBasic(@Param("houseId") Long houseId, @Param("name") String name, @Param("remark") String remark, @Param("updateBy") String updateBy);

    int markDeleted(@Param("houseId") Long houseId, @Param("updateBy") String updateBy);
}
