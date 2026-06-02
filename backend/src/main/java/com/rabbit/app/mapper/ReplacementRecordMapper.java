package com.rabbit.app.mapper;

import com.rabbit.app.model.ReplacementRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

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

    int markDueAsNotified(@Param("houseId") Long houseId, @Param("today") Date today, @Param("updateBy") String updateBy);

    int markNotified(@Param("houseId") Long houseId, @Param("id") Long id, @Param("notifyDate") Date notifyDate, @Param("updateBy") String updateBy);
}
