package com.rabbit.app.model;

import java.util.Date;

public class BreedingPerformance {
    private Long id;
    private Long houseId;
    private Long rabbitId;
    private Integer totalLitters;
    private Integer totalKits;
    private Integer totalLiveKits;
    private Integer totalWeaned;
    private Integer successBreedingCount;
    private Integer failedBreedingCount;
    private Double avgLitterSize;
    private Double avgWeaningSize;
    private Date lastLitterDate;
    private Integer performanceScore;
    private String remark;
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

    public Integer getTotalLitters() {
        return totalLitters;
    }

    public void setTotalLitters(Integer totalLitters) {
        this.totalLitters = totalLitters;
    }

    public Integer getTotalKits() {
        return totalKits;
    }

    public void setTotalKits(Integer totalKits) {
        this.totalKits = totalKits;
    }

    public Integer getTotalLiveKits() {
        return totalLiveKits;
    }

    public void setTotalLiveKits(Integer totalLiveKits) {
        this.totalLiveKits = totalLiveKits;
    }

    public Integer getTotalWeaned() {
        return totalWeaned;
    }

    public void setTotalWeaned(Integer totalWeaned) {
        this.totalWeaned = totalWeaned;
    }

    public Integer getSuccessBreedingCount() {
        return successBreedingCount;
    }

    public void setSuccessBreedingCount(Integer successBreedingCount) {
        this.successBreedingCount = successBreedingCount;
    }

    public Integer getFailedBreedingCount() {
        return failedBreedingCount;
    }

    public void setFailedBreedingCount(Integer failedBreedingCount) {
        this.failedBreedingCount = failedBreedingCount;
    }

    public Double getAvgLitterSize() {
        return avgLitterSize;
    }

    public void setAvgLitterSize(Double avgLitterSize) {
        this.avgLitterSize = avgLitterSize;
    }

    public Double getAvgWeaningSize() {
        return avgWeaningSize;
    }

    public void setAvgWeaningSize(Double avgWeaningSize) {
        this.avgWeaningSize = avgWeaningSize;
    }

    public Date getLastLitterDate() {
        return lastLitterDate;
    }

    public void setLastLitterDate(Date lastLitterDate) {
        this.lastLitterDate = lastLitterDate;
    }

    public Integer getPerformanceScore() {
        return performanceScore;
    }

    public void setPerformanceScore(Integer performanceScore) {
        this.performanceScore = performanceScore;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
