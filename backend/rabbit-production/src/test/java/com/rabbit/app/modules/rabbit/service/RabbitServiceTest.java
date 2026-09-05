package com.rabbit.app.modules.rabbit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.service.OpenCycleCommand;
import com.rabbit.app.modules.repro.service.OperatorNameResolver;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RabbitServiceTest {
    @Test
    void commodityEntryDerivesStageAndMaturityFromTheWeaningDate() {
        CreationFixture fixture = creationFixture();
        Date weaningDate = DateUtil.now();
        Rabbit rabbit = rabbit(
            "2",
            weaningDate,
            DateUtil.plusDays(weaningDate, -10)
        );
        rabbit.setGrowthStage("FATTENING");

        fixture.service().createRabbit(7L, 8L, rabbit, null, "commodity-auto-stage");

        ArgumentCaptor<WorkTaskWriter.RabbitTaskScheduleRequest> task =
            ArgumentCaptor.forClass(WorkTaskWriter.RabbitTaskScheduleRequest.class);
        verify(fixture.workTaskWriter()).scheduleForRabbit(task.capture());
        assertEquals("ADAPTATION", rabbit.getGrowthStage());
        assertEquals(weaningDate, rabbit.getGrowthStageEnteredAt());
        assertEquals(
            DateUtil.plusDays(weaningDate, 37),
            task.getValue().dueTime()
        );
    }

    @Test
    void replacementRecordAndMaturityUseHistoricalStageDate() {
        CreationFixture fixture = creationFixture();
        Date arrivalDate = new Date(1_688_169_600_000L);
        Date stageEnteredAt = new Date(1_706_745_600_000L);
        Rabbit rabbit = rabbit("1", arrivalDate, stageEnteredAt);
        rabbit.setReproductiveStage("RESERVE");

        fixture.service().createRabbit(7L, 8L, rabbit, null, "replacement-history");

        ArgumentCaptor<ReplacementRecord> replacement =
            ArgumentCaptor.forClass(ReplacementRecord.class);
        verify(fixture.replacementRecordMapper()).insert(replacement.capture());
        assertEquals(stageEnteredAt, replacement.getValue().getReplacementDate());
        assertNull(rabbit.getGrowthStage());
        assertNull(rabbit.getGrowthStageEnteredAt());
        assertEquals(
            DateUtil.plusDays(stageEnteredAt, 90),
            replacement.getValue().getExpectedMatureDate()
        );
    }

    @Test
    void nonCommodityRabbitRejectsGrowthStage() {
        CreationFixture fixture = creationFixture();
        Rabbit rabbit = rabbit("0", DateUtil.now(), null);
        rabbit.setGrowthStage("MATURE");

        BizException error = assertThrows(BizException.class,
            () -> fixture.service().createRabbit(7L, 8L, rabbit, null, "breeder-growth"));

        assertEquals(400, error.getCode());
        assertEquals("只有商品兔可以维护生长阶段", error.getMessage());
    }

    @Test
    void oldClientStageFieldsAreIgnoredInFavorOfTheWeaningDate() {
        CreationFixture fixture = creationFixture();
        Date weaningDate = new Date(1_706_745_600_000L);
        Rabbit rabbit = rabbit("2", weaningDate, DateUtil.plusDays(weaningDate, -5));
        rabbit.setGrowthStage("ADAPTATION");

        fixture.service().createRabbit(7L, 8L, rabbit, null, "legacy-client");

        assertEquals("MATURE", rabbit.getGrowthStage());
        assertEquals(
            DateUtil.plusDays(weaningDate, 37),
            rabbit.getGrowthStageEnteredAt()
        );
    }

    @Test
    void commodityEntryRejectsAFutureWeaningDate() {
        CreationFixture fixture = creationFixture();
        Date tomorrow = DateUtil.plusDays(DateUtil.now(), 1);
        Rabbit rabbit = rabbit("2", tomorrow, null);

        BizException error = assertThrows(BizException.class,
            () -> fixture.service().createRabbit(7L, 8L, rabbit, null, "future-weaning"));

        assertEquals(400, error.getCode());
        assertEquals("断奶日期不能晚于今天", error.getMessage());
    }

    @Test
    void earlyPromotionRequiresReasonBeforeChangingRabbit() {
        PromotionFixture fixture = promotionFixture(DateUtil.plusDays(DateUtil.now(), 1));

        BizException error = assertThrows(BizException.class,
            () -> fixture.service().promoteReplacement(7L, 8L, 81L, null, "promote-early"));

        assertEquals(400, error.getCode());
        assertEquals("提前转种必须填写原因", error.getMessage());
        verify(fixture.rabbitMapper(), never()).promoteReplacement(
            anyLong(), anyLong(), anyString(), anyString()
        );
    }

    @Test
    void earlyDoePromotionKeepsIdentityAndOpensReadyCycle() {
        PromotionFixture fixture = promotionFixture(DateUtil.plusDays(DateUtil.now(), 1));

        fixture.service().promoteReplacement(
            7L, 8L, 81L, "育种计划提前", "promote-ready"
        );

        verify(fixture.rabbitMapper()).promoteReplacement(8L, 81L, null, "7");
        ArgumentCaptor<RabbitStatusHistory> history =
            ArgumentCaptor.forClass(RabbitStatusHistory.class);
        verify(fixture.historyMapper()).insert(history.capture());
        assertEquals("提前转种：育种计划提前", history.getValue().getReason());
        ArgumentCaptor<OpenCycleCommand> command =
            ArgumentCaptor.forClass(OpenCycleCommand.class);
        verify(fixture.stateMachine()).openCycleAt(command.capture());
        assertEquals(81L, command.getValue().motherRabbitId());
        assertNull(command.getValue().batchId());
        assertEquals(ReproStage.READY, command.getValue().targetStage());
    }

    @Test
    void convertToReplacementRequiresControlAtServiceBoundary() {
        HouseService houseService = new HouseService(null, null, null, null, null, null) {
            @Override
            public void assertHousePermission(Long userId, Long houseId, String requiredPerm) {
                throw new BizException(403, "权限不足");
            }
        };
        RabbitService service = new RabbitService(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, houseService, 10
        );

        BizException error = assertThrows(BizException.class,
                () -> service.convertToReplacement(
                        7L, 8L, List.of(1L), false, null, "request-1", null
                ));

        assertEquals(403, error.getCode());
    }

    @Test
    void listsBatchMembershipsWithActiveFilterHouseIsolationAndStageProjection() {
        RabbitMapper rabbitMapper = org.mockito.Mockito.mock(RabbitMapper.class);
        BatchRabbitMapper batchRabbitMapper = org.mockito.Mockito.mock(BatchRabbitMapper.class);

        Rabbit rabbit = new Rabbit();
        rabbit.setId(31L);
        rabbit.setHouseId(7L);
        org.mockito.Mockito.when(rabbitMapper.selectById(7L, 31L)).thenReturn(rabbit);

        BatchRabbitItem active = new BatchRabbitItem();
        active.setBatchId(41L);
        active.setRabbitId(31L);
        active.setIsActive(Boolean.TRUE);
        active.setCurrentStage("AWAIT_PALPATION");
        active.setCurrentCycleId(51L);
        BatchRabbitItem inactive = new BatchRabbitItem();
        inactive.setBatchId(42L);
        inactive.setRabbitId(31L);
        inactive.setIsActive(Boolean.FALSE);

        org.mockito.Mockito.when(batchRabbitMapper.selectItemsByRabbit(7L, 31L, Boolean.TRUE))
            .thenReturn(List.of(active));
        org.mockito.Mockito.when(batchRabbitMapper.selectItemsByRabbit(7L, 31L, Boolean.FALSE))
            .thenReturn(List.of(inactive));

        RabbitService service = new RabbitService(
            rabbitMapper, null, null, null, batchRabbitMapper, null, null, null, null, null,
            null, null, null, null, null, 10
        );

        List<BatchRabbitItem> activeResult = service.listBatchMemberships(7L, 31L, Boolean.TRUE);
        List<BatchRabbitItem> inactiveResult = service.listBatchMemberships(7L, 31L, Boolean.FALSE);

        assertEquals("AWAIT_PALPATION", activeResult.get(0).getCurrentStage());
        assertEquals(51L, activeResult.get(0).getCurrentCycleId());
        assertEquals(Boolean.FALSE, inactiveResult.get(0).getIsActive());
        org.mockito.Mockito.verify(batchRabbitMapper)
            .selectItemsByRabbit(7L, 31L, Boolean.TRUE);
        org.mockito.Mockito.verify(batchRabbitMapper)
            .selectItemsByRabbit(7L, 31L, Boolean.FALSE);
        org.mockito.Mockito.verify(rabbitMapper, org.mockito.Mockito.times(2))
            .selectById(7L, 31L);
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
        WorkTaskWriter workTaskWriter = org.mockito.Mockito.mock(WorkTaskWriter.class);

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
            // 兔子离场会先走 RETIRE 结清生产周期；本用例只验锁序，给个 mock 即可。
            org.mockito.Mockito.mock(
                com.rabbit.app.modules.repro.service.ReproActionService.class),
            // 录入时按生产阶段入轨会用到状态机与操作人解析；本用例不走创建路径。
            org.mockito.Mockito.mock(
                com.rabbit.app.modules.repro.service.ReproStateMachineService.class),
            org.mockito.Mockito.mock(
                com.rabbit.app.modules.repro.service.OperatorNameResolver.class),
            historyMapper,
            departureMapper,
            dedup,
            workTaskWriter,
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
        // 周期结清已改由 ReproActionService.retireMother 负责（同时维护 lifecycle、
        // 待办与母兔投影），旧的 closeOpenByMother/countOpenByMother 已随 V28 一并删除。
        org.mockito.Mockito.verify(batchRabbitMapper).deactivateIfActive(
            1L,
            103L,
            actionDate,
            "兔离场:cull",
            "7"
        );
        org.mockito.Mockito.verify(workTaskWriter).cancelAllForRabbit(1L, 3L, "7");
    }

    private static CreationFixture creationFixture() {
        RabbitMapper rabbitMapper = mock(RabbitMapper.class);
        CageMapper cageMapper = mock(CageMapper.class);
        SettingService settingService = mock(SettingService.class);
        ReplacementRecordMapper replacementRecordMapper = mock(ReplacementRecordMapper.class);
        RabbitStatusHistoryMapper historyMapper = mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = mock(RequestDedupService.class);
        WorkTaskWriter workTaskWriter = mock(WorkTaskWriter.class);

        Cage cage = new Cage();
        cage.setId(11L);
        cage.setHouseId(8L);
        cage.setStatus("0");
        cage.setRabbitCount(0);
        cage.setIsEnabled(Boolean.TRUE);
        when(cageMapper.selectByIdForUpdate(8L, 11L)).thenReturn(cage);
        when(cageMapper.updateRabbitCountAndStatus(
            anyLong(), anyLong(), anyInt(), anyString(), anyString()
        )).thenReturn(1);
        doAnswer(invocation -> {
            Rabbit rabbit = invocation.getArgument(0);
            rabbit.setId(81L);
            return 1;
        }).when(rabbitMapper).insert(org.mockito.ArgumentMatchers.any(Rabbit.class));

        GlobalSetting setting = new GlobalSetting();
        setting.setAdaptationDays(5);
        setting.setGrowingDays(21);
        setting.setFatteningDays(10);
        setting.setReplacementDays(90);
        when(settingService.getEffectiveSetting(7L, 8L)).thenReturn(setting);

        RabbitService service = new RabbitService(
            rabbitMapper,
            cageMapper,
            settingService,
            replacementRecordMapper,
            null,
            null,
            null,
            null,
            null,
            null,
            historyMapper,
            null,
            dedup,
            workTaskWriter,
            null,
            10
        );
        return new CreationFixture(service, replacementRecordMapper, workTaskWriter);
    }

    private static PromotionFixture promotionFixture(Date matureAt) {
        RabbitMapper rabbitMapper = mock(RabbitMapper.class);
        CageMapper cageMapper = mock(CageMapper.class);
        ReplacementRecordMapper replacementRecordMapper = mock(ReplacementRecordMapper.class);
        RabbitStatusHistoryMapper historyMapper = mock(RabbitStatusHistoryMapper.class);
        RequestDedupService dedup = mock(RequestDedupService.class);
        WorkTaskWriter workTaskWriter = mock(WorkTaskWriter.class);
        HouseService houseService = mock(HouseService.class);
        ReproStateMachineService stateMachine = mock(ReproStateMachineService.class);
        OperatorNameResolver operatorNameResolver = mock(OperatorNameResolver.class);

        Rabbit rabbit = rabbit("1", DateUtil.now(), null);
        rabbit.setId(81L);
        rabbit.setHouseId(8L);
        rabbit.setGender("0");
        rabbit.setIsActive(Boolean.TRUE);
        ReplacementRecord replacement = new ReplacementRecord();
        replacement.setId(91L);
        replacement.setExpectedMatureDate(matureAt);
        Cage cage = new Cage();
        cage.setId(11L);
        cage.setHouseId(8L);
        cage.setIsEnabled(Boolean.TRUE);
        when(rabbitMapper.selectByIdsForUpdate(8L, List.of(81L)))
            .thenReturn(List.of(rabbit));
        when(replacementRecordMapper.selectPendingByRabbitForUpdate(8L, 81L))
            .thenReturn(replacement);
        when(cageMapper.selectByIdForUpdate(8L, 11L)).thenReturn(cage);
        when(rabbitMapper.selectActiveByCageForUpdate(8L, 11L))
            .thenReturn(List.of(rabbit));
        when(rabbitMapper.promoteReplacement(8L, 81L, null, "7")).thenReturn(1);
        when(cageMapper.updateRabbitCountAndStatus(8L, 11L, 1, "1", "7"))
            .thenReturn(1);
        when(replacementRecordMapper.markPromoted(
            org.mockito.ArgumentMatchers.eq(8L),
            org.mockito.ArgumentMatchers.eq(91L),
            any(Date.class),
            org.mockito.ArgumentMatchers.eq("7")
        )).thenReturn(1);
        when(operatorNameResolver.resolve(7L)).thenReturn("operator");

        RabbitService service = new RabbitService(
            rabbitMapper,
            cageMapper,
            mock(SettingService.class),
            replacementRecordMapper,
            mock(BatchRabbitMapper.class),
            mock(BatchMapper.class),
            mock(BreedingCycleMapper.class),
            null,
            stateMachine,
            operatorNameResolver,
            historyMapper,
            null,
            dedup,
            workTaskWriter,
            houseService,
            10
        );
        return new PromotionFixture(service, rabbitMapper, historyMapper, stateMachine);
    }

    private static Rabbit rabbit(String type, Date arrivalDate, Date growthStageEnteredAt) {
        Rabbit rabbit = new Rabbit();
        rabbit.setCageId(11L);
        rabbit.setType(type);
        rabbit.setGender("0");
        rabbit.setArrivalDate(arrivalDate);
        rabbit.setGrowthStageEnteredAt(growthStageEnteredAt);
        return rabbit;
    }

    private record CreationFixture(
        RabbitService service,
        ReplacementRecordMapper replacementRecordMapper,
        WorkTaskWriter workTaskWriter
    ) {
    }

    private record PromotionFixture(
        RabbitService service,
        RabbitMapper rabbitMapper,
        RabbitStatusHistoryMapper historyMapper,
        ReproStateMachineService stateMachine
    ) {
    }
}
