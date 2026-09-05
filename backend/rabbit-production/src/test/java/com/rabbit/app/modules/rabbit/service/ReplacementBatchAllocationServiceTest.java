package com.rabbit.app.modules.rabbit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.rabbit.dto.ReplacementBatchAllocationInput;
import com.rabbit.app.modules.rabbit.entity.ReplacementBatchAllocation;
import com.rabbit.app.modules.rabbit.mapper.ReplacementBatchAllocationMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReplacementBatchAllocationServiceTest {
    private ReplacementBatchAllocationMapper mapper;
    private BatchStatisticsLegacyWriteService legacy;
    private ReplacementBatchAllocationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReplacementBatchAllocationMapper.class);
        legacy = mock(BatchStatisticsLegacyWriteService.class);
        service = new ReplacementBatchAllocationService(mapper, legacy);
    }

    @Test
    void savesMeasuredWeightOncePerSourceBatch() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        counts.put(101L, 2);
        ReplacementBatchAllocationService.AllocationPlan plan = service.prepare(
            7L,
            8L,
            "replacement-1",
            counts,
            List.of(new ReplacementBatchAllocationInput(
                101L, 2, new BigDecimal("4.250")
            ))
        );
        service.save(plan);

        ArgumentCaptor<List<ReplacementBatchAllocation>> rows = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(rows.capture());
        assertEquals(1, rows.getValue().size());
        assertEquals(101L, rows.getValue().getFirst().getSourceBatchId());
        assertEquals(2, rows.getValue().getFirst().getRabbitCount());
        assertEquals(new BigDecimal("4.250"), rows.getValue().getFirst().getTotalWeightKg());
    }

    @Test
    void rejectsClientCountWhenTheLockedRabbitGroupChanged() {
        Map<Long, Integer> counts = Map.of(101L, 2);

        BizException error = assertThrows(BizException.class, () -> service.prepare(
            7L,
            8L,
            "replacement-1",
            counts,
            List.of(new ReplacementBatchAllocationInput(
                101L, 1, new BigDecimal("2.100")
            ))
        ));

        assertEquals("转后备分组只数已变化，请刷新后重试", error.getMessage());
    }

    @Test
    void legacyWriteRecordsEveryAffectedSourceBatch() {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        counts.put(101L, 2);
        counts.put(null, 1);

        ReplacementBatchAllocationService.AllocationPlan plan = service.prepare(
            7L, 8L, "replacement-1", counts, null
        );

        assertEquals(0, plan.rows().size());
        verify(legacy).requireLegacyWriteEnabled();
        verify(legacy).recordGap(
            7L,
            8L,
            101L,
            "replacement-1",
            "rabbit.toReplacement",
            BatchStatisticsLegacyWriteService.LEGACY_REPLACEMENT_WEIGHT_GAP
        );
    }
}
