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
     * 母兔当前所处的生产阶段（ReproStage 名，如 AWAIT_PALPATION），取自 rabbits 的投影列。
     * 与 currentStatus 的区别是：currentStatus 是旧写路径留下的中文快照，旧写路径删除后不再更新；
     * 这一列由生产状态机在推进周期的同一事务里维护，是权威现状。为空表示该母兔尚未入轨。
     */
    private String currentStage;

    /** 母兔当前进行中的周期 id。客户端提交生产动作时需要它。 */
    private Long currentCycleId;

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
