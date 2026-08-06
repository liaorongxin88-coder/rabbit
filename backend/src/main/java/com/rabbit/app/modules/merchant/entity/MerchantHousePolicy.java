package com.rabbit.app.modules.merchant.entity;

import java.util.Date;

public class MerchantHousePolicy {
    private Long merchantId;
    private Boolean houseCreationEnabled;
    private Boolean houseMemberManagementEnabled;
    private Integer maxHouseCount;
    private Integer maxMembersPerHouse;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Boolean getHouseCreationEnabled() {
        return houseCreationEnabled;
    }

    public void setHouseCreationEnabled(Boolean houseCreationEnabled) {
        this.houseCreationEnabled = houseCreationEnabled;
    }

    public Boolean getHouseMemberManagementEnabled() {
        return houseMemberManagementEnabled;
    }

    public void setHouseMemberManagementEnabled(Boolean houseMemberManagementEnabled) {
        this.houseMemberManagementEnabled = houseMemberManagementEnabled;
    }

    public Integer getMaxHouseCount() {
        return maxHouseCount;
    }

    public void setMaxHouseCount(Integer maxHouseCount) {
        this.maxHouseCount = maxHouseCount;
    }

    public Integer getMaxMembersPerHouse() {
        return maxMembersPerHouse;
    }

    public void setMaxMembersPerHouse(Integer maxMembersPerHouse) {
        this.maxMembersPerHouse = maxMembersPerHouse;
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
