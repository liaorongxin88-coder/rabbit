package com.rabbit.app.modules.report.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.report.dto.DashboardSummary;
import com.rabbit.app.modules.report.service.DashboardReportService;
import com.rabbit.app.security.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReportControllerTest {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void dashboardPassesHeaderHouseAndBatchScopeToService() {
        DashboardReportService service = mock(DashboardReportService.class);
        DashboardSummary expected = new DashboardSummary();
        when(service.load(7L, 8L, 11L, 2026)).thenReturn(expected);
        ReportController controller = controller(service);
        AuthContext.setUserId(7L);

        controller.dashboard(8L, 8L, 11L, 2026);

        verify(service).load(7L, 8L, 11L, 2026);
    }

    @Test
    void dashboardRequiresHouseHeaderForBatchScope() {
        ReportController controller = controller(mock(DashboardReportService.class));

        BizException error = assertThrows(
            BizException.class,
            () -> controller.dashboard(null, 8L, 11L, 2026)
        );

        assertEquals(400, error.getCode());
        assertEquals("选择批次时必须指定兔舍", error.getMessage());
    }

    @Test
    void dashboardRejectsConflictingHeaderAndQueryHouse() {
        ReportController controller = controller(mock(DashboardReportService.class));

        BizException error = assertThrows(
            BizException.class,
            () -> controller.dashboard(9L, 8L, 11L, 2026)
        );

        assertEquals(400, error.getCode());
        assertEquals("兔舍统计范围不一致", error.getMessage());
    }

    private static ReportController controller(DashboardReportService service) {
        return new ReportController(null, null, null, null, null, service);
    }
}
