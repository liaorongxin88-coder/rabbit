package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReplacementRecordMapper {
    int insert(ReplacementRecord record);

    List<ReplacementRecord> selectDue(@Param("houseId") Long houseId, @Param("today") Date today);

    List<ReplacementRecord> selectDueUnnotified(@Param("houseId") Long houseId, @Param("today") Date today);

    List<ReplacementRecord> selectByHouse(@Param("houseId") Long houseId,
                                          @Param("matureNotified") Boolean matureNotified,
                                          @Param("from") Date from,
                                          @Param("to") Date to,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    List<ReplacementRecord> selectByRequestId(
        @Param("houseId") Long houseId,
        @Param("requestId") String requestId
    );

    int markDueAsNotified(@Param("houseId") Long houseId,
                          @Param("today") Date today,
                          @Param("updateBy") String updateBy,
                          @Param("limit") int limit);

    int markNotified(@Param("houseId") Long houseId, @Param("id") Long id, @Param("notifyDate") Date notifyDate, @Param("updateBy") String updateBy);

    ReplacementRecord selectPendingByRabbitForUpdate(
        @Param("houseId") Long houseId,
        @Param("rabbitId") Long rabbitId
    );

    int markPromoted(
        @Param("houseId") Long houseId,
        @Param("id") Long id,
        @Param("promotedAt") Date promotedAt,
        @Param("updateBy") String updateBy
    );
}
