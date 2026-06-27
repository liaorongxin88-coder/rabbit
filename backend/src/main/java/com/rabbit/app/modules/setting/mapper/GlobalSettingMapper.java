package com.rabbit.app.modules.setting.mapper;

import com.rabbit.app.modules.setting.entity.GlobalSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GlobalSettingMapper {
    int insert(GlobalSetting setting);

    GlobalSetting selectByUserId(@Param("userId") Long userId);

    GlobalSetting selectFirstByUserHouse(@Param("userId") Long userId);

    GlobalSetting selectByHouseId(@Param("houseId") Long houseId);

    int updateByUser(GlobalSetting setting);

    int updateByHouse(GlobalSetting setting);
}
