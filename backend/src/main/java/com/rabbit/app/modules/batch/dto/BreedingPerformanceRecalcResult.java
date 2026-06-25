package com.rabbit.app.modules.batch.dto;

public class BreedingPerformanceRecalcResult {
    private Integer totalRabbits;
    private Integer updatedRows;

    public Integer getTotalRabbits() {
        return totalRabbits;
    }

    public void setTotalRabbits(Integer totalRabbits) {
        this.totalRabbits = totalRabbits;
    }

    public Integer getUpdatedRows() {
        return updatedRows;
    }

    public void setUpdatedRows(Integer updatedRows) {
        this.updatedRows = updatedRows;
    }
}

