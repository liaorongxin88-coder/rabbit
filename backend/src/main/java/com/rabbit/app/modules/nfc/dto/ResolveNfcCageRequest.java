package com.rabbit.app.modules.nfc.dto;

import jakarta.validation.constraints.NotBlank;

public class ResolveNfcCageRequest {
    @NotBlank(message = "payload不能为空")
    private String payload;

    @NotBlank(message = "tagUid不能为空")
    private String tagUid;

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTagUid() {
        return tagUid;
    }

    public void setTagUid(String tagUid) {
        this.tagUid = tagUid;
    }
}
