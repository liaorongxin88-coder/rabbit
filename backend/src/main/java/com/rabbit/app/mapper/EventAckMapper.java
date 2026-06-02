package com.rabbit.app.mapper;

import com.rabbit.app.model.EventAck;
import com.rabbit.app.dto.EventAckSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface EventAckMapper {
    int upsert(EventAck ack);

    EventAck selectOne(@Param("userId") Long userId, @Param("houseId") Long houseId, @Param("category") String category, @Param("recordId") Long recordId);

    List<EventAck> selectByUserAndHouse(@Param("userId") Long userId, @Param("houseId") Long houseId);

    List<Long> selectSuppressedRecordIds(@Param("userId") Long userId,
                                         @Param("houseId") Long houseId,
                                         @Param("category") String category,
                                         @Param("now") Date now);

    EventAckSummary selectProdAckSummary(@Param("houseId") Long houseId,
                                         @Param("from") Date from,
                                         @Param("to") Date to);

    EventAckSummary selectReplacementAckSummary(@Param("houseId") Long houseId,
                                                @Param("from") Date from,
                                                @Param("to") Date to);
}
