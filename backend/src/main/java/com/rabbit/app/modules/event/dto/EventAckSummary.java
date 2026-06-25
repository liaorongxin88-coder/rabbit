package com.rabbit.app.modules.event.dto;

public class EventAckSummary {
    private String category;
    private Integer ackCount;
    private Integer ignoreCount;
    private Integer snoozeCount;
    private Double avgHandleHours;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getAckCount() {
        return ackCount;
    }

    public void setAckCount(Integer ackCount) {
        this.ackCount = ackCount;
    }

    public Integer getIgnoreCount() {
        return ignoreCount;
    }

    public void setIgnoreCount(Integer ignoreCount) {
        this.ignoreCount = ignoreCount;
    }

    public Integer getSnoozeCount() {
        return snoozeCount;
    }

    public void setSnoozeCount(Integer snoozeCount) {
        this.snoozeCount = snoozeCount;
    }

    public Double getAvgHandleHours() {
        return avgHandleHours;
    }

    public void setAvgHandleHours(Double avgHandleHours) {
        this.avgHandleHours = avgHandleHours;
    }
}
