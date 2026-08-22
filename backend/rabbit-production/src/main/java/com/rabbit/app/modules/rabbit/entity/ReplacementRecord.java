package com.rabbit.app.modules.rabbit.entity;

import java.util.Date;

public class ReplacementRecord {
    private Long id;
    private Long houseId;
    private Long rabbitId;
    private String requestId;
    private String originalType;
    private Date replacementDate;
    private Date expectedMatureDate;
    private Boolean isMatureNotified;
    private Date matureNotifyDate;
    private String status;
    private Date promotedAt;
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

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getOriginalType() {
        return originalType;
    }

    public void setOriginalType(String originalType) {
        this.originalType = originalType;
    }

    public Date getReplacementDate() {
        return replacementDate;
    }

    public void setReplacementDate(Date replacementDate) {
        this.replacementDate = replacementDate;
    }

    public Date getExpectedMatureDate() {
        return expectedMatureDate;
    }

    public void setExpectedMatureDate(Date expectedMatureDate) {
        this.expectedMatureDate = expectedMatureDate;
    }

    public Boolean getIsMatureNotified() {
        return isMatureNotified;
    }

    public void setIsMatureNotified(Boolean matureNotified) {
        isMatureNotified = matureNotified;
    }

    public Date getMatureNotifyDate() {
        return matureNotifyDate;
    }

    public void setMatureNotifyDate(Date matureNotifyDate) {
        this.matureNotifyDate = matureNotifyDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(Date promotedAt) {
        this.promotedAt = promotedAt;
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
