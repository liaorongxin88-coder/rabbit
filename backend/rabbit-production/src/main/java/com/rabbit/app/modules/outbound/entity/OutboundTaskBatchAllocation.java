package com.rabbit.app.modules.outbound.entity;

import java.math.BigDecimal;
import java.util.Date;

public class OutboundTaskBatchAllocation {
    private Long id;
    private String taskId;
    private Long houseId;
    private Long batchId;
    private BigDecimal actualWeightKg;
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public BigDecimal getActualWeightKg() { return actualWeightKg; }
    public void setActualWeightKg(BigDecimal actualWeightKg) { this.actualWeightKg = actualWeightKg; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
