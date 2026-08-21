package com.rabbit.app.modules.batch.dto;

import java.util.Date;

public class BatchRabbitItem {
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
    private Boolean isActive;
    private Date joinDate;
    private Date exitDate;

    private String rabbitType;
    private String rabbitGender;
    private Long cageId;

    /**
     * 该批次标签下当前最先要处理的开放周期阶段（ReproStage 名，如 AWAIT_PALPATION）。
     * 母兔同时带多个批次标签时不得读取 rabbits 的跨批次全局投影，否则会把另一个批次的
     * 周期显示到本批次。为空表示这个标签下已经没有开放周期。
     */
    private String currentStage;

    /** 与 currentStage 对应、且属于本批次标签的开放周期 id。 */
    private Long currentCycleId;

    /** 下列字段全部限定在 batchId + rabbitId 关系内，不是母兔的跨批次总计。 */
    private Integer batchCycleCount;
    private Integer batchOperationCount;
    private Integer batchLitterCount;
    private Integer batchTotalKits;
    private Integer batchLiveKits;
    private Integer batchWeanedKits;
    private Date batchLastOperationAt;

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public Long getCurrentCycleId() {
        return currentCycleId;
    }

    public void setCurrentCycleId(Long currentCycleId) {
        this.currentCycleId = currentCycleId;
    }

    public Integer getBatchCycleCount() {
        return batchCycleCount;
    }

    public void setBatchCycleCount(Integer batchCycleCount) {
        this.batchCycleCount = batchCycleCount;
    }

    public Integer getBatchOperationCount() {
        return batchOperationCount;
    }

    public void setBatchOperationCount(Integer batchOperationCount) {
        this.batchOperationCount = batchOperationCount;
    }

    public Integer getBatchLitterCount() {
        return batchLitterCount;
    }

    public void setBatchLitterCount(Integer batchLitterCount) {
        this.batchLitterCount = batchLitterCount;
    }

    public Integer getBatchTotalKits() {
        return batchTotalKits;
    }

    public void setBatchTotalKits(Integer batchTotalKits) {
        this.batchTotalKits = batchTotalKits;
    }

    public Integer getBatchLiveKits() {
        return batchLiveKits;
    }

    public void setBatchLiveKits(Integer batchLiveKits) {
        this.batchLiveKits = batchLiveKits;
    }

    public Integer getBatchWeanedKits() {
        return batchWeanedKits;
    }

    public void setBatchWeanedKits(Integer batchWeanedKits) {
        this.batchWeanedKits = batchWeanedKits;
    }

    public Date getBatchLastOperationAt() {
        return batchLastOperationAt;
    }

    public void setBatchLastOperationAt(Date batchLastOperationAt) {
        this.batchLastOperationAt = batchLastOperationAt;
    }

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

    public String getRabbitType() {
        return rabbitType;
    }

    public void setRabbitType(String rabbitType) {
        this.rabbitType = rabbitType;
    }

    public String getRabbitGender() {
        return rabbitGender;
    }

    public void setRabbitGender(String rabbitGender) {
        this.rabbitGender = rabbitGender;
    }

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }
}
