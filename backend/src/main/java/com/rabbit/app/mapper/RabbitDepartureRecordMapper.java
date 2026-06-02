package com.rabbit.app.mapper;

import com.rabbit.app.model.RabbitDepartureRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface RabbitDepartureRecordMapper {
    int insert(RabbitDepartureRecord r);

    RabbitDepartureRecord selectByHouseRabbitRequest(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("requestId") String requestId);

    List<RabbitDepartureRecord> selectByRabbit(@Param("houseId") Long houseId, @Param("rabbitId") Long rabbitId, @Param("limit") int limit);

    List<RabbitDepartureRecord> selectPageByHouse(@Param("houseId") Long houseId,
                                                 @Param("rabbitId") Long rabbitId,
                                                 @Param("from") Date from,
                                                 @Param("to") Date to,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);
}
