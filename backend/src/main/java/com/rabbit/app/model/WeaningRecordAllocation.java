package com.rabbit.app.model;

import java.util.Date;

public class WeaningRecordAllocation {
    private Long id;
    private Long weaningRecordId;
    private Long cageId;
    private Integer allocCount;
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWeaningRecordId() {
        return weaningRecordId;
    }

    public void setWeaningRecordId(Long weaningRecordId) {
        this.weaningRecordId = weaningRecordId;
    }

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Integer getAllocCount() {
        return allocCount;
    }

    public void setAllocCount(Integer allocCount) {
        this.allocCount = allocCount;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}

