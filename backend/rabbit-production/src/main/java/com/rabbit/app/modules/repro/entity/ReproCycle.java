package com.rabbit.app.modules.repro.entity;

import java.util.Date;

/**
 * breeding_cycles 的 V2 视图（设计 §4.2）。
 *
 * <p>刻意与 batch 模块的 {@code BreedingCycle} 并存映射同一张表：两者是同一行的不同投影，
 * 旧实体保留给旧写路径，字节级不变，P3 前生产行为零变化；V28 退役 batch 模块后只剩本实体。
 *
 * <p>其中 {@code status} 及 kit 计数器属于兼容双写字段——新逻辑一律不读它们，
 * 只为让未升级的老 APK 仍能正确渲染而维护，V28 随旧列一并删除。
 */
public class ReproCycle {
    private Long id;
    private Long tenantId;
    private Long houseId;
    /** 正式生产批次；从配种进入待摸胎时才绑定。 */
    private Long batchId;
    /** 休养、催情或待配种阶段的新入栏计划批次，不参与周期约束。 */
    private Long plannedBatchId;
    private Long motherRabbitId;
    private Long maleRabbitId;
    private Integer cycleNo;
    private String stage;
    private Date stageEnteredAt;
    private String lifecycle;
    private String result;
    private String matingMethod;
    private Long stateVersion;
    private Date matingDate;
    private Date pregnancyCheckDate;
    private String pregnancyResult;
    /** 预产期参考值，默认由配种日加 30 天得出。 */
    private Date expectedBirthDate;
    private Date birthDate;
    private Date weaningDate;
    private Date closedAt;
    private String closeReason;
    private String requestId;
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    /** 兼容双写：权威值在 litters。 */
    private Integer totalKits;
    /** 兼容双写：权威值在 litters。 */
    private Integer liveKits;
    /** 兼容双写：权威值在 litters。 */
    private Integer currentNursingKits;
    /** 兼容双写：权威值在 litters。 */
    private Integer weanedKits;
    /** 兼容双写：权威值在 litters。 */
    private Double avgWeaningWeight;

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

    public Long getPlannedBatchId() {
        return plannedBatchId;
    }

    public void setPlannedBatchId(Long plannedBatchId) {
        this.plannedBatchId = plannedBatchId;
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

    public Date getWeaningDate() {
        return weaningDate;
    }

    public void setWeaningDate(Date weaningDate) {
        this.weaningDate = weaningDate;
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

    public Double getAvgWeaningWeight() {
        return avgWeaningWeight;
    }

    public void setAvgWeaningWeight(Double avgWeaningWeight) {
        this.avgWeaningWeight = avgWeaningWeight;
    }




}
