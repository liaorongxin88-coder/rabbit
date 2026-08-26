package com.rabbit.app.modules.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

public class EventItem {
    private Long recordId;
    private String category;
    private String eventType;
    private Date eventDate;
    private Long batchId;
    /**
     * 批次编号，供界面直接显示。
     *
     * <p>只给 batchId 的话客户端只能显示成「批次 #12」，而批次列表和批次详情显示的是
     * 批次编号，两边对不上号；操作者会把这个内部 id 当成周期号。批次查不到时留空，
     * 客户端据此隐藏批次字段，而不是显示一个查无此物的号码。
     */
    private String batchCode;
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

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
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
