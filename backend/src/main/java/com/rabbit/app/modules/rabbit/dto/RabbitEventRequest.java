package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class RabbitEventRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    @NotBlank(message = "eventType不能为空")
    private String eventType;

    @NotNull(message = "actionDate不能为空")
    private Date actionDate;

    private String reason;
    private String remark;
    private Boolean forceExitBatch;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Date getActionDate() {
        return actionDate;
    }

    public void setActionDate(Date actionDate) {
        this.actionDate = actionDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Boolean getForceExitBatch() {
        return forceExitBatch;
    }

    public void setForceExitBatch(Boolean forceExitBatch) {
        this.forceExitBatch = forceExitBatch;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
