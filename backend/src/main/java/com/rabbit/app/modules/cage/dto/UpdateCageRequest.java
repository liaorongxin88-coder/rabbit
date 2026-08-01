package com.rabbit.app.modules.cage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateCageRequest {
    @NotBlank(message = "笼位编号不能为空")
    @Size(max = 50, message = "笼位编号过长")
    private String cageNumber;

    private Boolean isEnabled;

    @Size(max = 40, message = "排号过长")
    private String rowCode;
    private Integer layerIndex;
    private Integer positionIndex;

    private String remark;

    public String getCageNumber() {
        return cageNumber;
    }

    public void setCageNumber(String cageNumber) {
        this.cageNumber = cageNumber;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean enabled) {
        isEnabled = enabled;
    }

    public String getRowCode() { return rowCode; }
    public void setRowCode(String rowCode) { this.rowCode = rowCode; }
    public Integer getLayerIndex() { return layerIndex; }
    public void setLayerIndex(Integer layerIndex) { this.layerIndex = layerIndex; }
    public Integer getPositionIndex() { return positionIndex; }
    public void setPositionIndex(Integer positionIndex) { this.positionIndex = positionIndex; }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
