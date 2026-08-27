package com.rabbit.app.modules.vaccination.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.vaccination.dto.VaccinationBatchResult;
import com.rabbit.app.modules.vaccination.entity.VaccinationRecord;
import com.rabbit.app.modules.vaccination.mapper.VaccinationRecordMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * 批量接种。
 *
 * <p>接种记录是防疫判断的唯一依据：某只兔该不该补针、整舍的免疫覆盖率够不够，
 * 全看这张表。错误在这里不会当场爆发——疫苗照打了，人也走了，只有等到
 * 疫情排查时才发现记录和现实对不上，那时候已经无从追溯。
 *
 * <p>三处关键守卫。<b>整批原子</b>：任一只不合法就整批拒绝，不允许「跳过异常的
 * 继续打剩下的」——静默漏掉几只会让人以为整笼都打过。<b>周期</b>：下次接种日
 * 必须严格晚于本次，等于或早于都会让这条记录一落库就是过期状态，立刻挤进
 * 待接种列表。<b>收口</b>：补针后要把同一疫苗的旧待接种记录关掉，且不能把
 * 刚写的这批自己关掉——收口范围错了，要么旧针永远挂着，要么新针一出生就是已完成。
 */
class VaccinationServiceTest {
    private static final Long USER_ID = 9L;
    private static final Long HOUSE_ID = 1L;
    private static final String REQ = "req-1";
    private static final String API = "vaccination:create";
    private static final Date SHOT_TIME = new Date(1_700_000_000_000L);
    private static final Date NEXT_DUE = new Date(1_700_000_000_000L + 86_400_000L * 21);

    private RabbitMapper rabbitMapper;
    private VaccinationRecordMapper recordMapper;
    private RequestDedupService dedupService;
    private VaccinationService service;

    @BeforeEach
    void setUp() {
        rabbitMapper = mock(RabbitMapper.class);
        recordMapper = mock(VaccinationRecordMapper.class);
        dedupService = mock(RequestDedupService.class);
        service = new VaccinationService(rabbitMapper, recordMapper, dedupService);
    }

    // ---------- 幂等 ----------

    /**
     * 重放回查旧记录并报 created=0。若照常再插一批，同一针会在履历上出现两次，
     * 下次判断「这只兔打过几针」就会多算。
     */
    @Test
    void aReplayedBatchIsReadBackAndReportsNothingCreated() {
        List<VaccinationRecord> stored = List.of(new VaccinationRecord());
        when(dedupService.shouldSkipAsDone(HOUSE_ID, USER_ID, API, REQ)).thenReturn(true);
        when(recordMapper.selectByReq(HOUSE_ID, REQ)).thenReturn(stored);

        VaccinationBatchResult result = service.create(USER_ID, HOUSE_ID, List.of(7L), template(), REQ);

        assertEquals(0, result.created());
        assertSame(stored, result.records());
        verify(recordMapper, never()).insertBatch(anyList());
        verifyNoInteractions(rabbitMapper);
    }

    // ---------- 目标兔只 ----------

    @Test
    void aBatchWithoutTargetsIsRejected() {
        assertEquals("请选择需要接种的兔只", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, null, template(), REQ)).getMessage());
        assertEquals("请选择需要接种的兔只", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(), template(), REQ)).getMessage());
    }

    @Test
    void anEmptyTemplateIsRejected() {
        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), null, REQ));
        assertEquals(400, error.getCode());
        assertEquals("接种记录不能为空", error.getMessage());
    }

    /**
     * 非法 ID 要整批拒绝而不是悄悄跳过。跳过的那只兔实际没打针，
     * 但操作者从返回结果里看不出少了谁。
     */
    @Test
    void anInvalidRabbitIdFailsTheWholeBatch() {
        assertEquals("rabbitId不合法", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, Arrays.asList(7L, null), template(), REQ)).getMessage());
        assertEquals("rabbitId不合法", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, Arrays.asList(7L, 0L), template(), REQ)).getMessage());
        verify(recordMapper, never()).insertBatch(anyList());
    }

    /**
     * 重复勾选要去重并保序。重复的 (house, rabbit, request) 会撞 uk_vr_req，
     * 报出来的唯一键错误和用户的操作对不上，人只会看到一句看不懂的数据库异常。
     */
    @Test
    void duplicateTargetsAreDedupedWhileKeepingTheSubmittedOrder() {
        stubActiveRabbits(8L, 7L);

        VaccinationBatchResult result = service.create(
                USER_ID, HOUSE_ID, Arrays.asList(8L, 7L, 8L), template(), REQ);

        assertEquals(2, result.created());
        List<VaccinationRecord> rows = capturedRows();
        assertEquals(8L, rows.get(0).getRabbitId());
        assertEquals(7L, rows.get(1).getRabbitId());
    }

    @Test
    void aBatchLargerThanTheCapIsRejected() {
        List<Long> tooMany = new ArrayList<Long>();
        for (long i = 1; i <= VaccinationService.MAX_BATCH_SIZE + 1; i++) {
            tooMany.add(i);
        }

        assertEquals("单次接种不能超过500只兔", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, tooMany, template(), REQ)).getMessage());
        verifyNoInteractions(rabbitMapper);
    }

    // ---------- 疫苗与周期 ----------

    @Test
    void anUnnamedVaccineIsRejected() {
        VaccinationRecord template = template();
        template.setVaccineName("   ");

        assertEquals("疫苗名称不能为空", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), template, REQ)).getMessage());
    }

    /**
     * 下次接种日必须严格晚于本次。相等或更早的记录一落库就已过期，
     * 会立刻出现在待接种列表里，让人以为刚打完的针又该补了。
     */
    @Test
    void theNextDueDateMustBeStrictlyAfterThisShot() {
        VaccinationRecord sameDay = template();
        sameDay.setVaccinatedAt(SHOT_TIME);
        sameDay.setNextDueDate(SHOT_TIME);

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), sameDay, REQ));
        assertEquals(400, error.getCode());
        assertEquals("下次接种日期必须晚于本次接种时间", error.getMessage());

        VaccinationRecord backwards = template();
        backwards.setVaccinatedAt(SHOT_TIME);
        backwards.setNextDueDate(new Date(SHOT_TIME.getTime() - 1));
        assertThrows(BizException.class, () -> service.create(USER_ID, HOUSE_ID, List.of(7L), backwards, REQ));

        verify(recordMapper, never()).insertBatch(anyList());
    }

    /**
     * 周期校验必须拿「实际使用的接种时间」比，而不是模板里那个可能为 null 的值。
     * 不传接种时间时补的是当下，一个早于当下的 nextDueDate 同样要被拦住。
     */
    @Test
    void thePeriodIsCheckedAgainstTheTimeActuallyUsed() {
        VaccinationRecord template = template();
        template.setVaccinatedAt(null);
        template.setNextDueDate(new Date(SHOT_TIME.getTime()));

        assertEquals("下次接种日期必须晚于本次接种时间", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), template, REQ)).getMessage());
    }

    @Test
    void anAbsentShotTimeFallsBackToNow() {
        stubActiveRabbits(7L);
        VaccinationRecord template = template();
        template.setVaccinatedAt(null);
        template.setNextDueDate(null);

        service.create(USER_ID, HOUSE_ID, List.of(7L), template, REQ);

        assertNotNull(capturedRows().get(0).getVaccinatedAt());
    }

    /**
     * 有下次接种日就是待接种，没有就是本次结清。状态判错会让该补的针从
     * 待接种列表里消失，或者让打完的针永远挂在列表上，两者都会误导防疫安排。
     */
    @Test
    void aShotWithAFollowUpIsLeftScheduled() {
        stubActiveRabbits(7L);
        VaccinationRecord withFollowUp = template();
        withFollowUp.setVaccinatedAt(SHOT_TIME);
        withFollowUp.setNextDueDate(NEXT_DUE);

        service.create(USER_ID, HOUSE_ID, List.of(7L), withFollowUp, REQ);

        assertEquals(VaccinationService.STATUS_SCHEDULED, capturedRows().get(0).getStatus());
    }

    @Test
    void aOneOffShotIsClosedImmediately() {
        stubActiveRabbits(7L);
        VaccinationRecord oneOff = template();
        oneOff.setVaccinatedAt(SHOT_TIME);
        oneOff.setNextDueDate(null);

        service.create(USER_ID, HOUSE_ID, List.of(7L), oneOff, REQ);

        assertEquals(VaccinationService.STATUS_DONE, capturedRows().get(0).getStatus());
    }

    // ---------- 目标兔只的在场校验 ----------

    /**
     * 查不到的兔子要点名报出来。整批放行会给一只不存在的兔建立接种档案，
     * 而它对应的实体兔其实一针没打。
     */
    @Test
    void rabbitsThatCannotBeFoundAreNamedInTheError() {
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), anyList())).thenReturn(List.of());

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, Arrays.asList(7L, 8L), template(), REQ));
        assertEquals(400, error.getCode());
        assertEquals("兔子不存在：7、8", error.getMessage());
        verify(recordMapper, never()).insertBatch(anyList());
    }

    /**
     * 跨舍的兔只算作不存在。mapper 已按 houseId 过滤，这层复核是防止有人
     * 换掉查询后把别舍的兔混进本舍的免疫档案。
     */
    @Test
    void aRabbitBelongingToAnotherHouseCountsAsMissing() {
        Rabbit foreign = rabbit(7L, 99L, true);
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), anyList())).thenReturn(List.of(foreign));

        assertEquals("兔子不存在：7", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), template(), REQ)).getMessage());
    }

    /**
     * 已离场的兔不能补记接种。给一只卖掉的兔记上今天的针，会让免疫覆盖率
     * 虚高，而真正在栏的那只兔反而漏了。
     */
    @Test
    void aRabbitThatHasLeftCannotBeVaccinated() {
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), anyList()))
                .thenReturn(List.of(rabbit(7L, HOUSE_ID, false)));

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), template(), REQ));
        assertEquals("兔子不在场：7", error.getMessage());
    }

    @Test
    void aRabbitWithNoActivityFlagIsTreatedAsAbsent() {
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), anyList()))
                .thenReturn(List.of(rabbit(7L, HOUSE_ID, null)));

        assertEquals("兔子不在场：7", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, List.of(7L), template(), REQ)).getMessage());
    }

    /**
     * 报错列表超过 5 只时截断并给出总数。整串 500 个 ID 拼进错误信息，
     * 前端弹窗塞不下，人一个也读不到。
     */
    @Test
    void aLongListOfBadTargetsIsSummarised() {
        List<Long> many = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), anyList())).thenReturn(List.of());

        assertEquals("兔子不存在：1、2、3、4、5 等 7 只", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, many, template(), REQ)).getMessage());
    }

    // ---------- 落库 ----------

    /**
     * 收口必须发生在插入之前，且要把本批的 requestId 排除在外。
     * 顺序反了或漏传 excludeRequestId，刚写进去的新记录会被自己收口成 DONE，
     * 该补的针从待接种列表上凭空消失。
     */
    @Test
    void oldSchedulesAreClosedBeforeTheNewBatchLandsAndTheNewBatchIsSpared() {
        stubActiveRabbits(7L);

        service.create(USER_ID, HOUSE_ID, List.of(7L), template(), REQ);

        InOrder order = inOrder(recordMapper);
        order.verify(recordMapper).markSupersededDone(
                eq(HOUSE_ID), anyList(), eq("兔瘟疫苗"), eq(REQ), eq("9"));
        order.verify(recordMapper).insertBatch(anyList());
    }

    /**
     * 收口只针对同一种疫苗。用错疫苗名会把别的疫苗的待接种计划一起关掉，
     * 整舍的免疫排程静默清空。
     */
    @Test
    void closingOldSchedulesIsScopedToTheSameVaccine() {
        stubActiveRabbits(7L);
        VaccinationRecord template = template();
        template.setVaccineName("  巴氏杆菌苗  ");

        service.create(USER_ID, HOUSE_ID, List.of(7L), template, REQ);

        verify(recordMapper).markSupersededDone(
                eq(HOUSE_ID), anyList(), eq("巴氏杆菌苗"), eq(REQ), eq("9"));
        assertEquals("巴氏杆菌苗", capturedRows().get(0).getVaccineName());
    }

    @Test
    void everyRowCarriesTheHouseTheRequestAndTheOperator() {
        stubActiveRabbits(7L);
        VaccinationRecord template = template();
        template.setVaccinatedAt(SHOT_TIME);
        template.setNextDueDate(NEXT_DUE);

        VaccinationBatchResult result = service.create(USER_ID, HOUSE_ID, List.of(7L), template, REQ);

        assertEquals(1, result.created());
        VaccinationRecord row = capturedRows().get(0);
        assertEquals(HOUSE_ID, row.getHouseId());
        assertEquals(REQ, row.getRequestId());
        assertEquals("9", row.getCreateBy());
        assertEquals("9", row.getUpdateBy());
        assertEquals(SHOT_TIME, row.getVaccinatedAt());
        assertEquals(NEXT_DUE, row.getNextDueDate());
    }

    /**
     * 空白的可选字段要归 null，不能留下一串空格。空字符串在「有没有填批号」
     * 这类判断里是 true，会让追溯疫苗批号时查出一批看似有值的空记录。
     */
    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        stubActiveRabbits(7L);
        VaccinationRecord template = template();
        template.setVaccineBatchNo("  ");
        template.setDose("");
        template.setRoute("   ");
        template.setRemark(" ");

        service.create(USER_ID, HOUSE_ID, List.of(7L), template, REQ);

        VaccinationRecord row = capturedRows().get(0);
        assertNull(row.getVaccineBatchNo());
        assertNull(row.getDose());
        assertNull(row.getRoute());
        assertNull(row.getRemark());
    }

    @Test
    void aRejectedBatchIsRecordedAsFailed() {
        assertThrows(BizException.class, () -> service.create(USER_ID, HOUSE_ID, List.of(), template(), REQ));

        verify(dedupService).markProcessing(HOUSE_ID, USER_ID, API, REQ);
        verify(dedupService).markFailed(HOUSE_ID, USER_ID, API, REQ, "请选择需要接种的兔只");
        verify(dedupService, never()).markDone(anyLong(), anyLong(), anyString(), anyString());
    }

    // ---------- 查询 ----------

    @Test
    void historyLimitsAreClamped() {
        service.listByRabbit(HOUSE_ID, 7L, 0);
        verify(recordMapper).selectByRabbit(HOUSE_ID, 7L, 50);

        service.listByRabbit(HOUSE_ID, 7L, 9999);
        verify(recordMapper).selectByRabbit(HOUSE_ID, 7L, 200);
    }

    @Test
    void theDueListIsScopedToTheHouse() {
        service.listDue(HOUSE_ID);
        verify(recordMapper).selectDueByHouse(eq(HOUSE_ID), any(Date.class));
    }

    // ---------- 夹具 ----------

    private void stubActiveRabbits(Long... ids) {
        List<Rabbit> rabbits = new ArrayList<Rabbit>();
        for (Long id : ids) {
            rabbits.add(rabbit(id, HOUSE_ID, true));
        }
        when(rabbitMapper.selectByIdsForUpdate(eq(HOUSE_ID), anyList())).thenReturn(rabbits);
    }

    private Rabbit rabbit(Long id, Long houseId, Boolean active) {
        Rabbit r = new Rabbit();
        r.setId(id);
        r.setHouseId(houseId);
        r.setIsActive(active);
        return r;
    }

    private List<VaccinationRecord> capturedRows() {
        ArgumentCaptor<List<VaccinationRecord>> rows = ArgumentCaptor.forClass(List.class);
        verify(recordMapper).insertBatch(rows.capture());
        return rows.getValue();
    }

    private VaccinationRecord template() {
        VaccinationRecord template = new VaccinationRecord();
        template.setVaccineName("兔瘟疫苗");
        template.setVaccinatedAt(SHOT_TIME);
        template.setNextDueDate(NEXT_DUE);
        return template;
    }
}
