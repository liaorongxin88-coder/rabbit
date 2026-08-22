package com.rabbit.app.modules.sale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class SaleOrderItem {
    private Long id;
    private Long saleOrderId;
    private Long rabbitId;
    private Long cageIdSnapshot;
    private String cageNumberSnapshot;
    private String rowCodeSnapshot;
    private Integer layerIndexSnapshot;
    private Integer positionIndexSnapshot;
    private String rabbitTypeSnapshot;
    private String stageSnapshot;
    private String parallelStatusSnapshot;
    private Long stateVersionSnapshot;
    private Boolean earlySale;
    private String earlySaleReason;
    private Long batchIdSnapshot;
    private Double weight;
    private BigDecimal price;
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

    public Long getSaleOrderId() {
        return saleOrderId;
    }

    public void setSaleOrderId(Long saleOrderId) {
        this.saleOrderId = saleOrderId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getCageIdSnapshot() { return cageIdSnapshot; }
    public void setCageIdSnapshot(Long cageIdSnapshot) { this.cageIdSnapshot = cageIdSnapshot; }
    public String getCageNumberSnapshot() { return cageNumberSnapshot; }
    public void setCageNumberSnapshot(String cageNumberSnapshot) { this.cageNumberSnapshot = cageNumberSnapshot; }
    public String getRowCodeSnapshot() { return rowCodeSnapshot; }
    public void setRowCodeSnapshot(String rowCodeSnapshot) { this.rowCodeSnapshot = rowCodeSnapshot; }
    public Integer getLayerIndexSnapshot() { return layerIndexSnapshot; }
    public void setLayerIndexSnapshot(Integer layerIndexSnapshot) { this.layerIndexSnapshot = layerIndexSnapshot; }
    public Integer getPositionIndexSnapshot() { return positionIndexSnapshot; }
    public void setPositionIndexSnapshot(Integer positionIndexSnapshot) { this.positionIndexSnapshot = positionIndexSnapshot; }
    public String getRabbitTypeSnapshot() { return rabbitTypeSnapshot; }
    public void setRabbitTypeSnapshot(String rabbitTypeSnapshot) { this.rabbitTypeSnapshot = rabbitTypeSnapshot; }
    public String getStageSnapshot() { return stageSnapshot; }
    public void setStageSnapshot(String stageSnapshot) { this.stageSnapshot = stageSnapshot; }
    public String getParallelStatusSnapshot() { return parallelStatusSnapshot; }
    public void setParallelStatusSnapshot(String parallelStatusSnapshot) { this.parallelStatusSnapshot = parallelStatusSnapshot; }
    public Long getStateVersionSnapshot() { return stateVersionSnapshot; }
    public void setStateVersionSnapshot(Long stateVersionSnapshot) { this.stateVersionSnapshot = stateVersionSnapshot; }
    public Boolean getEarlySale() { return earlySale; }
    public void setEarlySale(Boolean earlySale) { this.earlySale = earlySale; }
    public String getEarlySaleReason() { return earlySaleReason; }
    public void setEarlySaleReason(String earlySaleReason) { this.earlySaleReason = earlySaleReason; }
    public Long getBatchIdSnapshot() { return batchIdSnapshot; }
    public void setBatchIdSnapshot(Long batchIdSnapshot) { this.batchIdSnapshot = batchIdSnapshot; }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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
