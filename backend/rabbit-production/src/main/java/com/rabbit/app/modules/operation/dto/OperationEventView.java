package com.rabbit.app.modules.operation.dto;

import com.rabbit.app.modules.repro.entity.ReproEvent;
import java.util.Date;

/**
 * 操作事件流里的一条留痕。
 *
 * <p>不含 payload 与 requestId：payload 是操作差异的内部结构，requestId 是幂等键，
 * 两者外泄都会让客户端依赖服务端内部约定。这条边界沿用 ReproEventView。
 *
 * <p>operatorName 是写入当时的展示名快照，不 join sys_user —— 事故复盘要的是
 * 「当时是谁」，join 出来的是「现在叫什么」。
 */
public record OperationEventView(
    Long id,
    Date occurredAt,
    String operationCode,
    String eventType,
    String eventLabel,
    String targetType,
    Long targetId,
    Long cageId,
    Long batchId,
    Long rabbitId,
    Long cycleId,
    Long litterId,
    String fromStage,
    String toStage,
    Long operatorId,
    String operatorName
) {
    public static OperationEventView of(ReproEvent event, String eventLabel) {
        return new OperationEventView(
            event.getId(),
            event.getOccurredAt(),
            event.getOperationCode(),
            event.getEventType(),
            eventLabel,
            event.getTargetType(),
            event.getTargetId(),
            event.getCageId(),
            event.getBatchId(),
            event.getMotherRabbitId(),
            event.getCycleId(),
            event.getLitterId(),
            event.getFromStage(),
            event.getToStage(),
            event.getOperatorId(),
            event.getOperatorName()
        );
    }
}
