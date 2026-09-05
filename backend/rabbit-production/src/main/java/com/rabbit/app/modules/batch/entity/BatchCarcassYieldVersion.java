package com.rabbit.app.modules.batch.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

public class BatchCarcassYieldVersion {
    private Long id;
    private Long houseId;
    private Long batchId;
    private BigDecimal yieldRate;
    private String sourceUnit;
    private LocalDate measuredDate;
    private String reportNumber;
    private String evidenceFile;
    private String remark;
    private String changeReason;
    private String requestId;
    private String payloadHash;
    private Long createdBy;
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public BigDecimal getYieldRate() { return yieldRate; }
    public void setYieldRate(BigDecimal yieldRate) { this.yieldRate = yieldRate; }
    public String getSourceUnit() { return sourceUnit; }
    public void setSourceUnit(String sourceUnit) { this.sourceUnit = sourceUnit; }
    public LocalDate getMeasuredDate() { return measuredDate; }
    public void setMeasuredDate(LocalDate measuredDate) { this.measuredDate = measuredDate; }
    public String getReportNumber() { return reportNumber; }
    public void setReportNumber(String reportNumber) { this.reportNumber = reportNumber; }
    public String getEvidenceFile() { return evidenceFile; }
    public void setEvidenceFile(String evidenceFile) { this.evidenceFile = evidenceFile; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
