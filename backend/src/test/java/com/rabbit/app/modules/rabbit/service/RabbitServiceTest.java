package com.rabbit.app.modules.rabbit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class RabbitServiceTest {
    @Test
    void convertToReplacementRequiresControlAtServiceBoundary() {
        HouseService houseService = new HouseService(null, null, null, null, null, null) {
            @Override
            public void assertHousePermission(Long userId, Long houseId, String requiredPerm) {
                throw new BizException(403, "权限不足");
            }
        };
        RabbitService service = new RabbitService(
                null, null, null, null, null, null, null, null, null,
                null, houseService, 10
        );

        BizException error = assertThrows(BizException.class,
                () -> service.convertToReplacement(
                        7L, 8L, List.of(1L), false, null, "request-1"
                ));

        assertEquals(403, error.getCode());
    }

    @Test
    void forceExitLocksBatchBeforeRabbitAndRechecksOpenCycles() {
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        CageMapper cageMapper = org.mockito.Mockito.mock(CageMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        BatchMapper batchMapper = org.mockito.Mockito.mock(BatchMapper.class);
        BreedingCycleMapper cycleMapper = org.mockito.Mockito.mock(BreedingCycleMapper.class);
        RabbitStatusHistoryMapper historyMapper = org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class);
        RabbitDepartureRecordMapper departureMapper = org.mockito.Mockito.mock(RabbitDepartureRecordMapper.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);

        Rabbit rabbit = new Rabbit();
        rabbit.setId(3L);
        rabbit.setHouseId(1L);
        rabbit.setIsActive(Boolean.TRUE);
        BatchRabbit link = new BatchRabbit();
        link.setId(103L);
        link.setBatchId(2L);
        link.setRabbitId(3L);
        link.setIsActive(Boolean.TRUE);
        Batch batch = new Batch();
        batch.setId(2L);
        batch.setHouseId(1L);
        batch.setStatus("进行中");

        org.mockito.Mockito.when(rabbitMapper.selectById(1L, 3L)).thenReturn(rabbit);
        org.mockito.Mockito.when(batchRabbitMapper.selectActiveByRabbit(1L, 3L))
            .thenReturn(List.of(link));
        org.mockito.Mockito.when(batchMapper.selectByIdForUpdate(1L, 2L)).thenReturn(batch);
        org.mockito.Mockito.when(rabbitMapper.selectByIdsForUpdate(1L, List.of(3L)))
            .thenReturn(List.of(rabbit));
        org.mockito.Mockito.when(batchRabbitMapper.selectActiveByRabbitForUpdate(1L, 3L))
            .thenReturn(List.of(link));
        org.mockito.Mockito.when(cycleMapper.countOpenByMother(1L, 2L, 3L)).thenReturn(0);
        org.mockito.Mockito.when(batchRabbitMapper.countActiveByBatch(2L)).thenReturn(0);
        org.mockito.Mockito.when(batchMapper.selectById(1L, 2L)).thenReturn(batch);

        Date actionDate = new Date();
        RabbitService service = new RabbitService(
            rabbitMapper,
            cageMapper,
            null,
            null,
            batchRabbitMapper,
            batchMapper,
            cycleMapper,
            historyMapper,
            departureMapper,
            dedup,
            null,
            10
        );
        service.rabbitEvent(
            7L,
            1L,
            3L,
            "cull",
            actionDate,
            "health",
            null,
            true,
            "exit-1"
        );

        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(
            batchMapper,
            rabbitMapper,
            batchRabbitMapper
        );
        lockOrder.verify(batchMapper).selectByIdForUpdate(1L, 2L);
        lockOrder.verify(rabbitMapper).selectByIdsForUpdate(1L, List.of(3L));
        lockOrder.verify(batchRabbitMapper).selectActiveByRabbitForUpdate(1L, 3L);
        org.mockito.Mockito.verify(cycleMapper, org.mockito.Mockito.times(2))
            .closeOpenByMother(1L, 2L, 3L, actionDate, "兔离场:cull", "7");
        org.mockito.Mockito.verify(cycleMapper).countOpenByMother(1L, 2L, 3L);
        org.mockito.Mockito.verify(batchRabbitMapper).deactivateIfActive(
            1L,
            103L,
            actionDate,
            "兔离场:cull",
            "7"
        );
    }
}
