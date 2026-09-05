package com.rabbit.app.modules.sale.mapper;

import com.rabbit.app.modules.sale.entity.SaleOrderBatchAllocation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SaleOrderBatchAllocationMapper {
    int insertBatch(@Param("allocations") List<SaleOrderBatchAllocation> allocations);

}
