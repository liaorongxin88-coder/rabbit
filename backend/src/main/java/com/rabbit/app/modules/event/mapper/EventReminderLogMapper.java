package com.rabbit.app.modules.event.mapper;

import com.rabbit.app.modules.event.entity.EventReminderLog;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventReminderLogMapper {
    int insertDueBatchEventLogs(@Param("houseId") Long houseId, @Param("today") Date today);


    int insertDueReplacementLogs(@Param("houseId") Long houseId, @Param("today") Date today);

    List<EventReminderLog> selectByHouseAndDateRange(@Param("houseId") Long houseId, @Param("from") Date from, @Param("to") Date to, @Param("limit") Integer limit);
}
