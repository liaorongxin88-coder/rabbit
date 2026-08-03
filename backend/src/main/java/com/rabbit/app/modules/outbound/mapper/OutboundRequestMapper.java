package com.rabbit.app.modules.outbound.mapper;

import com.rabbit.app.modules.outbound.entity.OutboundRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundRequestMapper {
    int insertIgnore(OutboundRequest request);
    OutboundRequest selectById(@Param("houseId") Long houseId, @Param("requestId") String requestId);
    int markConflict(@Param("houseId") Long houseId, @Param("requestId") String requestId,
                     @Param("errorCode") String errorCode,
                     @Param("errorMessage") String errorMessage, @Param("conflictsJson") String conflictsJson);
    int markFailed(@Param("houseId") Long houseId, @Param("requestId") String requestId,
                   @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);
    int markCompleted(@Param("houseId") Long houseId, @Param("requestId") String requestId,
                      @Param("saleOrderId") Long saleOrderId);
}
