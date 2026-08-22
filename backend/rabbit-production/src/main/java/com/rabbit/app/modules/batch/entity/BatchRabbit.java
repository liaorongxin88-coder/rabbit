package com.rabbit.app.modules.batch.entity;

import java.util.Date;

public class BatchRabbit {
    private Long id;
    private Long batchId;
    private Long rabbitId;
    private Long maleRabbitId;
    private Long latestCycleId;
    private Integer currentNursingKits;
    private Integer nursingLitterCount;
    private String joinReason;
    private String batchRole;
    private String currentStatus;
    private Date lastEventDate;
    private Date nextEventDate;
    private String nextEventType;
    private Boolean isEventNotified;
    private Date eventNotifyDate;
    private Boolean isActive;
    private Date joinDate;
    private Date exitDate;
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

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getMaleRabbitId() {
        return maleRabbitId;
    }

    public void setMaleRabbitId(Long maleRabbitId) {
        this.maleRabbitId = maleRabbitId;
    }

    public Long getLatestCycleId() {
        return latestCycleId;
    }

    public void setLatestCycleId(Long latestCycleId) {
        this.latestCycleId = latestCycleId;
    }

    public Integer getCurrentNursingKits() {
        return currentNursingKits;
    }

    public void setCurrentNursingKits(Integer currentNursingKits) {
        this.currentNursingKits = currentNursingKits;
    }

    public Integer getNursingLitterCount() {
        return nursingLitterCount;
    }

    public void setNursingLitterCount(Integer nursingLitterCount) {
        this.nursingLitterCount = nursingLitterCount;
    }

    public String getJoinReason() {
        return joinReason;
    }

    public void setJoinReason(String joinReason) {
        this.joinReason = joinReason;
    }

    public String getBatchRole() {
        return batchRole;
    }

    public void setBatchRole(String batchRole) {
        this.batchRole = batchRole;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Date getLastEventDate() {
        return lastEventDate;
    }

    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
    }

    public Date getNextEventDate() {
        return nextEventDate;
    }

    public void setNextEventDate(Date nextEventDate) {
        this.nextEventDate = nextEventDate;
    }

    public String getNextEventType() {
        return nextEventType;
    }

    public void setNextEventType(String nextEventType) {
        this.nextEventType = nextEventType;
    }

    public Boolean getIsEventNotified() {
        return isEventNotified;
    }

    public void setIsEventNotified(Boolean eventNotified) {
        isEventNotified = eventNotified;
    }

    public Date getEventNotifyDate() {
        return eventNotifyDate;
    }

    public void setEventNotifyDate(Date eventNotifyDate) {
        this.eventNotifyDate = eventNotifyDate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Date getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(Date joinDate) {
        this.joinDate = joinDate;
    }

    public Date getExitDate() {
        return exitDate;
    }

    public void setExitDate(Date exitDate) {
        this.exitDate = exitDate;
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
