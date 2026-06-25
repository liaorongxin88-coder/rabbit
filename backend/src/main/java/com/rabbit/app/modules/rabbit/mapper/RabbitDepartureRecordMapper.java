package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
