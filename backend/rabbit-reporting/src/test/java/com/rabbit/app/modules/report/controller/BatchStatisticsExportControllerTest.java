package com.rabbit.app.modules.report.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.service.BatchStatisticsService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.report.service.BatchStatisticsWorkbookWriter;
import com.rabbit.app.modules.report.service.BatchStatisticsWorkbookWriter.WorkbookSnapshot;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class BatchStatisticsExportControllerTest {
    private final HouseService houseService = mock(HouseService.class);
    private final BatchStatisticsService statisticsService = mock(BatchStatisticsService.class);
    private final BatchStatisticsWorkbookWriter workbookWriter = mock(BatchStatisticsWorkbookWriter.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final BatchStatisticsExportController controller = new BatchStatisticsExportController(
            houseService,
            statisticsService,
            workbookWriter
    );

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void checksScopeAndUsesOneStatisticsSnapshotForTheStream() throws Exception {
        AuthContext.setUserId(7L);
        BatchStatistics statistics = statistics();
        WorkbookSnapshot snapshot = snapshot();
        when(statisticsService.getStatistics(91L, 101L)).thenReturn(statistics);
        when(workbookWriter.prepare(statistics, 91L, 101L)).thenReturn(snapshot);
        when(request.getAttribute("traceId")).thenReturn("trace-1");

        ResponseEntity<StreamingResponseBody> response = controller.export(91L, 101L, request);

        assertEquals(
                BatchStatisticsExportController.XLSX_MEDIA_TYPE,
                response.getHeaders().getContentType().toString()
        );
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.contains("filename=\"batch-EXPORT-A-statistics-20260904032000.xlsx\""));
        assertTrue(disposition.contains("filename*=UTF-8''"));
        assertTrue(disposition.contains("%E6%89%B9%E6%AC%A1"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        verify(houseService).assertHousePermission(7L, 91L, "view");
        verify(statisticsService, times(1)).getStatistics(91L, 101L);
        verify(workbookWriter).prepare(statistics, 91L, 101L);
        verify(workbookWriter).write(snapshot, output);
    }

    @Test
    void leavesBatchOwnershipFailureBeforeWorkbookStreaming() throws Exception {
        AuthContext.setUserId(7L);
        when(statisticsService.getStatistics(92L, 101L))
                .thenThrow(new BizException(404, "批次不存在"));

        BizException error = assertThrows(
                BizException.class,
                () -> controller.export(92L, 101L, request)
        );

        assertEquals(404, error.getCode());
        assertEquals("批次不存在", error.getMessage());
        verify(houseService).assertHousePermission(7L, 92L, "view");
        verify(statisticsService, times(1)).getStatistics(92L, 101L);
        verify(workbookWriter, never()).prepare(any(), any(), any());
        verify(workbookWriter, never()).write(any(), any());
    }

    @Test
    void requiresLoginBeforeHouseOrBatchLookups() {
        BizException error = assertThrows(
                BizException.class,
                () -> controller.export(91L, 101L, request)
        );

        assertEquals(401, error.getCode());
        assertEquals("未登录", error.getMessage());
        verify(houseService, never()).assertHousePermission(any(), any(), any());
        verify(statisticsService, never()).getStatistics(any(), any());
    }

    @Test
    void endpointRequiresReportExportPermission() throws Exception {
        RequiresPermission permission = BatchStatisticsExportController.class
                .getDeclaredMethod("export", Long.class, Long.class, HttpServletRequest.class)
                .getAnnotation(RequiresPermission.class);

        assertEquals(PermissionCode.RABBIT_REPORTS_EXPORT, permission.value());
    }

    private BatchStatistics statistics() {
        return new BatchStatistics(
                1,
                101L,
                "验收兔舍",
                "EXPORT-A",
                Instant.parse("2026-09-04T03:20:00Z"),
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    private WorkbookSnapshot snapshot() {
        return new WorkbookSnapshot(
                List.of(),
                "验收兔舍",
                "EXPORT-A",
                Instant.parse("2026-09-04T03:20:00Z"),
                "批次-EXPORT-A-统计-20260904032000.xlsx",
                "batch-EXPORT-A-statistics-20260904032000.xlsx"
        );
    }
}
