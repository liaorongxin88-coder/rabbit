package com.rabbit.app.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

public class AckEventRequest {
    @NotBlank(message = "category不能为空")
    private String category;

    @NotNull(message = "recordId不能为空")
    private Long recordId;

    @NotBlank(message = "action不能为空")
    private String action;

    private Date snoozeUntil;

    private String remark;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Date getSnoozeUntil() {
        return snoozeUntil;
    }

    public void setSnoozeUntil(Date snoozeUntil) {
        this.snoozeUntil = snoozeUntil;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
