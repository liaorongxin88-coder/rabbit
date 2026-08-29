package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import com.rabbit.app.tracking.OperationEvent;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReproOperationEventSinkTest {

    @Test
    void appendsTheWholeOperationBatchInOneMapperCall() {
        ReproEventMapper mapper = mock(ReproEventMapper.class);
        ReproOperationEventSink sink = new ReproOperationEventSink(mapper, new ObjectMapper());
        Date occurredAt = new Date(1_700_000_000_000L);

        sink.append(List.of(
            event(11L, 101L, occurredAt, Map.of("source", "bulk")),
            event(11L, 102L, occurredAt, Map.of())
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReproEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(events.capture());
        List<ReproEvent> rows = events.getValue();
        assertEquals(2, rows.size());
        assertEquals("vaccination:create", rows.get(0).getOperationCode());
        assertEquals("RABBIT", rows.get(0).getTargetType());
        assertEquals(101L, rows.get(0).getTargetId());
        assertEquals(31L, rows.get(0).getCageId());
        assertEquals("req-1", rows.get(0).getRequestId());
        assertEquals("{\"source\":\"bulk\"}", rows.get(0).getPayload());
        assertNull(rows.get(1).getPayload());
    }

    @Test
    void ignoresAnEmptyBatch() {
        ReproEventMapper mapper = mock(ReproEventMapper.class);
        ReproOperationEventSink sink = new ReproOperationEventSink(mapper, new ObjectMapper());

        sink.append(List.of());

        verify(mapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    private OperationEvent event(Long houseId, Long rabbitId, Date occurredAt, Map<String, Object> payload) {
        return new OperationEvent.Builder()
            .operationCode("vaccination:create")
            .eventType("VACCINATION_RECORDED")
            .targetType("RABBIT")
            .targetId(rabbitId)
            .houseId(houseId)
            .cageId(31L)
            .operatorId(7L)
            .operatorName("operator")
            .requestId("req-1")
            .occurredAt(occurredAt)
            .payload(payload)
            .build();
    }
}
