package com.rabbit.app.modules.setting.mapper;

import com.rabbit.app.modules.setting.entity.GlobalSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GlobalSettingMapper {
    int insert(GlobalSetting setting);

    GlobalSetting selectByHouseId(@Param("houseId") Long houseId);

    int updateByHouse(GlobalSetting setting);
}
