package com.rabbit.app.modules.repro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import com.rabbit.app.tracking.OperationEvent;
import com.rabbit.app.tracking.OperationEventSink;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Persists tracked operations in the append-only event stream within the caller transaction. */
@Component
public class ReproOperationEventSink implements OperationEventSink {

    private static final String FALLBACK_TARGET_TYPE = "OPERATION";

    private final ReproEventMapper reproEventMapper;
    private final ObjectMapper objectMapper;

    public ReproOperationEventSink(ReproEventMapper reproEventMapper, ObjectMapper objectMapper) {
        this.reproEventMapper = reproEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(List<OperationEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<ReproEvent> rows = new ArrayList<>(events.size());
        for (OperationEvent event : events) {
            rows.add(toReproEvent(event));
        }
        reproEventMapper.insertBatch(rows);
    }

    private ReproEvent toReproEvent(OperationEvent event) {
        ReproEvent row = new ReproEvent();
        row.setHouseId(event.getHouseId());
        row.setBatchId(event.getBatchId());
        row.setCageId(event.getCageId());
        row.setOperationCode(event.getOperationCode());
        row.setTargetType(hasText(event.getTargetType()) ? event.getTargetType() : FALLBACK_TARGET_TYPE);
        row.setTargetId(event.getTargetId());
        row.setEventType(event.getEventType());
        row.setOccurredAt(event.getOccurredAt());
        row.setPayload(payload(event));
        row.setOperatorId(event.getOperatorId());
        row.setOperatorName(event.getOperatorName());
        row.setRequestId(event.getRequestId());
        return row;
    }

    private String payload(OperationEvent event) {
        if (event.getPayload().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(event.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作事件 payload 无法序列化", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
