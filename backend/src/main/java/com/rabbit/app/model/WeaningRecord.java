package com.rabbit.app.model;

import java.util.Date;

public class WeaningRecord {
    private Long id;
    private Long batchId;
    private Long rabbitId;
    private Long targetCageId;
    private String targetCageNumber;
    private Long inCageId;
    private String inCageNumber;
    private String allocSummary;
    private Date weaningDate;
    private Integer weaningCount;
    private Integer waitingCount;
    private Double avgWeight;
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getTargetCageId() {
        return targetCageId;
    }

    public void setTargetCageId(Long targetCageId) {
        this.targetCageId = targetCageId;
    }

    public String getTargetCageNumber() {
        return targetCageNumber;
    }

    public void setTargetCageNumber(String targetCageNumber) {
        this.targetCageNumber = targetCageNumber;
    }

    public Long getInCageId() {
        return inCageId;
    }

    public void setInCageId(Long inCageId) {
        this.inCageId = inCageId;
    }

    public String getInCageNumber() {
        return inCageNumber;
    }

    public void setInCageNumber(String inCageNumber) {
        this.inCageNumber = inCageNumber;
    }

    public String getAllocSummary() {
        return allocSummary;
    }

    public void setAllocSummary(String allocSummary) {
        this.allocSummary = allocSummary;
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

    public Integer getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(Integer waitingCount) {
        this.waitingCount = waitingCount;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
