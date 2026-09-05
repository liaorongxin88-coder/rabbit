package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class BatchStatisticsLegacyWriteServiceTest {
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void disabledCompatibilityReturnsTheUpgradeMessage() {
        BatchStatisticsLegacyWriteService service = new BatchStatisticsLegacyWriteService(
            mock(ReproEventMapper.class), new ObjectMapper(), false
        );

        BizException error = assertThrows(
            BizException.class, service::requireLegacyWriteEnabled
        );

        assertEquals(409, error.getCode());
        assertEquals(BatchStatisticsLegacyWriteService.UPGRADE_MESSAGE, error.getMessage());
    }

    @Test
    void rejectsGapEventNamesOutsideTheApprovedCatalog() {
        BatchStatisticsLegacyWriteService service = new BatchStatisticsLegacyWriteService(
            mock(ReproEventMapper.class), new ObjectMapper(), true
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            service.recordGap(7L, 8L, 101L, "request-1", "feed:add", "LEGACY_OTHER_GAP")
        );

        assertEquals("不支持的旧客户端缺口事件: LEGACY_OTHER_GAP", error.getMessage());
    }

    @Test
    void gapEventUsesUnknownWhenTheAppBuildHeaderIsMissing() {
        ReproEventMapper mapper = mock(ReproEventMapper.class);
        BatchStatisticsLegacyWriteService service = new BatchStatisticsLegacyWriteService(
            mapper, new ObjectMapper(), true
        );

        service.recordGap(
            7L,
            8L,
            101L,
            "request-unknown",
            "rabbit.toReplacement",
            BatchStatisticsLegacyWriteService.LEGACY_REPLACEMENT_WEIGHT_GAP
        );

        ArgumentCaptor<List<ReproEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(events.capture());
        assertEquals("{\"clientBuild\":\"UNKNOWN\"}", events.getValue().getFirst().getPayload());
    }

    @Test
    void gapEventCapturesTheAppBuildWithoutTheRequestBody() {
        ReproEventMapper mapper = mock(ReproEventMapper.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-App-Build")).thenReturn("  12345  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BatchStatisticsLegacyWriteService service = new BatchStatisticsLegacyWriteService(
            mapper, new ObjectMapper(), true
        );

        service.recordGap(
            7L,
            8L,
            101L,
            "request-1",
            "feed:add",
            BatchStatisticsLegacyWriteService.LEGACY_FEED_ALLOCATION_GAP
        );

        ArgumentCaptor<List<ReproEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(events.capture());
        ReproEvent event = events.getValue().getFirst();
        assertEquals(8L, event.getHouseId());
        assertEquals(101L, event.getBatchId());
        assertEquals("BATCH", event.getTargetType());
        assertEquals("request-1", event.getRequestId());
        assertEquals("{\"clientBuild\":\"12345\"}", event.getPayload());
    }
}
