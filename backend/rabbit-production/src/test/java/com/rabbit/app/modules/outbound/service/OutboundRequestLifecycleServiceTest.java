package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.outbound.entity.OutboundRequest;
import com.rabbit.app.modules.outbound.mapper.OutboundRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 出库请求的幂等生命周期。
 *
 * <p>这层是出库提交的幂等底座：先靠唯一键抢占 requestId，再把最终状态写回去。两处细节
 * 决定了重试到底安不安全：
 *
 * <ul>
 *   <li>抢占失败后必须能查到那一行。查不到说明同一个 requestId 挂在别的兔场名下，
 *       属于越权复用，要当场拒绝而不是当成幂等命中；
 *   <li>终态写回时「影响 0 行」不一定是错。重试时那行可能已经是目标状态了，
 *       这时要放行；只有状态对不上才算真失败。
 * </ul>
 */
class OutboundRequestLifecycleServiceTest {
    private OutboundRequestMapper requestMapper;
    private OutboundRequestLifecycleService service;

    @BeforeEach
    void setUp() {
        requestMapper = mock(OutboundRequestMapper.class);
        service = new OutboundRequestLifecycleService(requestMapper);
    }

    // ---------- 抢占 ----------

    @Test
    void winningTheInsertClaimsTheRequest() {
        OutboundRequest request = request("PENDING");
        when(requestMapper.insertIgnore(request)).thenReturn(1);

        OutboundRequestLifecycleService.ClaimResult result = service.claim(request);

        assertTrue(result.claimed());
        assertSame(request, result.request());
        verify(requestMapper, never()).selectById(anyLong(), anyString());
    }

    @Test
    void losingTheInsertReturnsTheExistingRequestInsteadOfClaiming() {
        OutboundRequest request = request("PENDING");
        OutboundRequest existing = request("COMPLETED");
        when(requestMapper.insertIgnore(request)).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(existing);

        OutboundRequestLifecycleService.ClaimResult result = service.claim(request);

        assertFalse(result.claimed());
        assertSame(existing, result.request());
    }

    @Test
    void failedRequestIsAtomicallyReclaimedForTheSameTaskAndPayload() {
        OutboundRequest failed = request("FAILED");
        when(requestMapper.reclaimFailed(1L, "req-1", "task-1", "payload-hash"))
            .thenReturn(1);

        OutboundRequestLifecycleService.ClaimResult result = service.reclaimFailed(failed);

        assertTrue(result.claimed());
        assertSame(failed, result.request());
        assertEquals("PROCESSING", failed.getStatus());
        assertEquals(null, failed.getErrorCode());
        verify(requestMapper, never()).selectById(anyLong(), anyString());
    }

    @Test
    void concurrentFailedReclaimHasOnlyOneOwner() {
        OutboundRequest failed = request("FAILED");
        OutboundRequest processing = request("PROCESSING");
        when(requestMapper.reclaimFailed(1L, "req-1", "task-1", "payload-hash"))
            .thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(processing);

        OutboundRequestLifecycleService.ClaimResult result = service.reclaimFailed(failed);

        assertFalse(result.claimed());
        assertSame(processing, result.request());
    }

    /**
     * 插入被唯一键挡下，却在本兔场查不到这一行 —— 说明这个 requestId 属于别的兔场。
     * 当成幂等命中会把另一个兔场的结果返回给调用方，所以必须报冲突。
     */
    @Test
    void aRequestIdOwnedByAnotherHouseIsRejectedRatherThanTreatedAsAReplay() {
        OutboundRequest request = request("PENDING");
        when(requestMapper.insertIgnore(request)).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.claim(request));
        assertEquals(409, error.getCode());
        assertEquals("REQUEST_ID_SCOPE_MISMATCH", error.getMessage());
    }

    // ---------- 查询 ----------

    @Test
    void findDelegatesToTheMapper() {
        OutboundRequest existing = request("COMPLETED");
        when(requestMapper.selectById(1L, "req-1")).thenReturn(existing);

        assertSame(existing, service.find(1L, "req-1"));
    }

    // ---------- 终态写回 ----------

    @Test
    void markCompletedAcceptsASingleUpdatedRow() {
        when(requestMapper.markCompleted(1L, "req-1", 99L)).thenReturn(1);

        service.markCompleted(1L, "req-1", 99L);

        verify(requestMapper, never()).selectById(anyLong(), anyString());
    }

    /**
     * 重试时那行已经是 COMPLETED 且挂着同一个销售单，更新 0 行是正常的，要放行。
     */
    @Test
    void markCompletedToleratesANoOpWhenTheRowAlreadyMatches() {
        when(requestMapper.markCompleted(1L, "req-1", 99L)).thenReturn(0);
        OutboundRequest current = request("COMPLETED");
        current.setSaleOrderId(99L);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(current);

        assertDoesNotThrow(() -> service.markCompleted(1L, "req-1", 99L));
    }

    /**
     * 同一个 requestId 已经完成，但挂的是**另一个**销售单 —— 这不是幂等，是数据串了，
     * 放行会让调用方以为自己的单子成交了。
     */
    @Test
    void markCompletedRejectsARowCompletedUnderADifferentSaleOrder() {
        when(requestMapper.markCompleted(1L, "req-1", 99L)).thenReturn(0);
        OutboundRequest current = request("COMPLETED");
        current.setSaleOrderId(12345L);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(current);

        BizException error = assertThrows(BizException.class, () -> service.markCompleted(1L, "req-1", 99L));
        assertEquals(500, error.getCode());
        assertEquals("提交请求最终状态写入失败", error.getMessage());
    }

    @Test
    void markCompletedCanRecoverAFailedLifecycleRow() {
        when(requestMapper.markCompleted(1L, "req-1", 99L)).thenReturn(1);

        assertDoesNotThrow(() -> service.markCompleted(1L, "req-1", 99L));
    }

    @Test
    void markCompletedRejectsARowStuckInAnotherTerminalStatus() {
        when(requestMapper.markCompleted(1L, "req-1", 99L)).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(request("CONFLICT"));

        assertEquals(500, assertThrows(BizException.class,
                () -> service.markCompleted(1L, "req-1", 99L)).getCode());
    }

    @Test
    void markCompletedRejectsAVanishedRow() {
        when(requestMapper.markCompleted(1L, "req-1", 99L)).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(null);

        assertEquals(500, assertThrows(BizException.class,
                () -> service.markCompleted(1L, "req-1", 99L)).getCode());
    }

    @Test
    void markFailedToleratesANoOpWhenTheRowIsAlreadyFailed() {
        when(requestMapper.markFailed(eq(1L), eq("req-1"), anyString(), anyString())).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(request("FAILED"));

        assertDoesNotThrow(() -> service.markFailed(1L, "req-1", "E", "boom"));
    }

    @Test
    void markFailedRejectsARowInAnotherStatus() {
        when(requestMapper.markFailed(eq(1L), eq("req-1"), anyString(), anyString())).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(request("COMPLETED"));

        assertEquals(500, assertThrows(BizException.class,
                () -> service.markFailed(1L, "req-1", "E", "boom")).getCode());
    }

    @Test
    void markConflictToleratesANoOpWhenTheRowIsAlreadyInConflict() {
        when(requestMapper.markConflict(eq(1L), eq("req-1"), anyString(), anyString(), anyString())).thenReturn(0);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(request("CONFLICT"));

        assertDoesNotThrow(() -> service.markConflict(1L, "req-1", "E", "boom", "[]"));
    }

    /**
     * 失败和冲突不比对 saleOrderId，只看状态 —— 那两条路径本来就没有销售单。
     */
    @Test
    void failureStatesAreVerifiedWithoutASaleOrder() {
        when(requestMapper.markFailed(eq(1L), eq("req-1"), anyString(), anyString())).thenReturn(0);
        OutboundRequest current = request("FAILED");
        current.setSaleOrderId(777L);
        when(requestMapper.selectById(1L, "req-1")).thenReturn(current);

        assertDoesNotThrow(() -> service.markFailed(1L, "req-1", "E", "boom"));
    }

    // ---------- 截断 ----------

    /**
     * 错误码和错误信息都有列宽。不截断就是一条插入失败，而这里正处在异常处理路径上，
     * 再抛一次会把原始失败原因盖掉，现场就彻底没了。
     */
    @Test
    void oversizedErrorFieldsAreTruncatedBeforeTheyReachTheDatabase() {
        when(requestMapper.markFailed(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);

        service.markFailed(1L, "req-1", "C".repeat(100), "M".repeat(900));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(requestMapper).markFailed(eq(1L), eq("req-1"), code.capture(), message.capture());
        assertEquals(64, code.getValue().length());
        assertEquals(500, message.getValue().length());
    }

    @Test
    void shortErrorFieldsArePassedThroughUnchanged() {
        when(requestMapper.markConflict(anyLong(), anyString(), any(), any(), any())).thenReturn(1);

        service.markConflict(1L, "req-1", "STATE_VERSION_CONFLICT", "兔只状态已变更", "[{\"id\":1}]");

        verify(requestMapper).markConflict(
                eq(1L), eq("req-1"), eq("STATE_VERSION_CONFLICT"), eq("兔只状态已变更"), eq("[{\"id\":1}]"));
    }

    @Test
    void nullErrorFieldsSurviveTruncation() {
        when(requestMapper.markFailed(anyLong(), anyString(), isNull(), isNull())).thenReturn(1);

        assertDoesNotThrow(() -> service.markFailed(1L, "req-1", null, null));

        verify(requestMapper).markFailed(eq(1L), eq("req-1"), isNull(), isNull());
    }

    private OutboundRequest request(String status) {
        OutboundRequest request = new OutboundRequest();
        request.setHouseId(1L);
        request.setRequestId("req-1");
        request.setTaskId("task-1");
        request.setPayloadHash("payload-hash");
        request.setStatus(status);
        return request;
    }
}
