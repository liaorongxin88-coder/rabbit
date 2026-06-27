package com.rabbit.app.modules.setting.dto;

import com.rabbit.app.modules.setting.entity.GlobalSetting;

public class HouseSettingResponse {
    private GlobalSetting setting;
    private Boolean customized;

    public static HouseSettingResponse of(GlobalSetting setting, boolean customized) {
        HouseSettingResponse response = new HouseSettingResponse();
        response.setSetting(setting);
        response.setCustomized(customized);
        return response;
    }

    public GlobalSetting getSetting() {
        return setting;
    }

    public void setSetting(GlobalSetting setting) {
        this.setting = setting;
    }

    public Boolean getCustomized() {
        return customized;
    }

    public void setCustomized(Boolean customized) {
        this.customized = customized;
    }
}
