package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class BatchCarcassYieldRequest {
    @NotNull(message = "yieldRate不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "yieldRate必须大于0")
    @DecimalMax(value = "1", message = "yieldRate不能大于1")
    private BigDecimal yieldRate;

    @NotBlank(message = "sourceUnit不能为空")
    @Size(max = 100, message = "sourceUnit不能超过100个字符")
    private String sourceUnit;

    @NotNull(message = "measuredDate不能为空")
    private LocalDate measuredDate;

    @Size(max = 100, message = "reportNumber不能超过100个字符")
    private String reportNumber;

    @Size(max = 64, message = "evidenceFileId不能超过64个字符")
    private String evidenceFileId;

    @Size(max = 2000, message = "remark不能超过2000个字符")
    private String remark;

    @NotBlank(message = "changeReason不能为空")
    @Size(max = 300, message = "changeReason不能超过300个字符")
    private String changeReason;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    private String requestId;

    public BigDecimal getYieldRate() { return yieldRate; }
    public void setYieldRate(BigDecimal yieldRate) { this.yieldRate = yieldRate; }
    public String getSourceUnit() { return sourceUnit; }
    public void setSourceUnit(String sourceUnit) { this.sourceUnit = sourceUnit; }
    public LocalDate getMeasuredDate() { return measuredDate; }
    public void setMeasuredDate(LocalDate measuredDate) { this.measuredDate = measuredDate; }
    public String getReportNumber() { return reportNumber; }
    public void setReportNumber(String reportNumber) { this.reportNumber = reportNumber; }
    public String getEvidenceFileId() { return evidenceFileId; }
    public void setEvidenceFileId(String evidenceFileId) { this.evidenceFileId = evidenceFileId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
