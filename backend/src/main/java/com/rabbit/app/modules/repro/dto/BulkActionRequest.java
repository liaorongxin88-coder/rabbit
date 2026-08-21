package com.rabbit.app.modules.repro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

/**
 * POST /api/repro/tasks/bulk-actions —— 批量操作（设计 §5.1）。
 *
 * <p>目标二选一：显式 {@code taskIds}，或 {@code filter} 由服务端解析为该批次
 * 当前的 PENDING 任务集。后者正是「批次 = 批量选择集」这一模型的落点：
 * 批次不再是有状态的生命周期实体，只是一次批量操作的选择依据。
 *
 * <p>只承载全批共享的标量字段。接产/分笼这类每只数据都不同的操作不能批量，
 * 服务端会拒绝——共享一个 totalKits 会把同一窝仔数写给每一只母兔。
 */
public class BulkActionRequest {

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotBlank(message = "操作不能为空")
    private String action;

    private String outcome;

    @NotNull(message = "执行时间不能为空")
    private Date occurredAt;

    /** 推迟到期时间；action=POSTPONE 时必填。 */
    private Date nextRemindAt;

    private String remark;

    private String reason;

    private Long maleRabbitId;

    private String matingMethod;

    private String palpationResult;

    /** 目标形式一：显式任务 id 列表。 */
    private List<Long> taskIds;

    /** 目标形式二：按条件解析当前 PENDING 任务集。 */
    private Filter filter;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Date getNextRemindAt() {
        return nextRemindAt;
    }

    public void setNextRemindAt(Date nextRemindAt) {
        this.nextRemindAt = nextRemindAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getMaleRabbitId() {
        return maleRabbitId;
    }

    public void setMaleRabbitId(Long maleRabbitId) {
        this.maleRabbitId = maleRabbitId;
    }

    public String getMatingMethod() {
        return matingMethod;
    }

    public void setMatingMethod(String matingMethod) {
        this.matingMethod = matingMethod;
    }

    public String getPalpationResult() {
        return palpationResult;
    }

    public void setPalpationResult(String palpationResult) {
        this.palpationResult = palpationResult;
    }

    public List<Long> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(List<Long> taskIds) {
        this.taskIds = taskIds;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /** 批次 + 任务类型定位一组待办；两者都可空，但至少要有一个。 */
    public static class Filter {
        private Long batchId;
        private String taskType;
        private Long cageId;

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public Long getCageId() {
            return cageId;
        }

        public void setCageId(Long cageId) {
            this.cageId = cageId;
        }
    }
}
