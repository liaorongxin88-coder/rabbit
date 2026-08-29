package com.rabbit.app.modules.batch.dto;

public class BatchStatistics {
    private Integer totalLitters;
    private Integer totalKits;
    private Integer totalLiveKits;
    private Integer totalWeaned;

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
}
