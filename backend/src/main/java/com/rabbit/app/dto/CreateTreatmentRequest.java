package com.rabbit.app.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.util.Date;

public class CreateTreatmentRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    @NotNull(message = "startDate不能为空")
    private Date startDate;

    private String diagnosis;
    private String drug;
    private String dose;
    private Integer days;
    private Date nextReviewDate;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getDrug() {
        return drug;
    }

    public void setDrug(String drug) {
        this.drug = drug;
    }

    public String getDose() {
        return dose;
    }

    public void setDose(String dose) {
        this.dose = dose;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public Date getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(Date nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
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
