package com.rabbit.app.modules.rabbit.entity;

import java.math.BigDecimal;
import java.util.Date;

public class ReplacementBatchAllocation {
    private Long id;
    private Long houseId;
    private String requestId;
    private Long sourceBatchId;
    private Integer rabbitCount;
    private BigDecimal totalWeightKg;
    private Long createdBy;
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getSourceBatchId() { return sourceBatchId; }
    public void setSourceBatchId(Long sourceBatchId) { this.sourceBatchId = sourceBatchId; }
    public Integer getRabbitCount() { return rabbitCount; }
    public void setRabbitCount(Integer rabbitCount) { this.rabbitCount = rabbitCount; }
    public BigDecimal getTotalWeightKg() { return totalWeightKg; }
    public void setTotalWeightKg(BigDecimal totalWeightKg) { this.totalWeightKg = totalWeightKg; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
