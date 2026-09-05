package com.rabbit.app.modules.feed.entity;

import java.math.BigDecimal;
import java.util.Date;

public class FeedLogBatchAllocation {
    private Long id;
    private Long feedLogId;
    private Long houseId;
    private Long batchId;
    private String phase;
    private BigDecimal amountKg;
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFeedLogId() { return feedLogId; }
    public void setFeedLogId(Long feedLogId) { this.feedLogId = feedLogId; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public BigDecimal getAmountKg() { return amountKg; }
    public void setAmountKg(BigDecimal amountKg) { this.amountKg = amountKg; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
