package com.rabbit.app.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class BindCageNfcTagRequest {
    @NotNull(message = "cageId不能为空")
    private Long cageId;

    @NotBlank(message = "tagUid不能为空")
    private String tagUid;

    private String remark;

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

