package com.rabbit.app.dto;

public class BreedingSummary {
    private Integer totalLitters;
    private Integer totalKits;
    private Integer totalLiveKits;
    private Integer totalWeaned;
    private Integer successBreedingCount;
    private Integer failedBreedingCount;

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
}
