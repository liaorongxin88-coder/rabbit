package com.rabbit.app.modules.outbound.entity;

import java.math.BigDecimal;
import java.util.Date;

public class OutboundTask {
    private String taskId;
    private Long houseId;
    private Long operatorId;
    private String entryType;
    private Long sourceRabbitId;
    private Long sourceCageId;
    private String sourceRowCode;
    private String status;
    private Long revision;
    private Date saleTime;
    private Double totalWeight;
    private BigDecimal unitPrice;
    private String customer;
    private String remark;
    private String requestId;
    private Long saleOrderId;
    private Date snapshotTime;
    private Date completedTime;
    private Date createTime;
    private Date updateTime;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public Long getSourceRabbitId() { return sourceRabbitId; }
    public void setSourceRabbitId(Long sourceRabbitId) { this.sourceRabbitId = sourceRabbitId; }
    public Long getSourceCageId() { return sourceCageId; }
    public void setSourceCageId(Long sourceCageId) { this.sourceCageId = sourceCageId; }
    public String getSourceRowCode() { return sourceRowCode; }
    public void setSourceRowCode(String sourceRowCode) { this.sourceRowCode = sourceRowCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public Date getSaleTime() { return saleTime; }
    public void setSaleTime(Date saleTime) { this.saleTime = saleTime; }
    public Double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(Double totalWeight) { this.totalWeight = totalWeight; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getSaleOrderId() { return saleOrderId; }
    public void setSaleOrderId(Long saleOrderId) { this.saleOrderId = saleOrderId; }
    public Date getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Date snapshotTime) { this.snapshotTime = snapshotTime; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
