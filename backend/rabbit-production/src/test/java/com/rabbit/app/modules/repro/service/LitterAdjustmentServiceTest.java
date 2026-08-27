package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.domain.LitterStatus;
import com.rabbit.app.modules.repro.domain.ReproEventType;
import com.rabbit.app.modules.repro.dto.AdjustKeptKitsRequest;
import com.rabbit.app.modules.repro.dto.KeptKitsAdjustmentResponse;
import com.rabbit.app.modules.repro.entity.Litter;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.mapper.LitterMapper;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 留崽数调整的加减守恒与来源约束。
 *
 * <p>调整留崽数实际上是记一次寄养：数字变大是「从别的窝抱进来」，变小是「抱出去」。
 * 只改 kept_kits 而不同步 foster_in / foster_out，全场仔兔总数就会凭空增减——而且
 * 因为每一窝单独看都自洽，这种错要等到汇总报表才暴露，那时早已无从倒查是哪一次调整。
 *
 * <p>因此这里守三件事：
 *
 * <ul>
 *   <li>增量只进 foster_in、减量只进 foster_out，两个计数只增不减；</li>
 *   <li>抱进来必须指明来源母兔且必须是本舍种母，否则这批仔兔来路不明；</li>
 *   <li>抱出去不许填来源，避免把一次减少记成一次寄养入。</li>
 * </ul>
 *
 * <p>另外 requestId 幂等要能挡住整单重放，否则一次网络重试就会把寄养数再加一遍。
 */
class LitterAdjustmentServiceTest {

    private static final Long HOUSE_ID = 8L;
    private static final Long CYCLE_ID = 77L;
    private static final Long LITTER_ID = 55L;
    private static final Long MOTHER_ID = 100L;
    private static final Long SOURCE_MOTHER_ID = 101L;
    private static final String REQUEST_ID = "req-1";

    private ReproCycleMapper reproCycleMapper;
    private ReproEventMapper reproEventMapper;
    private LitterMapper litterMapper;
    private RabbitMapper rabbitMapper;
    private LitterAdjustmentService service;

    @BeforeEach
    void setUp() {
        reproCycleMapper = mock(ReproCycleMapper.class);
        reproEventMapper = mock(ReproEventMapper.class);
        litterMapper = mock(LitterMapper.class);
        rabbitMapper = mock(RabbitMapper.class);
        service = new LitterAdjustmentService(
            reproCycleMapper, reproEventMapper, litterMapper, rabbitMapper, new ObjectMapper()
        );
    }

    // ---------- 数量守恒 ----------

    /**
     * 留崽数增加 = 寄养进来，只累加 foster_in；foster_out 不能被顺手动到，
     * 否则两个方向的累计数会互相抵消，看上去像从没寄养过。
     */
    @Test
    void increasingKeptKitsAccumulatesFosterInOnly() {
        Litter litter = nursingLitter(8, 1, 2);
        stubHappyPath(litter);
        stubBreedingDoe();

        KeptKitsAdjustmentResponse response =
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, SOURCE_MOTHER_ID));

        assertEquals(8, response.previousKeptKits().intValue());
        assertEquals(10, response.keptKits().intValue());
        assertEquals(10, litter.getKeptKits().intValue());
        assertEquals(3, litter.getFosterIn().intValue());
        assertEquals(2, litter.getFosterOut().intValue());
        assertFalse(response.replayed());
    }

    /** 留崽数减少 = 寄养出去，只累加 foster_out。 */
    @Test
    void decreasingKeptKitsAccumulatesFosterOutOnly() {
        Litter litter = nursingLitter(8, 1, 2);
        stubHappyPath(litter);

        service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null));

        assertEquals(5, litter.getKeptKits().intValue());
        assertEquals(1, litter.getFosterIn().intValue());
        assertEquals(5, litter.getFosterOut().intValue());
    }

    /**
     * 当前哺乳数必须跟着留崽数走。它是断奶时的基准，落后一步就会让断奶数
     * 校验用错分母。
     */
    @Test
    void currentNursingFollowsTheNewKeptKits() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(3, null));

        assertEquals(3, litter.getCurrentNursing().intValue());
    }

    /** 数量没变时两个寄养计数都不该动。 */
    @Test
    void anUnchangedCountTouchesNeitherFosterCounter() {
        Litter litter = nursingLitter(8, 1, 2);
        stubHappyPath(litter);

        service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(8, null));

        assertEquals(1, litter.getFosterIn().intValue());
        assertEquals(2, litter.getFosterOut().intValue());
    }

    /** 历史数据里寄养计数可能为 NULL，按 0 起算而不是整单炸掉。 */
    @Test
    void nullFosterCountersStartFromZero() {
        Litter litter = nursingLitter(4, null, null);
        stubHappyPath(litter);
        stubBreedingDoe();

        service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(6, SOURCE_MOTHER_ID));

        assertEquals(2, litter.getFosterIn().intValue());
        assertEquals(0, litter.getFosterOut().intValue());
    }

    /** 留崽数为 NULL 时前值按 0 算，从 0 涨到 3 是一次寄养入而不是空操作。 */
    @Test
    void aNullPreviousKeptKitsCountsAsZero() {
        Litter litter = nursingLitter(null, 0, 0);
        stubHappyPath(litter);
        stubBreedingDoe();

        KeptKitsAdjustmentResponse response =
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(3, SOURCE_MOTHER_ID));

        assertEquals(0, response.previousKeptKits().intValue());
        assertEquals(3, litter.getFosterIn().intValue());
    }

    // ---------- 来源母兔约束 ----------

    /** 抱进来却不说从哪抱的，这批仔兔就成了来路不明的存栏。 */
    @Test
    void increasingWithoutASourceMotherIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, null)));

        assertEquals(400, error.getCode());
        assertEquals("留崽数增加时请选择留崽来源母兔", error.getMessage());
        verify(litterMapper, never()).update(any());
    }

    /** 来源写成自己等于凭空多出仔兔，账面两头都对不上。 */
    @Test
    void increasingWithSelfAsSourceIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, MOTHER_ID)));

        assertEquals(400, error.getCode());
        assertEquals("留崽来源母兔不能是当前母兔", error.getMessage());
        verify(litterMapper, never()).update(any());
    }

    /** 来源必须是本舍在册的种母兔；跨舍或查无此兔都要拦下。 */
    @Test
    void increasingFromAnUnknownRabbitIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);
        when(rabbitMapper.selectById(HOUSE_ID, SOURCE_MOTHER_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, SOURCE_MOTHER_ID)));

        assertEquals(400, error.getCode());
        assertEquals("留崽来源必须是当前兔舍的种母兔", error.getMessage());
    }

    /** 公兔不可能有仔兔可抱，来源填公兔说明选错了对象。 */
    @Test
    void increasingFromABuckIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);
        when(rabbitMapper.selectById(HOUSE_ID, SOURCE_MOTHER_ID)).thenReturn(rabbit("0", "1"));

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, SOURCE_MOTHER_ID)));

        assertEquals(400, error.getCode());
        assertEquals("留崽来源必须是当前兔舍的种母兔", error.getMessage());
    }

    /** 商品兔（类型 2）不在繁育序列里，不能作为寄养来源。 */
    @Test
    void increasingFromACommodityRabbitIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);
        when(rabbitMapper.selectById(HOUSE_ID, SOURCE_MOTHER_ID)).thenReturn(rabbit("2", "0"));

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, SOURCE_MOTHER_ID)));

        assertEquals(400, error.getCode());
    }

    /** id 传 0 与不传等价，不能因为「非 null」就放行。 */
    @Test
    void increasingWithANonPositiveSourceIdIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, 0L)));

        assertEquals(400, error.getCode());
        assertEquals("留崽数增加时请选择留崽来源母兔", error.getMessage());
    }

    /**
     * 减少却填了来源，说明操作者把方向记反了。放过去会写出一条语义相反的事件，
     * 事后无法判断这一窝到底是抱进还是抱出。
     */
    @Test
    void decreasingWithASourceMotherIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, SOURCE_MOTHER_ID)));

        assertEquals(400, error.getCode());
        assertEquals("留崽数未增加时不能填写来源母兔", error.getMessage());
        verify(litterMapper, never()).update(any());
    }

    /** 数量不变时同样不接受来源，理由与减少一致。 */
    @Test
    void anUnchangedCountWithASourceMotherIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(8, SOURCE_MOTHER_ID)));

        assertEquals(400, error.getCode());
        assertEquals("留崽数未增加时不能填写来源母兔", error.getMessage());
    }

    // ---------- 前置状态 ----------

    /** 已断奶的窝再调留崽数没有意义，改了也不会反映到任何存栏上。 */
    @Test
    void adjustingAWeanedLitterIsRejected() {
        Litter litter = nursingLitter(8, 0, 0);
        litter.setStatus(LitterStatus.WEANED.name());
        stubHappyPath(litter);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null)));

        assertEquals(409, error.getCode());
        assertEquals("只有哺乳中的窝可以调整留崽数", error.getMessage());
    }

    @Test
    void adjustingAMissingCycleIsRejected() {
        when(reproCycleMapper.selectByIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null)));

        assertEquals(404, error.getCode());
        assertEquals("生产周期不存在", error.getMessage());
    }

    @Test
    void adjustingACycleWithoutALitterIsRejected() {
        when(reproCycleMapper.selectByIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(cycle());
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null)));

        assertEquals(404, error.getCode());
        assertEquals("该生产周期没有窝记录", error.getMessage());
    }

    /** 未来时间的执行时间说明设备时钟或输入有问题，允许 5 分钟的时钟漂移。 */
    @Test
    void anOccurredAtBeyondTheClockSkewWindowIsRejected() {
        AdjustKeptKitsRequest request = request(5, null);
        request.setOccurredAt(new Date(System.currentTimeMillis() + 30L * 60 * 1000));

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request));

        assertEquals(400, error.getCode());
        assertEquals("执行时间不能晚于当前时间", error.getMessage());
    }

    /** 允许的时钟漂移窗口内不该被误伤。 */
    @Test
    void anOccurredAtWithinTheClockSkewWindowIsAccepted() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);
        AdjustKeptKitsRequest request = request(5, null);
        request.setOccurredAt(new Date(System.currentTimeMillis() + 60L * 1000));

        KeptKitsAdjustmentResponse response =
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request);

        assertEquals(5, response.keptKits().intValue());
    }

    /** 乐观更新没命中说明窝在读到写之间被人改过，此时的加减基准已经失效。 */
    @Test
    void aLostUpdateRaceAbortsTheAdjustment() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);
        when(litterMapper.update(any())).thenReturn(0);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null)));

        assertEquals(409, error.getCode());
        assertEquals("窝数据已变化，请刷新后重试", error.getMessage());
        verify(reproEventMapper, never()).insert(any());
    }

    // ---------- 幂等 ----------

    /**
     * 同一个 requestId 重放时直接返回历史结果，不能再改一次窝数据——
     * 否则一次客户端重试就把寄养数加了两遍。
     */
    @Test
    void aReplayedRequestReturnsTheStoredResultWithoutTouchingTheLitter() {
        ReproEvent stored = new ReproEvent();
        stored.setId(9001L);
        stored.setCycleId(CYCLE_ID);
        stored.setLitterId(LITTER_ID);
        stored.setEventType(ReproEventType.KEPT_KITS_ADJUSTED.name());
        stored.setPayload(
            "{\"previousKeptKits\":8,\"keptKits\":10,\"sourceMotherRabbitId\":101}"
        );
        when(reproEventMapper.selectByRequestId(HOUSE_ID, REQUEST_ID)).thenReturn(stored);

        KeptKitsAdjustmentResponse response =
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(10, SOURCE_MOTHER_ID));

        assertTrue(response.replayed());
        assertEquals(8, response.previousKeptKits().intValue());
        assertEquals(10, response.keptKits().intValue());
        assertEquals(SOURCE_MOTHER_ID, response.sourceMotherRabbitId());
        verify(litterMapper, never()).update(any());
        verify(reproEventMapper, never()).insert(any());
        verify(reproCycleMapper, never()).selectByIdForUpdate(any(), any());
    }

    /** 回放里没有来源母兔时要还原成 null，不能塞一个 0 让客户端以为有来源。 */
    @Test
    void aReplayedDecreaseHasNoSourceMother() {
        ReproEvent stored = new ReproEvent();
        stored.setId(9001L);
        stored.setEventType(ReproEventType.KEPT_KITS_ADJUSTED.name());
        stored.setPayload("{\"previousKeptKits\":8,\"keptKits\":5}");
        when(reproEventMapper.selectByRequestId(HOUSE_ID, REQUEST_ID)).thenReturn(stored);

        KeptKitsAdjustmentResponse response =
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null));

        assertNull(response.sourceMotherRabbitId());
        assertTrue(response.replayed());
    }

    /**
     * requestId 被别的生产操作用过时必须报冲突。否则会把一次配种事件当成
     * 留崽调整的回放，返回一份完全错误的数字。
     */
    @Test
    void aRequestIdOwnedByAnotherOperationIsRejected() {
        ReproEvent stored = new ReproEvent();
        stored.setId(9001L);
        stored.setEventType(ReproEventType.MATING_DONE.name());
        when(reproEventMapper.selectByRequestId(HOUSE_ID, REQUEST_ID)).thenReturn(stored);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null)));

        assertEquals(409, error.getCode());
        assertEquals("requestId已被其他生产操作使用", error.getMessage());
    }

    /** 回放载荷坏掉时宁可报错，也不能返回一份默认 0 的假结果。 */
    @Test
    void anUnparsablePayloadIsReportedInsteadOfReturningZeroes() {
        ReproEvent stored = new ReproEvent();
        stored.setId(9001L);
        stored.setEventType(ReproEventType.KEPT_KITS_ADJUSTED.name());
        stored.setPayload("{not json");
        when(reproEventMapper.selectByRequestId(HOUSE_ID, REQUEST_ID)).thenReturn(stored);

        BizException error = assertThrows(BizException.class, () ->
            service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(5, null)));

        assertEquals(500, error.getCode());
    }

    // ---------- 事件留痕 ----------

    /**
     * 事件载荷是事后倒查这一窝数字怎么变的唯一凭据：前后值、来源母兔都要在。
     * 阶段不变，因为调整留崽数不推进周期。
     */
    @Test
    void theEventRecordsBothTheOldAndNewCountAndKeepsTheStage() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);
        stubBreedingDoe();

        service.adjust(HOUSE_ID, 5L, "张三", CYCLE_ID, request(11, SOURCE_MOTHER_ID));

        ArgumentCaptor<ReproEvent> captor = ArgumentCaptor.forClass(ReproEvent.class);
        verify(reproEventMapper).insert(captor.capture());
        ReproEvent event = captor.getValue();
        assertEquals(ReproEventType.KEPT_KITS_ADJUSTED.name(), event.getEventType());
        assertEquals(event.getFromStage(), event.getToStage());
        assertEquals(LITTER_ID, event.getLitterId());
        assertEquals(REQUEST_ID, event.getRequestId());
        assertEquals("张三", event.getOperatorName());
        assertTrue(event.getPayload().contains("\"previousKeptKits\":8"));
        assertTrue(event.getPayload().contains("\"keptKits\":11"));
        assertTrue(event.getPayload().contains("\"sourceMotherRabbitId\":101"));
    }

    /** 操作人姓名缺失时回落到用户 id，事件不能留下一个空署名。 */
    @Test
    void aBlankOperatorNameFallsBackToTheUserId() {
        Litter litter = nursingLitter(8, 0, 0);
        stubHappyPath(litter);

        service.adjust(HOUSE_ID, 5L, "  ", CYCLE_ID, request(5, null));

        ArgumentCaptor<ReproEvent> captor = ArgumentCaptor.forClass(ReproEvent.class);
        verify(reproEventMapper).insert(captor.capture());
        assertEquals("5", captor.getValue().getOperatorName());
        assertEquals("5", litter.getUpdateBy());
    }

    // ---------- 查询 ----------

    @Test
    void queryingACycleWithoutALitterIsReportedAsNotFound() {
        when(litterMapper.selectByCycleId(HOUSE_ID, CYCLE_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
            () -> service.getByCycle(HOUSE_ID, CYCLE_ID));

        assertEquals(404, error.getCode());
        assertEquals("该生产周期没有窝记录", error.getMessage());
    }

    // ---------- 夹具 ----------

    private void stubHappyPath(Litter litter) {
        when(reproEventMapper.selectByRequestId(HOUSE_ID, REQUEST_ID)).thenReturn(null);
        when(reproCycleMapper.selectByIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(cycle());
        when(litterMapper.selectByCycleIdForUpdate(HOUSE_ID, CYCLE_ID)).thenReturn(litter);
        when(litterMapper.update(any())).thenReturn(1);
    }

    private void stubBreedingDoe() {
        when(rabbitMapper.selectById(HOUSE_ID, SOURCE_MOTHER_ID)).thenReturn(rabbit("0", "0"));
    }

    private static Rabbit rabbit(String type, String gender) {
        Rabbit rabbit = new Rabbit();
        rabbit.setType(type);
        rabbit.setGender(gender);
        return rabbit;
    }

    private static ReproCycle cycle() {
        ReproCycle cycle = new ReproCycle();
        cycle.setId(CYCLE_ID);
        cycle.setHouseId(HOUSE_ID);
        cycle.setMotherRabbitId(MOTHER_ID);
        cycle.setStage("NURSING");
        return cycle;
    }

    private static Litter nursingLitter(Integer keptKits, Integer fosterIn, Integer fosterOut) {
        Litter litter = new Litter();
        litter.setId(LITTER_ID);
        litter.setHouseId(HOUSE_ID);
        litter.setCycleId(CYCLE_ID);
        litter.setMotherRabbitId(MOTHER_ID);
        litter.setStatus(LitterStatus.NURSING.name());
        litter.setKeptKits(keptKits);
        litter.setFosterIn(fosterIn);
        litter.setFosterOut(fosterOut);
        return litter;
    }

    private static AdjustKeptKitsRequest request(int keptKits, Long sourceMotherId) {
        AdjustKeptKitsRequest request = new AdjustKeptKitsRequest();
        request.setOccurredAt(new Date(System.currentTimeMillis() - 60_000L));
        request.setKeptKits(keptKits);
        request.setSourceMotherRabbitId(sourceMotherId);
        request.setRequestId(REQUEST_ID);
        return request;
    }
}
