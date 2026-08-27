package com.rabbit.app.modules.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.rabbit.app.modules.inventory.entity.InventoryItem;
import com.rabbit.app.modules.inventory.entity.InventoryTx;
import com.rabbit.app.modules.inventory.mapper.InventoryItemMapper;
import com.rabbit.app.modules.inventory.mapper.InventoryTxMapper;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 库存的流水与结存。
 *
 * <p>这是全后端最典型的「错了不报错」的地方：数量算错既不会抛异常也不会
 * 让请求失败，只是把一个错误的结存悄悄写进 inventory_items，然后被后续每一笔
 * 出入库继续叠加。等到盘点对不上时，中间已经压了几百条流水，没法反推是哪一笔
 * 出的问题——只能整体重算。
 *
 * <p>因此这里的用例分三层守：<b>方向</b>（IN 必须为正、OUT/CONSUME 必须为负，
 * 符号搞反等于把入库记成出库）、<b>金额一致性</b>（流水行里的 qtyDelta 必须和
 * 真正施加到结存上的增量是同一个数）、<b>并发</b>（禁负模式下的 CAS 读-改-写
 * 必须拿读到的旧值做条件，失败要重试、重试完要报冲突而不是硬写）。
 *
 * <p>另有一层是幂等：重放的请求绝不能第二次扣减。库存扣两次比扣错更隐蔽，
 * 因为两次都「成功」了。
 */
class InventoryServiceTest {
    private static final Long USER_ID = 9L;
    private static final Long HOUSE_ID = 1L;
    private static final Long ITEM_ID = 100L;
    private static final String REQ = "req-1";
    private static final String TX_API = "inventory:tx:OUT";

    private InventoryItemMapper itemMapper;
    private InventoryTxMapper txMapper;
    private RequestDedupService dedupService;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        itemMapper = mock(InventoryItemMapper.class);
        txMapper = mock(InventoryTxMapper.class);
        dedupService = mock(RequestDedupService.class);
        service = newService(false, 5);
    }

    private InventoryService newService(boolean forbidNegative, int casRetryTimes) {
        return new InventoryService(itemMapper, txMapper, dedupService, forbidNegative, casRetryTimes);
    }

    // ---------- 建档 ----------

    /**
     * 重放建档时必须回查同名物料并原样返回，不能再插一条。
     * 插重了会出现两个同名物料，之后的出入库随机落到其中一个，结存从此分家。
     */
    @Test
    void aReplayedCreateReturnsTheStoredItemWithoutInsertingAgain() {
        InventoryItem stored = item("玉米", "kg", new BigDecimal("30"));
        when(dedupService.shouldSkipAsDone(HOUSE_ID, USER_ID, "inventory:item:create", REQ)).thenReturn(true);
        when(itemMapper.selectByHouseAndName(HOUSE_ID, "玉米")).thenReturn(stored);

        InventoryItem result = service.createItem(USER_ID, HOUSE_ID, item("玉米", "kg", null), BigDecimal.TEN, REQ);

        assertSame(stored, result);
        verify(itemMapper, never()).insert(any());
        verifyNoInteractions(txMapper);
    }

    @Test
    void aReplayedCreateWithNoStoredRowEchoesTheRequest() {
        InventoryItem submitted = item("玉米", "kg", null);
        when(dedupService.shouldSkipAsDone(HOUSE_ID, USER_ID, "inventory:item:create", REQ)).thenReturn(true);
        when(itemMapper.selectByHouseAndName(HOUSE_ID, "玉米")).thenReturn(null);

        assertSame(submitted, service.createItem(USER_ID, HOUSE_ID, submitted, BigDecimal.TEN, REQ));
        verify(itemMapper, never()).insert(any());
    }

    @Test
    void aNamelessOrUnitlessItemIsRejected() {
        assertEquals("item不能为空", assertThrows(BizException.class,
                () -> service.createItem(USER_ID, HOUSE_ID, null, null, REQ)).getMessage());
        assertEquals("name不能为空", assertThrows(BizException.class,
                () -> service.createItem(USER_ID, HOUSE_ID, item("  ", "kg", null), null, REQ)).getMessage());
        assertEquals("unit不能为空", assertThrows(BizException.class,
                () -> service.createItem(USER_ID, HOUSE_ID, item("玉米", " ", null), null, REQ)).getMessage());
    }

    /**
     * 负数期初等于开账就欠库存，后面所有的消耗都会从一个错误的基线往下走。
     */
    @Test
    void aNegativeOpeningQuantityIsRejected() {
        BizException error = assertThrows(BizException.class, () -> service.createItem(
                USER_ID, HOUSE_ID, item("玉米", "kg", null), new BigDecimal("-0.01"), REQ));
        assertEquals(400, error.getCode());
        assertEquals("initQty不能为负数", error.getMessage());
        verify(itemMapper, never()).insert(any());
    }

    /**
     * 期初为零（含不传）只建档不记账。硬写一条 0 的 IN 流水会让「有没有入过货」
     * 这个判断永远为真，进货记录的可信度就没了。
     */
    @Test
    void anEmptyOpeningStockCreatesNoLedgerRow() {
        service.createItem(USER_ID, HOUSE_ID, item("玉米", "kg", null), null, REQ);
        service.createItem(USER_ID, HOUSE_ID, item("豆粕", "kg", null), BigDecimal.ZERO, REQ);

        verify(itemMapper, times(2)).insert(any());
        verifyNoInteractions(txMapper);
    }

    @Test
    void anAbsentOpeningQuantityIsStoredAsZeroNotNull() {
        service.createItem(USER_ID, HOUSE_ID, item("玉米", "kg", null), null, REQ);

        ArgumentCaptor<InventoryItem> saved = ArgumentCaptor.forClass(InventoryItem.class);
        verify(itemMapper).insert(saved.capture());
        assertAmount("0", saved.getValue().getCurrentQty());
        assertEquals(HOUSE_ID, saved.getValue().getHouseId());
        assertEquals("9", saved.getValue().getCreateBy());
    }

    /**
     * 期初结存和期初流水必须是同一个数，且流水挂在刚建出来的物料上。
     * 两者对不上，第一次盘点就会差出一个期初量。
     */
    @Test
    void aPositiveOpeningStockIsMirroredByAnInboundLedgerRow() {
        when(itemMapper.insert(any())).thenAnswer(call -> {
            call.<InventoryItem>getArgument(0).setId(ITEM_ID);
            return 1;
        });

        InventoryItem created = service.createItem(
                USER_ID, HOUSE_ID, item("玉米", "kg", null), new BigDecimal("12.50"), REQ);

        assertAmount("12.50", created.getCurrentQty());
        ArgumentCaptor<InventoryTx> tx = ArgumentCaptor.forClass(InventoryTx.class);
        verify(txMapper).insert(tx.capture());
        assertEquals("IN", tx.getValue().getTxType());
        assertAmount("12.50", tx.getValue().getQtyDelta());
        assertEquals(ITEM_ID, tx.getValue().getItemId());
        assertEquals(HOUSE_ID, tx.getValue().getHouseId());
        assertEquals(REQ, tx.getValue().getRequestId());
    }

    @Test
    void aRejectedCreateIsRecordedAsFailedSoTheRequestIdCanBeReused() {
        assertThrows(BizException.class,
                () -> service.createItem(USER_ID, HOUSE_ID, item("玉米", " ", null), null, REQ));

        verify(dedupService).markProcessing(HOUSE_ID, USER_ID, "inventory:item:create", REQ);
        verify(dedupService).markFailed(HOUSE_ID, USER_ID, "inventory:item:create", REQ, "unit不能为空");
        verify(dedupService, never()).markDone(anyLong(), anyLong(), anyString(), anyString());
    }

    // ---------- 流水方向 ----------

    /**
     * 重放的出库绝不能再扣一次。这是本类最贵的一条：两次扣减都会「成功」，
     * 没有任何报错，只有结存少了一倍。
     */
    @Test
    void aReplayedTransactionNeitherRecordsNorDeducts() {
        when(dedupService.shouldSkipAsDone(HOUSE_ID, USER_ID, TX_API, REQ)).thenReturn(true);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null);

        verifyNoInteractions(txMapper);
        verify(itemMapper, never()).updateQtyDelta(anyLong(), anyLong(), any(), anyString());
        verify(itemMapper, never()).updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString());
    }

    /**
     * 幂等键按 txType 分桶。若所有类型共用一个键，同一个 requestId 的入库和出库
     * 就会互相顶掉，后一笔被当成重放丢弃。
     */
    @Test
    void theIdempotencyKeyIsScopedPerTransactionType() {
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "IN", new BigDecimal("5"), null, null, REQ, null, null);
        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null);

        verify(dedupService).markProcessing(HOUSE_ID, USER_ID, "inventory:tx:IN", REQ);
        verify(dedupService).markProcessing(HOUSE_ID, USER_ID, "inventory:tx:OUT", REQ);
    }

    @Test
    void aTransactionAgainstAnUnknownItemIsRejected() {
        when(itemMapper.selectById(HOUSE_ID, ITEM_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "IN", BigDecimal.ONE, null, null, REQ, null, null));
        assertEquals(400, error.getCode());
        assertEquals("item不存在", error.getMessage());
        verifyNoInteractions(txMapper);
    }

    @Test
    void anUnknownTransactionTypeIsRejected() {
        stubItem(new BigDecimal("50"));

        assertEquals("txType不能为空", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "  ", BigDecimal.ONE, null, null, REQ, null, null)).getMessage());
        assertEquals("txType不支持", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "TRANSFER", BigDecimal.ONE, null, null, REQ, null, null)).getMessage());
        verifyNoInteractions(txMapper);
    }

    /**
     * 零增量不是合法流水：它不改结存，却会在流水里留一行看似发生过的动作。
     */
    @Test
    void aZeroOrMissingDeltaIsRejected() {
        stubItem(new BigDecimal("50"));

        assertEquals("qtyDelta不能为空", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "IN", null, null, null, REQ, null, null)).getMessage());
        assertEquals("qtyDelta不能为空", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "IN", new BigDecimal("0.00"), null, null, REQ, null, null)).getMessage());
    }

    /**
     * 方向校验是符号错误的唯一防线。把 -5 当入库记，结存不降反升；
     * 把 5 当出库记，结存不减反增。两种都不会报错，只会让账面凭空多出货。
     */
    @Test
    void inboundMustCarryAPositiveDelta() {
        stubItem(new BigDecimal("50"));

        assertEquals("IN必须为正数", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "IN", new BigDecimal("-5"), null, null, REQ, null, null)).getMessage());
        verifyNoInteractions(txMapper);
    }

    @Test
    void outboundAndConsumptionMustCarryANegativeDelta() {
        stubItem(new BigDecimal("50"));

        assertEquals("OUT必须为负数", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("5"), null, null, REQ, null, null)).getMessage());
        assertEquals("CONSUME必须为负数", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "CONSUME", new BigDecimal("5"), null, null, REQ, null, null)).getMessage());
    }

    /**
     * 盘点调整是唯一允许双向的类型——盘盈盘亏都得能记。
     */
    @Test
    void anAdjustmentMayGoEitherWay() {
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "ADJUST", new BigDecimal("3"), null, null, REQ, null, null);
        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "ADJUST", new BigDecimal("-3"), null, null, REQ, null, null);

        verify(txMapper, times(2)).insert(any());
    }

    @Test
    void theTransactionTypeIsNormalisedBeforeItIsStored() {
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "  out  ", new BigDecimal("-5"), null, null, REQ, null, null);

        assertEquals("OUT", capturedTx().getTxType());
    }

    /**
     * 流水行里的数量必须和真正施加到结存上的增量逐位相同，否则流水和结存
     * 各说各话，事后无法用流水重算出结存。
     */
    @Test
    void theLedgerRowAndTheStockUpdateShareTheSameDelta() {
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(1);
        Date txTime = new Date(1_700_000_000_000L);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-2.35"), txTime,
                "喂料", REQ, "feed_records", 77L);

        InventoryTx tx = capturedTx();
        assertAmount("-2.35", tx.getQtyDelta());
        assertEquals(txTime, tx.getTxTime());
        assertEquals("feed_records", tx.getRefTable());
        assertEquals(77L, tx.getRefId());
        assertEquals(REQ, tx.getRequestId());

        ArgumentCaptor<BigDecimal> applied = ArgumentCaptor.forClass(BigDecimal.class);
        verify(itemMapper).updateQtyDelta(eq(HOUSE_ID), eq(ITEM_ID), applied.capture(), eq("9"));
        assertAmount("-2.35", applied.getValue());
    }

    /**
     * 不传时间必须补当下。留空会让流水落到 NULL 时间上，
     * 之后按时间段做的耗料统计会直接漏掉这一笔。
     */
    @Test
    void anAbsentTransactionTimeFallsBackToNow() {
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "IN", BigDecimal.ONE, null, null, REQ, null, null);

        assertNotNull(capturedTx().getTxTime());
    }

    // ---------- 允许负库存时的直扣 ----------

    @Test
    void whenNegativeStockIsAllowedTheDeductionIsUnconditional() {
        stubItem(new BigDecimal("1"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null);

        verify(itemMapper).updateQtyDelta(eq(HOUSE_ID), eq(ITEM_ID), any(), eq("9"));
        verify(itemMapper, never()).updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString());
    }

    /**
     * 扣减影响 0 行说明物料在本次事务里被删掉了。不报错就等于流水已经写进去、
     * 结存却没动，账面凭空少一笔。
     */
    @Test
    void aDeductionThatTouchesNoRowIsAnError() {
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDelta(anyLong(), anyLong(), any(), anyString())).thenReturn(0);

        assertEquals("item不存在", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null)).getMessage());
    }

    // ---------- 禁负库存时的 CAS ----------

    /**
     * 禁负模式下超扣必须在写结存之前就拦住。放过去就是负库存，
     * 而负库存意味着有人在系统外拿了货，账面再也追不回来。
     */
    @Test
    void theCasPathRefusesToDriveStockBelowZero() {
        service = newService(true, 5);
        stubItem(new BigDecimal("4.99"));

        BizException error = assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null));
        assertEquals(400, error.getCode());
        assertEquals("库存不足", error.getMessage());
        verify(itemMapper, never()).updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString());
    }

    /**
     * 恰好扣到零是合法的，别把边界一起拒了——否则最后一包料永远发不出去。
     */
    @Test
    void theCasPathAllowsStockToLandExactlyOnZero() {
        service = newService(true, 5);
        stubItem(new BigDecimal("5.00"));
        when(itemMapper.updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null);

        verify(dedupService).markDone(HOUSE_ID, USER_ID, TX_API, REQ);
    }

    @Test
    void anItemWithNoRecordedQuantityCountsAsZeroStock() {
        service = newService(true, 5);
        stubItem(null);

        assertEquals("库存不足", assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-0.01"), null, null, REQ, null, null)).getMessage());
    }

    /**
     * CAS 的条件必须是刚读到的那个旧值。用别的值当条件，并发写就拦不住，
     * 两个请求会各自基于同一个旧结存扣一次，少扣一笔。
     */
    @Test
    void theCasPathUpdatesOnlyIfTheStockIsStillWhatItRead() {
        service = newService(true, 5);
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null);

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> expected = ArgumentCaptor.forClass(BigDecimal.class);
        verify(itemMapper).updateQtyDeltaIfCurrent(
                eq(HOUSE_ID), eq(ITEM_ID), delta.capture(), expected.capture(), eq(true), eq("9"));
        assertAmount("-5", delta.getValue());
        assertAmount("50", expected.getValue());
    }

    /**
     * 抢输一轮要重新读再试，不能直接失败——正常的并发喂料一天几十次，
     * 一撞就报错等于把功能废掉。
     */
    @Test
    void aLostRaceIsRetriedWithAFreshRead() {
        service = newService(true, 5);
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(0, 1);

        service.addTx(USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null);

        verify(itemMapper, times(3)).selectById(HOUSE_ID, ITEM_ID);
        verify(dedupService).markDone(HOUSE_ID, USER_ID, TX_API, REQ);
    }

    /**
     * 重试用光必须报冲突并回滚，不能退化成无条件硬写——那正是 CAS 要防的事。
     */
    @Test
    void exhaustingTheRetriesIsAConflictNotABlindWrite() {
        service = newService(true, 3);
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null));
        assertEquals(409, error.getCode());
        assertEquals("库存并发冲突，请重试", error.getMessage());
        verify(itemMapper, times(3)).updateQtyDeltaIfCurrent(
                anyLong(), anyLong(), any(), any(), anyBoolean(), anyString());
        verify(itemMapper, never()).updateQtyDelta(anyLong(), anyLong(), any(), anyString());
        verify(dedupService, never()).markDone(anyLong(), anyLong(), anyString(), anyString());
    }

    /**
     * 配置成 0 或负数时退回默认 5 次。若原样使用，循环一次都不跑就直接抛冲突。
     */
    @Test
    void aNonPositiveRetryConfigFallsBackToTheDefault() {
        service = newService(true, 0);
        stubItem(new BigDecimal("50"));
        when(itemMapper.updateQtyDeltaIfCurrent(anyLong(), anyLong(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(0);

        assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("-5"), null, null, REQ, null, null));

        verify(itemMapper, times(5)).updateQtyDeltaIfCurrent(
                anyLong(), anyLong(), any(), any(), anyBoolean(), anyString());
    }

    @Test
    void aFailedTransactionIsRecordedAsFailed() {
        stubItem(new BigDecimal("50"));

        assertThrows(BizException.class, () -> service.addTx(
                USER_ID, HOUSE_ID, ITEM_ID, "OUT", new BigDecimal("5"), null, null, REQ, null, null));

        verify(dedupService).markFailed(HOUSE_ID, USER_ID, TX_API, REQ, "OUT必须为负数");
    }

    // ---------- 查询 ----------

    @Test
    void aPartialItemLookupReturnsNullWithoutQuerying() {
        assertNull(service.getItem(null, ITEM_ID));
        assertNull(service.getItem(HOUSE_ID, null));
        verifyNoInteractions(itemMapper);
    }

    /**
     * 分页参数越界会直接进 SQL 的 LIMIT/OFFSET。负 offset 在 MySQL 上是语法错误，
     * 超大 pageSize 则会把整表拉进内存。
     */
    @Test
    void ledgerPagingClampsItsBounds() {
        service.listTxByItem(HOUSE_ID, ITEM_ID, 0, 0);
        verify(txMapper).selectPageByItem(HOUSE_ID, ITEM_ID, 0, 50);

        service.listTxByItem(HOUSE_ID, ITEM_ID, 3, 500);
        verify(txMapper).selectPageByItem(HOUSE_ID, ITEM_ID, 400, 200);
    }

    @Test
    void exportPagingClampsItsBounds() {
        service.listTxExportPage(HOUSE_ID, ITEM_ID, null, null, -10, 0);
        verify(txMapper).selectExportPage(HOUSE_ID, ITEM_ID, null, null, 0, 1000);

        service.listTxExportPage(HOUSE_ID, ITEM_ID, null, null, 20, 99_999);
        verify(txMapper).selectExportPage(HOUSE_ID, ITEM_ID, null, null, 20, 5000);
    }

    // ---------- 夹具 ----------

    private void stubItem(BigDecimal currentQty) {
        InventoryItem stored = item("玉米", "kg", currentQty);
        stored.setId(ITEM_ID);
        when(itemMapper.selectById(HOUSE_ID, ITEM_ID)).thenReturn(stored);
    }

    private InventoryTx capturedTx() {
        ArgumentCaptor<InventoryTx> tx = ArgumentCaptor.forClass(InventoryTx.class);
        verify(txMapper).insert(tx.capture());
        return tx.getValue();
    }

    private InventoryItem item(String name, String unit, BigDecimal currentQty) {
        InventoryItem it = new InventoryItem();
        it.setName(name);
        it.setUnit(unit);
        it.setCurrentQty(currentQty);
        return it;
    }

    /** BigDecimal 必须比值不比对象：{@code equals} 认为 5 和 5.0 不同。 */
    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "期望 " + expected + " 实际 " + actual);
    }
}
