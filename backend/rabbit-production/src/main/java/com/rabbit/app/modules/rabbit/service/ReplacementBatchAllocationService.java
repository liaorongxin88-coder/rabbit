package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.rabbit.dto.ReplacementBatchAllocationInput;
import com.rabbit.app.modules.rabbit.entity.ReplacementBatchAllocation;
import com.rabbit.app.modules.rabbit.mapper.ReplacementBatchAllocationMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReplacementBatchAllocationService {
    private static final String OPERATION_CODE = "rabbit.toReplacement";

    private final ReplacementBatchAllocationMapper allocationMapper;
    private final BatchStatisticsLegacyWriteService legacyWriteService;
    private final BatchMapper batchMapper;

    public ReplacementBatchAllocationService(
        ReplacementBatchAllocationMapper allocationMapper,
        BatchStatisticsLegacyWriteService legacyWriteService
    ) {
        this(allocationMapper, legacyWriteService, null);
    }

    @Autowired
    public ReplacementBatchAllocationService(
        ReplacementBatchAllocationMapper allocationMapper,
        BatchStatisticsLegacyWriteService legacyWriteService,
        BatchMapper batchMapper
    ) {
        this.allocationMapper = allocationMapper;
        this.legacyWriteService = legacyWriteService;
        this.batchMapper = batchMapper;
    }

    public void assertRequestAllowed(List<ReplacementBatchAllocationInput> requested) {
        if (requested == null || requested.isEmpty()) {
            legacyWriteService.requireLegacyWriteEnabled();
        }
    }

    public AllocationPlan prepare(
        Long userId,
        Long houseId,
        String requestId,
        Map<Long, Integer> groupCounts,
        List<ReplacementBatchAllocationInput> requested
    ) {
        Map<Long, Integer> counts = normalizedCounts(groupCounts);
        assertHouseBatches(houseId, counts);
        if (requested == null || requested.isEmpty()) {
            legacyWriteService.requireLegacyWriteEnabled();
            counts.keySet().stream().filter(Objects::nonNull).forEach(batchId ->
                legacyWriteService.recordGap(
                    userId,
                    houseId,
                    batchId,
                    requestId,
                    OPERATION_CODE,
                    BatchStatisticsLegacyWriteService.LEGACY_REPLACEMENT_WEIGHT_GAP
                )
            );
            return new AllocationPlan(List.of());
        }

        Map<Long, ReplacementBatchAllocationInput> inputs = new LinkedHashMap<>();
        for (ReplacementBatchAllocationInput input : requested) {
            if (input == null || inputs.putIfAbsent(input.batchId(), input) != null) {
                throw new BizException(400, "同一来源批次不能重复录入转后备重量");
            }
        }
        if (!inputs.keySet().equals(counts.keySet())) {
            throw new BizException(409, "转后备来源批次分组已变化，请刷新后重试");
        }

        List<ReplacementBatchAllocation> rows = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            ReplacementBatchAllocationInput input = inputs.get(entry.getKey());
            if (!entry.getValue().equals(input.rabbitCount())) {
                throw new BizException(409, "转后备分组只数已变化，请刷新后重试");
            }
            ReplacementBatchAllocation row = new ReplacementBatchAllocation();
            row.setHouseId(houseId);
            row.setRequestId(requestId);
            row.setSourceBatchId(entry.getKey());
            row.setRabbitCount(entry.getValue());
            row.setTotalWeightKg(normalizeWeight(input.totalWeightKg()));
            row.setCreatedBy(userId);
            rows.add(row);
        }
        return new AllocationPlan(rows);
    }

    public void save(AllocationPlan plan) {
        if (!plan.rows().isEmpty()) {
            allocationMapper.insertBatch(plan.rows());
        }
    }

    private void assertHouseBatches(Long houseId, Map<Long, Integer> counts) {
        if (batchMapper == null) {
            return;
        }
        for (Long batchId : counts.keySet()) {
            if (batchId != null && batchMapper.selectById(houseId, batchId) == null) {
                throw new BizException(409, "转后备来源批次不属于当前兔舍: " + batchId);
            }
        }
    }

    private static Map<Long, Integer> normalizedCounts(Map<Long, Integer> groupCounts) {
        if (groupCounts == null || groupCounts.isEmpty()) {
            throw new BizException(400, "转后备来源批次分组不能为空");
        }
        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(groupCounts.entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.nullsLast(Long::compareTo)));
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : entries) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new BizException(400, "转后备来源批次分组只数不合法");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static BigDecimal normalizeWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "转后备实测总重必须大于0");
        }
        try {
            return value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new BizException(400, "转后备实测总重最多保留三位小数");
        }
    }

    public record AllocationPlan(List<ReplacementBatchAllocation> rows) {
        public AllocationPlan {
            rows = List.copyOf(rows);
        }
    }
}
