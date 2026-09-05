package com.rabbit.app.modules.batch.dto;

import java.time.LocalDate;

public class BatchStatisticsMatingDateRow {
    private LocalDate date;
    private Integer cycleCount;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(Integer cycleCount) {
        this.cycleCount = cycleCount;
    }
}
