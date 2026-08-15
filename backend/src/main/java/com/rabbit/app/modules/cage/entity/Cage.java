package com.rabbit.app.modules.cage.entity;

import java.util.Date;

public class Cage {
    private Long id;
    private Long houseId;
    private String cageNumber;
    private String rowCode;
    private Integer layerIndex;
    private Integer positionIndex;
    private String status;
    private Integer rabbitCount;
    private String breedingOccupantGender;
    private Boolean isFed;
    private Boolean isEnabled;
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

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public String getCageNumber() {
        return cageNumber;
    }

    public void setCageNumber(String cageNumber) {
        this.cageNumber = cageNumber;
    }

    public String getRowCode() {
        return rowCode;
    }

    public void setRowCode(String rowCode) {
        this.rowCode = rowCode;
    }

    public Integer getLayerIndex() {
        return layerIndex;
    }

    public void setLayerIndex(Integer layerIndex) {
        this.layerIndex = layerIndex;
    }

    public Integer getPositionIndex() {
        return positionIndex;
    }

    public void setPositionIndex(Integer positionIndex) {
        this.positionIndex = positionIndex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRabbitCount() {
        return rabbitCount;
    }

    public void setRabbitCount(Integer rabbitCount) {
        this.rabbitCount = rabbitCount;
    }

    public String getBreedingOccupantGender() {
        return breedingOccupantGender;
    }

    public void setBreedingOccupantGender(String breedingOccupantGender) {
        this.breedingOccupantGender = breedingOccupantGender;
    }

    public Boolean getIsFed() {
        return isFed;
    }

    public void setIsFed(Boolean fed) {
        isFed = fed;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean enabled) {
        isEnabled = enabled;
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
