package com.rabbit.app.modules.rabbit.mapper;

import com.rabbit.app.modules.rabbit.entity.ReplacementBatchAllocation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReplacementBatchAllocationMapper {
    int insertBatch(@Param("allocations") List<ReplacementBatchAllocation> allocations);

}
