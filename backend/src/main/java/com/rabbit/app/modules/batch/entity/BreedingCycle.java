package com.rabbit.app.modules.batch.entity;

import java.util.Date;

public class BreedingCycle {
    private Long id;
    /** V26 预留列，租户模型落地后回填。 */
    private Long tenantId;
    private Long houseId;
    private Long batchId;
    private Long motherRabbitId;
    private Long maleRabbitId;
    private Integer cycleNo;
    /** 旧中文状态列；V27 回填后由 {@link #stage} 取代，V28 删除。 */
    private String status;
    /** V26 新增：统一词汇的繁育阶段（DoeStage）。 */
    private String stage;
    /** 进入当前阶段的时间，支持「录入时已在该阶段 N 天」。 */
    private Date stageEnteredAt;
    /** OPEN / CLOSED；并发守卫生成列只认这一列。 */
    private String lifecycle;
    /** 周期关闭结果，仅 lifecycle=CLOSED 时非空。 */
    private String result;
    /** NATURAL 体配 / AI 人工授精。 */
    private String matingMethod;
    /** 乐观锁。 */
    private Long stateVersion;
    private Date matingDate;
    private Date pregnancyCheckDate;
    private String pregnancyResult;
    private Date expectedBirthDate;
    private Date birthDate;
    private Integer totalKits;
    private Integer liveKits;
    private Integer fosterInKits;
    private Integer fosterOutKits;
    private Integer currentNursingKits;
    private Integer weanedKits;
    private Integer preweaningLossKits;
    private Date weaningDate;
    private Double avgWeaningWeight;
    private Integer postpartumRematingDays;
    private Integer lactationDays;
    private Integer overlapLitterCycleNo;
    private Date overlapStartDate;
    private Date overlapEndDate;
    private Integer overlapDays;
    private Date nextEventDate;
    private String nextEventType;
    private Boolean isEventNotified;
    private Date eventNotifyDate;
    private Date closedAt;
    private String closeReason;
    private String requestId;
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Date getStageEnteredAt() {
        return stageEnteredAt;
    }

    public void setStageEnteredAt(Date stageEnteredAt) {
        this.stageEnteredAt = stageEnteredAt;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMatingMethod() {
        return matingMethod;
    }

    public void setMatingMethod(String matingMethod) {
        this.matingMethod = matingMethod;
    }

    public Long getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Long stateVersion) {
        this.stateVersion = stateVersion;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getMotherRabbitId() {
        return motherRabbitId;
    }

    public void setMotherRabbitId(Long motherRabbitId) {
        this.motherRabbitId = motherRabbitId;
    }

    public Long getMaleRabbitId() {
        return maleRabbitId;
    }

    public void setMaleRabbitId(Long maleRabbitId) {
        this.maleRabbitId = maleRabbitId;
    }

    public Integer getCycleNo() {
        return cycleNo;
    }

    public void setCycleNo(Integer cycleNo) {
        this.cycleNo = cycleNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getMatingDate() {
        return matingDate;
    }

    public void setMatingDate(Date matingDate) {
        this.matingDate = matingDate;
    }

    public Date getPregnancyCheckDate() {
        return pregnancyCheckDate;
    }

    public void setPregnancyCheckDate(Date pregnancyCheckDate) {
        this.pregnancyCheckDate = pregnancyCheckDate;
    }

    public String getPregnancyResult() {
        return pregnancyResult;
    }

    public void setPregnancyResult(String pregnancyResult) {
        this.pregnancyResult = pregnancyResult;
    }

    public Date getExpectedBirthDate() {
        return expectedBirthDate;
    }

    public void setExpectedBirthDate(Date expectedBirthDate) {
        this.expectedBirthDate = expectedBirthDate;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getTotalKits() {
        return totalKits;
    }

    public void setTotalKits(Integer totalKits) {
        this.totalKits = totalKits;
    }

    public Integer getLiveKits() {
        return liveKits;
    }

    public void setLiveKits(Integer liveKits) {
        this.liveKits = liveKits;
    }

    public Integer getFosterInKits() {
        return fosterInKits;
    }

    public void setFosterInKits(Integer fosterInKits) {
        this.fosterInKits = fosterInKits;
    }

    public Integer getFosterOutKits() {
        return fosterOutKits;
    }

    public void setFosterOutKits(Integer fosterOutKits) {
        this.fosterOutKits = fosterOutKits;
    }

    public Integer getCurrentNursingKits() {
        return currentNursingKits;
    }

    public void setCurrentNursingKits(Integer currentNursingKits) {
        this.currentNursingKits = currentNursingKits;
    }

    public Integer getWeanedKits() {
        return weanedKits;
    }

    public void setWeanedKits(Integer weanedKits) {
        this.weanedKits = weanedKits;
    }

    public Integer getPreweaningLossKits() {
        return preweaningLossKits;
    }

    public void setPreweaningLossKits(Integer preweaningLossKits) {
        this.preweaningLossKits = preweaningLossKits;
    }

    public Date getWeaningDate() {
        return weaningDate;
    }

    public void setWeaningDate(Date weaningDate) {
        this.weaningDate = weaningDate;
    }

    public Double getAvgWeaningWeight() {
        return avgWeaningWeight;
    }

    public void setAvgWeaningWeight(Double avgWeaningWeight) {
        this.avgWeaningWeight = avgWeaningWeight;
    }

    public Integer getPostpartumRematingDays() {
        return postpartumRematingDays;
    }

    public void setPostpartumRematingDays(Integer postpartumRematingDays) {
        this.postpartumRematingDays = postpartumRematingDays;
    }

    public Integer getLactationDays() {
        return lactationDays;
    }

    public void setLactationDays(Integer lactationDays) {
        this.lactationDays = lactationDays;
    }

    public Integer getOverlapLitterCycleNo() {
        return overlapLitterCycleNo;
    }

    public void setOverlapLitterCycleNo(Integer overlapLitterCycleNo) {
        this.overlapLitterCycleNo = overlapLitterCycleNo;
    }

    public Date getOverlapStartDate() {
        return overlapStartDate;
    }

    public void setOverlapStartDate(Date overlapStartDate) {
        this.overlapStartDate = overlapStartDate;
    }

    public Date getOverlapEndDate() {
        return overlapEndDate;
    }

    public void setOverlapEndDate(Date overlapEndDate) {
        this.overlapEndDate = overlapEndDate;
    }

    public Integer getOverlapDays() {
        return overlapDays;
    }

    public void setOverlapDays(Integer overlapDays) {
        this.overlapDays = overlapDays;
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

    public Date getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Date closedAt) {
        this.closedAt = closedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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
