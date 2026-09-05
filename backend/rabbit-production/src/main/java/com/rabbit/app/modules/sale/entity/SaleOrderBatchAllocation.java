package com.rabbit.app.modules.sale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class SaleOrderBatchAllocation {
    private Long id;
    private Long saleOrderId;
    private Long houseId;
    private Long batchId;
    private Integer rabbitCount;
    private BigDecimal actualWeightKg;
    private BigDecimal unitPricePerKg;
    private BigDecimal amount;
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSaleOrderId() { return saleOrderId; }
    public void setSaleOrderId(Long saleOrderId) { this.saleOrderId = saleOrderId; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getRabbitCount() { return rabbitCount; }
    public void setRabbitCount(Integer rabbitCount) { this.rabbitCount = rabbitCount; }
    public BigDecimal getActualWeightKg() { return actualWeightKg; }
    public void setActualWeightKg(BigDecimal actualWeightKg) { this.actualWeightKg = actualWeightKg; }
    public BigDecimal getUnitPricePerKg() { return unitPricePerKg; }
    public void setUnitPricePerKg(BigDecimal unitPricePerKg) { this.unitPricePerKg = unitPricePerKg; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
