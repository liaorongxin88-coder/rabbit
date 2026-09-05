package com.rabbit.app.modules.repro.entity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * litters —— 窝（设计 §4.3）。
 *
 * <p>把「窝」从周期里拆出来，哺乳段才能与下一轮管线并行推进（血配）。
 */
public class Litter {
    private Long id;
    private Long tenantId;
    private Long houseId;
    private Long cycleId;
    private Long motherRabbitId;
    private Long sireRabbitId;
    private Long batchId;
    private Date birthDate;
    private Integer totalKits;
    private Integer liveKits;
    /** 留崽数（接产表单）。 */
    private Integer keptKits;
    private Integer fosterIn;
    private Integer fosterOut;
    private Integer lossCount;
    /** 计数器，事务内维护。 */
    private Integer currentNursing;
    /** NURSING / WEANED。 */
    private String status;
    private Date weaningDate;
    private Integer weanedCount;
    private Double avgWeaningWeight;
    private BigDecimal weaningTotalWeightKg;
    private Long nursingCageId;
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

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getMotherRabbitId() {
        return motherRabbitId;
    }

    public void setMotherRabbitId(Long motherRabbitId) {
        this.motherRabbitId = motherRabbitId;
    }

    public Long getSireRabbitId() {
        return sireRabbitId;
    }

    public void setSireRabbitId(Long sireRabbitId) {
        this.sireRabbitId = sireRabbitId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
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

    public Integer getKeptKits() {
        return keptKits;
    }

    public void setKeptKits(Integer keptKits) {
        this.keptKits = keptKits;
    }

    public Integer getFosterIn() {
        return fosterIn;
    }

    public void setFosterIn(Integer fosterIn) {
        this.fosterIn = fosterIn;
    }

    public Integer getFosterOut() {
        return fosterOut;
    }

    public void setFosterOut(Integer fosterOut) {
        this.fosterOut = fosterOut;
    }

    public Integer getLossCount() {
        return lossCount;
    }

    public void setLossCount(Integer lossCount) {
        this.lossCount = lossCount;
    }

    public Integer getCurrentNursing() {
        return currentNursing;
    }

    public void setCurrentNursing(Integer currentNursing) {
        this.currentNursing = currentNursing;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getWeaningDate() {
        return weaningDate;
    }

    public void setWeaningDate(Date weaningDate) {
        this.weaningDate = weaningDate;
    }

    public Integer getWeanedCount() {
        return weanedCount;
    }

    public void setWeanedCount(Integer weanedCount) {
        this.weanedCount = weanedCount;
    }

    public Double getAvgWeaningWeight() {
        return avgWeaningWeight;
    }

    public void setAvgWeaningWeight(Double avgWeaningWeight) {
        this.avgWeaningWeight = avgWeaningWeight;
    }

    public BigDecimal getWeaningTotalWeightKg() {
        return weaningTotalWeightKg;
    }

    public void setWeaningTotalWeightKg(BigDecimal weaningTotalWeightKg) {
        this.weaningTotalWeightKg = weaningTotalWeightKg;
    }

    public Long getNursingCageId() {
        return nursingCageId;
    }

    public void setNursingCageId(Long nursingCageId) {
        this.nursingCageId = nursingCageId;
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
