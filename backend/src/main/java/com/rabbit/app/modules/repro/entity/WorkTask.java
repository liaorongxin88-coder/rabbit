package com.rabbit.app.modules.repro.entity;

import java.util.Date;

/**
 * work_tasks —— 统一任务/提醒中心（设计 §4.4）。
 *
 * <p>不变式：每个 OPEN 周期恰有 1 条关联 PENDING 任务。管线段挂 CYCLE，
 * 哺乳段的分笼任务挂 LITTER，因此血配时同一母兔能同时看到两条互不干扰的待办。
 */
public class WorkTask {
    private Long id;
    private Long tenantId;
    private Long houseId;
    private String taskType;
    private String subjectType;
    private Long subjectId;
    /** 冗余定位列，供列表页免 join。 */
    private Long cycleId;
    private Long rabbitId;
    private Long batchId;
    private Long cageId;
    private Date dueDate;
    private Date dueTime;
    private String status;
    /** 推迟次数；任务永不删除，便于识别长期拖延。 */
    private Integer snoozeCount;
    /** 完成时回链 repro_events。 */
    private Long completedEventId;
    /** 如 cycle:{id}:PALPATION，保证同一主体同类任务只有一条。 */
    private String dedupKey;
    private String remark;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    public Date getDueTime() {
        return dueTime;
    }

    public void setDueTime(Date dueTime) {
        this.dueTime = dueTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSnoozeCount() {
        return snoozeCount;
    }

    public void setSnoozeCount(Integer snoozeCount) {
        this.snoozeCount = snoozeCount;
    }

    public Long getCompletedEventId() {
        return completedEventId;
    }

    public void setCompletedEventId(Long completedEventId) {
        this.completedEventId = completedEventId;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
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
