package com.rabbit.app.modules.batch.dto;

public class PendingWeaningSummary {
    private Integer recordCount;
    private Integer waitingCount;

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Integer getWaitingCount() {
        return waitingCount;
    }

    public void setWaitingCount(Integer waitingCount) {
        this.waitingCount = waitingCount;
    }
}
