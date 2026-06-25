package com.rabbit.app.modules.sale.mapper;

import com.rabbit.app.modules.sale.dto.SaleOrderItemView;
import com.rabbit.app.modules.sale.entity.SaleOrderItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SaleOrderItemMapper {
    int insertBatch(@Param("list") List<SaleOrderItem> list);

    List<SaleOrderItem> selectByOrder(@Param("saleOrderId") Long saleOrderId);

    List<SaleOrderItemView> selectViewByOrder(@Param("houseId") Long houseId, @Param("saleOrderId") Long saleOrderId);
}
