package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class PrepartumRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    private Long breedingCycleId;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotNull(message = "actionDate不能为空")
    private Date actionDate;

    private String remark;

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getBreedingCycleId() {
        return breedingCycleId;
    }

    public void setBreedingCycleId(Long breedingCycleId) {
        this.breedingCycleId = breedingCycleId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Date getActionDate() {
        return actionDate;
    }

    public void setActionDate(Date actionDate) {
        this.actionDate = actionDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
