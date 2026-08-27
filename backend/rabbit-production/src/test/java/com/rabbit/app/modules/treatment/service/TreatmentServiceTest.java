package com.rabbit.app.modules.treatment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import com.rabbit.app.modules.treatment.mapper.TreatmentRecordMapper;
import com.rabbit.app.tracking.OperationContext;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 治疗的开始与结束。
 *
 * <p>与接种不同，治疗<b>会改兔只状态</b>：开单把兔子从「在栏」推到「治疗」，
 * 复查完成再推回来。所以每一步都要同时落两张表——treatment_records 和
 * rabbit_status_history——并且推进 state_version。三者必须同生共死：
 * 只写了记录没推版本，别的写操作就会拿着过期快照继续动这只兔；
 * 只推了版本没写履历，兔子的状态轨迹就断了一截。
 *
 * <p>最值钱的两条守卫都是<b>乐观锁</b>：{@code bumpStateVersionIfActive} 和
 * {@code bumpStateVersion} 影响 0 行，意味着这只兔在本次操作读取之后被别人
 * 卖了、死了或转走了。此时必须整体放弃，不能继续往下写——否则会给一只
 * 已经出栏的兔子开出一张在治的单子，用药记录挂在空气上。
 *
 * <p>另一条是<b>重复完成</b>：已经 DONE 的记录再走一次 complete，会二次推进
 * 版本并多写一条「复查完成」履历，让人以为复查了两次。
 */
class TreatmentServiceTest {
    private static final Long USER_ID = 9L;
    private static final Long HOUSE_ID = 1L;
    private static final Long RABBIT_ID = 7L;
    private static final Long RECORD_ID = 55L;
    private static final String REQ = "req-1";

    private RabbitMapper rabbitMapper;
    private TreatmentRecordMapper recordMapper;
    private RabbitStatusHistoryMapper historyMapper;
    private TreatmentService service;

    @BeforeEach
    void setUp() {
        rabbitMapper = mock(RabbitMapper.class);
        recordMapper = mock(TreatmentRecordMapper.class);
        historyMapper = mock(RabbitStatusHistoryMapper.class);
        service = new TreatmentService(rabbitMapper, recordMapper, historyMapper);
    }

    /** 跟踪上下文是 ThreadLocal，不清会漏给同线程的下一个用例。 */
    @AfterEach
    void tearDown() {
        OperationContext.clear();
    }

    // ---------- 开单：幂等回放 ----------

    /**
     * 命中回放要返回原记录。照常再开一张会让同一次治疗留下两条在治单，
     * 复查提醒也跟着重复。
     */
    @Test
    void aReplayedTreatmentReturnsTheStoredRecord() {
        bindReplay();
        TreatmentRecord stored = new TreatmentRecord();
        when(recordMapper.selectByReq(HOUSE_ID, RABBIT_ID, REQ)).thenReturn(stored);

        assertSame(stored, service.create(USER_ID, HOUSE_ID, record("阿莫西林"), REQ));

        verify(recordMapper, never()).insert(any());
        verifyNoInteractions(rabbitMapper, historyMapper);
    }

    @Test
    void aReplayWithNoStoredRecordEchoesTheRequest() {
        bindReplay();
        TreatmentRecord submitted = record("阿莫西林");
        when(recordMapper.selectByReq(HOUSE_ID, RABBIT_ID, REQ)).thenReturn(null);

        assertSame(submitted, service.create(USER_ID, HOUSE_ID, submitted, REQ));
        verify(recordMapper, never()).insert(any());
    }

    // ---------- 开单：入参与在场校验 ----------

    @Test
    void anEmptyOrUnaddressedRecordIsRejected() {
        assertEquals("治疗记录不能为空", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, null, REQ)).getMessage());

        TreatmentRecord noRabbit = record("阿莫西林");
        noRabbit.setRabbitId(null);
        assertEquals("rabbitId不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, noRabbit, REQ)).getMessage());

        TreatmentRecord badRabbit = record("阿莫西林");
        badRabbit.setRabbitId(0L);
        assertEquals("rabbitId不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, badRabbit, REQ)).getMessage());

        verifyNoInteractions(rabbitMapper, recordMapper, historyMapper);
    }

    @Test
    void treatingAnUnknownRabbitIsRejected() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, record("阿莫西林"), REQ));
        assertEquals(400, error.getCode());
        assertEquals("兔子不存在", error.getMessage());
        verify(recordMapper, never()).insert(any());
    }

    /**
     * 跨舍的兔只算作不存在，别让 A 舍的用药记录落到 B 舍的兔子上。
     */
    @Test
    void aRabbitFromAnotherHouseIsRejected() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(99L, true));

        assertEquals("兔子不存在", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, record("阿莫西林"), REQ)).getMessage());
    }

    /**
     * 已离场的兔不能开治疗单。这条要是漏了，卖出去的兔还会挂在待复查列表上，
     * 每天提醒人去治一只已经不在的兔子。
     */
    @Test
    void aRabbitThatHasLeftCannotBeTreated() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(HOUSE_ID, false));

        assertEquals("兔子不在场", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, record("阿莫西林"), REQ)).getMessage());

        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(HOUSE_ID, null));
        assertEquals("兔子不在场", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, record("阿莫西林"), REQ)).getMessage());

        verify(recordMapper, never()).insert(any());
    }

    // ---------- 开单：乐观锁 ----------

    /**
     * 读到在场、写的时候已经不在场——这就是 bumpStateVersionIfActive 影响 0 行
     * 的含义。必须整体放弃：继续写下去会给一只刚出栏的兔子建一张在治单，
     * 而它永远不会被复查完成，会一直留在待复查列表里。
     */
    @Test
    void aRabbitThatLeftBetweenTheReadAndTheWriteAbortsEverything() {
        stubActiveRabbit();
        when(rabbitMapper.bumpStateVersionIfActive(HOUSE_ID, RABBIT_ID, "9")).thenReturn(0);

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, record("阿莫西林"), REQ));
        assertEquals(409, error.getCode());
        assertEquals("兔只状态已变化，请刷新后重试", error.getMessage());
        verify(recordMapper, never()).insert(any());
        verifyNoInteractions(historyMapper);
    }

    // ---------- 开单：落库 ----------

    @Test
    void aNewTreatmentIsStampedOpenAndScopedToTheHouse() {
        stubActiveRabbit();
        stubBumpSucceeds();
        TreatmentRecord submitted = record("阿莫西林");

        TreatmentRecord saved = service.create(USER_ID, HOUSE_ID, submitted, REQ);

        assertSame(submitted, saved);
        assertEquals(HOUSE_ID, saved.getHouseId());
        assertEquals(TreatmentService.STATUS_OPEN, saved.getStatus());
        assertEquals(REQ, saved.getRequestId());
        verify(recordMapper).insert(submitted);
    }

    @Test
    void anAbsentStartDateFallsBackToNow() {
        stubActiveRabbit();
        stubBumpSucceeds();
        TreatmentRecord submitted = record("阿莫西林");
        submitted.setStartDate(null);

        assertNotNull(service.create(USER_ID, HOUSE_ID, submitted, REQ).getStartDate());
    }

    /**
     * 履历的时间必须取治疗单的开始日，不能取当下。补录三天前的治疗时，
     * 用当下会让状态轨迹的顺序错乱——「治疗」的时间点排在后来的事件之后。
     */
    @Test
    void theStatusHistoryFollowsTheTreatmentIntoTreatingAtItsStartDate() {
        stubActiveRabbit();
        stubBumpSucceeds();
        Date startDate = new Date(1_700_000_000_000L);
        TreatmentRecord submitted = record("阿莫西林");
        submitted.setStartDate(startDate);
        when(recordMapper.insert(any())).thenAnswer(call -> {
            call.<TreatmentRecord>getArgument(0).setId(RECORD_ID);
            return 1;
        });

        service.create(USER_ID, HOUSE_ID, submitted, REQ);

        RabbitStatusHistory history = capturedHistory();
        assertEquals(HOUSE_ID, history.getHouseId());
        assertEquals(RABBIT_ID, history.getRabbitId());
        assertEquals("在栏", history.getFromStatus());
        assertEquals("治疗", history.getToStatus());
        assertEquals(startDate, history.getChangeTime());
        assertEquals("治疗：阿莫西林", history.getReason());
        assertEquals(RECORD_ID, history.getRelatedRecordId());
        assertEquals("treatment_records", history.getRelatedRecordTable());
    }

    /**
     * 没填药名也要能开单，履历里留个空尾巴而不是 "治疗：null"——
     * 那串字会原样出现在给养殖户看的状态轨迹上。
     */
    @Test
    void aTreatmentWithoutADrugNameStillProducesAReadableReason() {
        stubActiveRabbit();
        stubBumpSucceeds();

        service.create(USER_ID, HOUSE_ID, record(null), REQ);

        assertEquals("治疗：", capturedHistory().getReason());
    }

    // ---------- 复查完成 ----------

    @Test
    void aReplayedCompletionWritesNothing() {
        bindReplay();

        service.complete(USER_ID, HOUSE_ID, RECORD_ID, null, null, REQ);

        verifyNoInteractions(recordMapper, rabbitMapper, historyMapper);
    }

    @Test
    void completingAnUnknownRecordIsRejected() {
        when(recordMapper.selectById(HOUSE_ID, RECORD_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.complete(USER_ID, HOUSE_ID, RECORD_ID, null, null, REQ));
        assertEquals(400, error.getCode());
        assertEquals("记录不存在", error.getMessage());
    }

    /**
     * 重复完成必须拦住。放过去会二次推进 state_version 并多写一条「复查完成」
     * 履历，看上去像是复查了两次；用药疗程的统计也会跟着重复计数。
     */
    @Test
    void completingAnAlreadyClosedRecordIsAConflict() {
        when(recordMapper.selectById(HOUSE_ID, RECORD_ID))
                .thenReturn(storedRecord(TreatmentService.STATUS_DONE));

        BizException error = assertThrows(BizException.class,
                () -> service.complete(USER_ID, HOUSE_ID, RECORD_ID, null, null, REQ));
        assertEquals(409, error.getCode());
        assertEquals("记录已完成", error.getMessage());
        verify(rabbitMapper, never()).bumpStateVersion(anyLong(), anyLong(), anyString());
        verify(recordMapper, never()).updateStatus(anyLong(), anyLong(), anyString(), anyString());
        verifyNoInteractions(historyMapper);
    }

    /**
     * 完成时兔只状态已变，同样要整体放弃。不然会把一只已经卖掉的兔子的
     * 治疗单标成复查完成，还补一条它其实不在栏时的状态履历。
     */
    @Test
    void aStateChangeDuringCompletionAbortsTheUpdate() {
        when(recordMapper.selectById(HOUSE_ID, RECORD_ID))
                .thenReturn(storedRecord(TreatmentService.STATUS_OPEN));
        when(rabbitMapper.bumpStateVersion(HOUSE_ID, RABBIT_ID, "9")).thenReturn(0);

        BizException error = assertThrows(BizException.class,
                () -> service.complete(USER_ID, HOUSE_ID, RECORD_ID, null, null, REQ));
        assertEquals(409, error.getCode());
        verify(recordMapper, never()).updateStatus(anyLong(), anyLong(), anyString(), anyString());
        verifyNoInteractions(historyMapper);
    }

    @Test
    void aCompletedTreatmentIsClosedAndLoggedBackToReviewed() {
        when(recordMapper.selectById(HOUSE_ID, RECORD_ID))
                .thenReturn(storedRecord(TreatmentService.STATUS_OPEN));
        when(rabbitMapper.bumpStateVersion(HOUSE_ID, RABBIT_ID, "9")).thenReturn(1);
        Date completeTime = new Date(1_700_000_000_000L);

        service.complete(USER_ID, HOUSE_ID, RECORD_ID, completeTime, null, REQ);

        verify(recordMapper).updateStatus(HOUSE_ID, RECORD_ID, TreatmentService.STATUS_DONE, "9");
        RabbitStatusHistory history = capturedHistory();
        assertEquals("治疗", history.getFromStatus());
        assertEquals("复查完成", history.getToStatus());
        assertEquals(completeTime, history.getChangeTime());
        assertEquals(RABBIT_ID, history.getRabbitId());
        assertEquals(RECORD_ID, history.getRelatedRecordId());
    }

    @Test
    void anAbsentCompletionTimeFallsBackToNow() {
        when(recordMapper.selectById(HOUSE_ID, RECORD_ID))
                .thenReturn(storedRecord(TreatmentService.STATUS_OPEN));
        when(rabbitMapper.bumpStateVersion(HOUSE_ID, RABBIT_ID, "9")).thenReturn(1);

        service.complete(USER_ID, HOUSE_ID, RECORD_ID, null, null, REQ);

        assertNotNull(capturedHistory().getChangeTime());
    }

    /**
     * 目标兔只只有进了方法才查得到，要补回上下文，否则这次操作产生的事件
     * 会缺少兔只坐标，事后按兔子回溯操作履历就少一条。
     */
    @Test
    void theTrackingContextLearnsTheRabbitBehindTheRecord() {
        OperationContext context = OperationContext.bind();
        when(recordMapper.selectById(HOUSE_ID, RECORD_ID))
                .thenReturn(storedRecord(TreatmentService.STATUS_OPEN));
        when(rabbitMapper.bumpStateVersion(HOUSE_ID, RABBIT_ID, "9")).thenReturn(1);

        service.complete(USER_ID, HOUSE_ID, RECORD_ID, null, null, REQ);

        assertEquals(RABBIT_ID, context.getRabbitId());
    }

    // ---------- 查询 ----------

    @Test
    void historyLimitsAreClamped() {
        service.listByRabbit(HOUSE_ID, RABBIT_ID, 0);
        verify(recordMapper).selectByRabbit(HOUSE_ID, RABBIT_ID, 50);

        service.listByRabbit(HOUSE_ID, RABBIT_ID, 9999);
        verify(recordMapper).selectByRabbit(HOUSE_ID, RABBIT_ID, 200);
    }

    @Test
    void theDueReviewListIsScopedToTheHouse() {
        service.listDueReviews(HOUSE_ID);
        verify(recordMapper).selectDueReviewsByHouse(any(), any(Date.class));
    }

    // ---------- 夹具 ----------

    private void bindReplay() {
        OperationContext context = OperationContext.bind();
        context.setDedupReplay(true);
    }

    private void stubActiveRabbit() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(HOUSE_ID, true));
    }

    private void stubBumpSucceeds() {
        when(rabbitMapper.bumpStateVersionIfActive(HOUSE_ID, RABBIT_ID, "9")).thenReturn(1);
    }

    private RabbitStatusHistory capturedHistory() {
        ArgumentCaptor<RabbitStatusHistory> history = ArgumentCaptor.forClass(RabbitStatusHistory.class);
        verify(historyMapper).insert(history.capture());
        return history.getValue();
    }

    private Rabbit rabbit(Long houseId, Boolean active) {
        Rabbit r = new Rabbit();
        r.setId(RABBIT_ID);
        r.setHouseId(houseId);
        r.setIsActive(active);
        return r;
    }

    private TreatmentRecord record(String drug) {
        TreatmentRecord r = new TreatmentRecord();
        r.setRabbitId(RABBIT_ID);
        r.setDrug(drug);
        r.setStartDate(new Date(1_700_000_000_000L));
        return r;
    }

    private TreatmentRecord storedRecord(String status) {
        TreatmentRecord r = record("阿莫西林");
        r.setId(RECORD_ID);
        r.setHouseId(HOUSE_ID);
        r.setStatus(status);
        return r;
    }
}
