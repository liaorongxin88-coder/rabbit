package com.rabbit.app.modules.sale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import com.rabbit.app.modules.sale.entity.SaleOrderBatchAllocation;
import com.rabbit.app.modules.sale.mapper.SaleOrderBatchAllocationMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SaleBatchAllocationServiceTest {
    private SaleOrderBatchAllocationMapper mapper;
    private BatchStatisticsLegacyWriteService legacy;
    private SaleBatchAllocationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SaleOrderBatchAllocationMapper.class);
        legacy = mock(BatchStatisticsLegacyWriteService.class);
        service = new SaleBatchAllocationService(mapper, legacy);
    }

    @Test
    void assignsTheOneCentTailToTheLargestThenLowestBatch() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        counts.put(101L, 1);
        counts.put(102L, 1);

        SaleBatchAllocationService.AllocationPlan plan = service.prepareOutbound(
            7L,
            8L,
            counts,
            BigDecimal.ONE,
            new BigDecimal("12.01"),
            List.of(
                new SaleBatchAllocationInput(101L, new BigDecimal("0.500")),
                new SaleBatchAllocationInput(102L, new BigDecimal("0.500"))
            ),
            "sale-1"
        );

        assertEquals(new BigDecimal("12.01"), plan.totalAmount());
        assertEquals(new BigDecimal("6.00"), plan.rows().get(0).getAmount());
        assertEquals(new BigDecimal("6.01"), plan.rows().get(1).getAmount());
    }

    @Test
    void derivesRabbitCountsAndPersistsTheSnapshot() {
        service.allocateAndSave(
            7L,
            8L,
            99L,
            "sale-1",
            new BigDecimal("4.000"),
            new BigDecimal("10.00"),
            List.of(101L, 101L),
            null,
            "sale:create"
        );

        ArgumentCaptor<List<SaleOrderBatchAllocation>> rows = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(rows.capture());
        assertEquals(99L, rows.getValue().getFirst().getSaleOrderId());
        assertEquals(2, rows.getValue().getFirst().getRabbitCount());
        assertEquals(new BigDecimal("4.000"), rows.getValue().getFirst().getActualWeightKg());
        assertEquals(new BigDecimal("40.00"), rows.getValue().getFirst().getAmount());
    }

    @Test
    void explicitAllocationsRequireAPositiveUnitPrice() {
        Map<Long, Integer> counts = Map.of(101L, 1);

        BizException error = assertThrows(BizException.class, () -> service.prepareOutbound(
            7L,
            8L,
            counts,
            BigDecimal.ONE,
            null,
            List.of(new SaleBatchAllocationInput(101L, BigDecimal.ONE)),
            "sale-1"
        ));

        assertEquals("统一重量单价必须大于0", error.getMessage());
    }

    @Test
    void legacyHighPrecisionPriceKeepsTheOrderButDoesNotFabricateAPriceSnapshot() {
        SaleBatchAllocationService.AllocationPlan plan = service.prepareOutbound(
            7L,
            8L,
            Map.of(101L, 1),
            new BigDecimal("3.000"),
            new BigDecimal("12.345"),
            null,
            "sale-precision"
        );

        assertEquals(new BigDecimal("37.04"), SaleBatchAllocationService.orderAmount(
            new BigDecimal("3.000"), new BigDecimal("12.345")
        ));
        assertEquals(1, plan.rows().size());
        assertNull(plan.rows().getFirst().getUnitPricePerKg());
        assertNull(plan.rows().getFirst().getAmount());
        assertNull(plan.totalAmount());
        verify(legacy).requireLegacyWriteEnabled();
        verify(legacy).recordGap(
            7L, 8L, 101L, "sale-precision", "outbound:submit", "LEGACY_SALE_PRICE_GAP"
        );
    }

    @Test
    void explicitAllocationsRejectAHighPrecisionPrice() {
        BizException error = assertThrows(BizException.class, () -> service.prepareOutbound(
            7L,
            8L,
            Map.of(101L, 1),
            BigDecimal.ONE,
            new BigDecimal("12.345"),
            List.of(new SaleBatchAllocationInput(101L, BigDecimal.ONE)),
            "sale-precision"
        ));

        assertEquals("统一重量单价最多保留两位小数", error.getMessage());
    }

    @Test
    void rejectsAWeightTotalThatDoesNotMatchTheOrder() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        counts.put(101L, 1);
        counts.put(102L, 1);

        BizException error = assertThrows(BizException.class, () -> service.prepareOutbound(
            7L,
            8L,
            counts,
            new BigDecimal("3.000"),
            BigDecimal.TEN,
            List.of(
                new SaleBatchAllocationInput(101L, BigDecimal.ONE),
                new SaleBatchAllocationInput(102L, BigDecimal.ONE)
            ),
            "sale-1"
        ));

        assertEquals("销售批次分配重量合计必须等于订单总重量", error.getMessage());
    }

    @Test
    void legacyMixedBatchWriteRecordsAllocationGaps() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        counts.put(101L, 1);
        counts.put(102L, 1);

        SaleBatchAllocationService.AllocationPlan plan = service.prepareOutbound(
            7L, 8L, counts, BigDecimal.ONE, BigDecimal.TEN, null, "sale-1"
        );

        assertEquals(0, plan.rows().size());
        verify(legacy).requireLegacyWriteEnabled();
        verify(legacy).recordGap(
            7L, 8L, 101L, "sale-1", "outbound:submit", "LEGACY_SALE_ALLOCATION_GAP"
        );
        verify(legacy).recordGap(
            7L, 8L, 102L, "sale-1", "outbound:submit", "LEGACY_SALE_ALLOCATION_GAP"
        );
    }
}
