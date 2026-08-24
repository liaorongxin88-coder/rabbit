package com.rabbit.app.modules.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public class EventItem {
    private Long recordId;
    private String category;
    private String eventType;
    private Date eventDate;
    private Long batchId;
    private Long rabbitId;
    private String status;
    private String content;

    public EventItem() {
    }

    public EventItem(Long recordId, String category, String eventType, Date eventDate, Long batchId, Long rabbitId, String status) {
        this(recordId, category, eventType, eventDate, batchId, rabbitId, status, null);
    }

    public EventItem(Long recordId, String category, String eventType, Date eventDate, Long batchId, Long rabbitId, String status, String content) {
        this.recordId = recordId;
        this.category = category;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.batchId = batchId;
        this.rabbitId = rabbitId;
        this.status = status;
        this.content = content;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Shanghai")
    public Date getEventDate() {
        return eventDate;
    }

    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
