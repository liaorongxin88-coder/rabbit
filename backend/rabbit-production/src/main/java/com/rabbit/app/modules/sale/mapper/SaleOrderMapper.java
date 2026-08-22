package com.rabbit.app.modules.sale.mapper;

import com.rabbit.app.modules.sale.entity.SaleOrder;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SaleOrderMapper {
    int insert(SaleOrder r);

    SaleOrder selectByReq(@Param("houseId") Long houseId, @Param("requestId") String requestId);

    List<SaleOrder> selectPageByHouse(@Param("houseId") Long houseId, @Param("offset") int offset, @Param("limit") int limit);

    SaleOrder selectById(@Param("houseId") Long houseId, @Param("id") Long id);
}
