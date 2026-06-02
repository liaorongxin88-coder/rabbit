package com.rabbit.app.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class UpdateSettingRequest {
    @NotNull
    @Min(0)
    private Integer aphrodisiacDays;

    @NotNull
    @Min(0)
    private Integer palpationDays;

    @NotNull
    @Min(0)
    private Integer prepartumDays;

    @NotNull
    @Min(0)
    private Integer weaningDays;

    @NotNull
    @Min(0)
    private Integer postpartumDays;

    @NotNull
    @Min(0)
    private Integer saleDays;

    @NotNull
    @Min(0)
    private Integer replacementDays;

    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Integer getAphrodisiacDays() {
        return aphrodisiacDays;
    }

    public void setAphrodisiacDays(Integer aphrodisiacDays) {
        this.aphrodisiacDays = aphrodisiacDays;
    }

    public Integer getPalpationDays() {
        return palpationDays;
    }

    public void setPalpationDays(Integer palpationDays) {
        this.palpationDays = palpationDays;
    }

    public Integer getPrepartumDays() {
        return prepartumDays;
    }

    public void setPrepartumDays(Integer prepartumDays) {
        this.prepartumDays = prepartumDays;
    }

    public Integer getWeaningDays() {
        return weaningDays;
    }

    public void setWeaningDays(Integer weaningDays) {
        this.weaningDays = weaningDays;
    }

    public Integer getPostpartumDays() {
        return postpartumDays;
    }

    public void setPostpartumDays(Integer postpartumDays) {
        this.postpartumDays = postpartumDays;
    }

    public Integer getSaleDays() {
        return saleDays;
    }

    public void setSaleDays(Integer saleDays) {
        this.saleDays = saleDays;
    }

    public Integer getReplacementDays() {
        return replacementDays;
    }

    public void setReplacementDays(Integer replacementDays) {
        this.replacementDays = replacementDays;
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
