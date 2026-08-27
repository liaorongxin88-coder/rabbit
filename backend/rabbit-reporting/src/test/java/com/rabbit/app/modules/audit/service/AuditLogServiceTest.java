package com.rabbit.app.modules.audit.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.audit.entity.AuditLog;
import com.rabbit.app.modules.audit.mapper.AuditLogMapper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 审计写入与分页。
 *
 * <p>两处约束值得钉死：写入失败必须被吞掉（审计挂了不能连累业务请求），以及分页参数的
 * 上下限必须真的生效（导出接口若能传任意 limit，一次请求就能把库拖垮）。
 */
class AuditLogServiceTest {
    private AuditLogMapper auditLogMapper;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        service = new AuditLogService(auditLogMapper);
    }

    @Test
    void nullLogIsIgnoredWithoutHittingTheMapper() {
        service.write(null);
        verify(auditLogMapper, never()).insert(any());
    }

    @Test
    void normalLogIsInserted() {
        AuditLog log = new AuditLog();
        service.write(log);
        verify(auditLogMapper).insert(log);
    }

    /**
     * 审计写入失败绝不能冒泡。它跑在 afterCompletion 里，抛出去会把一个已经成功的
     * 业务请求变成 500。
     */
    @Test
    void mapperFailureIsSwallowedSoTheRequestStillSucceeds() {
        doThrow(new RuntimeException("表不存在")).when(auditLogMapper).insert(any());

        assertDoesNotThrow(() -> service.write(new AuditLog()));
    }

    @Test
    void nonPositivePageFallsBackToTheFirstPage() {
        service.listPage(null, null, null, null, null, null, 0, 20);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void nonPositivePageSizeFallsBackToFifty() {
        service.listPage(null, null, null, null, null, null, 1, 0);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(50));
    }

    @Test
    void pageSizeIsCappedAtTwoHundred() {
        service.listPage(null, null, null, null, null, null, 1, 5000);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(200));
    }

    @Test
    void offsetIsDerivedFromPageAndSize() {
        service.listPage(null, null, null, null, null, null, 3, 25);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(50), eq(25));
    }

    @Test
    void filtersArePassedThroughUntouched() {
        Date from = new Date(1_000L);
        Date to = new Date(2_000L);

        service.listPage(7L, 8L, "/api/admin/farms", 200, from, to, 1, 10);

        verify(auditLogMapper).selectPage(
                eq(7L), eq(8L), eq("/api/admin/farms"), eq(200), eq(from), eq(to), eq(0), eq(10));
    }

    @Test
    void exportLimitFallsBackToOneThousand() {
        service.listExportPage(null, null, null, null, null, null, 0, 0);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(1000));
    }

    @Test
    void exportLimitIsCappedAtFiveThousand() {
        service.listExportPage(null, null, null, null, null, null, 0, 100_000);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(5000));
    }

    @Test
    void negativeExportOffsetIsClampedToZero() {
        service.listExportPage(null, null, null, null, null, null, -20, 100);

        verify(auditLogMapper).selectPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(100));
    }

    @Test
    void exportReturnsWhateverTheMapperFound() {
        AuditLog row = new AuditLog();
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        when(auditLogMapper.selectPage(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), limit.capture()))
                .thenReturn(List.of(row));

        List<AuditLog> found = service.listExportPage(null, null, null, null, null, null, 0, 4999);

        assertEquals(List.of(row), found);
        assertEquals(4999, limit.getValue());
    }
}
