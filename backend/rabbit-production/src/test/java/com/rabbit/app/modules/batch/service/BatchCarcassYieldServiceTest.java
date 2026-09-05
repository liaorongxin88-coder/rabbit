package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldPage;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldRequest;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldView;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchCarcassYieldVersion;
import com.rabbit.app.modules.batch.mapper.BatchCarcassYieldMapper;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.file.service.BusinessFileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchCarcassYieldServiceTest {
    private BatchMapper batchMapper;
    private BatchCarcassYieldMapper mapper;
    private RequestDedupService dedup;
    private BatchCarcassYieldService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(BatchMapper.class);
        mapper = mock(BatchCarcassYieldMapper.class);
        dedup = mock(RequestDedupService.class);
        service = new BatchCarcassYieldService(
            batchMapper, mapper, dedup, mock(BusinessFileService.class)
        );
        when(batchMapper.selectByIdForUpdate(8L, 101L)).thenReturn(new Batch());
        when(batchMapper.selectById(8L, 101L)).thenReturn(new Batch());
        when(mapper.insert(any())).thenAnswer(invocation -> {
            invocation.<BatchCarcassYieldVersion>getArgument(0).setId(55L);
            return 1;
        });
    }

    @Test
    void appendsANormalizedMeasuredVersion() {
        BatchCarcassYieldView result = service.append(7L, 8L, 101L, request("yield-1", "0.56"));

        ArgumentCaptor<BatchCarcassYieldVersion> version = ArgumentCaptor.forClass(
            BatchCarcassYieldVersion.class
        );
        verify(mapper).insert(version.capture());
        assertEquals(55L, result.id());
        assertEquals(new BigDecimal("0.560000"), version.getValue().getYieldRate());
        assertEquals("测试屠宰场", version.getValue().getSourceUnit());
        assertEquals(7L, version.getValue().getCreatedBy());
        verify(dedup).begin(eq(8L), eq(7L), eq("batch:carcass-yield"), eq("yield-1"), anyString());
        verify(dedup).markDone(8L, 7L, "batch:carcass-yield", "yield-1");
    }

    @Test
    void sameRequestAndPayloadReturnsTheOriginalVersion() {
        BatchCarcassYieldRequest request = request("yield-1", "0.56");
        service.append(7L, 8L, 101L, request);
        ArgumentCaptor<BatchCarcassYieldVersion> version = ArgumentCaptor.forClass(
            BatchCarcassYieldVersion.class
        );
        verify(mapper).insert(version.capture());
        when(mapper.selectByRequestId(8L, "yield-1")).thenReturn(version.getValue());

        BatchCarcassYieldView replay = service.append(7L, 8L, 101L, request);

        assertEquals(55L, replay.id());
    }

    @Test
    void sameRequestWithDifferentPayloadIsRejected() {
        BatchCarcassYieldRequest first = request("yield-1", "0.56");
        service.append(7L, 8L, 101L, first);
        ArgumentCaptor<BatchCarcassYieldVersion> version = ArgumentCaptor.forClass(
            BatchCarcassYieldVersion.class
        );
        verify(mapper).insert(version.capture());
        when(mapper.selectByRequestId(8L, "yield-1")).thenReturn(version.getValue());

        BizException error = assertThrows(BizException.class, () ->
            service.append(7L, 8L, 101L, request("yield-1", "0.57"))
        );

        assertEquals("requestId已用于不同的出肉率记录", error.getMessage());
    }

    @Test
    void historyIsHouseScopedAndPaged() {
        BatchCarcassYieldVersion version = new BatchCarcassYieldVersion();
        version.setId(55L);
        version.setBatchId(101L);
        version.setYieldRate(new BigDecimal("0.560000"));
        when(mapper.selectPage(8L, 101L, 20, 20)).thenReturn(List.of(version));
        when(mapper.countByBatch(8L, 101L)).thenReturn(21L);

        BatchCarcassYieldPage result = service.list(8L, 101L, 2, 20);

        assertEquals(21L, result.total());
        assertEquals(2, result.page());
        assertEquals(55L, result.items().getFirst().id());
    }

    private static BatchCarcassYieldRequest request(String requestId, String yieldRate) {
        BatchCarcassYieldRequest request = new BatchCarcassYieldRequest();
        request.setYieldRate(new BigDecimal(yieldRate));
        request.setSourceUnit(" 测试屠宰场 ");
        request.setMeasuredDate(LocalDate.of(2024, 8, 1));
        request.setChangeReason("首次录入");
        request.setRequestId(requestId);
        return request;
    }
}
