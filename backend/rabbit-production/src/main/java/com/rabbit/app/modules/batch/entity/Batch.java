package com.rabbit.app.modules.batch.entity;

import java.util.Date;

public class Batch {
    private Long id;
    private Long houseId;
    private String batchCode;
    private String status;
    private Date startDate;
    private Date endDate;
    private String requestId;
    private String remark;
    private String createBy;
    private Date createTime;
    /**
     * 派生字段（非数据库列）：批次内已无在册母兔，提示用户可以结束。
     *
     * <p>旧实现在成员全部退出时自动把批次置为已完成，但“这一轮生产算不算结束”
     * 是业务判断：母兔可能只是暂时全部离场，用户还想往里补兔。现在改为只提醒，
     * 结束动作交回给人。
     */
    private Boolean pendingCompletion;

    private String updateBy;
    private Date updateTime;

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

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public Boolean getPendingCompletion() {
        return pendingCompletion;
    }

    public void setPendingCompletion(Boolean pendingCompletion) {
        this.pendingCompletion = pendingCompletion;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
