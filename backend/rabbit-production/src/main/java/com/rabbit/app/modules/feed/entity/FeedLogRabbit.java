package com.rabbit.app.modules.feed.entity;

import java.util.Date;

public class FeedLogRabbit {
    private Long id;
    private Long houseId;
    private Long feedLogId;
    private Long rabbitId;
    private Long cageId;
    private Date createTime;

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

    public Long getFeedLogId() {
        return feedLogId;
    }

    public void setFeedLogId(Long feedLogId) {
        this.feedLogId = feedLogId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
