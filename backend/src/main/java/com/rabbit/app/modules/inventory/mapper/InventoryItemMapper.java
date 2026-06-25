package com.rabbit.app.modules.inventory.mapper;

import com.rabbit.app.modules.inventory.entity.InventoryItem;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryItemMapper {
    int insert(InventoryItem r);

    InventoryItem selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    InventoryItem selectByHouseAndName(@Param("houseId") Long houseId, @Param("name") String name);

    List<InventoryItem> selectByHouse(@Param("houseId") Long houseId);

    int updateQtyDelta(@Param("houseId") Long houseId,
                       @Param("id") Long id,
                       @Param("delta") BigDecimal delta,
                       @Param("updateBy") String updateBy);

    int updateQtyDeltaIfCurrent(@Param("houseId") Long houseId,
                               @Param("id") Long id,
                               @Param("delta") BigDecimal delta,
                               @Param("expectedQty") BigDecimal expectedQty,
                               @Param("forbidNegative") boolean forbidNegative,
                               @Param("updateBy") String updateBy);
}
