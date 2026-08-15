package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import java.util.List;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BatchServiceTest {

    @Test
    void locksMothersInStableOrderBeforeCreatingBatch() {
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        when(batchMapper.selectByHouseAndRequestId(1L, "request-1")).thenReturn(null);
        when(dedup.shouldSkipAsDone(1L, 7L, "batch.create", "request-1"))
            .thenReturn(false);
        when(rabbitMapper.selectByIdsForUpdate(1L, List.of(1L, 2L)))
            .thenReturn(List.of(mother(1L), mother(2L)));
        when(batchRabbitMapper.selectActiveRabbitIdsForUpdate(1L, List.of(1L, 2L)))
            .thenReturn(List.of());
        doAnswer(invocation -> {
            ((Batch) invocation.getArgument(0)).setId(9L);
            return 1;
        }).when(batchMapper).insert(any(Batch.class));

        Batch result = service(
            batchMapper,
            batchRabbitMapper,
            rabbitMapper,
            historyMapper,
            dedup
        ).createBatch(7L, 1L, "B-1", List.of(2L, 1L), null, "request-1");

        assertEquals(9L, result.getId());
        InOrder lockingOrder = org.mockito.Mockito.inOrder(
            rabbitMapper,
            batchRabbitMapper,
            batchMapper
        );
        lockingOrder.verify(rabbitMapper).selectByIdsForUpdate(
            1L,
            List.of(1L, 2L)
        );
        lockingOrder.verify(batchRabbitMapper).selectActiveRabbitIdsForUpdate(
            1L,
            List.of(1L, 2L)
        );
        lockingOrder.verify(batchMapper).insert(any(Batch.class));
    }

    @Test
    void rejectsActiveMotherBeforeInsertingAnotherBatch() {
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        when(batchMapper.selectByHouseAndRequestId(1L, "request-2")).thenReturn(null);
        when(dedup.shouldSkipAsDone(1L, 7L, "batch.create", "request-2"))
            .thenReturn(false);
        when(rabbitMapper.selectByIdsForUpdate(1L, List.of(3L)))
            .thenReturn(List.of(mother(3L)));
        when(batchRabbitMapper.selectActiveRabbitIdsForUpdate(1L, List.of(3L)))
            .thenReturn(List.of(3L));

        BizException error = assertThrows(
            BizException.class,
            () -> service(
                batchMapper,
                batchRabbitMapper,
                rabbitMapper,
                historyMapper,
                dedup
            ).createBatch(7L, 1L, "B-2", List.of(3L), null, "request-2")
        );

        assertEquals("母兔已在活跃批次中", error.getMessage());
        verify(batchMapper, never()).insert(any(Batch.class));
        verify(historyMapper, never()).insertBatch(anyList());
    }

    @Test
    void forceCompletionLocksBatchAndRechecksOpenCyclesAfterDeactivation() {
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        BreedingCycleMapper cycleMapper = org.mockito.Mockito.mock(BreedingCycleMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        Batch batch = new Batch();
        batch.setId(9L);
        batch.setHouseId(1L);
        batch.setStatus("进行中");
        when(batchMapper.selectByIdForUpdate(1L, 9L)).thenReturn(batch);
        when(batchRabbitMapper.countActiveByBatch(9L)).thenReturn(1);
        when(batchRabbitMapper.deactivateByBatchLimited(
            eq(1L), eq(9L), any(Date.class), any(), eq("7"), anyInt()
        )).thenReturn(1, 0);
        when(cycleMapper.countOpenByBatch(1L, 9L)).thenReturn(0);

        Date endDate = new Date();
        service(
            batchMapper,
            batchRabbitMapper,
            rabbitMapper,
            historyMapper,
            dedup,
            cycleMapper
        ).completeBatch(7L, 1L, 9L, endDate, true, "done", "complete-1");

        verify(batchMapper).selectByIdForUpdate(1L, 9L);
        verify(cycleMapper, org.mockito.Mockito.times(2)).closeOpenByBatch(
            1L,
            9L,
            endDate,
            "批次强制结束",
            "7",
            500
        );
        verify(cycleMapper).countOpenByBatch(1L, 9L);
        verify(batchMapper).updateStatusAndDates(
            1L,
            9L,
            "已完成",
            null,
            endDate,
            "7"
        );
    }

    private BatchService service(
        BatchMapper batchMapper,
        BatchRabbitMapper batchRabbitMapper,
        RabbitMapper rabbitMapper,
        RabbitStatusHistoryMapper historyMapper,
        RequestDedupService dedup
    ) {
        return service(
            batchMapper,
            batchRabbitMapper,
            rabbitMapper,
            historyMapper,
            dedup,
            null
        );
    }

    private BatchService service(
        BatchMapper batchMapper,
        BatchRabbitMapper batchRabbitMapper,
        RabbitMapper rabbitMapper,
        RabbitStatusHistoryMapper historyMapper,
        RequestDedupService dedup,
        BreedingCycleMapper cycleMapper
    ) {
        return new BatchService(
            batchMapper,
            batchRabbitMapper,
            cycleMapper,
            rabbitMapper,
            null,
            null,
            null,
            null,
            null,
            null,
            historyMapper,
            null,
            null,
            null,
            null,
            null,
            dedup,
            null,
            10
        );
    }

    private Rabbit mother(Long id) {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(id);
        rabbit.setHouseId(1L);
        rabbit.setIsActive(Boolean.TRUE);
        rabbit.setGender("0");
        rabbit.setType("0");
        return rabbit;
    }
}
