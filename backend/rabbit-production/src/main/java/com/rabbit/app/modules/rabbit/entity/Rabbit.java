package com.rabbit.app.modules.rabbit.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public class Rabbit {
    private Long id;
    private Long houseId;
    private Long cageId;
    private Long motherId;
    private Long fatherId;
    private Long birthBatchId;
    private Long birthCycleId;
    private String type;
    private String gender;
    private String breed;
    private String arrivalMethod;
    private String sourceSeller;
    private Date arrivalDate;
    private Double weight;
    private String growthStage;
    private Date growthStageEnteredAt;
    /** 旧繁育阶段列；V26 起由 {@link #currentStage} 取代，V28 删除。 */
    private String reproductiveStage;
    /**
     * V26 新增：统一词汇的当前阶段投影。
     *
     * <p>写者只有状态机服务一个，且与周期变更同事务写入。旧模型里同一事实有三个写点
     * （rabbits.reproductive_stage / batch_rabbits.current_status / breeding_cycles.status），
     * 靠手工调 syncBreedingSummary() 对齐，漏调即漂移——飞书 recvsrp9E2dqvB 的根因。
     */
    private String currentStage;
    private Long currentCycleId;
    private Date stageEnteredAt;
    /** 种公兔专用（录入需求）。 */
    private Date lastMatingDate;
    private Long stateVersion;
    private Boolean isActive;
    private Boolean isQuarantined;
    private Date quarantineTime;
    private String quarantineReason;
    private String requestId;
    private Date departureDate;
    private String departureReason;
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

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Shanghai")
    public Date getStageEnteredAt() {
        return stageEnteredAt;
    }

    public void setStageEnteredAt(Date stageEnteredAt) {
        this.stageEnteredAt = stageEnteredAt;
    }

    public Date getLastMatingDate() {
        return lastMatingDate;
    }

    public void setLastMatingDate(Date lastMatingDate) {
        this.lastMatingDate = lastMatingDate;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Long getMotherId() {
        return motherId;
    }

    public void setMotherId(Long motherId) {
        this.motherId = motherId;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public void setFatherId(Long fatherId) {
        this.fatherId = fatherId;
    }

    public Long getBirthBatchId() {
        return birthBatchId;
    }

    public void setBirthBatchId(Long birthBatchId) {
        this.birthBatchId = birthBatchId;
    }

    public Long getBirthCycleId() {
        return birthCycleId;
    }

    public void setBirthCycleId(Long birthCycleId) {
        this.birthCycleId = birthCycleId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getArrivalMethod() {
        return arrivalMethod;
    }

    public void setArrivalMethod(String arrivalMethod) {
        this.arrivalMethod = arrivalMethod;
    }

    public String getSourceSeller() {
        return sourceSeller;
    }

    public void setSourceSeller(String sourceSeller) {
        this.sourceSeller = sourceSeller;
    }

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Shanghai")
    public Date getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(Date arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getGrowthStage() {
        return growthStage;
    }

    public void setGrowthStage(String growthStage) {
        this.growthStage = growthStage;
    }

    public Date getGrowthStageEnteredAt() {
        return growthStageEnteredAt;
    }

    public void setGrowthStageEnteredAt(Date growthStageEnteredAt) {
        this.growthStageEnteredAt = growthStageEnteredAt;
    }

    public String getReproductiveStage() {
        return reproductiveStage;
    }

    public void setReproductiveStage(String reproductiveStage) {
        this.reproductiveStage = reproductiveStage;
    }

    public Long getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Long stateVersion) {
        this.stateVersion = stateVersion;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Boolean getIsQuarantined() {
        return isQuarantined;
    }

    public void setIsQuarantined(Boolean quarantined) {
        isQuarantined = quarantined;
    }

    public Date getQuarantineTime() {
        return quarantineTime;
    }

    public void setQuarantineTime(Date quarantineTime) {
        this.quarantineTime = quarantineTime;
    }

    public String getQuarantineReason() {
        return quarantineReason;
    }

    public void setQuarantineReason(String quarantineReason) {
        this.quarantineReason = quarantineReason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Date getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(Date departureDate) {
        this.departureDate = departureDate;
    }

    public String getDepartureReason() {
        return departureReason;
    }

    public void setDepartureReason(String departureReason) {
        this.departureReason = departureReason;
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
