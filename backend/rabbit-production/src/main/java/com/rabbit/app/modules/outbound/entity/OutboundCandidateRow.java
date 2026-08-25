package com.rabbit.app.modules.outbound.entity;

import java.util.Date;

public class OutboundCandidateRow {
    private Long rabbitId;
    private Long houseId;
    private Long cageId;
    private String cageNumber;
    private String rowCode;
    private Integer layerIndex;
    private Integer positionIndex;
    private Boolean cageEnabled;
    private String rabbitType;
    private String gender;
    private Double weight;
    private Boolean active;
    private Boolean quarantined;
    private Long stateVersion;
    private Long batchId;
    private String stage;
    private String growthStage;
    private Date nextEventDate;
    private String nextEventType;
    private Boolean saleReadyTask;
    private Boolean saleReadyTaskDue;
    private Boolean openTreatment;
    private Boolean unresolvedAbnormal;

    public Long getRabbitId() { return rabbitId; }
    public void setRabbitId(Long rabbitId) { this.rabbitId = rabbitId; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getCageId() { return cageId; }
    public void setCageId(Long cageId) { this.cageId = cageId; }
    public String getCageNumber() { return cageNumber; }
    public void setCageNumber(String cageNumber) { this.cageNumber = cageNumber; }
    public String getRowCode() { return rowCode; }
    public void setRowCode(String rowCode) { this.rowCode = rowCode; }
    public Integer getLayerIndex() { return layerIndex; }
    public void setLayerIndex(Integer layerIndex) { this.layerIndex = layerIndex; }
    public Integer getPositionIndex() { return positionIndex; }
    public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }
    public Boolean getCageEnabled() { return cageEnabled; }
    public void setCageEnabled(Boolean cageEnabled) { this.cageEnabled = cageEnabled; }
    public String getRabbitType() { return rabbitType; }
    public void setRabbitType(String rabbitType) { this.rabbitType = rabbitType; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Boolean getQuarantined() { return quarantined; }
    public void setQuarantined(Boolean quarantined) { this.quarantined = quarantined; }
    public Long getStateVersion() { return stateVersion; }
    public void setStateVersion(Long stateVersion) { this.stateVersion = stateVersion; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getGrowthStage() { return growthStage; }
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }
    public Date getNextEventDate() { return nextEventDate; }
    public void setNextEventDate(Date nextEventDate) { this.nextEventDate = nextEventDate; }
    public String getNextEventType() { return nextEventType; }
    public void setNextEventType(String nextEventType) { this.nextEventType = nextEventType; }
    public Boolean getSaleReadyTask() { return saleReadyTask; }
    public void setSaleReadyTask(Boolean saleReadyTask) { this.saleReadyTask = saleReadyTask; }
    public Boolean getSaleReadyTaskDue() { return saleReadyTaskDue; }
    public void setSaleReadyTaskDue(Boolean saleReadyTaskDue) { this.saleReadyTaskDue = saleReadyTaskDue; }
    public Boolean getOpenTreatment() { return openTreatment; }
    public void setOpenTreatment(Boolean openTreatment) { this.openTreatment = openTreatment; }
    public Boolean getUnresolvedAbnormal() { return unresolvedAbnormal; }
    public void setUnresolvedAbnormal(Boolean unresolvedAbnormal) { this.unresolvedAbnormal = unresolvedAbnormal; }
}
