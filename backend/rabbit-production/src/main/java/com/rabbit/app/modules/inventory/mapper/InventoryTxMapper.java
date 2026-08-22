package com.rabbit.app.modules.inventory.mapper;

import com.rabbit.app.modules.inventory.entity.InventoryTx;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
