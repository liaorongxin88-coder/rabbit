package com.rabbit.app.modules.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BindNfcCageRequest {
    @NotNull(message = "cageId不能为空")
    private Long cageId;

    @NotBlank(message = "tagUid不能为空")
    private String tagUid;

    @NotBlank(message = "payload不能为空")
    private String payload;

    private Boolean replaceExisting;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public String getTagUid() {
        return tagUid;
    }

    public void setTagUid(String tagUid) {
        this.tagUid = tagUid;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Boolean getReplaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(Boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
