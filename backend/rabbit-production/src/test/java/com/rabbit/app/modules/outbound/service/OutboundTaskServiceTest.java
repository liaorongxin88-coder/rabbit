package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import com.rabbit.app.modules.outbound.entity.OutboundTask;
import com.rabbit.app.modules.outbound.entity.OutboundTaskBatchAllocation;
import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskBatchAllocationMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskItemMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskMapper;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 出库任务的草稿生命周期。
 *
 * <p>这层是提交前的最后一道拦截：兔只还在不在、够不够格出、状态版本有没有被别人改过、
 * 草稿本身有没有被并发覆盖。任何一条漏掉，错误都会一路带到真正的扣减事务里，
 * 那时再发现就得回滚整批。
 *
 * <p>用例集中在校验分支，因为成功路径的返回值组装（{@code view}）依赖资格服务的
 * 一串聚合，断言它意义不大；真正值得钉住的是「什么情况下必须拒绝」。
 */
class OutboundTaskServiceTest {
    private static final Long USER_ID = 5L;
    private static final Long HOUSE_ID = 1L;
    private static final String TASK_ID = "task-1";

    private OutboundTaskMapper taskMapper;
    private OutboundTaskItemMapper itemMapper;
    private OutboundTaskBatchAllocationMapper allocationMapper;
    private OutboundEligibilityService eligibilityService;
    private HouseService houseService;
    private OutboundTaskService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(OutboundTaskMapper.class);
        itemMapper = mock(OutboundTaskItemMapper.class);
        allocationMapper = mock(OutboundTaskBatchAllocationMapper.class);
        eligibilityService = mock(OutboundEligibilityService.class);
        houseService = mock(HouseService.class);
        service = new OutboundTaskService(
            taskMapper, itemMapper, allocationMapper, eligibilityService, houseService
        );
    }

    // ---------- 建任务的入参 ----------

    @Test
    void unknownEntryTypeIsRejected() {
        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, createRequest("FLOOR", null, null, null, false)));
        assertEquals(400, error.getCode());
        assertEquals("entryType不支持", error.getMessage());
    }

    @Test
    void entryTypeIsCaseInsensitiveAndTrimmed() {
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("CAGE"), any(), eq(9L), any())).thenReturn(List.of());
        stubViewFor("CAGE");

        service.create(USER_ID, HOUSE_ID, createRequest("  cage  ", null, 9L, null, false));

        ArgumentCaptor<OutboundTask> task = ArgumentCaptor.forClass(OutboundTask.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("CAGE", task.getValue().getEntryType());
    }

    @Test
    void eachEntryTypeRequiresItsOwnSource() {
        assertEquals("rabbitId不能为空", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, createRequest("RABBIT", null, null, null, false))).getMessage());
        assertEquals("cageId不能为空", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, createRequest("CAGE", null, 0L, null, false))).getMessage());
        assertEquals("rowCode不能为空", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, createRequest("ROW", null, null, "  ", false))).getMessage());
    }

    /**
     * 整舍出库不需要任何 source 字段，别被 validateSource 误伤。
     */
    @Test
    void houseEntryNeedsNoSourceIdentifier() {
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any())).thenReturn(List.of());
        stubViewFor("HOUSE");

        service.create(USER_ID, HOUSE_ID, createRequest("HOUSE", null, null, null, false));

        verify(taskMapper).insert(any());
    }

    @Test
    void aSingleRabbitEntryThatMatchesNothingIsANotFound() {
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("RABBIT"), eq(7L), any(), any())).thenReturn(List.of());

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, createRequest("RABBIT", 7L, null, null, false)));
        assertEquals(404, error.getCode());
        assertEquals("目标兔只不存在", error.getMessage());
    }

    /**
     * 更宽的入口（笼/排/舍）扫不到候选是合法的空任务，不该报 404 —— 空笼子本来就没兔子。
     */
    @Test
    void aWiderEntryThatMatchesNothingIsAnEmptyTaskNotAnError() {
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("CAGE"), any(), eq(9L), any())).thenReturn(List.of());
        stubViewFor("CAGE");

        service.create(USER_ID, HOUSE_ID, createRequest("CAGE", null, 9L, null, false));

        verify(taskMapper).insert(any());
    }

    // ---------- 续用已有草稿 ----------

    /**
     * resumeExisting 不传时默认续用。默认新建会让用户每次进页面都丢掉上次的勾选。
     */
    @Test
    void resumeDefaultsToOnWhenTheFlagIsAbsent() {
        OutboundTask existing = task("SELECTING", 3L);
        when(taskMapper.selectLatestEditable(HOUSE_ID, USER_ID)).thenReturn(existing);
        stubViewFor("HOUSE");

        OutboundDtos.TaskView view = service.create(USER_ID, HOUSE_ID, createRequest("HOUSE", null, null, null, null));

        assertTrue(view.resumed());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void resumeIsSkippedWhenExplicitlyTurnedOff() {
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any())).thenReturn(List.of());
        stubViewFor("HOUSE");

        OutboundDtos.TaskView view = service.create(USER_ID, HOUSE_ID, createRequest("HOUSE", null, null, null, false));

        assertFalse(view.resumed());
        verify(taskMapper, never()).selectLatestEditable(anyLong(), anyLong());
        verify(taskMapper).insert(any());
    }

    @Test
    void withNothingToResumeANewTaskIsCreated() {
        when(taskMapper.selectLatestEditable(HOUSE_ID, USER_ID)).thenReturn(null);
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any())).thenReturn(List.of());
        stubViewFor("HOUSE");

        service.create(USER_ID, HOUSE_ID, createRequest("HOUSE", null, null, null, true));

        verify(taskMapper).insert(any());
    }

    /**
     * 新建的任务必须从 revision 0 起步，乐观锁靠它比对；起始值错了，
     * 第一次保存就会莫名其妙报冲突。
     */
    @Test
    void aNewTaskStartsSelectingAtRevisionZero() {
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any())).thenReturn(List.of());
        stubViewFor("HOUSE");

        service.create(USER_ID, HOUSE_ID, createRequest("HOUSE", null, null, null, false));

        ArgumentCaptor<OutboundTask> task = ArgumentCaptor.forClass(OutboundTask.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("SELECTING", task.getValue().getStatus());
        assertEquals(0L, task.getValue().getRevision());
        assertEquals(USER_ID, task.getValue().getOperatorId());
    }

    // ---------- 保存草稿 ----------

    @Test
    void savingAnUnknownTaskIsANotFound() {
        when(taskMapper.selectByIdForUpdate(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID, saveRequest(0L, "SELECTING", List.of())));
        assertEquals(404, error.getCode());
        assertEquals("OUTBOUND_TASK_NOT_FOUND", error.getMessage());
    }

    @Test
    void unknownDraftStatusIsRejected() {
        stubEditableTask();

        assertEquals("status不支持", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID, saveRequest(0L, "DONE", List.of()))).getMessage());
    }

    /**
     * 空清单可以存成草稿，但不能推进到待确认 —— 那等于提交一个空单。
     */
    @Test
    void anEmptySelectionCannotBeAdvancedToWaitingConfirmation() {
        stubEditableTask();

        BizException error = assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID, saveRequest(0L, "WAITING_CONFIRMATION", List.of())));
        assertEquals(400, error.getCode());
        assertEquals("请选择兔只", error.getMessage());
    }

    @Test
    void duplicateOrInvalidRabbitIdsAreRejected() {
        stubEditableTask();

        assertEquals("rabbitIds包含无效或重复值", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, null, null), input(7L, 1L, null, null))))
        ).getMessage());

        assertEquals("rabbitIds包含无效或重复值", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(0L, 1L, null, null))))
        ).getMessage());
    }

    /**
     * 勾选的兔子在候选集里查不到，说明它在编辑期间离场或被转走了。
     */
    @Test
    void aNullDraftItemIsRejectedBeforeServiceDereference() {
        stubEditableTask();
        OutboundDtos.SaveDraftRequest request = saveRequest(
            0L,
            "SELECTING",
            Collections.singletonList(null)
        );

        BizException error = assertThrows(BizException.class,
            () -> service.save(USER_ID, HOUSE_ID, TASK_ID, request));

        assertEquals(400, error.getCode());
        assertEquals("items不能包含空项", error.getMessage());
        verify(taskMapper, never()).updateDraft(
            anyLong(), anyLong(), anyString(), anyLong(), anyString(),
            any(), any(), any(), any(), any()
        );
    }

    @Test
    void aRabbitThatLeftDuringEditingIsReportedAsNotPresent() {
        stubEditableTask();
        when(eligibilityService.rowsByIds(eq(HOUSE_ID), anyList())).thenReturn(List.of());

        BizException error = assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, null, null)))));
        assertEquals(409, error.getCode());
        assertEquals("RABBIT_NOT_PRESENT: 7", error.getMessage());
    }

    @Test
    void aNormalSelectionMustActuallyBeEligible() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.EARLY_SALE, 1L);

        assertEquals("RABBIT_NOT_ELIGIBLE: 7", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "NORMAL", null))))).getMessage());
    }

    @Test
    void anEarlySaleSelectionMustBeAllowedForThatRabbit() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);

        assertEquals("EARLY_SALE_NOT_ALLOWED: 7", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "EARLY_SALE", "急售"))))).getMessage());
    }

    @Test
    void anEarlySaleSelectionMustCarryAReason() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.EARLY_SALE, 1L);

        assertEquals("EARLY_SALE_REASON_REQUIRED: 7", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "EARLY_SALE", "   "))))).getMessage());
    }

    /**
     * 提前出售要额外的 control 权限，普通成员勾不了。权限检查必须发生在
     * 资格判断之前，否则无权用户能靠错误信息试探出兔只状态。
     */
    @Test
    void earlySaleRequiresTheControlPermission() {
        stubEditableTask();
        doThrow(new BizException(403, "无权限"))
                .when(houseService).assertHousePermission(USER_ID, HOUSE_ID, "control");

        assertEquals(403, assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "EARLY_SALE", "急售"))))).getCode());
        verify(eligibilityService, never()).rowsByIds(anyLong(), anyList());
    }

    @Test
    void aNormalOnlySelectionDoesNotNeedTheControlPermission() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        stubViewFor("HOUSE");

        service.save(USER_ID, HOUSE_ID, TASK_ID, saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "NORMAL", null))));

        verify(houseService, never()).assertHousePermission(anyLong(), anyLong(), anyString());
    }

    /**
     * 客户端提交的状态版本和库里对不上，说明这只兔子在编辑期间被别人改过，
     * 必须让用户重新确认，不能拿旧快照往下走。
     */
    @Test
    void aStaleStateVersionIsRejected() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 5L);

        assertEquals("RABBIT_STATE_CHANGED: 7", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "NORMAL", null))))).getMessage());
    }

    /**
     * 草稿本身的乐观锁：updateDraft 影响 0 行说明 revision 已被别人推进。
     */
    @Test
    void aStaleDraftRevisionIsRejected() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(0);

        BizException error = assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "NORMAL", null)))));
        assertEquals(409, error.getCode());
        assertEquals("OUTBOUND_REVISION_CONFLICT", error.getMessage());
    }

    /**
     * 冲突时不能落下半套明细。先判 revision 再写明细，是这段代码的既定顺序。
     */
    @Test
    void aRevisionConflictLeavesTheItemsUntouched() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(BizException.class, () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "NORMAL", null)))));

        verify(itemMapper, never()).deleteByTaskLimited(anyString(), anyInt());
        verify(itemMapper, never()).insertBatch(anyList());
        verify(allocationMapper, never()).deleteByTaskLimited(anyLong(), anyString(), anyInt());
        verify(allocationMapper, never()).insertBatch(anyList());
    }

    @Test
    void omittedAllocationsArePreservedForAnExactlyEquivalentItemSnapshot() {
        assertOmittedAllocationBehavior(
            List.of(snapshotItem(7L, 101L, 1L, "NORMAL", null)),
            List.of(new DraftRabbit(7L, 101L, 1L, "NORMAL", null)),
            true
        );
    }

    @Test
    void omittedAllocationsAreClearedWhenASameGroupMemberIsAdded() {
        assertOmittedAllocationBehavior(
            List.of(snapshotItem(7L, 101L, 1L, "NORMAL", null)),
            List.of(
                new DraftRabbit(7L, 101L, 1L, "NORMAL", null),
                new DraftRabbit(8L, 101L, 1L, "NORMAL", null)
            ),
            false
        );
    }

    @Test
    void omittedAllocationsAreClearedWhenASameGroupMemberIsRemoved() {
        assertOmittedAllocationBehavior(
            List.of(
                snapshotItem(7L, 101L, 1L, "NORMAL", null),
                snapshotItem(8L, 101L, 1L, "NORMAL", null)
            ),
            List.of(new DraftRabbit(7L, 101L, 1L, "NORMAL", null)),
            false
        );
    }

    @Test
    void omittedAllocationsAreClearedWhenTheBatchSnapshotChanges() {
        assertOmittedAllocationBehavior(
            List.of(snapshotItem(7L, 101L, 1L, "NORMAL", null)),
            List.of(new DraftRabbit(7L, 102L, 1L, "NORMAL", null)),
            false
        );
    }

    @Test
    void omittedAllocationsAreClearedWhenTheStateVersionChanges() {
        assertOmittedAllocationBehavior(
            List.of(snapshotItem(7L, 101L, 1L, "NORMAL", null)),
            List.of(new DraftRabbit(7L, 101L, 2L, "NORMAL", null)),
            false
        );
    }

    @Test
    void omittedAllocationsAreClearedWhenTheSelectionTypeChanges() {
        assertOmittedAllocationBehavior(
            List.of(snapshotItem(7L, 101L, 1L, "NORMAL", null)),
            List.of(new DraftRabbit(7L, 101L, 1L, "EARLY_SALE", "提前出售")),
            false
        );
    }

    @Test
    void omittedAllocationsAreClearedWhenTheEarlySaleReasonChanges() {
        assertOmittedAllocationBehavior(
            List.of(snapshotItem(7L, 101L, 1L, "EARLY_SALE", "旧原因")),
            List.of(new DraftRabbit(7L, 101L, 1L, "EARLY_SALE", "新原因")),
            false
        );
    }

    @Test
    void legacyDraftWithoutAllocationsKeepsItsExistingPriceCompatibility() {
        stubEditableTask();
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        stubViewFor("HOUSE");
        OutboundDtos.SaveDraftRequest request = new OutboundDtos.SaveDraftRequest(
            0L,
            "SELECTING",
            List.of(),
            null,
            null,
            new BigDecimal("12.345"),
            null,
            null
        );

        service.save(USER_ID, HOUSE_ID, TASK_ID, request);

        verify(taskMapper).updateDraft(
            HOUSE_ID,
            USER_ID,
            TASK_ID,
            0L,
            "SELECTING",
            null,
            null,
            new BigDecimal("12.345"),
            null,
            null
        );
        verify(allocationMapper, never()).deleteByTaskLimited(anyLong(), anyString(), anyInt());
    }

    @Test
    void draftAllocationsAndCommonPriceRoundTrip() {
        stubEditableTask();
        OutboundCandidateRow assigned = candidate(7L, 101L);
        OutboundCandidateRow unassigned = candidate(8L, null);
        when(eligibilityService.rowsByIds(eq(HOUSE_ID), anyList()))
            .thenReturn(List.of(assigned, unassigned));
        when(eligibilityService.evaluate(assigned)).thenReturn(
            eligibilityView(7L, OutboundEligibilityService.NORMAL, 1L, 101L)
        );
        when(eligibilityService.evaluate(unassigned)).thenReturn(
            eligibilityView(8L, OutboundEligibilityService.NORMAL, 1L, null)
        );
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        OutboundTask saved = task("WAITING_CONFIRMATION", 1L);
        saved.setTotalWeight(4.0);
        saved.setUnitPrice(new BigDecimal("12.00"));
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(saved);
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any()))
            .thenReturn(List.of());
        when(eligibilityService.evaluate(anyList())).thenReturn(List.of());
        OutboundTaskBatchAllocation assignedRow = allocation(101L, "2.500");
        OutboundTaskBatchAllocation unassignedRow = allocation(null, "1.500");
        when(allocationMapper.selectByTask(HOUSE_ID, TASK_ID))
            .thenReturn(List.of(assignedRow, unassignedRow));

        OutboundDtos.TaskView view = service.save(
            USER_ID,
            HOUSE_ID,
            TASK_ID,
            allocationRequest(
                "WAITING_CONFIRMATION",
                List.of(
                    input(7L, 1L, "NORMAL", null),
                    input(8L, 1L, "NORMAL", null)
                ),
                List.of(
                    new SaleBatchAllocationInput(101L, new BigDecimal("2.5")),
                    new SaleBatchAllocationInput(null, new BigDecimal("1.500"))
                )
            )
        );

        ArgumentCaptor<List<OutboundTaskBatchAllocation>> rows = ArgumentCaptor.forClass(List.class);
        verify(allocationMapper).insertBatch(rows.capture());
        assertEquals(new BigDecimal("2.500"), rows.getValue().get(0).getActualWeightKg());
        assertEquals(new BigDecimal("1.500"), rows.getValue().get(1).getActualWeightKg());
        assertEquals(new BigDecimal("12.00"), view.unitPrice());
        assertEquals(view.unitPrice(), view.unitPricePerKg());
        assertEquals(2, view.batchAllocations().size());
        assertEquals(101L, view.batchAllocations().get(0).batchId());
        assertEquals(null, view.batchAllocations().get(1).batchId());
    }

    @Test
    void draftAllocationsRejectMalformedDuplicateAndUnknownGroups() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);

        assertEquals("batchAllocations不能包含空项", assertThrows(BizException.class,
            () -> service.save(USER_ID, HOUSE_ID, TASK_ID, allocationRequest(
                "SELECTING", List.of(input(7L, 1L, "NORMAL", null)), Collections.singletonList(null)
            ))).getMessage());
        assertEquals("同一销售批次不能重复分配", assertThrows(BizException.class,
            () -> service.save(USER_ID, HOUSE_ID, TASK_ID, allocationRequest(
                "SELECTING",
                List.of(input(7L, 1L, "NORMAL", null)),
                List.of(
                    new SaleBatchAllocationInput(null, BigDecimal.ONE),
                    new SaleBatchAllocationInput(null, BigDecimal.TWO)
                )
            ))).getMessage());
        assertEquals("销售批次分配包含未选择的批次", assertThrows(BizException.class,
            () -> service.save(USER_ID, HOUSE_ID, TASK_ID, allocationRequest(
                "SELECTING",
                List.of(input(7L, 1L, "NORMAL", null)),
                List.of(new SaleBatchAllocationInput(101L, BigDecimal.ONE))
            ))).getMessage());
    }

    @Test
    void draftAllocationsRejectInvalidScaleAndRange() {
        stubEditableTask();
        OutboundCandidateRow first = candidate(7L, 101L);
        OutboundCandidateRow second = candidate(8L, null);
        when(eligibilityService.rowsByIds(eq(HOUSE_ID), anyList()))
            .thenReturn(List.of(first, second));
        when(eligibilityService.evaluate(first)).thenReturn(
            eligibilityView(7L, OutboundEligibilityService.NORMAL, 1L, 101L)
        );
        when(eligibilityService.evaluate(second)).thenReturn(
            eligibilityView(8L, OutboundEligibilityService.NORMAL, 1L, null)
        );
        List<OutboundDtos.SelectedRabbitInput> items = List.of(
            input(7L, 1L, "NORMAL", null),
            input(8L, 1L, "NORMAL", null)
        );

        assertEquals("销售重量最多保留三位小数", assertThrows(BizException.class,
            () -> service.save(USER_ID, HOUSE_ID, TASK_ID, allocationRequest(
                "SELECTING", items,
                List.of(new SaleBatchAllocationInput(101L, new BigDecimal("1.0001")))
            ))).getMessage());
        assertEquals("actualWeightKg不能超过100000", assertThrows(BizException.class,
            () -> service.save(USER_ID, HOUSE_ID, TASK_ID, allocationRequest(
                "SELECTING", items,
                List.of(new SaleBatchAllocationInput(101L, new BigDecimal("100000.001")))
            ))).getMessage());
    }

    @Test
    void waitingConfirmationPersistsAndRestoresPartialAllocations() {
        stubEditableTask();
        OutboundCandidateRow first = candidate(7L, 101L);
        OutboundCandidateRow second = candidate(8L, null);
        when(eligibilityService.rowsByIds(eq(HOUSE_ID), anyList()))
            .thenReturn(List.of(first, second));
        when(eligibilityService.evaluate(first)).thenReturn(
            eligibilityView(7L, OutboundEligibilityService.NORMAL, 1L, 101L)
        );
        when(eligibilityService.evaluate(second)).thenReturn(
            eligibilityView(8L, OutboundEligibilityService.NORMAL, 1L, null)
        );
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        OutboundTask saved = task("WAITING_CONFIRMATION", 1L);
        saved.setTotalWeight(4.0);
        saved.setUnitPrice(new BigDecimal("12.00"));
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(saved);
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any()))
            .thenReturn(List.of());
        when(eligibilityService.evaluate(anyList())).thenReturn(List.of());
        when(allocationMapper.selectByTask(HOUSE_ID, TASK_ID))
            .thenReturn(List.of(allocation(101L, "2.500")));

        OutboundDtos.TaskView view = service.save(
            USER_ID,
            HOUSE_ID,
            TASK_ID,
            allocationRequest(
                "WAITING_CONFIRMATION",
                List.of(
                    input(7L, 1L, "NORMAL", null),
                    input(8L, 1L, "NORMAL", null)
                ),
                List.of(new SaleBatchAllocationInput(101L, new BigDecimal("2.500")))
            )
        );

        assertEquals(1, view.batchAllocations().size());
        assertEquals(101L, view.batchAllocations().getFirst().batchId());
    }

    @Test
    void waitingConfirmationAcceptsAnExplicitEmptyAllocationListAndClearsStaleRows() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        OutboundTask saved = task("WAITING_CONFIRMATION", 1L);
        saved.setTotalWeight(4.0);
        saved.setUnitPrice(new BigDecimal("12.00"));
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(saved);
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any()))
            .thenReturn(List.of());
        when(eligibilityService.evaluate(anyList())).thenReturn(List.of());
        when(allocationMapper.selectByTask(HOUSE_ID, TASK_ID)).thenReturn(List.of());

        OutboundDtos.TaskView view = service.save(
            USER_ID,
            HOUSE_ID,
            TASK_ID,
            allocationRequest(
                "WAITING_CONFIRMATION",
                List.of(input(7L, 1L, "NORMAL", null)),
                List.of()
            )
        );

        assertTrue(view.batchAllocations().isEmpty());
        verify(allocationMapper).deleteByTaskLimited(HOUSE_ID, TASK_ID, 1_000);
        verify(allocationMapper, never()).insertBatch(anyList());
    }

    @Test
    void legacyDraftWithoutAllocationsReturnsAnEmptyList() {
        OutboundTask task = task("SELECTING", 1L);
        task.setUnitPrice(new BigDecimal("10.00"));
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(task);
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any()))
            .thenReturn(List.of());
        when(eligibilityService.evaluate(anyList())).thenReturn(List.of());
        when(allocationMapper.selectByTask(HOUSE_ID, TASK_ID)).thenReturn(List.of());

        OutboundDtos.TaskView view = service.get(USER_ID, HOUSE_ID, TASK_ID);

        assertEquals(new BigDecimal("10.00"), view.unitPricePerKg());
        assertTrue(view.batchAllocations().isEmpty());
    }

    @Test
    void anAbsentSelectionTypeDefaultsToNormal() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        stubViewFor("HOUSE");

        service.save(USER_ID, HOUSE_ID, TASK_ID, saveRequest(0L, "SELECTING", List.of(input(7L, 1L, null, null))));

        ArgumentCaptor<List<OutboundTaskItem>> items = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).insertBatch(items.capture());
        assertEquals("NORMAL", items.getValue().get(0).getSelectionType());
    }

    @Test
    void unknownSelectionTypeIsRejected() {
        stubEditableTask();
        stubCandidate(7L, OutboundEligibilityService.NORMAL, 1L);

        assertEquals("selectionType不支持", assertThrows(BizException.class,
                () -> service.save(USER_ID, HOUSE_ID, TASK_ID,
                        saveRequest(0L, "SELECTING", List.of(input(7L, 1L, "GIFT", null))))).getMessage());
    }

    // ---------- 取消 ----------

    @Test
    void cancellingAnUnknownTaskIsANotFound() {
        when(taskMapper.markCancelled(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(0);
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class,
                () -> service.cancel(USER_ID, HOUSE_ID, TASK_ID)).getCode());
    }

    /**
     * 任务存在但取消影响 0 行，说明它已经推进到不可取消的状态（提交中/已完成）。
     * 这和「不存在」是两码事，报错要分开，否则前端无法提示用户。
     */
    @Test
    void cancellingATaskPastThePointOfNoReturnIsAConflictNotANotFound() {
        when(taskMapper.markCancelled(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(0);
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(task("COMPLETED", 2L));

        BizException error = assertThrows(BizException.class, () -> service.cancel(USER_ID, HOUSE_ID, TASK_ID));
        assertEquals(409, error.getCode());
        assertEquals("当前任务状态不可取消", error.getMessage());
    }

    @Test
    void cancellingAnEditableTaskSucceeds() {
        when(taskMapper.markCancelled(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(1);

        service.cancel(USER_ID, HOUSE_ID, TASK_ID);

        verify(taskMapper, never()).selectById(anyLong(), anyLong(), anyString());
    }

    // ---------- 读取 ----------

    @Test
    void readingAnUnknownTaskIsANotFound() {
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(null);

        assertEquals("OUTBOUND_TASK_NOT_FOUND", assertThrows(BizException.class,
                () -> service.get(USER_ID, HOUSE_ID, TASK_ID)).getMessage());
    }

    // ---------- 夹具 ----------

    private void assertOmittedAllocationBehavior(
        List<OutboundTaskItem> existingItems,
        List<DraftRabbit> draftRabbits,
        boolean expectPreserved
    ) {
        stubEditableTask();
        List<OutboundCandidateRow> candidates = draftRabbits.stream()
            .map(item -> candidate(item.rabbitId(), item.batchId()))
            .toList();
        when(eligibilityService.rowsByIds(eq(HOUSE_ID), anyList())).thenReturn(candidates);
        for (int index = 0; index < candidates.size(); index++) {
            DraftRabbit input = draftRabbits.get(index);
            OutboundCandidateRow candidate = candidates.get(index);
            String eligibility = "EARLY_SALE".equals(input.selectionType())
                ? OutboundEligibilityService.EARLY_SALE
                : OutboundEligibilityService.NORMAL;
            when(eligibilityService.evaluate(candidate)).thenReturn(
                eligibilityView(input.rabbitId(), eligibility, input.stateVersion(), input.batchId())
            );
        }
        when(itemMapper.selectByTask(TASK_ID)).thenReturn(existingItems);
        when(taskMapper.updateDraft(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                any(), any(), any(), any(), any())).thenReturn(1);
        OutboundTask saved = task("WAITING_CONFIRMATION", 1L);
        saved.setTotalWeight(2.0);
        saved.setUnitPrice(new BigDecimal("12.00"));
        when(taskMapper.selectById(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(saved);
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq("HOUSE"), any(), any(), any()))
            .thenReturn(List.of());
        when(eligibilityService.evaluate(anyList())).thenReturn(List.of());
        when(allocationMapper.selectByTask(HOUSE_ID, TASK_ID)).thenReturn(
            expectPreserved ? List.of(allocation(101L, "2.000")) : List.of()
        );
        List<OutboundDtos.SelectedRabbitInput> inputs = draftRabbits.stream()
            .map(item -> input(
                item.rabbitId(),
                item.stateVersion(),
                item.selectionType(),
                item.earlySaleReason()
            ))
            .toList();
        OutboundDtos.SaveDraftRequest request = new OutboundDtos.SaveDraftRequest(
            0L,
            "WAITING_CONFIRMATION",
            inputs,
            null,
            2.0,
            null,
            new BigDecimal("12.00"),
            null,
            null,
            null
        );

        OutboundDtos.TaskView view = service.save(
            USER_ID, HOUSE_ID, TASK_ID, request
        );

        assertEquals(expectPreserved ? 1 : 0, view.batchAllocations().size());
        if (expectPreserved) {
            verify(allocationMapper, never()).deleteByTaskLimited(
                anyLong(), anyString(), anyInt()
            );
        } else {
            verify(allocationMapper).deleteByTaskLimited(
                HOUSE_ID, TASK_ID, 1_000
            );
        }
    }

    private OutboundTaskItem snapshotItem(
        Long rabbitId,
        Long batchId,
        Long stateVersion,
        String selectionType,
        String earlySaleReason
    ) {
        OutboundTaskItem item = new OutboundTaskItem();
        item.setTaskId(TASK_ID);
        item.setRabbitId(rabbitId);
        item.setBatchIdSnapshot(batchId);
        item.setStateVersion(stateVersion);
        item.setSelectionType(selectionType);
        item.setEarlySaleReason(earlySaleReason);
        return item;
    }

    private record DraftRabbit(
        Long rabbitId,
        Long batchId,
        Long stateVersion,
        String selectionType,
        String earlySaleReason
    ) {}

    private void stubEditableTask() {
        when(taskMapper.selectByIdForUpdate(HOUSE_ID, USER_ID, TASK_ID)).thenReturn(task("SELECTING", 0L));
    }

    private void stubCandidate(Long rabbitId, String eligibility, Long stateVersion) {
        OutboundCandidateRow row = candidate(rabbitId, null);
        when(eligibilityService.rowsByIds(eq(HOUSE_ID), anyList())).thenReturn(List.of(row));
        when(eligibilityService.evaluate(row)).thenReturn(
            eligibilityView(rabbitId, eligibility, stateVersion, null)
        );
    }

    private OutboundCandidateRow candidate(Long rabbitId, Long batchId) {
        OutboundCandidateRow row = new OutboundCandidateRow();
        row.setRabbitId(rabbitId);
        row.setHouseId(HOUSE_ID);
        row.setCageId(3L);
        row.setBatchId(batchId);
        return row;
    }

    /** view() 会再扫一次候选并做汇总，这里给出足以让它跑完的空结果。 */
    private void stubViewFor(String entryType) {
        when(taskMapper.selectById(eq(HOUSE_ID), eq(USER_ID), anyString())).thenReturn(task("SELECTING", 0L));
        when(eligibilityService.scopeRows(eq(HOUSE_ID), eq(entryType), any(), any(), any())).thenReturn(List.of());
        when(eligibilityService.evaluate(anyList())).thenReturn(List.of());
        when(eligibilityService.summary(anyList())).thenReturn(null);
        when(itemMapper.selectByTask(anyString())).thenReturn(List.of());
    }

    private OutboundDtos.RabbitEligibilityView eligibilityView(
            Long rabbitId, String eligibility, Long stateVersion, Long batchId) {
        return new OutboundDtos.RabbitEligibilityView(
                rabbitId, 3L, "R1-1-1", "R1", 1, 1, "2", "0", 2.5, "可出售", batchId,
                stateVersion, eligibility, null, null, null, true);
    }

    private OutboundTaskBatchAllocation allocation(Long batchId, String weight) {
        OutboundTaskBatchAllocation allocation = new OutboundTaskBatchAllocation();
        allocation.setTaskId(TASK_ID);
        allocation.setHouseId(HOUSE_ID);
        allocation.setBatchId(batchId);
        allocation.setActualWeightKg(new BigDecimal(weight));
        return allocation;
    }

    private OutboundTask task(String status, Long revision) {
        OutboundTask task = new OutboundTask();
        task.setTaskId(TASK_ID);
        task.setHouseId(HOUSE_ID);
        task.setOperatorId(USER_ID);
        task.setEntryType("HOUSE");
        task.setStatus(status);
        task.setRevision(revision);
        return task;
    }

    private OutboundDtos.CreateTaskRequest createRequest(
            String entryType, Long rabbitId, Long cageId, String rowCode, Boolean resume) {
        return new OutboundDtos.CreateTaskRequest(entryType, rabbitId, cageId, rowCode, resume);
    }

    private OutboundDtos.SaveDraftRequest saveRequest(
            Long revision, String status, List<OutboundDtos.SelectedRabbitInput> items) {
        return new OutboundDtos.SaveDraftRequest(revision, status, items, null, null, null, null, null);
    }

    private OutboundDtos.SaveDraftRequest allocationRequest(
            String status,
            List<OutboundDtos.SelectedRabbitInput> items,
            List<SaleBatchAllocationInput> allocations) {
        return new OutboundDtos.SaveDraftRequest(
            0L,
            status,
            items,
            null,
            4.0,
            null,
            new BigDecimal("12.00"),
            allocations,
            null,
            null
        );
    }

    private OutboundDtos.SelectedRabbitInput input(
            Long rabbitId, Long stateVersion, String selectionType, String reason) {
        return new OutboundDtos.SelectedRabbitInput(rabbitId, stateVersion, selectionType, reason);
    }
}
