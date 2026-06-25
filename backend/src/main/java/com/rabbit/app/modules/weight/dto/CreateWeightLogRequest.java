package com.rabbit.app.modules.weight.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;

public class CreateWeightLogRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    @NotNull(message = "weighTime不能为空")
    private Date weighTime;

    @NotNull(message = "weightKg不能为空")
    private Double weightKg;

    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Date getWeighTime() {
        return weighTime;
    }

    public void setWeighTime(Date weighTime) {
        this.weighTime = weighTime;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
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
