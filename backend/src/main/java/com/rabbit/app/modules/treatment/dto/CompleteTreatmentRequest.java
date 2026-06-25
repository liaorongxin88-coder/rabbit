package com.rabbit.app.modules.treatment.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Date;

public class CompleteTreatmentRequest {
    private Date completeTime;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Date getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(Date completeTime) {
        this.completeTime = completeTime;
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
