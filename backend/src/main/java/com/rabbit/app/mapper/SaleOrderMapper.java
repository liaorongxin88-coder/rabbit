package com.rabbit.app.mapper;

import com.rabbit.app.model.SaleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SaleOrderMapper {
    int insert(SaleOrder r);

    SaleOrder selectByReq(@Param("houseId") Long houseId, @Param("requestId") String requestId);

    List<SaleOrder> selectPageByHouse(@Param("houseId") Long houseId, @Param("offset") int offset, @Param("limit") int limit);

    SaleOrder selectById(@Param("houseId") Long houseId, @Param("id") Long id);
}
