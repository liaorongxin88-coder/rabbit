package com.rabbit.app.modules.repro.entity;

import java.util.Date;

/**
 * repro_events —— append-only 操作事件流（设计 §4.1）。
 *
 * <p>三重职责：留痕、幂等（uk_re_request 冲突即回查首次结果）、可重放。
 * 刻意没有 update 方法：事件写入后不可改，纠错靠追加补偿事件。
 */
public class ReproEvent {
    private Long id;
    /** 预留列，租户模型落地后回填。 */
    private Long tenantId;
    private Long houseId;
    /** 周期外事件（如离场）可空。 */
    private Long cycleId;
    private Long litterId;
    private Long motherRabbitId;
    private Long batchId;
    private String eventType;
    private String fromStage;
    private String toStage;
    /** 业务时间，允许补录历史。 */
    private Date occurredAt;
    /** 操作差异字段的 JSON；附件只存 file_id 引用。 */
    private String payload;
    /** 历史回填事件无 user_id，故可空。 */
    private Long operatorId;
    /** 冗余快照，兼容现有 create_by 体系与历史回填。 */
    private String operatorName;
    private String requestId;
    private Date createTime;

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

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public Long getLitterId() {
        return litterId;
    }

    public void setLitterId(Long litterId) {
        this.litterId = litterId;
    }

    public Long getMotherRabbitId() {
        return motherRabbitId;
    }

    public void setMotherRabbitId(Long motherRabbitId) {
        this.motherRabbitId = motherRabbitId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFromStage() {
        return fromStage;
    }

    public void setFromStage(String fromStage) {
        this.fromStage = fromStage;
    }

    public String getToStage() {
        return toStage;
    }

    public void setToStage(String toStage) {
        this.toStage = toStage;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
