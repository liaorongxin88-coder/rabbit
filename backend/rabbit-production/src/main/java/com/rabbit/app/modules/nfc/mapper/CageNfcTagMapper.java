package com.rabbit.app.modules.nfc.mapper;

import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.nfc.entity.CageNfcTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CageNfcTagMapper {
    int upsert(@Param("item") CageNfcTag item);

    CageNfcTag selectByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);

    CageNfcTag selectByHouseAndCage(@Param("houseId") Long houseId, @Param("cageId") Long cageId);

    Cage selectCageByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);

    int deleteByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);

    int deleteByHouseAndCage(@Param("houseId") Long houseId, @Param("cageId") Long cageId);
}
