package com.rabbit.app.mapper;

import com.rabbit.app.model.Cage;
import com.rabbit.app.model.CageNfcTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CageNfcTagMapper {
    int upsert(@Param("item") CageNfcTag item);

    CageNfcTag selectByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);

    Cage selectCageByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);

    int deleteByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);
}
