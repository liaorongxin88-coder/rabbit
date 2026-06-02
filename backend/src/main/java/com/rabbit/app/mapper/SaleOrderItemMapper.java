package com.rabbit.app.mapper;

import com.rabbit.app.dto.SaleOrderItemView;
import com.rabbit.app.model.SaleOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SaleOrderItemMapper {
    int insertBatch(@Param("list") List<SaleOrderItem> list);

    List<SaleOrderItem> selectByOrder(@Param("saleOrderId") Long saleOrderId);

    List<SaleOrderItemView> selectViewByOrder(@Param("houseId") Long houseId, @Param("saleOrderId") Long saleOrderId);
}
