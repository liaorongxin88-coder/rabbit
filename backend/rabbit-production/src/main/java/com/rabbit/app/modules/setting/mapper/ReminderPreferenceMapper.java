package com.rabbit.app.modules.setting.mapper;

import com.rabbit.app.modules.setting.entity.ReminderPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderPreferenceMapper {
    ReminderPreference selectByUserAndHouse(
        @Param("userId") Long userId,
        @Param("houseId") Long houseId
    );

    int insert(ReminderPreference preference);

    int update(ReminderPreference preference);
}
