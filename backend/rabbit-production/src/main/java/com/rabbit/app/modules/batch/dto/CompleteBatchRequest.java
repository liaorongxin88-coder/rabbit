package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Date;

public class CompleteBatchRequest {
    private Date endDate;
    private Boolean force;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
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
