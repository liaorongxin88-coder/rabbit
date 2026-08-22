package com.rabbit.app.modules.event.mapper;

import com.rabbit.app.modules.event.dto.EventAckSummary;
import com.rabbit.app.modules.event.entity.EventAck;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
