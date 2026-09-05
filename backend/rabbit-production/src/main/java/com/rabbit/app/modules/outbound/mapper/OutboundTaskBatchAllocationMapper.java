package com.rabbit.app.modules.outbound.mapper;

import com.rabbit.app.modules.outbound.entity.OutboundTaskBatchAllocation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundTaskBatchAllocationMapper {
    int deleteByTaskLimited(@Param("houseId") Long houseId,
                            @Param("taskId") String taskId,
                            @Param("limit") int limit);

    int insertBatch(@Param("allocations") List<OutboundTaskBatchAllocation> allocations);

    List<OutboundTaskBatchAllocation> selectByTask(@Param("houseId") Long houseId,
                                                    @Param("taskId") String taskId);
}
