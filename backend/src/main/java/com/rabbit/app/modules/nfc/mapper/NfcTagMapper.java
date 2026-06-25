package com.rabbit.app.modules.nfc.mapper;

import com.rabbit.app.modules.nfc.dto.NfcTagView;
import com.rabbit.app.modules.nfc.entity.NfcTag;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NfcTagMapper {
    int upsert(@Param("item") NfcTag item);

    NfcTag selectByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);

    List<NfcTagView> selectViewPage(@Param("houseId") Long houseId,
                                   @Param("tagUid") String tagUid,
                                   @Param("targetType") String targetType,
                                   @Param("targetId") Long targetId,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int deleteByHouseAndUid(@Param("houseId") Long houseId, @Param("tagUid") String tagUid);
}
