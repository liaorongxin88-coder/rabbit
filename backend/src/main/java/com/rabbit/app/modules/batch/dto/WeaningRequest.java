package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class WeaningRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    private Long breedingCycleId;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotNull(message = "weaningDate不能为空")
    private Date weaningDate;

    @NotNull(message = "weaningCount不能为空")
    @Min(value = 0, message = "weaningCount不能小于0")
    private Integer weaningCount;

    @Min(value = 0, message = "maleCount不能小于0")
    private Integer maleCount;

    @Min(value = 0, message = "femaleCount不能小于0")
    private Integer femaleCount;

    private Long targetCageId;

    private Double avgWeight;
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

    public Date getWeaningDate() {
        return weaningDate;
    }

    public void setWeaningDate(Date weaningDate) {
        this.weaningDate = weaningDate;
    }

    public Integer getWeaningCount() {
        return weaningCount;
    }

    public void setWeaningCount(Integer weaningCount) {
        this.weaningCount = weaningCount;
    }

    public Integer getMaleCount() {
        return maleCount;
    }

    public void setMaleCount(Integer maleCount) {
        this.maleCount = maleCount;
    }

    public Integer getFemaleCount() {
        return femaleCount;
    }

    public void setFemaleCount(Integer femaleCount) {
        this.femaleCount = femaleCount;
    }

    public Long getTargetCageId() {
        return targetCageId;
    }

    public void setTargetCageId(Long targetCageId) {
        this.targetCageId = targetCageId;
    }

    public Double getAvgWeight() {
        return avgWeight;
    }

    public void setAvgWeight(Double avgWeight) {
        this.avgWeight = avgWeight;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
