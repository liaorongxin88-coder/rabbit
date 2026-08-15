package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BulkMatingResult;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.BreedingCycle;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import com.rabbit.app.modules.batch.mapper.ParturitionRecordMapper;
import com.rabbit.app.modules.batch.mapper.PregnancyCheckRecordMapper;
import com.rabbit.app.modules.batch.mapper.PrepartumRecordMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordAllocationMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.dedup.service.RequestDedupService.BeginResult;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import com.rabbit.app.modules.outbound.service.OutboundEligibilityService;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BulkMatingServiceTest {
    @Test
    void rejectsDuplicateOrOverLimitMotherIdsBeforeAnyWrite() {
        BatchService service = service();

        BizException duplicate = assertThrows(
            BizException.class,
            () -> service.matingBulk(7L, 1L, 2L, List.of(3L, 3L), 9L, new Date(), "bulk-1")
        );
        assertEquals("femaleRabbitIds包含无效或重复值", duplicate.getMessage());

        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 1001).boxed().toList();
        BizException overLimit = assertThrows(
            BizException.class,
            () -> service.matingBulk(7L, 1L, 2L, tooMany, 9L, new Date(), "bulk-2")
        );
        assertEquals("单次最多配种1000只母兔", overLimit.getMessage());
    }

    @Test
    void prevalidatesAllRowsAndDoesNotWriteWhenOneMotherIsNotInBatch() {
        BatchMapper batches = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper links = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbits = org.mockito.Mockito.mock(RabbitMapper.class);
        BreedingCycleMapper cycles = org.mockito.Mockito.mock(BreedingCycleMapper.class);
        SettingService settings = org.mockito.Mockito.mock(SettingService.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        when(dedup.begin(eq(1L), eq(7L), eq("batch.mating.bulk"), eq("bulk-3"), any()))
            .thenReturn(BeginResult.STARTED);
        when(batches.selectByIdForUpdate(1L, 2L)).thenReturn(batch());
        when(settings.getEffectiveSetting(7L, 1L)).thenReturn(setting());
        when(rabbits.selectByIdsForUpdate(1L, List.of(3L, 4L))).thenReturn(List.of(mother(3L), mother(4L)));
        when(rabbits.selectByIdsForUpdate(1L, List.of(9L))).thenReturn(List.of(male(9L)));
        when(rabbits.selectById(1L, 3L)).thenReturn(mother(3L));
        when(rabbits.selectById(1L, 4L)).thenReturn(mother(4L));
        when(rabbits.selectById(1L, 9L)).thenReturn(male(9L));
        when(links.selectActiveByBatchAndRabbitsForUpdate(1L, 2L, List.of(3L, 4L)))
            .thenReturn(List.of(link(3L)));

        BizException error = assertThrows(
            BizException.class,
            () -> service(
                batches,
                links,
                rabbits,
                cycles,
                settings,
                dedup
            ).matingBulk(7L, 1L, 2L, List.of(4L, 3L), 9L, new Date(), "bulk-3")
        );

        assertEquals("母兔不在该批次中", error.getMessage());
        verify(cycles, never()).insert(any(BreedingCycle.class));
        verify(links, never()).updateBreedingSummary(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void matesRowsAndReturnsSameCountForIdempotentRetry() {
        BatchMapper batches = org.mockito.Mockito.mock(BatchMapper.class);
        BatchRabbitMapper links = org.mockito.Mockito.mock(BatchRabbitMapper.class);
        RabbitMapper rabbits = org.mockito.Mockito.mock(RabbitMapper.class);
        BreedingCycleMapper cycles = org.mockito.Mockito.mock(BreedingCycleMapper.class);
        SettingService settings = org.mockito.Mockito.mock(SettingService.class);
        RequestDedupService dedup = org.mockito.Mockito.mock(RequestDedupService.class);
        when(dedup.begin(eq(1L), eq(7L), eq("batch.mating.bulk"), eq("bulk-4"), any()))
            .thenReturn(BeginResult.STARTED, BeginResult.DONE);
        when(batches.selectByIdForUpdate(1L, 2L)).thenReturn(batch());
        when(batches.selectById(1L, 2L)).thenReturn(batch());
        when(settings.getEffectiveSetting(7L, 1L)).thenReturn(setting());
        when(rabbits.selectByIdsForUpdate(1L, List.of(3L, 4L))).thenReturn(List.of(mother(3L), mother(4L)));
        when(rabbits.selectByIdsForUpdate(1L, List.of(9L))).thenReturn(List.of(male(9L)));
        when(rabbits.selectById(1L, 3L)).thenReturn(mother(3L));
        when(rabbits.selectById(1L, 4L)).thenReturn(mother(4L));
        when(rabbits.selectById(1L, 9L)).thenReturn(male(9L));
        when(links.selectActiveByBatchAndRabbitsForUpdate(1L, 2L, List.of(3L, 4L)))
            .thenReturn(List.of(link(3L), link(4L)));
        when(links.selectActiveByBatchAndRabbitForUpdate(1L, 2L, 3L)).thenReturn(link(3L));
        when(links.selectActiveByBatchAndRabbitForUpdate(1L, 2L, 4L)).thenReturn(link(4L));
        when(cycles.countOpenGestations(1L, 2L, 3L)).thenReturn(0);
        when(cycles.countOpenGestations(1L, 2L, 4L)).thenReturn(0);
        when(cycles.selectLatestByStatusesForUpdate(eq(1L), eq(2L), any(), anyList())).thenReturn(null);
        when(cycles.selectLatest(1L, 2L, 3L)).thenReturn(null);
        when(cycles.selectLatest(1L, 2L, 4L)).thenReturn(null);
        when(cycles.sumCurrentNursingKits(any(), any(), any())).thenReturn(0);
        when(cycles.countNursingLitters(any(), any(), any())).thenReturn(0);
        when(links.updateBreedingSummary(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(1);
        org.mockito.stubbing.Answer<Integer> insertCycle = invocation -> {
            BreedingCycle cycle = invocation.getArgument(0);
            cycle.setId(cycle.getMotherRabbitId());
            return 1;
        };
        org.mockito.Mockito.doAnswer(insertCycle).when(cycles).insert(any(BreedingCycle.class));

        BatchService service = service(batches, links, rabbits, cycles, settings, dedup);
        Date matingDate = new Date();
        BulkMatingResult first = service.matingBulk(7L, 1L, 2L, List.of(4L, 3L), 9L, matingDate, "bulk-4");
        BulkMatingResult retry = service.matingBulk(7L, 1L, 2L, List.of(3L, 4L), 9L, matingDate, "bulk-4");

        assertEquals(new BulkMatingResult("bulk-4", 2), first);
        assertEquals(first, retry);
        verify(cycles, org.mockito.Mockito.times(2)).insert(any(BreedingCycle.class));
        ArgumentCaptor<String> payloadHashes = ArgumentCaptor.forClass(String.class);
        verify(dedup, org.mockito.Mockito.times(2)).begin(
            eq(1L),
            eq(7L),
            eq("batch.mating.bulk"),
            eq("bulk-4"),
            payloadHashes.capture()
        );
        assertEquals(64, payloadHashes.getAllValues().get(0).length());
        assertEquals(
            payloadHashes.getAllValues().get(0),
            payloadHashes.getAllValues().get(1)
        );
        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(batches, rabbits, links);
        lockOrder.verify(batches).selectByIdForUpdate(1L, 2L);
        lockOrder.verify(rabbits).selectByIdsForUpdate(1L, List.of(3L, 4L));
        lockOrder.verify(links).selectActiveByBatchAndRabbitsForUpdate(1L, 2L, List.of(3L, 4L));
    }

    private BatchService service() {
        return service(
            org.mockito.Mockito.mock(BatchMapper.class),
            org.mockito.Mockito.mock(BatchRabbitMapper.class),
            org.mockito.Mockito.mock(RabbitMapper.class),
            org.mockito.Mockito.mock(BreedingCycleMapper.class),
            org.mockito.Mockito.mock(SettingService.class),
            org.mockito.Mockito.mock(RequestDedupService.class)
        );
    }

    private BatchService service(
        BatchMapper batches,
        BatchRabbitMapper links,
        RabbitMapper rabbits,
        BreedingCycleMapper cycles,
        SettingService settings,
        RequestDedupService dedup
    ) {
        return new BatchService(
            batches, links, cycles, rabbits, settings,
            org.mockito.Mockito.mock(PregnancyCheckRecordMapper.class),
            org.mockito.Mockito.mock(ParturitionRecordMapper.class),
            org.mockito.Mockito.mock(PrepartumRecordMapper.class),
            org.mockito.Mockito.mock(WeaningRecordMapper.class),
            org.mockito.Mockito.mock(WeaningRecordAllocationMapper.class),
            org.mockito.Mockito.mock(RabbitStatusHistoryMapper.class),
            org.mockito.Mockito.mock(BreedingPerformanceMapper.class),
            org.mockito.Mockito.mock(RabbitAbnormalConditionMapper.class),
            org.mockito.Mockito.mock(RabbitDepartureRecordMapper.class),
            org.mockito.Mockito.mock(CageMapper.class),
            org.mockito.Mockito.mock(ReplacementRecordMapper.class),
            dedup,
            org.mockito.Mockito.mock(OutboundEligibilityService.class),
            10
        );
    }

    private Batch batch() {
        Batch batch = new Batch();
        batch.setId(2L);
        batch.setHouseId(1L);
        batch.setStatus("进行中");
        batch.setStartDate(new Date());
        return batch;
    }

    private GlobalSetting setting() {
        GlobalSetting setting = new GlobalSetting();
        setting.setPalpationDays(7);
        return setting;
    }

    private Rabbit mother(Long id) {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(id);
        rabbit.setHouseId(1L);
        rabbit.setType("0");
        rabbit.setGender("0");
        rabbit.setIsActive(true);
        return rabbit;
    }

    private Rabbit male(Long id) {
        Rabbit rabbit = mother(id);
        rabbit.setGender("1");
        return rabbit;
    }

    private BatchRabbit link(Long rabbitId) {
        BatchRabbit link = new BatchRabbit();
        link.setId(rabbitId + 100L);
        link.setBatchId(2L);
        link.setRabbitId(rabbitId);
        link.setCurrentStatus("待配种");
        link.setIsActive(true);
        return link;
    }
}
