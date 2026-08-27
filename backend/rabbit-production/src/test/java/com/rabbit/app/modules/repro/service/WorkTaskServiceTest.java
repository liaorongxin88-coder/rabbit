package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.dto.BulkActionRequest;
import com.rabbit.app.modules.repro.dto.BulkActionResult;
import com.rabbit.app.modules.repro.dto.TaskPage;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 批量待办入口的校验与部分成功语义。
 *
 * <p>这是唯一一个「一次请求改动上百只母兔」的入口，它漏掉的每一条校验都会被批量
 * 放大：一个共享 payload 写给两百只兔、一次重试把整批推进两遍、一次死锁把两个操作员
 * 都卡住。所以下面守的是四类边界：
 *
 * <ul>
 *   <li><b>不能批量的动作</b>——接产、分笼、开新周期每只的仔数与目标笼都不同，
 *       共用一份参数产出的是一批「看起来成功、实则全错」的记录；</li>
 *   <li><b>目标解析</b>——taskIds 与 filter 必须二选一，重复 id 必须去重
 *       （否则第二次命中幂等回放，显示成功却什么也没做），上限必须在查库<em>之前</em>判；</li>
 *   <li><b>取锁定序</b>——一律按 rabbit_id 排序，两个并发批量才不会互等；</li>
 *   <li><b>部分成功</b>——单只失败收敛成一条明细，绝不能中断整批，也绝不能被算进成功数。</li>
 * </ul>
 */
class WorkTaskServiceTest {

    private static final Long HOUSE_ID = 8L;
    private static final Long USER_ID = 5L;
    private static final String OPERATOR = "张三";

    private WorkTaskMapper workTaskMapper;
    private ReproActionService reproActionService;
    private WorkTaskService service;

    @BeforeEach
    void setUp() {
        workTaskMapper = mock(WorkTaskMapper.class);
        reproActionService = mock(ReproActionService.class);
        service = new WorkTaskService(workTaskMapper, reproActionService);
    }

    // ---------- 不允许批量的动作 ----------

    /**
     * 接产每只的总仔数、活仔数都不同。批量共享一份 payload 会把同一个仔数写给
     * 整批母兔，产出一批全错却全部「成功」的窝记录。
     */
    @Test
    void bulkDeliveryIsRejectedOutright() {
        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.DELIVERY, 1L)));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("不支持批量"));
        verifyNoInteractions(workTaskMapper);
        verifyNoInteractions(reproActionService);
    }

    /** 分笼要指定每只的目标笼，同理不能批量。 */
    @Test
    void bulkWeaningIsRejectedOutright() {
        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.WEANING, 1L)));

        assertEquals(400, error.getCode());
        verifyNoInteractions(workTaskMapper);
    }

    @Test
    void bulkStartCycleIsRejectedOutright() {
        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.START_CYCLE, 1L)));

        assertEquals(400, error.getCode());
        verifyNoInteractions(workTaskMapper);
    }

    /** 批量配种已下线，要给旧客户端一条明确的业务错误而不是静默执行。 */
    @Test
    void bulkMatingIsRejectedWithItsOwnMessage() {
        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.MATING, 1L)));

        assertEquals(400, error.getCode());
        assertEquals("批量配种功能已下线，请逐只提交配种记录", error.getMessage());
        verifyNoInteractions(workTaskMapper);
    }

    /** 推迟没有下次提醒时间就是把待办推进虚空，永远不会再出现在列表里。 */
    @Test
    void postponeWithoutANextReminderIsRejected() {
        BulkActionRequest request = request(ReproAction.POSTPONE, 1L);
        request.setNextRemindAt(null);

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request));

        assertEquals(400, error.getCode());
        assertEquals("推迟必须指定下次提醒时间", error.getMessage());
        verifyNoInteractions(workTaskMapper);
    }

    @Test
    void postponeWithANextReminderIsAccepted() {
        BulkActionRequest request = request(ReproAction.POSTPONE, 1L);
        request.setNextRemindAt(new Date(1_700_000_000_000L));
        stubTask(1L, TaskType.MATING, 10L);
        stubSuccessfulApply();

        BulkActionResult result = service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        assertEquals(1, result.succeeded());
    }

    // ---------- 目标解析 ----------

    /** 两种目标形式都给了，服务端无从判断以哪个为准。 */
    @Test
    void supplyingBothTaskIdsAndFilterIsRejected() {
        BulkActionRequest request = request(ReproAction.ESTRUS, 1L);
        request.setFilter(filter(66L, null));

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request));

        assertEquals(400, error.getCode());
        assertEquals("批量目标请二选一：taskIds 或 filter", error.getMessage());
    }

    /** 两个都没给等于没说要操作谁，不能默默操作全场。 */
    @Test
    void supplyingNeitherTaskIdsNorFilterIsRejected() {
        BulkActionRequest request = request(ReproAction.ESTRUS);

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request));

        assertEquals(400, error.getCode());
        assertEquals("批量目标请二选一：taskIds 或 filter", error.getMessage());
        verifyNoInteractions(workTaskMapper);
    }

    /** 空的 filter（三个条件全空）不算给了目标。 */
    @Test
    void anEmptyFilterDoesNotCountAsATarget() {
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setFilter(filter(null, null));

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request));

        assertEquals(400, error.getCode());
    }

    /**
     * 上限必须先于加载判断。若先加载后判上限，客户端丢一万个 id 过来就已经
     * 白白打了一万次库——这正是把上限写在 loadByIds 之后会踩的坑。
     */
    @Test
    void anOversizedTaskIdListIsRejectedBeforeAnyDatabaseRead() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 501; i++) {
            ids.add(i);
        }
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setTaskIds(ids);

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("最多 500 项"));
        verify(workTaskMapper, never()).selectById(anyLong(), anyLong());
    }

    /** 正好 500 项是允许的边界。 */
    @Test
    void exactlyFiveHundredTaskIdsAreAccepted() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            ids.add(i);
            stubTask(i, TaskType.ESTRUS, i);
        }
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setTaskIds(ids);
        stubSuccessfulApply();

        BulkActionResult result = service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        assertEquals(500, result.total());
        assertEquals(500, result.succeeded());
    }

    /**
     * filter 路径按 limit = 上限 + 1 探测溢出：查回 501 条说明条件太宽，
     * 要让客户端缩小范围，而不是截断成 500 条静默少做一批。
     */
    @Test
    void anOverflowingFilterResultIsRejectedRatherThanTruncated() {
        List<WorkTask> rows = new ArrayList<>();
        for (long i = 1; i <= 501; i++) {
            rows.add(task(i, TaskType.ESTRUS, i));
        }
        when(workTaskMapper.selectPendingByFilter(HOUSE_ID, "ESTRUS", 66L, null, 501))
            .thenReturn(rows);
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setFilter(filter(66L, "ESTRUS"));

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request));

        assertEquals(400, error.getCode());
        verifyNoInteractions(reproActionService);
    }

    /** filter 里的任务类型要归一化成枚举名再下推，不能把原始字符串塞进 SQL。 */
    @Test
    void filterTaskTypeIsNormalizedBeforeQuerying() {
        when(workTaskMapper.selectPendingByFilter(HOUSE_ID, "ESTRUS", 66L, null, 501))
            .thenReturn(new ArrayList<>());
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setFilter(filter(66L, " estrus "));

        service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        verify(workTaskMapper).selectPendingByFilter(HOUSE_ID, "ESTRUS", 66L, null, 501);
    }

    /** 空白类型视作不限类型，而不是去匹配一个名为空串的类型。 */
    @Test
    void aBlankFilterTaskTypeIsTreatedAsNoTypeFilter() {
        when(workTaskMapper.selectPendingByFilter(HOUSE_ID, null, 66L, null, 501))
            .thenReturn(new ArrayList<>());
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setFilter(filter(66L, "  "));

        service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        verify(workTaskMapper).selectPendingByFilter(HOUSE_ID, null, 66L, null, 501);
    }

    /** 没有任何目标时返回空结果，而不是把空集合当成「全场」。 */
    @Test
    void anEmptyTargetSetProducesAnEmptyResult() {
        when(workTaskMapper.selectPendingByFilter(anyLong(), any(), any(), any(), anyInt()))
            .thenReturn(new ArrayList<>());
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setFilter(filter(66L, null));

        BulkActionResult result = service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        assertEquals(0, result.total());
        assertEquals(0, result.succeeded());
        assertEquals(0, result.failed());
        assertTrue(result.items().isEmpty());
        verifyNoInteractions(reproActionService);
    }

    /**
     * 重复 id 必须去重。不去重时第二次会命中同一幂等键，回放成一条「成功」明细，
     * 操作者会以为处理了两只兔。
     */
    @Test
    void duplicateTaskIdsAreCollapsedIntoOneTarget() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        BulkActionRequest request = request(ReproAction.ESTRUS, 1L, 1L, 1L);
        stubSuccessfulApply();

        BulkActionResult result = service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        assertEquals(1, result.total());
        verify(workTaskMapper, times(1)).selectById(HOUSE_ID, 1L);
        verify(reproActionService, times(1)).apply(any(), isNull());
    }

    /** 列表里的 null 直接跳过，不该变成一次 selectById(null) 或一条 404。 */
    @Test
    void nullTaskIdsAreSkipped() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        BulkActionRequest request = request(ReproAction.ESTRUS);
        request.setTaskIds(Arrays.asList(1L, null));
        stubSuccessfulApply();

        BulkActionResult result = service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        assertEquals(1, result.total());
        verify(workTaskMapper, never()).selectById(HOUSE_ID, null);
    }

    /** 指名道姓要操作的待办不存在，是请求本身错了，整批拒绝而非跳过。 */
    @Test
    void anUnknownTaskIdAbortsTheWholeBatch() {
        when(workTaskMapper.selectById(HOUSE_ID, 1L)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () ->
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L)));

        assertEquals(404, error.getCode());
        assertEquals("待办不存在: 1", error.getMessage());
        verifyNoInteractions(reproActionService);
    }

    // ---------- 取锁定序 ----------

    /**
     * 一律按 rabbit_id 升序推进。两个并发批量若以不同顺序锁同一批母兔就会互等，
     * 这是既有的防死锁约定，不能因为「反正结果一样」而放弃。
     */
    @Test
    void targetsAreAlwaysProcessedInRabbitIdOrder() {
        stubTask(1L, TaskType.ESTRUS, 30L);
        stubTask(2L, TaskType.ESTRUS, 10L);
        stubTask(3L, TaskType.ESTRUS, 20L);
        stubSuccessfulApply();

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L, 2L, 3L));

        assertEquals(
            List.of(10L, 20L, 30L),
            result.items().stream().map(BulkActionResult.Item::rabbitId).toList()
        );
    }

    /** rabbit_id 相同时按 task_id 兜底定序，排序结果必须是全序。 */
    @Test
    void tasksSharingARabbitAreOrderedByTaskId() {
        stubTask(7L, TaskType.ESTRUS, 10L);
        stubTask(3L, TaskType.ESTRUS, 10L);
        stubSuccessfulApply();

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 7L, 3L));

        assertEquals(
            List.of(3L, 7L),
            result.items().stream().map(BulkActionResult.Item::taskId).toList()
        );
    }

    /** 没有关联兔子的待办排在最后，不能让 null 把整个排序炸掉。 */
    @Test
    void tasksWithoutARabbitSortLastInsteadOfFailing() {
        stubTask(1L, TaskType.ESTRUS, null);
        stubTask(2L, TaskType.ESTRUS, 10L);
        stubSuccessfulApply();

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L, 2L));

        assertEquals(
            Arrays.asList(10L, null),
            result.items().stream().map(BulkActionResult.Item::rabbitId).toList()
        );
    }

    // ---------- 逐项校验 ----------

    /**
     * 待办类型与动作不匹配时只能失败这一项。放过去就是拿「待摸胎」的待办去执行
     * 催情，状态机会被喂进一个它没预期的组合。
     */
    @Test
    void anActionThatDoesNotMatchTheTaskTypeFailsOnlyThatItem() {
        stubTask(1L, TaskType.PALPATION, 10L);
        stubTask(2L, TaskType.ESTRUS, 20L);
        stubSuccessfulApply();

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L, 2L));

        assertEquals(2, result.total());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        BulkActionResult.Item failed = itemOf(result, 1L);
        assertFalse(failed.ok());
        assertEquals(400, failed.code());
        assertTrue(failed.message().contains("不支持操作"));
        verify(reproActionService, times(1)).apply(any(), isNull());
    }

    /** 待出售这类非繁育待办没有对应动作，任何繁育动作都不该落到它头上。 */
    @Test
    void aNonBreedingTaskRejectsBreedingActions() {
        stubTask(1L, TaskType.SALE_READY, 10L);

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L));

        assertEquals(0, result.succeeded());
        assertEquals(400, itemOf(result, 1L).code());
        verifyNoInteractions(reproActionService);
    }

    /** 推迟、离场、流产与任务类型无关，不能被类型匹配挡住。 */
    @Test
    void typeAgnosticActionsApplyToAnyTaskType() {
        stubTask(1L, TaskType.PALPATION, 10L);
        stubSuccessfulApply();

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.RETIRE, 1L));

        assertEquals(1, result.succeeded());
    }

    /** 不属于任何周期的待办没法走状态机，只能失败该项。 */
    @Test
    void aTaskWithoutACycleFailsOnlyThatItem() {
        WorkTask orphan = task(1L, TaskType.ESTRUS, 10L);
        orphan.setCycleId(null);
        when(workTaskMapper.selectById(HOUSE_ID, 1L)).thenReturn(orphan);

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L));

        assertEquals(0, result.succeeded());
        assertEquals(400, itemOf(result, 1L).code());
        assertTrue(itemOf(result, 1L).message().contains("不属于生产周期"));
        verifyNoInteractions(reproActionService);
    }

    /** 库里存了个已经下线的任务类型，只该拖垮这一项，不该炸掉整批。 */
    @Test
    void anUnparsableTaskTypeFailsOnlyThatItem() {
        WorkTask broken = task(1L, TaskType.ESTRUS, 10L);
        broken.setTaskType("LEGACY_UNKNOWN");
        when(workTaskMapper.selectById(HOUSE_ID, 1L)).thenReturn(broken);
        stubTask(2L, TaskType.ESTRUS, 20L);
        stubSuccessfulApply();

        BulkActionResult result =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L, 2L));

        assertEquals(1, result.succeeded());
        assertEquals(400, itemOf(result, 1L).code());
    }

    // ---------- 部分成功 ----------

    /**
     * 一只被他人并发推进过，不该让另外几只一起回滚。失败项带回可读原因，
     * 客户端据此只重试失败的那部分。
     */
    @Test
    void oneConflictingItemDoesNotStopTheRestOfTheBatch() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        stubTask(2L, TaskType.ESTRUS, 20L);
        stubTask(3L, TaskType.ESTRUS, 30L);
        when(reproActionService.apply(any(), isNull())).thenAnswer(invocation -> {
            ReproCommand command = invocation.getArgument(0);
            if (command.getMotherRabbitId().equals(20L)) {
                throw new BizException(409, "阶段已变化");
            }
            return result(false);
        });

        BulkActionResult batch =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L, 2L, 3L));

        assertEquals(3, batch.total());
        assertEquals(2, batch.succeeded());
        assertEquals(1, batch.failed());
        assertEquals(409, itemOf(batch, 2L).code());
        assertEquals("阶段已变化", itemOf(batch, 2L).message());
        verify(reproActionService, times(3)).apply(any(), isNull());
    }

    /** 幂等回放算成功，但要标出来——它没有产生新的状态变更。 */
    @Test
    void aReplayedItemCountsAsSucceededButIsFlagged() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        when(reproActionService.apply(any(), isNull())).thenReturn(result(true));

        BulkActionResult batch =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L));

        assertEquals(1, batch.succeeded());
        assertTrue(itemOf(batch, 1L).replayed());
    }

    /** 失败项永远不是回放，别让客户端把一条失败当成「上次已经做过了」。 */
    @Test
    void aFailedItemIsNeverMarkedAsReplayed() {
        stubTask(1L, TaskType.PALPATION, 10L);

        BulkActionResult batch =
            service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L));

        assertFalse(itemOf(batch, 1L).replayed());
    }

    // ---------- 下推给编排层的命令 ----------

    /**
     * 逐项派生幂等键 requestId-taskId。若整批共用一个 requestId，第二只就会命中
     * 第一只的回放，整批只有一只真正被推进。
     */
    @Test
    void eachItemGetsItsOwnDerivedIdempotencyKey() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        stubTask(2L, TaskType.ESTRUS, 20L);
        stubSuccessfulApply();

        service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L, 2L));

        ArgumentCaptor<ReproCommand> captor = ArgumentCaptor.forClass(ReproCommand.class);
        verify(reproActionService, times(2)).apply(captor.capture(), isNull());
        assertEquals(
            List.of("bulk-1-1", "bulk-1-2"),
            captor.getAllValues().stream().map(ReproCommand::getRequestId).sorted().toList()
        );
    }

    /** 整批共用一个执行时间；没传时统一取当前时间，不能每项各取一次。 */
    @Test
    void allItemsShareOneOccurredAt() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        stubTask(2L, TaskType.ESTRUS, 20L);
        stubSuccessfulApply();
        BulkActionRequest request = request(ReproAction.ESTRUS, 1L, 2L);
        request.setOccurredAt(null);

        service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        ArgumentCaptor<ReproCommand> captor = ArgumentCaptor.forClass(ReproCommand.class);
        verify(reproActionService, times(2)).apply(captor.capture(), isNull());
        assertEquals(
            captor.getAllValues().get(0).getOccurredAt(),
            captor.getAllValues().get(1).getOccurredAt()
        );
    }

    /** 待办自身的周期、母兔、批次要原样带给编排层，不能张冠李戴。 */
    @Test
    void theCommandCarriesTheTaskOwnCycleAndRabbit() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        stubSuccessfulApply();
        BulkActionRequest request = request(ReproAction.ESTRUS, 1L);
        request.setRemark("批量催情");
        request.setReason("常规");

        service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request);

        ArgumentCaptor<ReproCommand> captor = ArgumentCaptor.forClass(ReproCommand.class);
        verify(reproActionService).apply(captor.capture(), isNull());
        ReproCommand command = captor.getValue();
        assertEquals(HOUSE_ID, command.getHouseId());
        assertEquals(USER_ID, command.getUserId());
        assertEquals(OPERATOR, command.getOperatorName());
        assertEquals(901L, command.getCycleId());
        assertEquals(10L, command.getMotherRabbitId());
        assertEquals(66L, command.getBatchId());
        assertEquals(ReproAction.ESTRUS, command.getAction());
        assertEquals("批量催情", command.getRemark());
        assertEquals("常规", command.getReason());
    }

    /**
     * 批量必须走编排层而不是直连状态机。直连会跳过接产记账、分笼落位这些副作用，
     * 一旦日后放开某个带副作用的动作，副作用会被静默跳过。
     */
    @Test
    void bulkGoesThroughTheOrchestrationLayer() {
        stubTask(1L, TaskType.ESTRUS, 10L);
        stubSuccessfulApply();

        service.bulkApply(HOUSE_ID, USER_ID, OPERATOR, request(ReproAction.ESTRUS, 1L));

        verify(reproActionService).apply(any(ReproCommand.class), isNull());
    }

    // ---------- 待办查询 ----------

    /** 分页尺寸要夹到 1..200，否则一个 size=100000 的请求能把整表拉进内存。 */
    @Test
    void pageSizeIsClampedToTheAllowedRange() {
        stubEmptyPage();

        service.pendingDue(HOUSE_ID, null, null, null, null, null, 1, 100_000);

        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), any(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(200)
        );
    }

    /** 页码小于 1 时回落到第 1 页，负偏移会让 SQL 直接报错。 */
    @Test
    void aNonPositivePageFallsBackToTheFirstPage() {
        stubEmptyPage();

        TaskPage page = service.pendingDue(HOUSE_ID, null, null, null, null, null, 0, 20);

        assertEquals(1, page.page());
        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), any(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        );
    }

    @Test
    void theOffsetFollowsThePageAndSize() {
        stubEmptyPage();

        service.pendingDue(HOUSE_ID, null, null, null, null, null, 3, 20);

        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), any(), isNull(), isNull(), isNull(), isNull(), eq(40), eq(20)
        );
    }

    /** 要看全部未来待办时不能再带到期日上限，否则「未来」被截在今天。 */
    @Test
    void includingFutureDropsTheDueDateBound() {
        stubEmptyPage();

        service.pendingDue(
            HOUSE_ID, new Date(1_700_000_000_000L), null, null, null, null, 1, 20, true
        );

        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        );
        verify(workTaskMapper).countPendingDue(
            eq(HOUSE_ID), isNull(), isNull(), isNull(), isNull(), isNull()
        );
    }

    /** 不含未来时到期日上限必须存在，缺了就变成「查出全部未来待办」。 */
    @Test
    void omittingDueBeforeStillAppliesTodayAsTheBound() {
        stubEmptyPage();

        service.pendingDue(HOUSE_ID, null, null, null, null, null, 1, 20);

        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), captor.capture(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        );
        assertNotNull(captor.getValue());
    }

    /** 显式传了到期日就用它，不能被今天覆盖掉。 */
    @Test
    void anExplicitDueBeforeIsPassedThrough() {
        stubEmptyPage();
        Date bound = new Date(1_700_000_000_000L);

        service.pendingDue(HOUSE_ID, bound, null, null, null, null, 1, 20);

        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), eq(bound), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        );
    }

    /** 总数与列表必须用同一组过滤条件，否则首页角标和点进去的列表对不上。 */
    @Test
    void theTotalAndTheRowsShareTheSameFilters() {
        when(workTaskMapper.countPendingDue(HOUSE_ID, null, "ESTRUS", 66L, 12L, 10L))
            .thenReturn(7L);
        when(workTaskMapper.selectPendingDue(
            eq(HOUSE_ID), isNull(), eq("ESTRUS"), eq(66L), eq(12L), eq(10L), anyInt(), anyInt()
        )).thenReturn(new ArrayList<>());

        TaskPage page = service.pendingDue(
            HOUSE_ID, null, "estrus", 66L, 12L, 10L, 1, 20, true
        );

        assertEquals(7L, page.total());
    }

    /** 空白类型等于不筛类型，不能变成匹配空串。 */
    @Test
    void aBlankTaskTypeIsTreatedAsNoTypeFilter() {
        stubEmptyPage();

        service.pendingDue(HOUSE_ID, null, "  ", null, null, null, 1, 20);

        verify(workTaskMapper).selectPendingDue(
            eq(HOUSE_ID), any(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)
        );
    }

    /** 到期日早于今天的待办要标成逾期，这是首页红点的唯一依据。 */
    @Test
    void tasksDueBeforeTodayAreFlaggedAsOverdue() {
        WorkTask overdue = task(1L, TaskType.ESTRUS, 10L);
        overdue.setDueDate(new Date(1_600_000_000_000L));
        WorkTask future = task(2L, TaskType.ESTRUS, 20L);
        future.setDueDate(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        when(workTaskMapper.countPendingDue(any(), any(), any(), any(), any(), any()))
            .thenReturn(2L);
        when(workTaskMapper.selectPendingDue(
            any(), any(), any(), any(), any(), any(), anyInt(), anyInt()
        )).thenReturn(new ArrayList<>(List.of(overdue, future)));

        TaskPage page = service.pendingDue(HOUSE_ID, null, null, null, null, null, 1, 20, true);

        assertTrue(page.items().get(0).overdue());
        assertFalse(page.items().get(1).overdue());
    }

    /** 没有到期日的待办不算逾期，不能因为 null 就默认标红。 */
    @Test
    void aTaskWithoutADueDateIsNotOverdue() {
        WorkTask undated = task(1L, TaskType.ESTRUS, 10L);
        undated.setDueDate(null);
        when(workTaskMapper.countPendingDue(any(), any(), any(), any(), any(), any()))
            .thenReturn(1L);
        when(workTaskMapper.selectPendingDue(
            any(), any(), any(), any(), any(), any(), anyInt(), anyInt()
        )).thenReturn(new ArrayList<>(List.of(undated)));

        TaskPage page = service.pendingDue(HOUSE_ID, null, null, null, null, null, 1, 20, true);

        assertFalse(page.items().get(0).overdue());
        assertNull(page.items().get(0).dueDate());
    }

    // ---------- 夹具 ----------

    private void stubEmptyPage() {
        when(workTaskMapper.countPendingDue(any(), any(), any(), any(), any(), any()))
            .thenReturn(0L);
        when(workTaskMapper.selectPendingDue(
            any(), any(), any(), any(), any(), any(), anyInt(), anyInt()
        )).thenReturn(new ArrayList<>());
    }

    private void stubSuccessfulApply() {
        when(reproActionService.apply(any(), isNull())).thenReturn(result(false));
    }

    private void stubTask(Long id, TaskType type, Long rabbitId) {
        when(workTaskMapper.selectById(HOUSE_ID, id)).thenReturn(task(id, type, rabbitId));
    }

    private static WorkTask task(Long id, TaskType type, Long rabbitId) {
        WorkTask task = new WorkTask();
        task.setId(id);
        task.setHouseId(HOUSE_ID);
        task.setTaskType(type.name());
        task.setCycleId(901L);
        task.setRabbitId(rabbitId);
        task.setBatchId(66L);
        task.setStatus("PENDING");
        return task;
    }

    private static ReproResult result(boolean replayed) {
        return new ReproResult(
            901L, 901L, 1L, null, null, ReproStage.AWAIT_MATING, "ACTIVE", null, null, replayed
        );
    }

    private static BulkActionRequest request(ReproAction action, Long... taskIds) {
        BulkActionRequest request = new BulkActionRequest();
        request.setRequestId("bulk-1");
        request.setAction(action.name());
        request.setOccurredAt(new Date(1_700_000_000_000L));
        if (action == ReproAction.POSTPONE) {
            request.setNextRemindAt(new Date(1_700_100_000_000L));
        }
        if (taskIds.length > 0) {
            request.setTaskIds(new ArrayList<>(List.of(taskIds)));
        }
        return request;
    }

    private static BulkActionRequest.Filter filter(Long batchId, String taskType) {
        BulkActionRequest.Filter filter = new BulkActionRequest.Filter();
        filter.setBatchId(batchId);
        filter.setTaskType(taskType);
        return filter;
    }

    private static BulkActionResult.Item itemOf(BulkActionResult result, Long taskId) {
        return result.items().stream()
            .filter(item -> taskId.equals(item.taskId()))
            .findFirst()
            .orElseThrow();
    }
}
