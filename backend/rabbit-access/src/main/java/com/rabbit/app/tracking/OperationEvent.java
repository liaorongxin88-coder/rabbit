package com.rabbit.app.tracking;

import java.util.Date;
import java.util.Map;

/**
 * 一条操作事件。刻意做成不可变值对象而非实体：本阶段还没有承载它的表
 * （事件表扩列是 T4 的 V46），先把<b>形状</b>定死，落库实现随后接入
 * {@link OperationEventSink}。
 *
 * <p>字段选择直接对齐目标问题「谁、在哪个兔舍、对哪个批次的哪只兔、
 * 在哪个笼位、做了什么、什么时候」，外加复盘必需的 requestId / traceId
 * 两个串联键。
 */
public final class OperationEvent {

    private final String operationCode;
    private final String eventType;
    private final Long houseId;
    private final Long batchId;
    private final Long cageId;
    private final Long rabbitId;
    private final Long operatorId;
    private final String operatorName;
    private final String requestId;
    private final String traceId;
    private final Date occurredAt;
    private final Map<String, Object> payload;

    private OperationEvent(Builder builder) {
        this.operationCode = builder.operationCode;
        this.eventType = builder.eventType;
        this.houseId = builder.houseId;
        this.batchId = builder.batchId;
        this.cageId = builder.cageId;
        this.rabbitId = builder.rabbitId;
        this.operatorId = builder.operatorId;
        this.operatorName = builder.operatorName;
        this.requestId = builder.requestId;
        this.traceId = builder.traceId;
        this.occurredAt = builder.occurredAt == null ? new Date() : new Date(builder.occurredAt.getTime());
        this.payload = builder.payload == null ? Map.of() : Map.copyOf(builder.payload);
    }

    /**
     * 以当前上下文为底稿开一个 builder。批量场景下每只兔只需覆盖 rabbitId
     * 这一个差异字段，其余坐标沿用请求级上下文。
     */
    public static Builder from(OperationContext context) {
        Builder builder = new Builder();
        if (context != null) {
            builder.operationCode = context.getOperationCode();
            builder.houseId = context.getHouseId();
            builder.batchId = context.getBatchId();
            builder.cageId = context.getCageId();
            builder.rabbitId = context.getRabbitId();
            builder.operatorId = context.getUserId();
            builder.operatorName = context.getOperatorName();
            builder.requestId = context.getRequestId();
            builder.traceId = context.getTraceId();
        }
        return builder;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getHouseId() {
        return houseId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public Long getCageId() {
        return cageId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Date getOccurredAt() {
        return new Date(occurredAt.getTime());
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "OperationEvent{" + operationCode + "/" + eventType
                + ", house=" + houseId + ", batch=" + batchId + ", cage=" + cageId
                + ", rabbit=" + rabbitId + ", operator=" + operatorId + "/" + operatorName
                + ", requestId=" + requestId + ", traceId=" + traceId + '}';
    }

    public static final class Builder {
        private String operationCode;
        private String eventType;
        private Long houseId;
        private Long batchId;
        private Long cageId;
        private Long rabbitId;
        private Long operatorId;
        private String operatorName;
        private String requestId;
        private String traceId;
        private Date occurredAt;
        private Map<String, Object> payload;

        public Builder operationCode(String value) {
            this.operationCode = value;
            return this;
        }

        public Builder eventType(String value) {
            this.eventType = value;
            return this;
        }

        public Builder houseId(Long value) {
            this.houseId = value;
            return this;
        }

        public Builder batchId(Long value) {
            this.batchId = value;
            return this;
        }

        public Builder cageId(Long value) {
            this.cageId = value;
            return this;
        }

        public Builder rabbitId(Long value) {
            this.rabbitId = value;
            return this;
        }

        public Builder operatorId(Long value) {
            this.operatorId = value;
            return this;
        }

        public Builder operatorName(String value) {
            this.operatorName = value;
            return this;
        }

        public Builder requestId(String value) {
            this.requestId = value;
            return this;
        }

        public Builder traceId(String value) {
            this.traceId = value;
            return this;
        }

        public Builder occurredAt(Date value) {
            this.occurredAt = value;
            return this;
        }

        public Builder payload(Map<String, Object> value) {
            this.payload = value;
            return this;
        }

        public OperationEvent build() {
            return new OperationEvent(this);
        }
    }
}
