package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import java.util.List;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
            batchMapper
        );
        lockingOrder.verify(rabbitMapper).selectByIdsForUpdate(
            1L,
            List.of(1L, 2L)
        );
        lockingOrder.verify(batchMapper).insert(any(Batch.class));
    }

    @Test
    void rejectsOnlyDuplicateTagInsideTheSameBatch() {
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        Batch batch = new Batch();
        batch.setId(9L);
        batch.setHouseId(1L);
        batch.setStatus("进行中");
        BatchRabbit existing = new BatchRabbit();
        existing.setId(77L);
        existing.setBatchId(9L);
        existing.setRabbitId(3L);

        when(dedup.shouldSkipAsDone(1L, 7L, "batch.addMembers", "request-2"))
            .thenReturn(false);
        when(batchMapper.selectById(1L, 9L)).thenReturn(batch);
        when(rabbitMapper.selectByIdsForUpdate(1L, List.of(3L)))
            .thenReturn(List.of(mother(3L)));
        when(batchRabbitMapper.selectActiveByBatchAndRabbitsForUpdate(
            1L, 9L, List.of(3L)
        )).thenReturn(List.of(existing));

        BizException error = assertThrows(
            BizException.class,
            () -> service(
                batchMapper,
                batchRabbitMapper,
                rabbitMapper,
                historyMapper,
                dedup
            ).addMembers(7L, 1L, 9L, List.of(3L), "request-2")
        );

        assertEquals("兔只已绑定该批次", error.getMessage());
        verify(batchRabbitMapper, never()).insertBatch(anyList());
        verify(historyMapper, never()).insertBatch(anyList());
    }

    @Test
    void commodityRabbitJoinsBatchForFatteningWithoutOpeningReproCycle() {
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        ReproCycleMapper reproCycleMapper = org.mockito.Mockito.mock(ReproCycleMapper.class);
        ReproStateMachineService stateMachine = org.mockito.Mockito.mock(
            ReproStateMachineService.class
        );
        Batch batch = new Batch();
        batch.setId(9L);
        batch.setHouseId(1L);
        batch.setStatus("进行中");
        Rabbit commodity = commodityRabbit(18L);

        when(dedup.shouldSkipAsDone(1L, 7L, "batch.addMembers", "request-sale"))
            .thenReturn(false);
        when(batchMapper.selectById(1L, 9L)).thenReturn(batch);
        when(rabbitMapper.selectByIdsForUpdate(1L, List.of(18L)))
            .thenReturn(List.of(commodity));
        when(batchRabbitMapper.selectActiveByBatchAndRabbitsForUpdate(
            1L, 9L, List.of(18L)
        )).thenReturn(List.of());

        service(
            batchMapper,
            batchRabbitMapper,
            rabbitMapper,
            historyMapper,
            dedup,
            null,
            reproCycleMapper,
            stateMachine
        ).addMembers(7L, 1L, 9L, List.of(18L), "request-sale");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BatchRabbit>> links = ArgumentCaptor.forClass(List.class);
        verify(batchRabbitMapper).insertBatch(links.capture());
        BatchRabbit link = links.getValue().get(0);
        assertEquals("fattening", link.getBatchRole());
        assertEquals("养育/售卖", link.getJoinReason());
        assertEquals("成长期", link.getCurrentStatus());
        verify(stateMachine, never()).openCycleAt(any());
    }

    @Test
    void removesOnlyTheRequestedBatchTag() {
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        Batch batch = new Batch();
        batch.setId(9L);
        batch.setHouseId(1L);
        BatchRabbit link = new BatchRabbit();
        link.setId(77L);
        link.setBatchId(9L);
        link.setRabbitId(18L);

        when(dedup.shouldSkipAsDone(1L, 7L, "batch.removeMember", "remove-1"))
            .thenReturn(false);
        when(batchMapper.selectById(1L, 9L)).thenReturn(batch);
        when(batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(1L, 9L, 18L))
            .thenReturn(link);
        when(batchRabbitMapper.deactivateIfActive(
            eq(1L), eq(77L), any(Date.class), eq("手动移除批次标签"), eq("7")
        )).thenReturn(2);

        service(
            batchMapper,
            batchRabbitMapper,
            rabbitMapper,
            historyMapper,
            dedup
        ).removeMember(7L, 1L, 9L, 18L, "remove-1");

        verify(batchRabbitMapper).deactivateIfActive(
            eq(1L), eq(77L), any(Date.class), eq("手动移除批次标签"), eq("7")
        );
        verify(dedup).markDone(1L, 7L, "batch.removeMember", "remove-1");
        verify(historyMapper, never()).insertBatch(anyList());
    }

    /**
     * 批次结束现在是「守门」而不是「强关」。
     *
     * <p>旧实现会 UPDATE 把批次下所有周期置为「已终止」，而那条 SQL 不认识
     * lifecycle/stage，会造成旧视角已终止、新视角仍 OPEN 的分裂状态。
     * 现在改为以 lifecycle 为准检查，有未结束周期就拒绝；去活前后各查一次，
     * 因为去活期间可能有新周期被开出来。
     */
    @Test
    void forceCompletionLocksBatchAndRefusesWhileCyclesRemainOpen() {
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
        when(cycleMapper.countOpenLifecycleByBatch(1L, 9L)).thenReturn(0);

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
        // 旧的 closeOpenByBatch 已随 V28 一并删除（它绕过 lifecycle/stage/待办/投影），
        // 现在只剩下面这道守门：还有未结束的周期就拒绝结束批次。
        // 去活前后各守一次。
        verify(cycleMapper, org.mockito.Mockito.times(2)).countOpenLifecycleByBatch(1L, 9L);
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
        return service(
            batchMapper,
            batchRabbitMapper,
            rabbitMapper,
            historyMapper,
            dedup,
            cycleMapper,
            org.mockito.Mockito.mock(ReproCycleMapper.class),
            org.mockito.Mockito.mock(ReproStateMachineService.class)
        );
    }

    private BatchService service(
        BatchMapper batchMapper,
        BatchRabbitMapper batchRabbitMapper,
        RabbitMapper rabbitMapper,
        RabbitStatusHistoryMapper historyMapper,
        RequestDedupService dedup,
        BreedingCycleMapper cycleMapper,
        ReproCycleMapper reproCycleMapper,
        ReproStateMachineService stateMachine
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
            reproCycleMapper,
            stateMachine,
            org.mockito.Mockito.mock(
                com.rabbit.app.modules.repro.service.OperatorNameResolver.class),
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

    private Rabbit commodityRabbit(Long id) {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(id);
        rabbit.setHouseId(1L);
        rabbit.setIsActive(Boolean.TRUE);
        rabbit.setGender("1");
        rabbit.setType("2");
        return rabbit;
    }
}
