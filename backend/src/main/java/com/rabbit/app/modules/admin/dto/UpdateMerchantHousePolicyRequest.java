package com.rabbit.app.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateMerchantHousePolicyRequest {
    @NotNull(message = "建场权限不能为空")
    private Boolean houseCreationEnabled;

    @NotNull(message = "成员管理权限不能为空")
    private Boolean houseMemberManagementEnabled;

    @NotNull(message = "兔场上限不能为空")
    @Min(value = 1, message = "兔场上限不能小于1")
    @Max(value = 1000, message = "兔场上限不能超过1000")
    private Integer maxHouseCount;

    @NotNull(message = "单兔场成员上限不能为空")
    @Min(value = 1, message = "单兔场成员上限不能小于1")
    @Max(value = 500, message = "单兔场成员上限不能超过500")
    private Integer maxMembersPerHouse;

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
}
