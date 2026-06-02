package com.rabbit.app.mapper;

import com.rabbit.app.model.EventReminderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface EventReminderLogMapper {
    int insertDueBatchEventLogs(@Param("houseId") Long houseId, @Param("today") Date today);

    int insertDueReplacementLogs(@Param("houseId") Long houseId, @Param("today") Date today);

    List<EventReminderLog> selectByHouseAndDateRange(@Param("houseId") Long houseId, @Param("from") Date from, @Param("to") Date to, @Param("limit") Integer limit);
}
