package com.rabbit.app.modules.outbound.mapper;

import com.rabbit.app.modules.outbound.entity.OutboundTask;
import java.math.BigDecimal;
import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundTaskMapper {
    int insert(OutboundTask task);
    OutboundTask selectById(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId, @Param("taskId") String taskId);
    OutboundTask selectByIdForUpdate(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId, @Param("taskId") String taskId);
    OutboundTask selectLatestEditable(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId);

    int updateDraft(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId,
                    @Param("taskId") String taskId, @Param("revision") Long revision,
                    @Param("status") String status, @Param("saleTime") Date saleTime,
                    @Param("totalWeight") Double totalWeight, @Param("unitPrice") BigDecimal unitPrice,
                    @Param("customer") String customer, @Param("remark") String remark);

    int markCancelled(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId, @Param("taskId") String taskId);
    int markSubmitting(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId,
                       @Param("taskId") String taskId, @Param("requestId") String requestId);
    int restoreWaiting(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId,
                       @Param("taskId") String taskId, @Param("requestId") String requestId);
    int markCompleted(@Param("houseId") Long houseId, @Param("operatorId") Long operatorId,
                      @Param("taskId") String taskId, @Param("requestId") String requestId,
                      @Param("saleOrderId") Long saleOrderId, @Param("completedTime") Date completedTime);
}
