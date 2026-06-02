package com.rabbit.app.mapper;

import com.rabbit.app.model.InventoryTx;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface InventoryTxMapper {
    int insert(InventoryTx r);

    List<InventoryTx> selectPageByItem(@Param("houseId") Long houseId, @Param("itemId") Long itemId, @Param("offset") int offset, @Param("limit") int limit);

    List<InventoryTx> selectExportPage(@Param("houseId") Long houseId,
                                      @Param("itemId") Long itemId,
                                      @Param("from") Date from,
                                      @Param("to") Date to,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);
}
