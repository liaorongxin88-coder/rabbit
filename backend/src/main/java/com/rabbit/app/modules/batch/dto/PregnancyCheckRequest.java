package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class PregnancyCheckRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    private Long breedingCycleId;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotNull(message = "checkDate不能为空")
    private Date checkDate;

    @NotBlank(message = "result不能为空")
    private String result;

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

    public Date getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(Date checkDate) {
        this.checkDate = checkDate;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
