package com.rabbit.app.modules.sale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import com.rabbit.app.modules.sale.dto.SaleOrderDetail;
import com.rabbit.app.modules.sale.entity.SaleOrder;
import com.rabbit.app.modules.sale.entity.SaleOrderItem;
import com.rabbit.app.modules.sale.mapper.SaleOrderItemMapper;
import com.rabbit.app.modules.sale.mapper.SaleOrderMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 销售开单。
 *
 * <p>一张销售单同时定死三样东西：客户该付多少钱、哪几只兔子从此不在栏、
 * 这批兔子退出了哪个批次。三者都是一次性写入的历史凭证——单子开完就进了
 * 对账口径，事后订正要连带翻动库存、批次和结算，代价远高于当场拦住。
 *
 * <p>用例集中在两处最容易静默出错的地方。一是<b>金额</b>：单价乘重量走的是
 * {@code BigDecimal}，一旦有人图省事换成 double 或 {@code new BigDecimal(double)}，
 * 算出来的钱会带一串二进制尾数，金额字段照收不误，只有对账时才发现分不平。
 * 二是<b>兔只集合</b>：重复 ID 会让同一只兔被卖两次，而每只兔的离场事件靠
 * 各自派生的子 requestId 去重——派生错了就会被幂等层整批吞掉，单子开了、
 * 兔子还挂在栏里。
 */
class SaleServiceTest {
    private static final Long USER_ID = 9L;
    private static final Long HOUSE_ID = 1L;
    private static final Long ORDER_ID = 500L;
    private static final String REQ = "req-1";
    private static final String API = "sale:create";

    private SaleOrderMapper orderMapper;
    private SaleOrderItemMapper itemMapper;
    private RabbitService rabbitService;
    private RequestDedupService dedupService;
    private SaleService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(SaleOrderMapper.class);
        itemMapper = mock(SaleOrderItemMapper.class);
        rabbitService = mock(RabbitService.class);
        dedupService = mock(RequestDedupService.class);
        service = new SaleService(orderMapper, itemMapper, rabbitService, dedupService);
        when(orderMapper.insert(any())).thenAnswer(call -> {
            call.<SaleOrder>getArgument(0).setId(ORDER_ID);
            return 1;
        });
    }

    // ---------- 幂等 ----------

    /**
     * 重放必须回查原单返回，不能再开一张。重开的单子会把同一批兔子再卖一次，
     * 金额和出栏都翻倍。
     */
    @Test
    void aReplayedOrderIsReadBackRatherThanReissued() {
        SaleOrder stored = new SaleOrder();
        when(dedupService.begin(eq(HOUSE_ID), eq(USER_ID), eq(API), eq(REQ), anyString()))
            .thenReturn(RequestDedupService.BeginResult.DONE);
        when(orderMapper.selectByReq(HOUSE_ID, REQ)).thenReturn(stored);

        assertSame(stored, service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0,
                BigDecimal.TEN, "老王", null, REQ));

        verify(orderMapper, never()).insert(any());
        verifyNoInteractions(itemMapper, rabbitService);
    }

    @Test
    void aReplayWithNoStoredOrderYieldsNothingRatherThanANewOrder() {
        when(dedupService.begin(eq(HOUSE_ID), eq(USER_ID), eq(API), eq(REQ), anyString()))
            .thenReturn(RequestDedupService.BeginResult.DONE);
        when(orderMapper.selectByReq(HOUSE_ID, REQ)).thenReturn(null);

        assertNull(service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0, BigDecimal.TEN, null, null, REQ));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void payloadHashKeepsDelimiterBearingTextFieldsDistinct() {
        service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0,
                BigDecimal.TEN, "a|b", "c", "req-a");
        service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0,
                BigDecimal.TEN, "a", "b|c", "req-b");

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(dedupService, times(2)).begin(
                eq(HOUSE_ID), eq(USER_ID), eq(API), anyString(), hashes.capture());
        assertNotEquals(hashes.getAllValues().get(0), hashes.getAllValues().get(1));
    }

    // ---------- 入参 ----------

    @Test
    void anOrderWithoutRabbitsIsRejected() {
        assertEquals("rabbitIds不能为空", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, null, null, 3.0, BigDecimal.TEN, null, null, REQ)).getMessage());
        assertEquals("rabbitIds不能为空", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, List.of(), null, 3.0, BigDecimal.TEN, null, null, REQ)).getMessage());
        verify(orderMapper, never()).insert(any());
    }

    /**
     * 重量是金额的乘数，零或负重量会让整单金额归零或变成负数——
     * 那等于白送一批兔子，且账面看不出异常。
     */
    @Test
    void aNonPositiveTotalWeightIsRejected() {
        assertEquals("totalWeight不合法", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, List.of(7L), null, null, BigDecimal.TEN, null, null, REQ)).getMessage());
        assertEquals("totalWeight不合法", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, List.of(7L), null, 0.0, BigDecimal.TEN, null, null, REQ)).getMessage());
        assertEquals("totalWeight不合法", assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, List.of(7L), null, -1.0, BigDecimal.TEN, null, null, REQ)).getMessage());
    }

    @Test
    void aNegativeUnitPriceIsRejected() {
        BizException error = assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, List.of(7L), null, 3.0, new BigDecimal("-0.01"), null, null, REQ));
        assertEquals(400, error.getCode());
        assertEquals("unitPrice不合法", error.getMessage());
        verify(orderMapper, never()).insert(any());
    }

    /**
     * 白送或先发货后议价是真实场景，单价 0 得开得出来。
     */
    @Test
    void aZeroUnitPriceIsAllowedAndYieldsAZeroAmount() {
        SaleOrder order = service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0,
                BigDecimal.ZERO, null, null, REQ);

        assertAmount("0", order.getTotalAmount());
    }

    // ---------- 金额 ----------

    /**
     * 金额 = 单价 × 总重。这一行是整个模块唯一产生钱的地方。
     */
    @Test
    void theTotalAmountIsTheUnitPriceTimesTheTotalWeight() {
        SaleOrder order = service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.2,
                new BigDecimal("12.50"), null, null, REQ);

        assertAmount("40.00", order.getTotalAmount());
        assertAmount("12.50", order.getUnitPrice());
        assertEquals(3.2, order.getTotalWeight());
    }

    /**
     * 重量是 double，必须先转成十进制字符串再进 BigDecimal。直接用
     * {@code new BigDecimal(double)} 会把 0.3 展开成 0.29999999999999998…，
     * 金额跟着带一长串尾数落库；单笔差几厘看不出来，几百单累起来就是对不平的账。
     */
    @Test
    void theAmountKeepsDecimalPrecisionInsteadOfBinaryNoise() {
        SaleOrder order = service.create(USER_ID, HOUSE_ID, List.of(7L), null, 0.3,
                new BigDecimal("0.1"), null, null, REQ);

        assertEquals("0.03", order.getTotalAmount().toPlainString());
    }

    /**
     * 没报价就不该有金额。给个 0 会让「待定价」和「白送」变得无法区分，
     * 而这两种单子的对账处理完全不同。
     */
    @Test
    void anUnpricedOrderCarriesNoAmountAtAll() {
        SaleOrder order = service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0,
                null, null, null, REQ);

        assertNull(order.getTotalAmount());
        assertNull(order.getUnitPrice());
    }

    // ---------- 明细 ----------

    /**
     * 重复勾选的兔只必须收敛成一行。留着重复行会让这只兔在单据上卖两次，
     * 也会撞上明细表的唯一键，把整单回滚掉。
     */
    @Test
    void duplicateRabbitsCollapseIntoASingleLine() {
        service.create(USER_ID, HOUSE_ID, Arrays.asList(7L, 8L, 7L, 8L), null, 3.0,
                BigDecimal.TEN, null, null, REQ);

        List<SaleOrderItem> items = capturedItems();
        assertEquals(2, items.size());
        assertEquals(7L, items.get(0).getRabbitId());
        assertEquals(8L, items.get(1).getRabbitId());
        verify(rabbitService, times(2)).rabbitEvent(anyLong(), anyLong(), anyLong(), anyString(),
                any(), anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void unusableRabbitIdsAreDroppedFromTheOrder() {
        service.create(USER_ID, HOUSE_ID, Arrays.asList(7L, null, 0L, -3L), null, 3.0,
                BigDecimal.TEN, null, null, REQ);

        List<SaleOrderItem> items = capturedItems();
        assertEquals(1, items.size());
        assertEquals(7L, items.get(0).getRabbitId());
    }

    /**
     * 一只有效兔都没有时必须整单失败。放过去就是一张有金额、没兔子的单子，
     * 它照样进销售统计，凭空抬高销量。
     */
    @Test
    void anOrderThatEndsUpWithNoValidRabbitIsRejected() {
        BizException error = assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, Arrays.asList((Long) null, 0L), null, 3.0,
                BigDecimal.TEN, null, null, REQ));

        assertEquals(400, error.getCode());
        assertEquals("rabbitIds不合法", error.getMessage());
        verify(itemMapper, never()).insertBatch(anyList());
        verifyNoInteractions(rabbitService);
    }

    @Test
    void everyLineIsAttachedToTheOrderThatWasJustInserted() {
        service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0, BigDecimal.TEN, null, null, REQ);

        SaleOrderItem line = capturedItems().get(0);
        assertEquals(ORDER_ID, line.getSaleOrderId());
        org.junit.jupiter.api.Assertions.assertNull(line.getCreateBy(),
                "服务层不再手写 create_by，由 MyBatis 写入拦截器补齐");
    }

    // ---------- 离场事件 ----------

    /**
     * 每只兔的离场事件必须带各自派生的子 requestId。若整批共用父 requestId，
     * 下游的幂等层会认定第二只之后都是重放而直接跳过——单子开成了，
     * 兔子却还挂在栏上，直到某次盘点才发现。
     */
    @Test
    void eachRabbitLeavesUnderItsOwnDerivedRequestId() {
        service.create(USER_ID, HOUSE_ID, Arrays.asList(7L, 8L), null, 3.0,
                BigDecimal.TEN, null, null, REQ);

        ArgumentCaptor<String> childReq = ArgumentCaptor.forClass(String.class);
        verify(rabbitService, times(2)).rabbitEvent(anyLong(), anyLong(), anyLong(), anyString(),
                any(), anyString(), anyString(), anyBoolean(), childReq.capture());

        List<String> derived = childReq.getAllValues();
        assertNotEquals(derived.get(0), derived.get(1));
        assertNotEquals(REQ, derived.get(0));
        assertEquals(REQ + "-7", derived.get(0));
        assertEquals(REQ + "-8", derived.get(1));
    }

    /**
     * 出栏事件必须强制退批（forceExitBatch），否则卖掉的兔子还留在批次里，
     * 批次存栏和实际存栏从此对不上。
     */
    @Test
    void theDepartureEventMarksASaleAndForcesTheRabbitOutOfItsBatch() {
        Date saleTime = new Date(1_700_000_000_000L);

        service.create(USER_ID, HOUSE_ID, List.of(7L), saleTime, 3.0, BigDecimal.TEN, null, null, REQ);

        verify(rabbitService).rabbitEvent(eq(USER_ID), eq(HOUSE_ID), eq(7L), eq("sale"),
                eq(saleTime), eq("销售出栏"), eq("saleOrder#" + ORDER_ID), eq(true), anyString());
    }

    /**
     * 不传销售时间时补当下，且单据与离场事件必须用同一个时间。
     * 两处各取一次 now 会让出栏日和单据日差出几毫秒，跨零点时就是差一天。
     */
    @Test
    void anAbsentSaleTimeIsFilledInOnceAndSharedWithTheDepartureEvent() {
        SaleOrder order = service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0,
                BigDecimal.TEN, null, null, REQ);

        ArgumentCaptor<Date> eventTime = ArgumentCaptor.forClass(Date.class);
        verify(rabbitService).rabbitEvent(anyLong(), anyLong(), anyLong(), anyString(),
                eventTime.capture(), anyString(), anyString(), anyBoolean(), anyString());
        assertEquals(order.getSaleTime(), eventTime.getValue());
    }

    @Test
    void aRejectedOrderIsRecordedAsFailed() {
        assertThrows(BizException.class, () -> service.create(
                USER_ID, HOUSE_ID, List.of(7L), null, 0.0, BigDecimal.TEN, null, null, REQ));

        verify(dedupService).begin(eq(HOUSE_ID), eq(USER_ID), eq(API), eq(REQ), anyString());
        verify(dedupService).markFailed(HOUSE_ID, USER_ID, API, REQ, "totalWeight不合法");
        verify(dedupService, never()).markDone(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void aCompletedOrderIsRecordedAsDone() {
        service.create(USER_ID, HOUSE_ID, List.of(7L), null, 3.0, BigDecimal.TEN, null, null, REQ);

        verify(dedupService).markDone(HOUSE_ID, USER_ID, API, REQ);
    }

    // ---------- 查询 ----------

    @Test
    void aDetailLookupNeedsARealOrderId() {
        assertEquals("saleOrderId不能为空", assertThrows(BizException.class,
                () -> service.getDetail(HOUSE_ID, null)).getMessage());
        assertEquals("saleOrderId不能为空", assertThrows(BizException.class,
                () -> service.getDetail(HOUSE_ID, 0L)).getMessage());
        verifyNoInteractions(orderMapper);
    }

    /**
     * 跨舍取单必须 404。selectById 带 houseId，查不到就是别的舍的单子，
     * 不能退化成返回一个空壳。
     */
    @Test
    void anOrderFromAnotherHouseIsANotFound() {
        when(orderMapper.selectById(HOUSE_ID, ORDER_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.getDetail(HOUSE_ID, ORDER_ID));
        assertEquals(404, error.getCode());
        assertEquals("销售单不存在", error.getMessage());
        verify(itemMapper, never()).selectViewByOrder(anyLong(), anyLong());
    }

    @Test
    void aDetailCarriesBothTheOrderAndItsLines() {
        SaleOrder stored = new SaleOrder();
        when(orderMapper.selectById(HOUSE_ID, ORDER_ID)).thenReturn(stored);
        when(itemMapper.selectViewByOrder(HOUSE_ID, ORDER_ID)).thenReturn(List.of());

        SaleOrderDetail detail = service.getDetail(HOUSE_ID, ORDER_ID);

        assertSame(stored, detail.getOrder());
        assertEquals(0, detail.getItems().size());
    }

    @Test
    void listPagingClampsItsBounds() {
        service.listPage(HOUSE_ID, 0, 0);
        verify(orderMapper).selectPageByHouse(HOUSE_ID, 0, 20);

        service.listPage(HOUSE_ID, 3, 500);
        verify(orderMapper).selectPageByHouse(HOUSE_ID, 400, 200);
    }

    // ---------- 夹具 ----------

    private List<SaleOrderItem> capturedItems() {
        ArgumentCaptor<List<SaleOrderItem>> items = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).insertBatch(items.capture());
        return new ArrayList<SaleOrderItem>(items.getValue());
    }

    /** 金额比值不比对象：{@code equals} 认为 40 和 40.00 是两个东西。 */
    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "期望 " + expected + " 实际 " + actual);
    }
}
