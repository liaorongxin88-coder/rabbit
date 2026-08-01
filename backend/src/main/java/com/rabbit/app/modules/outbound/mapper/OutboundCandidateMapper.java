package com.rabbit.app.modules.outbound.mapper;

import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundCandidateMapper {
    List<OutboundCandidateRow> selectScope(@Param("houseId") Long houseId, @Param("entryType") String entryType,
                                           @Param("rabbitId") Long rabbitId, @Param("cageId") Long cageId,
                                           @Param("rowCode") String rowCode);
    List<OutboundCandidateRow> selectByIds(@Param("houseId") Long houseId, @Param("rabbitIds") List<Long> rabbitIds);
    List<Long> lockRabbitIds(@Param("houseId") Long houseId, @Param("rabbitIds") List<Long> rabbitIds);
}
