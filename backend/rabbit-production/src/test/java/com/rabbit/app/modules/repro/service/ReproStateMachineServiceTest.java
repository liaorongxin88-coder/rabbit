package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.file.service.BusinessFileService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.repro.domain.CycleLifecycle;
import com.rabbit.app.modules.repro.domain.CycleResult;
import com.rabbit.app.modules.repro.domain.DeliveryOutcome;
import com.rabbit.app.modules.repro.domain.LitterStatus;
import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproSettings;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.domain.TaskSubjectType;
import com.rabbit.app.modules.repro.entity.BizAttachment;
import com.rabbit.app.modules.repro.entity.Litter;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.BizAttachmentMapper;
import com.rabbit.app.modules.repro.mapper.LitterMapper;
import com.rabbit.app.modules.repro.mapper.RabbitStageProjectionMapper;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/**
 * 繁殖状态机的写路径守卫。
 *
 * <p>这个类是母兔生命周期唯一的写入口，而 {@code WorkTaskService.bulkApply}
 * 直连它、不经编排层：任何漏掉的校验都会被批量待办整片放大。状态机一旦放错行，
 * 现场只会看到「这只兔子怎么跳到这个阶段了」，从现象几乎无法倒推成因，
 * 所以下面的用例挑的都是「错了就留下不可逆脏数据」的那些判断。
 *
 * <p>覆盖的四类风险：
 * <ul>
 *   <li><b>幂等</b>——重复提交必须原样回放，不能第二次推进状态；</li>
 *   <li><b>并发</b>——先读后锁之间归属被改、或 state_version 过期，都必须 409 而不是静默覆盖；</li>
 *   <li><b>事实校验</b>——仔数、摸胎结论、复查日期这些一旦写歪就无法从报表倒查；</li>
 *   <li><b>投影与接续</b>——血配场景下母兔阶段投影和后继周期最容易被写成上一轮的影子。</li>
 * </ul>
 */
class ReproStateMachineServiceTest {
    private static final Long HOUSE_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long CYCLE_ID = 100L;
    private static final Long MOTHER_ID = 10L;
    private static final Long BUCK_ID = 20L;
    private static final Long BATCH_ID = 30L;
    private static final Long MEMBER_ID = 501L;
    private static final Long CAGE_ID = 55L;
    private static final Long LITTER_ID = 700L;
    private static final Long EVENT_ID = 900L;
    private static final Long TASK_ID = 800L;
    private static final Long VERSION = 7L;
    private static final String OPERATOR = "张三";
    private static final ReproSettings SETTINGS = new ReproSettings(2, 12, 15, 30, 10, 75, 90);

    private ReproCycleMapper reproCycleMapper;
    private ReproEventMapper reproEventMapper;
    private LitterMapper litterMapper;
    private BizAttachmentMapper bizAttachmentMapper;
    private RabbitStageProjectionMapper rabbitStageProjectionMapper;
    private RabbitMapper rabbitMapper;
    private BatchMapper batchMapper;
    private BatchRabbitMapper batchRabbitMapper;
    private RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private WorkTaskWriter workTaskWriter;
    private ReproSettingResolver settingResolver;
    private BreedingEligibilityValidator eligibilityValidator;
    private BusinessFileService businessFileService;
    private ReproStateMachineService service;

    @BeforeEach
    void setUp() {
        reproCycleMapper = mock(ReproCycleMapper.class);
        reproEventMapper = mock(ReproEventMapper.class);
        litterMapper = mock(LitterMapper.class);
        bizAttachmentMapper = mock(BizAttachmentMapper.class);
        rabbitStageProjectionMapper = mock(RabbitStageProjectionMapper.class);
        rabbitMapper = mock(RabbitMapper.class);
        batchMapper = mock(BatchMapper.class);
        batchRabbitMapper = mock(BatchRabbitMapper.class);
        rabbitStatusHistoryMapper = mock(RabbitStatusHistoryMapper.class);
        workTaskWriter = mock(WorkTaskWriter.class);
        settingResolver = mock(ReproSettingResolver.class);
        eligibilityValidator = mock(BreedingEligibilityValidator.class);
        businessFileService = mock(BusinessFileService.class);

        service = new ReproStateMachineService(
            reproCycleMapper,
            reproEventMapper,
            litterMapper,
            bizAttachmentMapper,
            rabbitStageProjectionMapper,
            rabbitMapper,
            batchMapper,
            batchRabbitMapper,
            rabbitStatusHistoryMapper,
            workTaskWriter,
            settingResolver,
            eligibilityValidator,
            new ObjectMapper(),
            businessFileService
        );

        when(settingResolver.resolve(any(), any())).thenReturn(SETTINGS);
        when(businessFileService.requireImages(any(), any(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(batchMapper.selectByIdForUpdate(anyLong(), anyLong())).thenReturn(activeBatch());
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), any())).thenReturn(List.of(mother()));
        when(rabbitMapper.selectById(HOUSE_ID, MOTHER_ID)).thenReturn(mother());
        when(batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(anyLong(), anyLong(), anyLong()))
            .thenReturn(breedingMember(MEMBER_ID));
        when(batchRabbitMapper.deactivateIfActive(anyLong(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(2);
        when(reproCycleMapper.applyTransition(any(), any())).thenReturn(1);
        when(reproCycleMapper.selectMaxCycleNo(any(), any(), any())).thenReturn(null);
        when(reproCycleMapper.selectOpenPipelineForUpdate(anyLong(), anyLong())).thenReturn(null);
        when(reproCycleMapper.selectOpenByMother(anyLong(), anyLong())).thenReturn(List.of());
        when(workTaskWriter.pendingBySubject(anyLong(), any(), anyLong())).thenReturn(List.of());
        when(workTaskWriter.schedule(any())).thenAnswer(invocation -> {
            WorkTaskWriter.TaskScheduleRequest request = invocation.getArgument(0);
            return task(TASK_ID, request.dueTime());
        });
        when(reproEventMapper.insert(any())).thenAnswer(invocation -> {
            ReproEvent event = invocation.getArgument(0);
            event.setId(EVENT_ID);
            return 1;
        });
        when(litterMapper.insert(any())).thenAnswer(invocation -> {
            Litter litter = invocation.getArgument(0);
            litter.setId(LITTER_ID);
            return 1;
        });
    }

    // ------------------------------------------------------------------ 幂等回放

    /**
     * 幂等的意义在于「重放不是第二次推进」。这条守卫一旦破，网络抖动下的一次重试
     * 就会把母兔多推一个阶段——而且事件流里看不出异常，只有阶段莫名前进。
     */
    @Test
    void aReplayedRequestReturnsTheFirstResultAndTouchesNoState() {
        ReproEvent first = replayEvent("{\"resultHasNextTask\":false}");
        when(reproEventMapper.selectByRequestId(HOUSE_ID, "req-1")).thenReturn(first);

        ReproResult result = service.apply(command(ReproAction.ESTRUS).requestId("req-1").build());

        assertTrue(result.replayed());
        assertEquals(EVENT_ID, result.eventId());
        verify(reproCycleMapper, never()).selectByIdForUpdate(anyLong(), anyLong());
        verify(reproCycleMapper, never()).applyTransition(any(), any());
        verify(reproEventMapper, never()).insert(any());
        verify(workTaskWriter, never()).schedule(any());
    }

    /**
     * 空 requestId 不是「免幂等」而是非法输入：真放过去，重复提交就再也识别不出来了。
     * 同时确认它没有拿空串去查回放（空串在库里能撞上任意历史行）。
     */
    @Test
    void aBlankRequestIdIsRejectedRatherThanSkippingIdempotency() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).requestId("  ").build()));

        assertEquals(400, error.getCode());
        assertEquals("requestId不能为空", error.getMessage());
        verify(reproEventMapper, never()).selectByRequestId(anyLong(), anyString());
    }

    /**
     * 首次结果里明确记着「这一步不产生后续待办」时，回放不能再去猜一个到期时间：
     * 猜出来的日期会让客户端以为还有下一条待办，用户等一个永远不来的提醒。
     */
    @Test
    void aReplayThatRecordedNoFollowUpTaskReportsNoDueTime() {
        when(reproEventMapper.selectByRequestId(HOUSE_ID, "req-1"))
            .thenReturn(replayEvent("{\"resultHasNextTask\":false}"));

        ReproResult result = service.apply(command(ReproAction.RETIRE).requestId("req-1").build());

        assertTrue(result.replayed());
        assertNull(result.nextDueTime());
        verify(workTaskWriter, never()).pendingBySubject(anyLong(), any(), anyLong());
    }

    /**
     * 分笼后原周期已关闭，待办挂在同事务新开的那条周期上。回放若只看原周期就永远查不到
     * 到期时间，用户重试一次就看到「没有下一步」，而实际待办正静静躺在后继周期里。
     */
    @Test
    void aReplayOfAClosedCycleFallsBackToTheSuccessorCycleForItsDueTime() {
        ReproCycle closed = openCycle(ReproStage.AWAIT_WEANING, BATCH_ID);
        closed.setLifecycle(CycleLifecycle.CLOSED.name());
        ReproCycle successor = openCycle(ReproStage.READY, null);
        successor.setId(101L);
        Date due = daysFromNow(10);

        when(reproEventMapper.selectByRequestId(HOUSE_ID, "req-1"))
            .thenReturn(replayEvent("{\"resultHasNextTask\":true}"));
        when(reproCycleMapper.selectById(HOUSE_ID, CYCLE_ID)).thenReturn(closed);
        when(reproCycleMapper.selectOpenByMother(HOUSE_ID, MOTHER_ID)).thenReturn(List.of(successor));
        when(workTaskWriter.pendingBySubject(HOUSE_ID, TaskSubjectType.CYCLE, 101L))
            .thenReturn(List.of(task(TASK_ID, due)));

        ReproResult result = service.apply(command(ReproAction.WEANING).requestId("req-1").build());

        assertTrue(result.replayed());
        assertEquals(due, result.nextDueTime());
    }

    // -------------------------------------------------------------- 锁与归属不变式

    @Test
    void aMissingCycleIsRejectedAsNotFound() {
        when(reproCycleMapper.selectById(HOUSE_ID, CYCLE_ID)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).build())).getCode());
    }

    @Test
    void aClosedCycleCannotBeAdvanced() {
        ReproCycle closed = openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID);
        closed.setLifecycle(CycleLifecycle.CLOSED.name());
        stubCycle(closed);

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).build()));
        assertEquals(409, error.getCode());
        assertEquals("该生产周期已结束，无法继续操作", error.getMessage());
    }

    /**
     * 校验读的是未加锁的快照，真正推进的是加锁后的行。若两者归属不同，说明中间有人
     * 把周期挪了批次——此时前面那轮成员关系校验校的是另一条数据，等于没校。
     */
    @Test
    void aCycleWhoseBatchChangedBetweenTheReadAndTheLockIsRejected() {
        ReproCycle observed = openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID);
        ReproCycle locked = openCycle(ReproStage.AWAIT_ESTRUS, 999L);
        stubCycle(observed, locked);

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).build()));
        assertEquals(409, error.getCode());
        assertEquals("生产周期归属已变化，请刷新后重试", error.getMessage());
    }

    @Test
    void aCycleWhoseMotherChangedBetweenTheReadAndTheLockIsRejected() {
        ReproCycle observed = openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID);
        ReproCycle locked = openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID);
        locked.setMotherRabbitId(11L);
        stubCycle(observed, locked);

        assertEquals("生产周期归属已变化，请刷新后重试", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).build())).getMessage());
    }

    /**
     * 摸胎及之后的阶段必然已经绑过批次。没有批次却停在这些阶段，说明数据已经坏了，
     * 这时继续推进只会把坏数据摊进事件流和窝记录，必须当场停住。
     */
    @Test
    void aPipelineStageWithoutABatchIsRejectedAsCorruptData() {
        stubCycle(openCycle(ReproStage.AWAIT_PALPATION, null));

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.PALPATION)
                .palpationResult(PalpationResult.PREGNANT).build()));
        assertEquals(409, error.getCode());
        assertEquals("该生产阶段缺少生产批次，请联系管理员修复", error.getMessage());
    }

    /** 休养 / 待催情 / 待配种三段本就不该有批次，批次要到配种时才绑。 */
    @Test
    void anEarlyStageCycleIsAllowedToRunWithoutABatch() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, null));

        assertDoesNotThrow(() -> service.apply(command(ReproAction.ESTRUS).build()));
        verify(batchMapper, never()).selectByIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void aBatchThatIsNoLongerRunningBlocksTheTransition() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));
        Batch finished = activeBatch();
        finished.setStatus("已结束");
        when(batchMapper.selectByIdForUpdate(HOUSE_ID, BATCH_ID)).thenReturn(finished);

        assertEquals("生产批次不在进行中", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.PREPARTUM).build())).getMessage());
    }

    /**
     * 成员关系是「这只母兔属于这个批次」的唯一凭据。它没了还继续推进，产出的窝会挂到
     * 一个母兔已经不在的批次上，批次统计从此对不上，而且没有任何报错留痕。
     */
    @Test
    void aMissingBatchMembershipBlocksTheTransition() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));
        when(batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(HOUSE_ID, BATCH_ID, MOTHER_ID))
            .thenReturn(null);

        assertEquals("生产周期对应的繁殖批次成员关系不存在", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.PREPARTUM).build())).getMessage());
    }

    @Test
    void aMemberWhoseRoleIsNotBreedingBlocksTheTransition() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));
        BatchRabbit fattening = breedingMember(MEMBER_ID);
        fattening.setBatchRole("commodity");
        when(batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(HOUSE_ID, BATCH_ID, MOTHER_ID))
            .thenReturn(fattening);

        assertEquals("生产周期对应的繁殖批次成员关系不存在", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.PREPARTUM).build())).getMessage());
    }

    @Test
    void aDepartedMotherBlocksTheTransition() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));
        Rabbit gone = mother();
        gone.setIsActive(Boolean.FALSE);
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), any())).thenReturn(List.of(gone));

        assertEquals("母兔不在场", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.PREPARTUM).build())).getMessage());
    }

    // ------------------------------------------------------------------ 乐观锁

    @Test
    void aStaleStateVersionIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));
        when(reproCycleMapper.applyTransition(any(), any())).thenReturn(0);

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).build()));
        assertEquals(409, error.getCode());
        assertEquals("状态已变化，请刷新后重试", error.getMessage());
    }

    /**
     * 比对用的版本号必须是「加锁那一刻读到的」。若误取成本次转换写完之后的值，
     * 乐观锁的 where 条件永远自洽，后写者会静默覆盖先写者而不报 409。
     */
    @Test
    void theVersionComparedOnWriteIsTheOneReadUnderTheRowLock() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));

        service.apply(command(ReproAction.ESTRUS).build());

        ArgumentCaptor<Long> version = ArgumentCaptor.forClass(Long.class);
        verify(reproCycleMapper).applyTransition(any(), version.capture());
        assertEquals(VERSION, version.getValue());
    }

    // -------------------------------------------------------------- 转换表与分流

    @Test
    void anActionTheCurrentStageDoesNotAllowIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.PALPATION)
                .palpationResult(PalpationResult.PREGNANT).build()));
        assertEquals(409, error.getCode());
        assertEquals("当前阶段【待配种】不允许执行【摸胎】", error.getMessage());
    }

    /**
     * 摸胎的三向分流语义上就写在 palpationResult 里。若判别值只读 outcome，
     * HTTP 客户端必须把同一个结论重复填两个字段，漏填就得到一句看不出原因的
     * 「当前阶段不允许摸胎」——正是被测类注释点名要修掉的那个坑。
     */
    @Test
    void aPalpationIsRoutedByItsResultEvenWhenTheOutcomeFieldIsEmpty() {
        stubCycle(openCycle(ReproStage.AWAIT_PALPATION, BATCH_ID));

        service.apply(command(ReproAction.PALPATION)
            .palpationResult(PalpationResult.PREGNANT)
            .outcome(null)
            .build());

        assertEquals(ReproStage.AWAIT_PREPARTUM.name(), capturedCycle().getStage());
    }

    // ------------------------------------------------------------------ 事实校验

    @Test
    void anOccurrenceTimeBeyondTheClockSkewWindowIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS)
                .occurredAt(daysFromNow(1)).build()));
        assertEquals(400, error.getCode());
        assertEquals("执行时间不能晚于当前时间", error.getMessage());
    }

    @Test
    void matingWithoutAMethodIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));

        assertEquals("请选择配种方式", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.MATING).maleRabbitId(BUCK_ID).build()))
            .getMessage());
    }

    @Test
    void naturalMatingWithoutABuckIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));

        assertEquals("请选择配种公兔", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.MATING)
                .matingMethod(MatingMethod.NATURAL).build())).getMessage());
    }

    /** 人工授精可以用混精或外购冻精，没有具体公兔是正常业务，不能被必填规则误伤。 */
    @Test
    void artificialInseminationWithoutABuckIsAccepted() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));

        assertDoesNotThrow(() -> service.apply(command(ReproAction.MATING)
            .matingMethod(MatingMethod.AI).build()));
    }

    @Test
    void matingWithNoBatchAnywhereIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, null));

        assertEquals("配种时请选择生产批次", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.MATING)
                .matingMethod(MatingMethod.NATURAL)
                .maleRabbitId(BUCK_ID)
                .build())).getMessage());
    }

    /**
     * 在已有周期上操作时客户端只传 cycleId，母兔必须取自加锁的周期行。若信了入参里的
     * 母兔，攻击面就是「拿 A 的资格去给 B 配种」，而落库的是 B——事后从数据完全看不出。
     */
    @Test
    void theMatingEligibilityCheckUsesTheLockedCycleMotherNotTheRequestPayload() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));
        Date occurred = daysFromNow(-1);

        service.apply(command(ReproAction.MATING)
            .motherRabbitId(999L)
            .maleRabbitId(BUCK_ID)
            .matingMethod(MatingMethod.NATURAL)
            .occurredAt(occurred)
            .build());

        verify(eligibilityValidator).validateMating(HOUSE_ID, MOTHER_ID, BUCK_ID, occurred);
    }

    /**
     * 摸胎不确定时阶段不变，唯一的推进力就是那个复查日期。不给日期就放行，这只兔子
     * 会永久停在待摸胎且没有任何待办——旧实现里「兔子消失在流程中」的典型成因。
     */
    @Test
    void anUnsurePalpationWithoutARecheckDateIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_PALPATION, BATCH_ID));

        assertEquals("摸胎结论为不确定时，请选择今天或未来的复查日期",
            assertThrows(BizException.class, () -> service.apply(command(ReproAction.PALPATION)
                .palpationResult(PalpationResult.UNSURE).build())).getMessage());
    }

    @Test
    void anUnsurePalpationWithAPastRecheckDateIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_PALPATION, BATCH_ID));

        assertEquals("下次提醒日期不能早于今天",
            assertThrows(BizException.class, () -> service.apply(command(ReproAction.PALPATION)
                .palpationResult(PalpationResult.UNSURE)
                .nextRemindAt(daysFromNow(-2))
                .build())).getMessage());
    }

    @Test
    void aFailedDeliveryThatStillReportsKitsIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        assertEquals("失败产的总产仔数、活仔数和留仔数必须为 0",
            assertThrows(BizException.class, () -> service.apply(deliveryCommand(
                DeliveryOutcome.FAILED, 1, 0, 0).remark("难产").build())).getMessage());
    }

    @Test
    void aFailedDeliveryWithoutTheDystociaDetailIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        assertEquals("请填写难产详情", assertThrows(BizException.class,
            () -> service.apply(deliveryCommand(DeliveryOutcome.FAILED, 0, 0, 0).build()))
            .getMessage());
    }

    /**
     * 活仔多于总产仔、留仔多于活仔都是物理上不可能的数。放进去之后窝的 lossCount
     * 会变成负数，繁殖成绩报表从此长期偏差，且没有任何线索指向这一笔。
     */
    @Test
    void moreLiveKitsThanTotalKitsIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        assertEquals("活仔数不能大于总产仔数", assertThrows(BizException.class,
            () -> service.apply(deliveryCommand(DeliveryOutcome.BORN, 6, 8, 6).build()))
            .getMessage());
    }

    @Test
    void moreKeptKitsThanLiveKitsIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        assertEquals("留仔数不能大于活仔数", assertThrows(BizException.class,
            () -> service.apply(deliveryCommand(DeliveryOutcome.BORN, 8, 6, 7).build()))
            .getMessage());
    }

    @Test
    void aNegativeKitCountIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        assertEquals("产仔数量不能为负数", assertThrows(BizException.class,
            () -> service.apply(deliveryCommand(DeliveryOutcome.BORN, 8, -1, 0).build()))
            .getMessage());
    }

    /**
     * 接产结果是转换表的判别值，拼写不对就查不到任何一行。这里要的是它被当场挡下，
     * 而不是被当成「正常产仔」建出一窝来历不明的仔兔。
     */
    @Test
    void aDeliveryOutcomeThatMatchesNoTransitionIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.DELIVERY)
                .outcome("BORN ")
                .totalKits(8).liveKits(8).keptKits(8)
                .build()));
        assertEquals(409, error.getCode());
        verify(litterMapper, never()).insert(any());
    }

    @Test
    void aNegativeWeanedCountIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(nursingLitter());

        assertEquals("断奶只数不能为负数", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.WEANING).weanedCount(-1).build()))
            .getMessage());
    }

    @Test
    void anAbortionWithoutAStillbirthCountIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));

        assertEquals("请填写流产死胎数", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ABORTION).remark("整窝流产").build()))
            .getMessage());
    }

    @Test
    void aPostponeWithoutAFutureReminderDateIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_PALPATION, BATCH_ID));

        assertEquals("请选择今天或未来的下次提醒日期", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.POSTPONE).build())).getMessage());
    }

    /**
     * 离场不会再产生待办，此时还接受「下次提醒日期」就是给用户一个虚假承诺：
     * 表单收下了日期，实际上没有任何任务会在那天出现。
     */
    @Test
    void aReminderDateOnAnActionThatEndsTheLineIsRejected() {
        stubCycle(openCycle(ReproStage.READY, null));

        assertEquals("本次操作不会生成后续待办，不能设置下次提醒日期",
            assertThrows(BizException.class, () -> service.apply(command(ReproAction.RETIRE)
                .nextRemindAt(daysFromNow(3)).build())).getMessage());
    }

    // -------------------------------------------------------------- 事实落库与窝

    @Test
    void matingRecordsTheMatingDateAndTheThirtyDayExpectedBirthDate() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));
        Date occurred = daysFromNow(-1);

        service.apply(command(ReproAction.MATING)
            .maleRabbitId(BUCK_ID)
            .matingMethod(MatingMethod.NATURAL)
            .occurredAt(occurred)
            .build());

        ReproCycle written = capturedCycle();
        assertEquals(occurred, written.getMatingDate());
        assertEquals(BUCK_ID, written.getMaleRabbitId());
        assertEquals(MatingMethod.NATURAL.name(), written.getMatingMethod());
        assertEquals(DateUtil.plusDays(occurred, 30), written.getExpectedBirthDate());
    }

    /** 公兔的最近配种日是另一条投影，配种时必须一并刷新，否则公兔使用频次统计失真。 */
    @Test
    void matingAlsoStampsTheBuckLastMatingDate() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));
        Date occurred = daysFromNow(-1);

        service.apply(command(ReproAction.MATING)
            .maleRabbitId(BUCK_ID)
            .matingMethod(MatingMethod.NATURAL)
            .occurredAt(occurred)
            .build());

        verify(rabbitStageProjectionMapper).touchLastMatingDate(HOUSE_ID, BUCK_ID, occurred, OPERATOR);
    }

    /**
     * 窝的计数是繁殖成绩的原始凭据。死亡数由总产仔减活仔算出、在哺乳数等于留仔数，
     * 这两个派生值算错就再也无法从别处还原。计数还要同步到周期的兼容列，
     * 否则没有 OTA 的老 APK 会显示上一窝的数字。
     */
    @Test
    void aNormalDeliveryCreatesTheLitterAndMirrorsItsCountsOntoTheCycle() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        service.apply(deliveryCommand(DeliveryOutcome.BORN, 9, 7, 6).build());

        ArgumentCaptor<Litter> litter = ArgumentCaptor.forClass(Litter.class);
        verify(litterMapper).insert(litter.capture());
        assertEquals(9, litter.getValue().getTotalKits());
        assertEquals(7, litter.getValue().getLiveKits());
        assertEquals(6, litter.getValue().getKeptKits());
        assertEquals(2, litter.getValue().getLossCount());
        assertEquals(6, litter.getValue().getCurrentNursing());
        assertEquals(LitterStatus.NURSING.name(), litter.getValue().getStatus());

        ReproCycle written = capturedCycle();
        assertEquals(9, written.getTotalKits());
        assertEquals(7, written.getLiveKits());
        assertEquals(6, written.getCurrentNursingKits());
    }

    @Test
    void aFailedDeliveryNeitherCreatesALitterNorRecordsABirthDate() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        service.apply(deliveryCommand(DeliveryOutcome.FAILED, 0, 0, 0).remark("难产").build());

        verify(litterMapper, never()).insert(any());
        assertNull(capturedCycle().getBirthDate());
        assertEquals(CycleResult.FAILED.name(), capturedCycle().getResult());
    }

    /**
     * 没有窝就没有可分笼的主体。硬推下去周期会被关掉，而仔兔既没有断奶记录也没有
     * 落位任务——「窝已断奶、仔兔不存在」的悬空态，只能靠人工翻事件流补。
     */
    @Test
    void weaningACycleThatHasNoLitterIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.WEANING).weanedCount(6).build()));
        assertEquals(409, error.getCode());
        assertEquals("该周期没有可分笼的窝", error.getMessage());
    }

    @Test
    void weaningClosesTheLitterAndZeroesTheNursingCount() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(nursingLitter());

        service.apply(command(ReproAction.WEANING).weanedCount(6).avgWeaningWeight(0.62).build());

        ArgumentCaptor<Litter> litter = ArgumentCaptor.forClass(Litter.class);
        verify(litterMapper).update(litter.capture());
        assertEquals(LitterStatus.WEANED.name(), litter.getValue().getStatus());
        assertEquals(0, litter.getValue().getCurrentNursing());
        assertEquals(6, litter.getValue().getWeanedCount());
        assertEquals(0, capturedCycle().getCurrentNursingKits());
        assertEquals(6, capturedCycle().getWeanedKits());
    }

    // ------------------------------------------------------------------ 关闭语义

    /**
     * 关闭周期时刻意保留 stage。流产统计要按「在哪个阶段流的」分组，关闭时把阶段抹平
     * 或改写成后继阶段，这条信息就永久丢了——事件流里也只有动作，没有当时的阶段。
     */
    @Test
    void closingACycleKeepsTheStageItStoppedAt() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));

        service.apply(command(ReproAction.ABORTION)
            .remark("整窝流产").stillbirthCount(4).build());

        ReproCycle written = capturedCycle();
        assertEquals(ReproStage.AWAIT_PREPARTUM.name(), written.getStage());
        assertEquals(CycleLifecycle.CLOSED.name(), written.getLifecycle());
        assertEquals(CycleResult.ABORTED.name(), written.getResult());
        assertNotNull(written.getClosedAt());
    }

    @Test
    void aCloseWithoutAnExplicitReasonFallsBackToTheResultLabel() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));

        service.apply(command(ReproAction.ABORTION)
            .remark("整窝流产").stillbirthCount(4).build());

        assertEquals(CycleResult.ABORTED.label(), capturedCycle().getCloseReason());
    }

    @Test
    void anExplicitCloseReasonIsPreserved() {
        stubCycle(openCycle(ReproStage.READY, null));

        service.apply(command(ReproAction.RETIRE).reason("售出").build());

        assertEquals("售出", capturedCycle().getCloseReason());
    }

    // ------------------------------------------------------------------ 任务流转

    /**
     * 推迟的语义是「今天没做，改天再提醒」。若它像正常推进那样先完成旧任务再建新任务，
     * 待办会先从今日清单里消失一次，用户当天就再也找不到这只兔子。
     */
    @Test
    void aPostponeOnlyMovesTheDueDateAndKeepsTheTaskPending() {
        stubCycle(openCycle(ReproStage.AWAIT_PALPATION, BATCH_ID));
        Date later = daysFromNow(3);
        when(workTaskWriter.pendingBySubject(HOUSE_ID, TaskSubjectType.CYCLE, CYCLE_ID))
            .thenReturn(List.of(task(TASK_ID, daysFromNow(0))));

        ReproResult result = service.apply(command(ReproAction.POSTPONE)
            .nextRemindAt(later).build());

        verify(workTaskWriter).postpone(HOUSE_ID, TASK_ID, later, OPERATOR);
        verify(workTaskWriter, never()).completeBySubject(anyLong(), any(), anyLong(), any(), anyString());
        verify(workTaskWriter, never()).schedule(any());
        assertEquals(later, result.nextDueTime());
        assertEquals(ReproStage.AWAIT_PALPATION.name(), capturedCycle().getStage());
    }

    @Test
    void retiringCancelsEveryTaskForTheMotherAndOpensNoSuccessor() {
        stubCycle(openCycle(ReproStage.READY, null));

        ReproResult result = service.apply(command(ReproAction.RETIRE).build());

        verify(workTaskWriter).cancelAllForRabbit(HOUSE_ID, MOTHER_ID, OPERATOR);
        verify(workTaskWriter, never()).schedule(any());
        verify(reproCycleMapper, never()).insert(any());
        assertNull(result.followUpCycleId());
        assertNull(result.nextDueTime());
    }

    @Test
    void weaningWithNoRunningPipelineOpensTheNextRecoveryCycle() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(nursingLitter());
        when(reproCycleMapper.insert(any())).thenAnswer(invocation -> {
            ((ReproCycle) invocation.getArgument(0)).setId(101L);
            return 1;
        });

        ReproResult result = service.apply(command(ReproAction.WEANING).weanedCount(6).build());

        ArgumentCaptor<ReproCycle> followUp = ArgumentCaptor.forClass(ReproCycle.class);
        verify(reproCycleMapper).insert(followUp.capture());
        assertEquals(ReproStage.READY.name(), followUp.getValue().getStage());
        assertNull(followUp.getValue().getBatchId());
        assertEquals(101L, result.followUpCycleId());
    }

    /**
     * 血配下母兔已经跑在另一条管线周期上。分笼时再开一条，同一母兔就有了两条管线周期，
     * V27 的 uk_bc_pipeline 会直接拒绝写入——整笔分笼回滚，仔兔的落位一并丢失。
     */
    @Test
    void weaningWhileAnotherPipelineCycleIsRunningDoesNotOpenASecondOne() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(nursingLitter());
        ReproCycle running = openCycle(ReproStage.AWAIT_PALPATION, 31L);
        running.setId(102L);
        when(reproCycleMapper.selectOpenPipelineForUpdate(HOUSE_ID, MOTHER_ID)).thenReturn(running);

        ReproResult result = service.apply(command(ReproAction.WEANING).weanedCount(6).build());

        verify(reproCycleMapper, never()).insert(any());
        assertNull(result.followUpCycleId());
        assertNull(result.nextDueTime());
    }

    /** 血配时周期任务与窝任务并存，分笼必须把两条都收掉，漏掉哪条它就永远 PENDING。 */
    @Test
    void weaningCompletesBothTheCycleTaskAndTheLitterTask() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(nursingLitter());

        service.apply(command(ReproAction.WEANING).weanedCount(6).build());

        verify(workTaskWriter).completeBySubject(
            HOUSE_ID, TaskSubjectType.CYCLE, CYCLE_ID, EVENT_ID, OPERATOR);
        verify(workTaskWriter).completeBySubject(
            HOUSE_ID, TaskSubjectType.LITTER, LITTER_ID, EVENT_ID, OPERATOR);
    }

    // -------------------------------------------------------------------- 投影

    @Test
    void retiringProjectsTheMotherAsDepartedWithNoCurrentCycle() {
        stubCycle(openCycle(ReproStage.READY, null));

        service.apply(command(ReproAction.RETIRE).build());

        verify(rabbitStageProjectionMapper).projectStage(
            eq(HOUSE_ID), eq(MOTHER_ID), eq(ReproStage.RETIRED.name()),
            isNull(), any(), eq(OPERATOR));
    }

    /**
     * 关闭哺乳周期时，转换表说后继是「休养期」，但血配下母兔其实正跑在待摸胎上。
     * 照抄转换结果会把她的阶段写回休养期，列表页从此显示错的阶段，
     * 而真实的待摸胎待办还挂着——用户看到的和要做的对不上。
     */
    @Test
    void closingANursingCycleProjectsTheRunningPipelineStageNotTheTableFollowUp() {
        stubCycle(openCycle(ReproStage.AWAIT_WEANING, BATCH_ID));
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(nursingLitter());
        ReproCycle running = openCycle(ReproStage.AWAIT_PALPATION, 31L);
        running.setId(102L);
        when(reproCycleMapper.selectOpenPipelineForUpdate(HOUSE_ID, MOTHER_ID)).thenReturn(running);

        service.apply(command(ReproAction.WEANING).weanedCount(6).build());

        verify(rabbitStageProjectionMapper).projectStage(
            eq(HOUSE_ID), eq(MOTHER_ID), eq(ReproStage.AWAIT_PALPATION.name()),
            eq(102L), any(), eq(OPERATOR));
    }

    /**
     * 分娩后母兔即可开始下一轮，哺乳状态留在窝和窝待办上。若把她投影成待分笼，
     * 她会被下一轮的配种筛选整条排除，血配这条业务路径直接消失。
     */
    @Test
    void aNursingCycleWithoutAPipelineProjectsTheMotherAsReadyForTheNextRound() {
        stubCycle(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        service.apply(deliveryCommand(DeliveryOutcome.BORN, 8, 8, 8).build());

        verify(rabbitStageProjectionMapper).projectStage(
            eq(HOUSE_ID), eq(MOTHER_ID), eq(ReproStage.READY.name()),
            isNull(), any(), eq(OPERATOR));
    }

    // -------------------------------------------------------------- 配种时绑批次

    @Test
    void matingOnACycleAlreadyBoundToADifferentBatchIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, BATCH_ID));

        assertEquals("生产周期已绑定其他批次，请刷新后重试", assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.MATING)
                .batchId(999L)
                .maleRabbitId(BUCK_ID)
                .matingMethod(MatingMethod.NATURAL)
                .build())).getMessage());
    }

    /**
     * 绑批次是条件更新，返回 0 说明并发下别人先绑了。当成成功继续走，本次的窝和事件
     * 会挂到内存里那个假的批次号上，与库里真实归属永久不一致。
     */
    @Test
    void aLostRaceOnBatchAssignmentIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, null));
        when(reproCycleMapper.assignBatchIfUnboundWithCycleNo(
            anyLong(), anyLong(), anyLong(), any(), anyString())).thenReturn(0);

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.MATING)
                .batchId(BATCH_ID)
                .maleRabbitId(BUCK_ID)
                .matingMethod(MatingMethod.NATURAL)
                .build()));
        assertEquals(409, error.getCode());
        assertEquals("生产周期批次已变化，请刷新后重试", error.getMessage());
    }

    /**
     * 入轨时预定了 A 批、配种时改选 B 批，A 批里那条成员关系必须解除。留着它，
     * 母兔会同时算在两个批次的在栏数里，A 批还会因为「还有成员」而无法结束。
     */
    @Test
    void matingReleasesThePlannedBatchMembershipWhenAnotherBatchIsChosen() {
        ReproCycle cycle = openCycle(ReproStage.AWAIT_MATING, null);
        cycle.setPlannedBatchId(77L);
        stubCycle(cycle);
        when(batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(HOUSE_ID, 77L, MOTHER_ID))
            .thenReturn(breedingMember(555L));
        when(reproCycleMapper.assignBatchIfUnboundWithCycleNo(
            anyLong(), anyLong(), anyLong(), any(), anyString())).thenReturn(1);

        service.apply(command(ReproAction.MATING)
            .batchId(BATCH_ID)
            .maleRabbitId(BUCK_ID)
            .matingMethod(MatingMethod.NATURAL)
            .build());

        verify(batchRabbitMapper).deactivateIfActive(
            eq(HOUSE_ID), eq(555L), any(), eq("配种时改选生产批次"), eq(OPERATOR));
        assertEquals(BATCH_ID, capturedCycle().getBatchId());
        assertNull(capturedCycle().getPlannedBatchId());
    }

    /** 已有周期占着这个批次，说明并行的下一轮撞了 uk_bc_batch_member，得改选批次。 */
    @Test
    void matingIntoABatchThatAlreadyHoldsACycleForThisMotherIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_MATING, null));
        when(reproCycleMapper.selectOpenByBatchAndMotherForUpdate(HOUSE_ID, BATCH_ID, MOTHER_ID))
            .thenReturn(openCycle(ReproStage.AWAIT_PALPATION, BATCH_ID));

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.MATING)
                .batchId(BATCH_ID)
                .maleRabbitId(BUCK_ID)
                .matingMethod(MatingMethod.NATURAL)
                .build()));
        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("并行的下一轮请改选其他批次"));
    }

    // ---------------------------------------------------------- 关闭时退出批次

    /**
     * 关闭周期要顺带把母兔退出批次。这条 UPDATE 影响 0 行说明成员关系已被并发改动，
     * 当作成功会留下「周期已结束、母兔仍在批次里」的残留，批次永远结不掉。
     */
    @Test
    void aClosedCycleWhoseMembershipVanishedConcurrentlyIsRejected() {
        stubCycle(openCycle(ReproStage.AWAIT_PREPARTUM, BATCH_ID));
        when(batchRabbitMapper.deactivateIfActive(anyLong(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(0);

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ABORTION)
                .remark("整窝流产").stillbirthCount(4).build()));
        assertEquals(409, error.getCode());
        assertEquals("批次成员关系已变化，请刷新后重试", error.getMessage());
    }

    // ------------------------------------------------------------ 事件与附件

    /**
     * 前置回查没命中但唯一键命中，说明并发的同一 requestId 正在处理。这里不能读回首次
     * 结果——本事务的快照看不见对方刚提交的行，读回只会拿到 null 并把它当成「没有」。
     */
    @Test
    void aConcurrentDuplicateSubmissionIsRejectedInsteadOfReadingBackNull() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));
        doThrow(new DuplicateKeyException("uk_re_request")).when(reproEventMapper).insert(any());

        BizException error = assertThrows(BizException.class,
            () -> service.apply(command(ReproAction.ESTRUS).build()));
        assertEquals(409, error.getCode());
        assertEquals("该操作正在处理中，请勿重复提交", error.getMessage());
    }

    /**
     * 事件 payload 里的 resultHasNextTask / resultNextDueTime 是回放唯一的依据。
     * 不落这两个值，重试时就只能靠猜，正是上面那条回放用例守的另一端。
     */
    @Test
    void theEventRecordsWhetherAFollowUpTaskWasCreated() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));

        service.apply(command(ReproAction.ESTRUS).build());

        ArgumentCaptor<ReproEvent> event = ArgumentCaptor.forClass(ReproEvent.class);
        verify(reproEventMapper).insert(event.capture());
        assertTrue(event.getValue().getPayload().contains("\"resultHasNextTask\":true"));
        assertEquals(ReproStage.AWAIT_ESTRUS.name(), event.getValue().getFromStage());
        assertEquals(ReproStage.AWAIT_MATING.name(), event.getValue().getToStage());
    }

    /** 附件顺序就是用户上传的顺序，序号乱了现场照片和文字描述就对不上。 */
    @Test
    void attachmentsArePersistedInTheOrderTheyWereSubmitted() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));

        service.apply(command(ReproAction.ESTRUS)
            .attachmentFileIds(List.of("f1", "f2", "f3")).build());

        ArgumentCaptor<BizAttachment> saved = ArgumentCaptor.forClass(BizAttachment.class);
        verify(bizAttachmentMapper, times(3)).insertIgnore(saved.capture());
        assertEquals(List.of("f1", "f2", "f3"),
            saved.getAllValues().stream().map(BizAttachment::getFileId).toList());
        assertEquals(List.of(0, 1, 2),
            saved.getAllValues().stream().map(BizAttachment::getSortNo).toList());
        assertEquals(EVENT_ID, saved.getAllValues().get(0).getBizId());
    }

    /** 没有操作人姓名时回落到用户 id，再不行才是 system；留痕不能是空字符串。 */
    @Test
    void anAnonymousOperatorFallsBackToTheUserId() {
        stubCycle(openCycle(ReproStage.AWAIT_ESTRUS, BATCH_ID));

        service.apply(command(ReproAction.ESTRUS).operatorName("  ").build());

        org.junit.jupiter.api.Assertions.assertNull(capturedCycle().getUpdateBy(),
                "服务层不再手写 update_by，由 MyBatis 写入拦截器补齐");
    }

    // ------------------------------------------------------------ openCycleAt

    /**
     * 从待摸胎入轨等于补录一次配种，必须带公兔和配种方式。给默认值会造出线上不可能
     * 出现的状态组合（没有公兔的自然配种），等到统计或切换时才暴露。
     */
    @Test
    void enteringAtPalpationWithoutTheMatingFactsIsRejected() {
        BizException error = assertThrows(BizException.class,
            () -> service.openCycleAt(openCommand(ReproStage.AWAIT_PALPATION, BATCH_ID, null)));
        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("补录"));
    }

    /** 摸胎及之后的阶段必须落在一个批次里，否则周期一入轨就是上面那种坏数据。 */
    @Test
    void enteringAtAStageThatRequiresABatchWithoutOneIsRejected() {
        BizException error = assertThrows(BizException.class,
            () -> service.openCycleAt(new OpenCycleCommand(
                HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, null, ReproStage.AWAIT_PALPATION,
                daysFromNow(-1), daysFromNow(-1), null, null, null, null, null, null,
                BUCK_ID, MatingMethod.NATURAL, null, null, "req-open")));
        assertEquals(400, error.getCode());
        assertEquals("从【待摸胎】入轨必须选择生产批次", error.getMessage());
    }

    /** 管线互斥：母兔已有在跑的管线周期时再入轨，会直接撞上 uk_bc_pipeline。 */
    @Test
    void enteringThePipelineWhileAnotherPipelineCycleIsRunningIsRejected() {
        when(reproCycleMapper.selectOpenPipelineForUpdate(HOUSE_ID, MOTHER_ID))
            .thenReturn(openCycle(ReproStage.AWAIT_DELIVERY, BATCH_ID));

        BizException error = assertThrows(BizException.class,
            () -> service.openCycleAt(openCommand(
                ReproStage.AWAIT_PALPATION, BATCH_ID, MatingMethod.NATURAL)));
        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("待分娩"));
    }

    /**
     * 待分笼入轨必须同事务建窝：没有窝就没有分笼任务的主体，母兔会卡在一个
     * 永远不会被提醒的阶段，仔兔也无从落位。
     */
    @Test
    void enteringAtWeaningCreatesTheNursingLitterInTheSameTransaction() {
        when(reproCycleMapper.insert(any())).thenAnswer(invocation -> {
            ((ReproCycle) invocation.getArgument(0)).setId(CYCLE_ID);
            return 1;
        });

        service.openCycleAt(new OpenCycleCommand(
            HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, BATCH_ID, ReproStage.AWAIT_WEANING,
            daysFromNow(-5), daysFromNow(-5), null, null, null, 9, 7, 6,
            BUCK_ID, MatingMethod.NATURAL, null, null, "req-open"));

        ArgumentCaptor<Litter> litter = ArgumentCaptor.forClass(Litter.class);
        verify(litterMapper).insert(litter.capture());
        assertEquals(LitterStatus.NURSING.name(), litter.getValue().getStatus());
        assertEquals(2, litter.getValue().getLossCount());
        assertEquals(6, litter.getValue().getCurrentNursing());
        assertEquals(CYCLE_ID, litter.getValue().getCycleId());
    }

    /**
     * 窝的计数必须在 insert 周期之前同步进兼容列：openCycleAt 全程只 insert 一次周期，
     * insert 之后再改就只改到了内存里，老 APK 会显示 0 只仔兔。
     */
    @Test
    void enteringAtWeaningMirrorsTheKitCountsBeforeTheCycleIsInserted() {
        ArgumentCaptor<ReproCycle> inserted = ArgumentCaptor.forClass(ReproCycle.class);
        when(reproCycleMapper.insert(any())).thenAnswer(invocation -> {
            ReproCycle cycle = invocation.getArgument(0);
            cycle.setId(CYCLE_ID);
            assertEquals(9, cycle.getTotalKits());
            assertEquals(7, cycle.getLiveKits());
            assertEquals(6, cycle.getCurrentNursingKits());
            return 1;
        });

        service.openCycleAt(new OpenCycleCommand(
            HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, BATCH_ID, ReproStage.AWAIT_WEANING,
            daysFromNow(-5), daysFromNow(-5), null, null, null, 9, 7, 6,
            BUCK_ID, MatingMethod.NATURAL, null, null, "req-open"));

        verify(reproCycleMapper).insert(inserted.capture());
        assertEquals(ReproStage.AWAIT_WEANING.name(), inserted.getValue().getStage());
    }

    @Test
    void enteringAtWeaningWithMoreKeptKitsThanLiveKitsIsRejected() {
        assertEquals("留仔数不能大于活仔数", assertThrows(BizException.class,
            () -> service.openCycleAt(new OpenCycleCommand(
                HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, BATCH_ID, ReproStage.AWAIT_WEANING,
                daysFromNow(-5), daysFromNow(-5), null, null, null, 9, 6, 7,
                BUCK_ID, MatingMethod.NATURAL, null, null, "req-open"))).getMessage());
    }

    /**
     * 前置检查与 insert 之间可能并发插入同一 (批次, 母兔)。让唯一键冲突以业务语义 409
     * 出去，而不是一个客户端看不懂、也无法据此改选批次的 500。
     */
    @Test
    void aUniqueKeyClashOnInsertSurfacesAsABusinessConflict() {
        doThrow(new DuplicateKeyException("uk_bc_batch_member")).when(reproCycleMapper).insert(any());

        BizException error = assertThrows(BizException.class,
            () -> service.openCycleAt(openCommand(
                ReproStage.AWAIT_PALPATION, BATCH_ID, MatingMethod.NATURAL)));
        assertEquals(409, error.getCode());
        assertEquals("该母兔在本批次已有进行中的生产周期，请改选其他批次", error.getMessage());
    }

    /**
     * 从待摸胎入轨等价于「配种已发生」，所以除了 CYCLE_START 还要补一条 MATING_DONE，
     * 否则这只母兔的事件流里没有配种记录，繁殖成绩无法回溯。
     */
    @Test
    void enteringAtPalpationAlsoWritesTheImpliedMatingEvent() {
        when(reproCycleMapper.insert(any())).thenAnswer(invocation -> {
            ((ReproCycle) invocation.getArgument(0)).setId(CYCLE_ID);
            return 1;
        });

        service.openCycleAt(openCommand(ReproStage.AWAIT_PALPATION, BATCH_ID, MatingMethod.NATURAL));

        ArgumentCaptor<ReproEvent> events = ArgumentCaptor.forClass(ReproEvent.class);
        verify(reproEventMapper, times(2)).insert(events.capture());
        assertEquals("CYCLE_START", events.getAllValues().get(0).getEventType());
        assertEquals("MATING_DONE", events.getAllValues().get(1).getEventType());
        assertFalse(events.getAllValues().get(0).getRequestId()
            .equals(events.getAllValues().get(1).getRequestId()));
    }

    /** 入轨若不给母兔加成员关系，批次里就看不到她，批次维度的统计全部漏算。 */
    @Test
    void enteringWithABatchEnrolsTheMotherAsABreedingMember() {
        when(reproCycleMapper.insert(any())).thenAnswer(invocation -> {
            ((ReproCycle) invocation.getArgument(0)).setId(CYCLE_ID);
            return 1;
        });
        when(batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(HOUSE_ID, BATCH_ID, MOTHER_ID))
            .thenReturn(null);

        service.openCycleAt(openCommand(ReproStage.AWAIT_PALPATION, BATCH_ID, MatingMethod.NATURAL));

        ArgumentCaptor<List<BatchRabbit>> links = ArgumentCaptor.forClass(List.class);
        verify(batchRabbitMapper).insertBatch(links.capture());
        assertEquals(1, links.getValue().size());
        assertEquals("breeding", links.getValue().get(0).getBatchRole());
        assertSame(Boolean.TRUE, links.getValue().get(0).getIsActive());
        verify(rabbitStatusHistoryMapper).insert(any());
    }

    // ------------------------------------------------------------------ 夹具

    private ReproCommand.Builder command(ReproAction action) {
        return ReproCommand.builder()
            .houseId(HOUSE_ID)
            .userId(USER_ID)
            .operatorName(OPERATOR)
            .cycleId(CYCLE_ID)
            .action(action)
            .occurredAt(daysFromNow(-1))
            .requestId("req-1");
    }

    private ReproCommand.Builder deliveryCommand(
        DeliveryOutcome outcome, Integer total, Integer live, Integer kept
    ) {
        return command(ReproAction.DELIVERY)
            .outcome(outcome.name())
            .totalKits(total)
            .liveKits(live)
            .keptKits(kept);
    }

    private OpenCycleCommand openCommand(ReproStage stage, Long batchId, MatingMethod method) {
        return new OpenCycleCommand(
            HOUSE_ID, USER_ID, OPERATOR, MOTHER_ID, batchId, stage,
            daysFromNow(-1), daysFromNow(-1), null, null, null, null, null, null,
            method == null ? null : BUCK_ID, method, null, null, "req-open");
    }

    private void stubCycle(ReproCycle cycle) {
        stubCycle(cycle, cycle);
    }

    private void stubCycle(ReproCycle observed, ReproCycle locked) {
        when(reproCycleMapper.selectById(HOUSE_ID, CYCLE_ID)).thenReturn(observed);
        when(reproCycleMapper.selectByIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(locked);
    }

    private ReproCycle capturedCycle() {
        ArgumentCaptor<ReproCycle> captor = ArgumentCaptor.forClass(ReproCycle.class);
        verify(reproCycleMapper).applyTransition(captor.capture(), any());
        return captor.getValue();
    }

    private ReproCycle openCycle(ReproStage stage, Long batchId) {
        ReproCycle cycle = new ReproCycle();
        cycle.setId(CYCLE_ID);
        cycle.setHouseId(HOUSE_ID);
        cycle.setBatchId(batchId);
        cycle.setMotherRabbitId(MOTHER_ID);
        cycle.setCycleNo(1);
        cycle.setStage(stage.name());
        cycle.setStageEnteredAt(daysFromNow(-5));
        cycle.setLifecycle(CycleLifecycle.OPEN.name());
        cycle.setStateVersion(VERSION);
        cycle.setMatingDate(daysFromNow(-15));
        cycle.setBirthDate(stage == ReproStage.AWAIT_WEANING ? daysFromNow(-20) : null);
        return cycle;
    }

    private Litter nursingLitter() {
        Litter litter = new Litter();
        litter.setId(LITTER_ID);
        litter.setHouseId(HOUSE_ID);
        litter.setCycleId(CYCLE_ID);
        litter.setMotherRabbitId(MOTHER_ID);
        litter.setTotalKits(9);
        litter.setLiveKits(7);
        litter.setKeptKits(6);
        litter.setCurrentNursing(6);
        litter.setStatus(LitterStatus.NURSING.name());
        return litter;
    }

    private ReproEvent replayEvent(String payload) {
        ReproEvent event = new ReproEvent();
        event.setId(EVENT_ID);
        event.setHouseId(HOUSE_ID);
        event.setCycleId(CYCLE_ID);
        event.setMotherRabbitId(MOTHER_ID);
        event.setPayload(payload);
        return event;
    }

    private Rabbit mother() {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(MOTHER_ID);
        rabbit.setHouseId(HOUSE_ID);
        rabbit.setCageId(CAGE_ID);
        rabbit.setGender("0");
        rabbit.setType("0");
        rabbit.setIsActive(Boolean.TRUE);
        rabbit.setCurrentStage(ReproStage.AWAIT_MATING.name());
        rabbit.setCurrentCycleId(CYCLE_ID);
        return rabbit;
    }

    private Batch activeBatch() {
        Batch batch = new Batch();
        batch.setId(BATCH_ID);
        batch.setHouseId(HOUSE_ID);
        batch.setStatus("进行中");
        return batch;
    }

    private BatchRabbit breedingMember(Long id) {
        BatchRabbit member = new BatchRabbit();
        member.setId(id);
        member.setBatchId(BATCH_ID);
        member.setRabbitId(MOTHER_ID);
        member.setBatchRole("breeding");
        member.setIsActive(Boolean.TRUE);
        return member;
    }

    private WorkTask task(Long id, Date dueTime) {
        WorkTask task = new WorkTask();
        task.setId(id);
        task.setHouseId(HOUSE_ID);
        task.setDueTime(dueTime);
        return task;
    }

    private static Date daysFromNow(int days) {
        return DateUtil.plusDays(DateUtil.now(), days);
    }
}
