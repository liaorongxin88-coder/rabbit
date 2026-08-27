package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 断奶登记与分笼落位的数量守恒。
 *
 * <p>这条链上任何一处加减写错，都不会当场报错：笼位计数、仔兔条数、batch_rabbits
 * 关联数会各自静静地对不上，等到报表里发现存栏差了几只时，已经无法倒查是哪一次分笼
 * 错的。所以这里逐条钉死三件事：
 *
 * <ul>
 *   <li>断奶登记时公母之和必须等于断奶数，waitingCount 必须等于断奶数（不能少写，
 *       否则后面永远分不完这一窝）；</li>
 *   <li>分笼时生成的兔子条数必须等于各笼 allocCount 之和，且不能超过待分笼数；</li>
 *   <li>分笼幂等键的序号从「已分笼数」接着往下排，否则第二次分笼会复用第一次的
 *       requestId，回查命中旧兔子，新兔子静默丢失。</li>
 * </ul>
 *
 * <p>笼位计数用的是带容量上限的条件自增，返回值不是 1 就说明笼位被并发改过，
 * 必须整单失败——放过去就是超容量塞兔。
 */
class KitPlacementServiceTest {

    private static final Long HOUSE_ID = 8L;
    private static final Long BATCH_ID = 66L;
    private static final Long CYCLE_ID = 77L;
    private static final Long MOTHER_ID = 100L;
    private static final Long FATHER_ID = 200L;
    private static final int CAGE_CAPACITY = 10;

    private CageMapper cageMapper;
    private RabbitMapper rabbitMapper;
    private RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private BatchRabbitMapper batchRabbitMapper;
    private WeaningRecordMapper weaningRecordMapper;
    private BreedingPerformanceRecorder performanceRecorder;
    private SettingService settingService;
    private WorkTaskWriter workTaskWriter;
    private KitPlacementService service;

    @BeforeEach
    void setUp() {
        cageMapper = mock(CageMapper.class);
        rabbitMapper = mock(RabbitMapper.class);
        rabbitStatusHistoryMapper = mock(RabbitStatusHistoryMapper.class);
        batchRabbitMapper = mock(BatchRabbitMapper.class);
        weaningRecordMapper = mock(WeaningRecordMapper.class);
        performanceRecorder = mock(BreedingPerformanceRecorder.class);
        settingService = mock(SettingService.class);
        workTaskWriter = mock(WorkTaskWriter.class);
        service = new KitPlacementService(
            cageMapper,
            rabbitMapper,
            rabbitStatusHistoryMapper,
            batchRabbitMapper,
            weaningRecordMapper,
            performanceRecorder,
            settingService,
            workTaskWriter,
            CAGE_CAPACITY
        );
    }

    // ---------- 断奶登记 ----------

    /**
     * waitingCount 是「还剩几只没分笼」的唯一依据。登记时若不等于断奶数，
     * 这一窝要么永远分不完，要么能分出比实际断奶更多的兔子。
     */
    @Test
    void registeringWeaningSeedsWaitingCountEqualToWeanedCount() {
        WeaningRecord saved = service.registerPending(weaningCommand(12, 7, 5));

        assertEquals(12, saved.getWeaningCount().intValue());
        assertEquals(12, saved.getWaitingCount().intValue());
        assertEquals(7, saved.getMaleCount().intValue());
        assertEquals(5, saved.getFemaleCount().intValue());
        verify(weaningRecordMapper).insert(saved);
        verify(performanceRecorder).recordWeaning(HOUSE_ID, MOTHER_ID, 12);
    }

    /**
     * 断奶登记绝不能顺手占笼。占了笼但没生成兔子，笼位计数就凭空多出一批
     * 查无实兔的占用，之后谁都排不进去。
     */
    @Test
    void registeringWeaningNeverAllocatesACage() {
        WeaningRecord saved = service.registerPending(weaningCommand(6, 0, 0));

        assertNull(saved.getTargetCageId());
        assertNull(saved.getInCageId());
        verifyNoInteractions(cageMapper);
        verifyNoInteractions(rabbitMapper);
    }

    @Test
    void weaningWithNegativeCountIsRejected() {
        BizException error = assertThrows(
            BizException.class, () -> service.registerPending(weaningCommand(-1, 0, 0))
        );

        assertEquals(400, error.getCode());
        assertEquals("断奶数量错误", error.getMessage());
        verifyNoInteractions(weaningRecordMapper);
    }

    /** 公 + 母 ≠ 总数会让后续按性别分笼的分配数对不上总数。 */
    @Test
    void weaningWithGenderSplitNotMatchingTotalIsRejected() {
        BizException error = assertThrows(
            BizException.class, () -> service.registerPending(weaningCommand(10, 7, 5))
        );

        assertEquals(400, error.getCode());
        assertEquals("公母数量之和需等于断奶数量", error.getMessage());
        verifyNoInteractions(weaningRecordMapper);
    }

    /** 公母都为 0 是「不区分性别」的合法录入，不是一次和为 0 的错误录入。 */
    @Test
    void weaningWithoutGenderSplitIsAccepted() {
        WeaningRecord saved = service.registerPending(weaningCommand(9, 0, 0));

        assertEquals(9, saved.getWeaningCount().intValue());
        verify(weaningRecordMapper).insert(saved);
    }

    /** 只填了公数、母数留空时，男女和为 6 与断奶数 9 不符，应当拦下。 */
    @Test
    void weaningWithPartialGenderSplitIsRejected() {
        BizException error = assertThrows(
            BizException.class, () -> service.registerPending(weaningCommand(9, 6, null))
        );

        assertEquals(400, error.getCode());
    }

    // ---------- 分笼数量守恒 ----------

    /**
     * 分笼总数不能超过待分笼数：超了就是凭空造兔，存栏直接多出来。
     */
    @Test
    void separatingMoreThanWaitingCountIsRejected() {
        WeaningRecord record = record(10, 4);

        BizException error = assertThrows(BizException.class, () -> service.separate(
            separationCommand(record, allocation(9L, 5, null, null))
        ));

        assertEquals(400, error.getCode());
        assertEquals("分笼数量超过待分笼数量", error.getMessage());
        verifyNoInteractions(cageMapper);
    }

    /** 正好等于待分笼数是合法的边界，不能被 &gt;= 写成误伤。 */
    @Test
    void separatingExactlyTheWaitingCountIsAccepted() {
        WeaningRecord record = record(10, 4);
        stubInsertAndRequery();

        List<Long> ids = service.separate(
            separationCommand(record, allocation(9L, 4, null, null))
        );

        assertEquals(4, ids.size());
    }

    /** 一只都不分是无意义请求，放过去会写出一条空事件。 */
    @Test
    void separatingZeroKitsIsRejected() {
        WeaningRecord record = record(10, 10);

        BizException error = assertThrows(BizException.class, () -> service.separate(
            separationCommand(record, allocation(9L, 0, null, null))
        ));

        assertEquals(400, error.getCode());
        assertEquals("分笼数量超过待分笼数量", error.getMessage());
    }

    @Test
    void separatingAgainstAnUnsavedWeaningRecordIsRejected() {
        WeaningRecord unsaved = record(10, 10);
        unsaved.setId(null);

        BizException error = assertThrows(BizException.class, () -> service.separate(
            separationCommand(unsaved, allocation(9L, 3, null, null))
        ));

        assertEquals(400, error.getCode());
        assertEquals("待分笼记录不存在", error.getMessage());
    }

    /**
     * 生成的兔子条数必须逐笼等于 allocCount，且落在对应笼位上。多一只少一只
     * 都会让笼位计数与实际兔数长期错位。
     */
    @Test
    void oneKitIsCreatedPerAllocatedSlotInEachCage() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();

        List<Long> ids = service.separate(separationCommand(
            record, allocation(9L, 3, null, null), allocation(11L, 2, null, null)
        ));

        assertEquals(5, ids.size());
        List<Rabbit> kits = capturedKits();
        assertEquals(5, kits.size());
        assertEquals(3, kits.stream().filter(k -> k.getCageId().equals(9L)).count());
        assertEquals(2, kits.stream().filter(k -> k.getCageId().equals(11L)).count());
        verify(cageMapper).incrementCommodityRabbitCountWithinCapacity(
            HOUSE_ID, 9L, 3, CAGE_CAPACITY, "op"
        );
        verify(cageMapper).incrementCommodityRabbitCountWithinCapacity(
            HOUSE_ID, 11L, 2, CAGE_CAPACITY, "op"
        );
    }

    /** allocCount 为 0 或负的行既不占笼也不生成兔子，直接跳过。 */
    @Test
    void allocationsWithoutPositiveCountAreSkipped() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();

        service.separate(separationCommand(
            record, allocation(9L, 0, null, null), allocation(11L, 4, null, null)
        ));

        assertEquals(4, capturedKits().size());
        verify(cageMapper, never()).incrementCommodityRabbitCountWithinCapacity(
            anyLong(), eq(9L), anyInt(), anyInt(), anyString()
        );
    }

    /**
     * 条件自增返回非 1 表示笼位状态或容量在读到写之间被改过。此时必须整单抛错，
     * 否则就会在超容量的笼里继续生成兔子。
     */
    @Test
    void aCageThatNoLongerHasCapacityAbortsTheWholeSeparation() {
        WeaningRecord record = record(10, 10);
        when(cageMapper.incrementCommodityRabbitCountWithinCapacity(
            anyLong(), anyLong(), anyInt(), anyInt(), anyString()
        )).thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.separate(
            separationCommand(record, allocation(9L, 3, null, null))
        ));

        assertEquals(409, error.getCode());
        assertEquals("笼位状态或容量已变化，请刷新后重试", error.getMessage());
        verify(rabbitMapper, never()).insertBatch(any());
    }

    /**
     * 幂等键序号接着「已分笼数」往下排。若每次都从 0 起，第二次分笼的
     * kit-0 会与第一次撞键，回查拿到旧兔子的 id，新兔子静默消失。
     */
    @Test
    void kitRequestIdsContinueFromAlreadySeparatedCount() {
        WeaningRecord record = record(10, 4);
        stubInsertAndRequery();

        service.separate(separationCommand(record, allocation(9L, 2, null, null)));

        List<String> requestIds = capturedKits().stream().map(Rabbit::getRequestId).toList();
        assertEquals(List.of("req-1-kit-6", "req-1-kit-7"), requestIds);
    }

    /** 同一次分笼内的幂等键必须两两不同，否则回查会少一条而整单失败。 */
    @Test
    void kitRequestIdsAreUniqueWithinOneSeparation() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();

        service.separate(separationCommand(
            record, allocation(9L, 3, null, null), allocation(11L, 3, null, null)
        ));

        Set<String> ids = new HashSet<>(capturedKits().stream().map(Rabbit::getRequestId).toList());
        assertEquals(6, ids.size());
    }

    // ---------- 性别分配 ----------

    /** 笼内按公数在前排性别：前 maleCount 只是公（1），其余是母（0）。 */
    @Test
    void genderIsAssignedByMaleCountWithinEachCage() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();

        service.separate(separationCommand(record, allocation(9L, 5, 2, 3)));

        List<String> genders = capturedKits().stream().map(Rabbit::getGender).toList();
        assertEquals(List.of("1", "1", "0", "0", "0"), genders);
    }

    /** 分配行没填性别时落成未知（2），不能默认成公或母而污染性别统计。 */
    @Test
    void allocationsWithoutGenderProduceUnknownGender() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();

        service.separate(separationCommand(record, allocation(9L, 2, null, null)));

        assertTrue(capturedKits().stream().allMatch(k -> "2".equals(k.getGender())));
    }

    // ---------- 写入与回查 ----------

    /** 批量写入返回的行数不等于提交条数，说明有兔子没落库，必须整单失败。 */
    @Test
    void aShortBatchInsertAbortsTheSeparation() {
        WeaningRecord record = record(10, 10);
        when(cageMapper.incrementCommodityRabbitCountWithinCapacity(
            anyLong(), anyLong(), anyInt(), anyInt(), anyString()
        )).thenReturn(1);
        when(rabbitMapper.insertBatch(any())).thenReturn(2);

        BizException error = assertThrows(BizException.class, () -> service.separate(
            separationCommand(record, allocation(9L, 3, null, null))
        ));

        assertEquals(500, error.getCode());
        assertEquals("仔兔批量写入失败", error.getMessage());
        verifyNoInteractions(batchRabbitMapper);
    }

    /** 回查条数少于提交条数说明主键没拿全，继续走下去会写出 rabbit_id 为空的关联。 */
    @Test
    void aShortRequeryAbortsTheSeparation() {
        WeaningRecord record = record(10, 10);
        when(cageMapper.incrementCommodityRabbitCountWithinCapacity(
            anyLong(), anyLong(), anyInt(), anyInt(), anyString()
        )).thenReturn(1);
        when(rabbitMapper.insertBatch(any())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        when(rabbitMapper.selectByHouseAndRequestIds(anyLong(), any()))
            .thenAnswer(inv -> {
                List<String> requestIds = inv.getArgument(1);
                List<Rabbit> saved = new ArrayList<>();
                saved.add(persisted(1L, requestIds.get(0)));
                return saved;
            });

        BizException error = assertThrows(BizException.class, () -> service.separate(
            separationCommand(record, allocation(9L, 3, null, null))
        ));

        assertEquals(500, error.getCode());
        assertEquals("仔兔批量写入回查失败", error.getMessage());
        verifyNoInteractions(batchRabbitMapper);
    }

    // ---------- 批次关联与后续待办 ----------

    /**
     * 每只仔兔都要有一条 batch_rabbits 关联和一条状态历史。少一条，这只兔子
     * 就不在批次统计里，从此变成账外存栏。
     */
    @Test
    void everyKitGetsABatchLinkAndAStatusHistory() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();
        stubSetting(40);

        service.separate(separationCommand(
            record, allocation(9L, 2, null, null), allocation(11L, 1, null, null)
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BatchRabbit>> links = ArgumentCaptor.forClass(List.class);
        verify(batchRabbitMapper).insertBatch(links.capture());
        assertEquals(3, links.getValue().size());
        assertTrue(links.getValue().stream().allMatch(l -> BATCH_ID.equals(l.getBatchId())));
        assertTrue(links.getValue().stream().allMatch(l -> l.getRabbitId() != null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RabbitStatusHistory>> histories = ArgumentCaptor.forClass(List.class);
        verify(rabbitStatusHistoryMapper).insertBatch(histories.capture());
        assertEquals(3, histories.getValue().size());
        assertTrue(histories.getValue().stream()
            .allMatch(h -> "幼兔适应期".equals(h.getToStatus())));
    }

    /**
     * 出售日按有效设置的商品兔成熟天数从分笼日推算。取错天数会让整批待售待办
     * 集体早到或晚到。
     */
    @Test
    void saleReadyTaskIsScheduledAtSeparationDatePlusMaturityDays() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();
        stubSetting(30);
        Date separatedAt = new Date(1_700_000_000_000L);

        service.separate(new KitSeparationCommand(
            5L, "op", record, MOTHER_ID, FATHER_ID,
            List.of(allocation(9L, 1, null, null)), separatedAt, "req-1"
        ));

        ArgumentCaptor<WorkTaskWriter.RabbitTaskScheduleRequest> captor =
            ArgumentCaptor.forClass(WorkTaskWriter.RabbitTaskScheduleRequest.class);
        verify(workTaskWriter).scheduleForRabbit(captor.capture());
        assertEquals(TaskType.SALE_READY, captor.getValue().taskType());
        assertEquals(
            separatedAt.getTime() + 30L * 24 * 60 * 60 * 1000,
            captor.getValue().dueTime().getTime()
        );
    }

    /** 适应期观察待办从分笼当天起排，晚一天就漏掉入笼当天最该看的那一次。 */
    @Test
    void adaptationCareTaskStartsOnTheSeparationDay() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();
        stubSetting(30);
        Date separatedAt = new Date(1_700_000_000_000L);

        service.separate(new KitSeparationCommand(
            5L, "op", record, MOTHER_ID, FATHER_ID,
            List.of(allocation(9L, 1, null, null)), separatedAt, "req-1"
        ));

        ArgumentCaptor<WorkTaskWriter.RabbitTaskScheduleRequest> captor =
            ArgumentCaptor.forClass(WorkTaskWriter.RabbitTaskScheduleRequest.class);
        verify(workTaskWriter).scheduleDailyForRabbit(captor.capture());
        assertEquals(TaskType.COMMODITY_ADAPTATION_CARE, captor.getValue().taskType());
        assertEquals(separatedAt, captor.getValue().dueTime());
    }

    /** 仔兔要带上父母与出生批次，否则谱系断链，之后无法回溯是哪一窝。 */
    @Test
    void kitsCarryParentAndBirthLineage() {
        WeaningRecord record = record(10, 10);
        stubInsertAndRequery();

        service.separate(separationCommand(record, allocation(9L, 1, null, null)));

        Rabbit kit = capturedKits().get(0);
        assertEquals(MOTHER_ID, kit.getMotherId());
        assertEquals(FATHER_ID, kit.getFatherId());
        assertEquals(BATCH_ID, kit.getBirthBatchId());
        assertEquals(HOUSE_ID, kit.getHouseId());
        assertEquals(Boolean.TRUE, kit.getIsActive());
        assertNotNull(kit.getGrowthStage());
    }

    // ---------- 夹具 ----------

    private KitPlacementCommand weaningCommand(int weaned, Integer male, Integer female) {
        return new KitPlacementCommand(
            HOUSE_ID, 5L, "op", BATCH_ID, CYCLE_ID, MOTHER_ID, FATHER_ID,
            new Date(1_700_000_000_000L), weaned, male, female, null, 0.5, "备注", "req-1"
        );
    }

    private KitSeparationCommand separationCommand(
        WeaningRecord record, WeaningRecordAllocation... allocations
    ) {
        return new KitSeparationCommand(
            5L, "op", record, MOTHER_ID, FATHER_ID,
            List.of(allocations), new Date(1_700_000_000_000L), "req-1"
        );
    }

    private WeaningRecord record(int weaningCount, int waitingCount) {
        WeaningRecord record = new WeaningRecord();
        record.setId(500L);
        record.setHouseId(HOUSE_ID);
        record.setBatchId(BATCH_ID);
        record.setBreedingCycleId(CYCLE_ID);
        record.setWeaningCount(weaningCount);
        record.setWaitingCount(waitingCount);
        record.setAvgWeight(0.5);
        return record;
    }

    private static WeaningRecordAllocation allocation(
        Long cageId, Integer count, Integer male, Integer female
    ) {
        WeaningRecordAllocation allocation = new WeaningRecordAllocation();
        allocation.setCageId(cageId);
        allocation.setAllocCount(count);
        allocation.setMaleCount(male);
        allocation.setFemaleCount(female);
        return allocation;
    }

    private void stubSetting(int maturityDays) {
        GlobalSetting setting = new GlobalSetting();
        setting.setSaleDays(maturityDays);
        when(settingService.getEffectiveSetting(anyLong(), anyLong())).thenReturn(setting);
    }

    /** 笼位自增成功、批量写入全中、回查按 requestId 逐一配上主键。 */
    private void stubInsertAndRequery() {
        when(cageMapper.incrementCommodityRabbitCountWithinCapacity(
            anyLong(), anyLong(), anyInt(), anyInt(), anyString()
        )).thenReturn(1);
        when(rabbitMapper.insertBatch(any()))
            .thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        AtomicLong sequence = new AtomicLong(1000L);
        when(rabbitMapper.selectByHouseAndRequestIds(anyLong(), any()))
            .thenAnswer(inv -> {
                List<String> requestIds = inv.getArgument(1);
                List<Rabbit> saved = new ArrayList<>(requestIds.size());
                for (String requestId : requestIds) {
                    saved.add(persisted(sequence.incrementAndGet(), requestId));
                }
                return saved;
            });
        stubSetting(33);
    }

    private static Rabbit persisted(Long id, String requestId) {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(id);
        rabbit.setRequestId(requestId);
        return rabbit;
    }

    private List<Rabbit> capturedKits() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Rabbit>> captor = ArgumentCaptor.forClass(List.class);
        verify(rabbitMapper).insertBatch(captor.capture());
        return new ArrayList<>(captor.getValue());
    }
}
