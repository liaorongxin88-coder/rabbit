package com.rabbit.app.modules.outbound.entity;

public class OutboundTaskItem {
    private String taskId;
    private Long rabbitId;
    private Long stateVersion;
    private String selectionType;
    private String earlySaleReason;
    private Long cageIdSnapshot;
    private String cageNumberSnapshot;
    private String rowCodeSnapshot;
    private Integer layerIndexSnapshot;
    private Integer positionIndexSnapshot;
    private String stageSnapshot;
    private Long batchIdSnapshot;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Long getRabbitId() { return rabbitId; }
    public void setRabbitId(Long rabbitId) { this.rabbitId = rabbitId; }
    public Long getStateVersion() { return stateVersion; }
    public void setStateVersion(Long stateVersion) { this.stateVersion = stateVersion; }
    public String getSelectionType() { return selectionType; }
    public void setSelectionType(String selectionType) { this.selectionType = selectionType; }
    public String getEarlySaleReason() { return earlySaleReason; }
    public void setEarlySaleReason(String earlySaleReason) { this.earlySaleReason = earlySaleReason; }
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
    public String getStageSnapshot() { return stageSnapshot; }
    public void setStageSnapshot(String stageSnapshot) { this.stageSnapshot = stageSnapshot; }
    public Long getBatchIdSnapshot() { return batchIdSnapshot; }
    public void setBatchIdSnapshot(Long batchIdSnapshot) { this.batchIdSnapshot = batchIdSnapshot; }
}
