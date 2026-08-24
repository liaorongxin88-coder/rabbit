package com.rabbit.app.modules.apprelease.dto;

import jakarta.validation.constraints.Size;

public class UpdateAppReleaseRequest {
    @Size(max = 2000, message = "更新说明不能超过2000个字符")
    private String releaseNotes;

    private Boolean forceUpdate;

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public Boolean getForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(Boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }
}
