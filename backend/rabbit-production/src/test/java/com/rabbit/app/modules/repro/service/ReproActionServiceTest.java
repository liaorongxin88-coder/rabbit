package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.repro.domain.DeliveryOutcome;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 生产动作编排层：状态迁移与领域副作用必须同生共死。
 *
 * <p>这一层唯一的职责就是把「周期怎么走」和「仔兔去哪儿 / 绩效记几笔」收进同一个事务。
 * 它出错的形态很特殊——状态机本身完全正确，脏的是副作用：幂等回放时副作用跑第二遍
 * 就是凭空多出一窝兔子、产仔数在繁殖成绩里加两遍；离场只关了被点名的那条周期，
 * 剩下那条会 OPEN 着却再没有待办，批次被一条看不见的周期永久卡住无法结束。
 * 这两类都不会报错，只会在几周后以「数字对不上」的形式浮出来。
 */
class ReproActionServiceTest {
    private static final Long HOUSE_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long CYCLE_ID = 100L;
    private static final Long MOTHER_ID = 10L;
    private static final Long BUCK_ID = 20L;
    private static final Long BATCH_ID = 30L;
    private static final String OPERATOR = "张三";

    private ReproStateMachineService stateMachine;
    private KitPlacementService kitPlacementService;
    private DeliveryAftercareService deliveryAftercareService;
    private ReproCycleMapper reproCycleMapper;
    private ReproActionService service;

    @BeforeEach
    void setUp() {
        stateMachine = mock(ReproStateMachineService.class);
        kitPlacementService = mock(KitPlacementService.class);
        deliveryAftercareService = mock(DeliveryAftercareService.class);
        reproCycleMapper = mock(ReproCycleMapper.class);
        service = new ReproActionService(
            stateMachine, kitPlacementService, deliveryAftercareService, reproCycleMapper
        );

        when(reproCycleMapper.selectById(HOUSE_ID, CYCLE_ID)).thenReturn(cycle(CYCLE_ID));
        when(reproCycleMapper.selectOpenByMother(anyLong(), anyLong())).thenReturn(List.of());
        when(kitPlacementService.registerPending(any())).thenReturn(weaningRecord(300L, 4));
    }

    // ------------------------------------------------------------------ 幂等

    /**
     * 回放意味着副作用早已发生过。这里再跑一次，落位服务会给同一窝仔兔再登记一遍待落位，
     * 繁殖成绩里这胎的产仔数也会被计两次——两者都没有任何唯一键兜底。
     */
    @Test
    void aReplayedWeaningDoesNotRegisterThePlacementASecondTime() {
        when(stateMachine.apply(any())).thenReturn(result(true));

        ReproResult result = service.apply(
            command(ReproAction.WEANING).weanedCount(6).build(), placement());

        assertTrue(result.replayed());
        verify(kitPlacementService, never()).registerPending(any());
        assertNull(result.weaningRecordId());
    }

    @Test
    void aReplayedDeliveryDoesNotRecordThePerformanceASecondTime() {
        when(stateMachine.apply(any())).thenReturn(result(true));

        service.apply(deliveryCommand(DeliveryOutcome.BORN).build(), null);

        verify(deliveryAftercareService, never()).record(
            anyLong(), anyLong(), anyInt(), anyInt(), any(), anyBoolean(), any(), anyString());
    }

    /**
     * 离场的回放同样要短路：否则每重试一次就把这只母兔剩余的周期再遍历关闭一轮，
     * 事件流里堆出一串重复的离场记录。
     */
    @Test
    void aReplayedRetireDoesNotSweepTheRemainingCyclesAgain() {
        when(stateMachine.apply(any())).thenReturn(result(true));

        service.apply(command(ReproAction.RETIRE).build(), null);

        verify(stateMachine, times(1)).apply(any());
        verify(reproCycleMapper, never()).selectOpenByMother(anyLong(), anyLong());
    }

    // ------------------------------------------------------------------ 接产

    @Test
    void aNormalDeliveryFeedsTheActualCountsIntoThePerformanceRecord() {
        when(stateMachine.apply(any())).thenReturn(result(false));
        Date occurred = DateUtil.plusDays(DateUtil.now(), -1);

        service.apply(deliveryCommand(DeliveryOutcome.BORN)
            .totalKits(9).liveKits(7).occurredAt(occurred).remark("顺产").build(), null);

        verify(deliveryAftercareService).record(
            HOUSE_ID, MOTHER_ID, 9, 7, occurred, false, "顺产", OPERATOR);
    }

    /**
     * 分娩失败按 0 仔记入绩效，而不是把表单里残留的数字带进去。带进去的话这胎会同时
     * 被计为「失败」和「产了 N 只」，产活率的分子分母各错一次。
     */
    @Test
    void aFailedDeliveryIsRecordedAsZeroKitsRegardlessOfThePayload() {
        when(stateMachine.apply(any())).thenReturn(result(false));

        service.apply(deliveryCommand(DeliveryOutcome.FAILED)
            .totalKits(9).liveKits(7).remark("难产").build(), null);

        verify(deliveryAftercareService).record(
            eq(HOUSE_ID), eq(MOTHER_ID), eq(0), eq(0), any(), eq(true), eq("难产"), eq(OPERATOR));
    }

    /** outcome 的大小写来自不同客户端，判失败时不能只认全大写。 */
    @Test
    void aFailedDeliveryIsRecognisedRegardlessOfOutcomeCasing() {
        when(stateMachine.apply(any())).thenReturn(result(false));

        service.apply(command(ReproAction.DELIVERY)
            .outcome("failed").totalKits(0).liveKits(0).keptKits(0).remark("难产").build(), null);

        verify(deliveryAftercareService).record(
            anyLong(), anyLong(), anyInt(), anyInt(), any(), eq(true), any(), anyString());
    }

    // ------------------------------------------------------------------ 分笼

    /**
     * 落位的批次、周期、母兔和公兔都必须取自库里那条周期，而不是请求里的字段：
     * 仔兔的血缘和批次归属一旦写错，后续所有系谱和批次统计都跟着错，且无从更正。
     */
    @Test
    void thePlacementTakesItsLineageFromTheStoredCycleNotTheRequest() {
        when(stateMachine.apply(any())).thenReturn(result(false));
        Date occurred = DateUtil.plusDays(DateUtil.now(), -1);

        service.apply(command(ReproAction.WEANING)
            .weanedCount(6).avgWeaningWeight(0.62).occurredAt(occurred).remark("分笼").build(),
            new ReproActionService.PlacementInput(88L, 3, 3));

        ArgumentCaptor<KitPlacementCommand> placement =
            ArgumentCaptor.forClass(KitPlacementCommand.class);
        verify(kitPlacementService).registerPending(placement.capture());
        KitPlacementCommand value = placement.getValue();
        assertEquals(BATCH_ID, value.batchId());
        assertEquals(CYCLE_ID, value.cycleId());
        assertEquals(MOTHER_ID, value.motherRabbitId());
        assertEquals(BUCK_ID, value.sireRabbitId());
        assertEquals(6, value.weanedCount());
        assertEquals(88L, value.targetCageId());
        assertEquals(3, value.maleCount());
        assertEquals(occurred, value.weaningDate());
    }

    /** 不传落位入参时按「不指定笼位、不分性别」处理，而不是 NPE。 */
    @Test
    void anAbsentPlacementInputIsTreatedAsUnspecifiedRatherThanFailing() {
        when(stateMachine.apply(any())).thenReturn(result(false));

        service.apply(command(ReproAction.WEANING).weanedCount(6).build(), null);

        ArgumentCaptor<KitPlacementCommand> placement =
            ArgumentCaptor.forClass(KitPlacementCommand.class);
        verify(kitPlacementService).registerPending(placement.capture());
        assertNull(placement.getValue().targetCageId());
        assertNull(placement.getValue().maleCount());
    }

    /** 断奶只数缺失按 0 只登记，避免把 null 传进落位的整数字段。 */
    @Test
    void anAbsentWeanedCountIsPlacedAsZero() {
        when(stateMachine.apply(any())).thenReturn(result(false));

        service.apply(command(ReproAction.WEANING).build(), placement());

        ArgumentCaptor<KitPlacementCommand> placement =
            ArgumentCaptor.forClass(KitPlacementCommand.class);
        verify(kitPlacementService).registerPending(placement.capture());
        assertEquals(0, placement.getValue().weanedCount());
    }

    /** 分笼结果要把落位单号和待落位数带回给客户端，否则前端拿不到后续落位入口。 */
    @Test
    void theWeaningResultCarriesThePlacementRecordBackToTheCaller() {
        when(stateMachine.apply(any())).thenReturn(result(false));

        ReproResult result = service.apply(
            command(ReproAction.WEANING).weanedCount(6).build(), placement());

        assertEquals(300L, result.weaningRecordId());
        assertEquals(4, result.waitingCount());
        assertEquals(CYCLE_ID, result.cycleId());
    }

    // ------------------------------------------------------------------ 离场

    /**
     * 血配时母兔同时持有哺乳与怀孕两条周期，状态机只关得掉被点名的那一条。剩下那条会
     * OPEN 着却再也没有待办：崽子等不到分笼提醒，批次也被这条看不见的周期永久卡住。
     */
    @Test
    void retiringSweepsEveryRemainingOpenCycleOfTheSameMother() {
        when(stateMachine.apply(any())).thenReturn(result(false));
        when(reproCycleMapper.selectOpenByMother(HOUSE_ID, MOTHER_ID))
            .thenReturn(List.of(cycle(201L), cycle(202L)));

        service.apply(command(ReproAction.RETIRE).reason("淘汰").build(), null);

        ArgumentCaptor<ReproCommand> commands = ArgumentCaptor.forClass(ReproCommand.class);
        verify(stateMachine, times(3)).apply(commands.capture());
        assertEquals(List.of(CYCLE_ID, 201L, 202L),
            commands.getAllValues().stream().map(ReproCommand::getCycleId).toList());
        assertTrue(commands.getAllValues().stream()
            .allMatch(each -> each.getAction() == ReproAction.RETIRE));
    }

    /**
     * 每条补发的离场必须有自己的幂等键。共用一个 requestId 的话，第二条会命中第一条的
     * 回放而被静默跳过——正好回到「剩余周期没关掉」这个 bug。
     */
    @Test
    void eachSweptCycleGetsItsOwnIdempotencyKey() {
        when(stateMachine.apply(any())).thenReturn(result(false));
        when(reproCycleMapper.selectOpenByMother(HOUSE_ID, MOTHER_ID))
            .thenReturn(List.of(cycle(201L), cycle(202L)));

        service.apply(command(ReproAction.RETIRE).requestId("req-1").build(), null);

        ArgumentCaptor<ReproCommand> commands = ArgumentCaptor.forClass(ReproCommand.class);
        verify(stateMachine, times(3)).apply(commands.capture());
        assertEquals("req-1-retire-201", commands.getAllValues().get(1).getRequestId());
        assertEquals("req-1-retire-202", commands.getAllValues().get(2).getRequestId());
    }

    /** 首条离场之后周期已被删或查不到时安静收手，不能拿 null 去取母兔 id。 */
    @Test
    void aVanishedCycleStopsTheSweepInsteadOfFailing() {
        when(stateMachine.apply(any())).thenReturn(result(false));
        when(reproCycleMapper.selectById(HOUSE_ID, CYCLE_ID)).thenReturn(null);

        service.apply(command(ReproAction.RETIRE).build(), null);

        verify(stateMachine, times(1)).apply(any());
    }

    @Test
    void retireMotherReportsHowManyCyclesItActuallyClosed() {
        when(stateMachine.apply(any())).thenReturn(result(false));
        when(reproCycleMapper.selectOpenByMother(HOUSE_ID, MOTHER_ID))
            .thenReturn(List.of(cycle(201L), cycle(202L)));

        int closed = service.retireMother(
            HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, DateUtil.now(), "死亡", "req-1");

        assertEquals(2, closed);
        verify(stateMachine, times(2)).apply(any());
    }

    @Test
    void retireMotherWithNothingOpenClosesNothing() {
        assertEquals(0, service.retireMother(
            HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, DateUtil.now(), "死亡", "req-1"));
        verify(stateMachine, never()).apply(any());
    }

    // ------------------------------------------------------------ 其余动作

    /**
     * 配种、摸胎、备产这些动作没有落位或绩效副作用，编排层必须原样透传。多调一次
     * 落位服务就等于给一窝还不存在的仔兔开了落位单。
     */
    @Test
    void anActionWithNoDomainSideEffectIsPassedThroughUntouched() {
        ReproResult expected = result(false);
        when(stateMachine.apply(any())).thenReturn(expected);

        ReproResult actual = service.apply(command(ReproAction.MATING).build(), placement());

        assertSame(expected, actual);
        verify(kitPlacementService, never()).registerPending(any());
        verify(deliveryAftercareService, never()).record(
            anyLong(), anyLong(), anyInt(), anyInt(), any(), anyBoolean(), any(), anyString());
        verify(reproCycleMapper, never()).selectById(anyLong(), anyLong());
    }

    /** 没有操作人姓名时副作用留痕回落到用户 id，与状态机保持同一口径。 */
    @Test
    void anAnonymousOperatorFallsBackToTheUserIdInSideEffects() {
        when(stateMachine.apply(any())).thenReturn(result(false));

        service.apply(deliveryCommand(DeliveryOutcome.BORN)
            .operatorName(null).totalKits(8).liveKits(8).build(), null);

        verify(deliveryAftercareService).record(
            anyLong(), anyLong(), anyInt(), anyInt(), any(), anyBoolean(), any(),
            eq(String.valueOf(USER_ID)));
    }

    // ------------------------------------------------------------------ 夹具

    private ReproCommand.Builder command(ReproAction action) {
        return ReproCommand.builder()
            .houseId(HOUSE_ID)
            .userId(USER_ID)
            .operatorName(OPERATOR)
            .cycleId(CYCLE_ID)
            .action(action)
            .occurredAt(DateUtil.plusDays(DateUtil.now(), -1))
            .requestId("req-1");
    }

    private ReproCommand.Builder deliveryCommand(DeliveryOutcome outcome) {
        return command(ReproAction.DELIVERY)
            .outcome(outcome.name())
            .totalKits(0)
            .liveKits(0)
            .keptKits(0);
    }

    private ReproActionService.PlacementInput placement() {
        return new ReproActionService.PlacementInput(88L, 3, 3);
    }

    private ReproResult result(boolean replayed) {
        return new ReproResult(
            CYCLE_ID, CYCLE_ID, 900L, 700L, 800L,
            ReproStage.READY, "OPEN", null, null, replayed);
    }

    private ReproCycle cycle(Long id) {
        ReproCycle cycle = new ReproCycle();
        cycle.setId(id);
        cycle.setHouseId(HOUSE_ID);
        cycle.setBatchId(BATCH_ID);
        cycle.setMotherRabbitId(MOTHER_ID);
        cycle.setMaleRabbitId(BUCK_ID);
        cycle.setStage(ReproStage.AWAIT_WEANING.name());
        return cycle;
    }

    private WeaningRecord weaningRecord(Long id, Integer waiting) {
        WeaningRecord record = new WeaningRecord();
        record.setId(id);
        record.setWaitingCount(waiting);
        return record;
    }
}
