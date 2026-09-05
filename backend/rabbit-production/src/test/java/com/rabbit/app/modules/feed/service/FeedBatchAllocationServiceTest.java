package com.rabbit.app.modules.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.feed.dto.FeedBatchAllocationInput;
import com.rabbit.app.modules.feed.entity.FeedAllocationCandidateRow;
import com.rabbit.app.modules.feed.entity.FeedLogBatchAllocation;
import com.rabbit.app.modules.feed.mapper.FeedLogBatchAllocationMapper;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeedBatchAllocationServiceTest {
    private FeedLogBatchAllocationMapper mapper;
    private BatchStatisticsLegacyWriteService legacy;
    private FeedBatchAllocationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FeedLogBatchAllocationMapper.class);
        legacy = mock(BatchStatisticsLegacyWriteService.class);
        service = new FeedBatchAllocationService(mapper, legacy);
    }

    @Test
    void savesExactBatchPhaseAllocations() {
        Date feedTime = new Date();
        when(mapper.selectCandidates(8L, List.of(81L, 82L), feedTime)).thenReturn(List.of(
            new FeedAllocationCandidateRow(81L, 101L, "BREEDING"),
            new FeedAllocationCandidateRow(82L, 102L, "FATTENING")
        ));

        FeedBatchAllocationService.AllocationPlan plan = service.prepare(
            7L,
            8L,
            List.of(82L, 81L),
            feedTime,
            "kg",
            BigDecimal.ONE,
            "feed-1",
            List.of(
                new FeedBatchAllocationInput(101L, "BREEDING", new BigDecimal("0.40")),
                new FeedBatchAllocationInput(102L, "FATTENING", new BigDecimal("0.60"))
            )
        );
        service.save(55L, plan);

        ArgumentCaptor<List<FeedLogBatchAllocation>> rows = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(rows.capture());
        assertEquals(2, rows.getValue().size());
        assertEquals(55L, rows.getValue().get(0).getFeedLogId());
        assertEquals(new BigDecimal("0.40"), rows.getValue().get(0).getAmountKg());
        assertEquals(new BigDecimal("0.60"), rows.getValue().get(1).getAmountKg());
    }

    @Test
    void automaticallyAssignsAUniqueKgGroup() {
        Date feedTime = new Date();
        when(mapper.selectCandidates(8L, List.of(81L), feedTime)).thenReturn(List.of(
            new FeedAllocationCandidateRow(81L, 101L, "FATTENING")
        ));

        FeedBatchAllocationService.AllocationPlan plan = service.prepare(
            7L, 8L, List.of(81L), feedTime, "KG", new BigDecimal("2.50"),
            "feed-1", null
        );

        assertEquals(1, plan.rows().size());
        assertEquals(101L, plan.rows().getFirst().getBatchId());
        assertEquals(new BigDecimal("2.50"), plan.rows().getFirst().getAmountKg());
    }

    @Test
    void rejectsAllocationsAfterTheServerGroupSetChanges() {
        Date feedTime = new Date();
        when(mapper.selectCandidates(8L, List.of(81L, 82L), feedTime)).thenReturn(List.of(
            new FeedAllocationCandidateRow(81L, 101L, "BREEDING"),
            new FeedAllocationCandidateRow(82L, 102L, "FATTENING")
        ));

        BizException error = assertThrows(BizException.class, () -> service.prepare(
            7L,
            8L,
            List.of(81L, 82L),
            feedTime,
            "kg",
            BigDecimal.ONE,
            "feed-1",
            List.of(new FeedBatchAllocationInput(
                101L, "BREEDING", BigDecimal.ONE
            ))
        ));

        assertEquals("投喂分组已变化，请刷新后重试", error.getMessage());
    }

    @Test
    void legacyAmbiguousWriteRecordsEveryAffectedBatch() {
        Date feedTime = new Date();
        when(mapper.selectCandidates(8L, List.of(81L, 82L), feedTime)).thenReturn(List.of(
            new FeedAllocationCandidateRow(81L, 101L, "BREEDING"),
            new FeedAllocationCandidateRow(82L, 102L, "FATTENING")
        ));

        FeedBatchAllocationService.AllocationPlan plan = service.prepare(
            7L, 8L, List.of(81L, 82L), feedTime, "kg", BigDecimal.ONE,
            "feed-1", null
        );

        assertEquals(0, plan.rows().size());
        verify(legacy).requireLegacyWriteEnabled();
        verify(legacy).recordGap(
            7L, 8L, 101L, "feed-1", "feed:add", "LEGACY_FEED_ALLOCATION_GAP"
        );
        verify(legacy).recordGap(
            7L, 8L, 102L, "feed-1", "feed:add", "LEGACY_FEED_ALLOCATION_GAP"
        );
    }
}
