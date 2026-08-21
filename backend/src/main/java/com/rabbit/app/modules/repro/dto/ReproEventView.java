package com.rabbit.app.modules.repro.dto;

import com.rabbit.app.modules.repro.domain.ReproEventType;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import java.util.Date;

/** 批次标签下的一条生产操作留痕；不暴露内部 payload 与幂等 requestId。 */
public record ReproEventView(
    Long id,
    Long cycleId,
    Long litterId,
    Long motherRabbitId,
    Long batchId,
    String eventType,
    String eventLabel,
    String fromStage,
    String fromStageLabel,
    String toStage,
    String toStageLabel,
    Date occurredAt,
    String operatorName
) {
    public static ReproEventView of(ReproEvent event) {
        return new ReproEventView(
            event.getId(),
            event.getCycleId(),
            event.getLitterId(),
            event.getMotherRabbitId(),
            event.getBatchId(),
            event.getEventType(),
            eventLabel(event.getEventType()),
            event.getFromStage(),
            stageLabel(event.getFromStage()),
            event.getToStage(),
            stageLabel(event.getToStage()),
            event.getOccurredAt(),
            event.getOperatorName()
        );
    }

    private static String eventLabel(String value) {
        if (value == null || value.isBlank()) {
            return "生产操作";
        }
        try {
            return ReproEventType.valueOf(value.trim().toUpperCase()).label();
        } catch (IllegalArgumentException ignored) {
            return value.trim();
        }
    }

    private static String stageLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ReproStage.parse(value).label();
        } catch (RuntimeException ignored) {
            return value.trim();
        }
    }
}
