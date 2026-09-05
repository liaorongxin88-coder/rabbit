package com.rabbit.app.modules.feed.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.feed.dto.FeedAllocationPreview;
import com.rabbit.app.modules.feed.dto.FeedBatchAllocationInput;
import com.rabbit.app.modules.feed.entity.FeedAllocationCandidateRow;
import com.rabbit.app.modules.feed.entity.FeedLogBatchAllocation;
import com.rabbit.app.modules.feed.mapper.FeedLogBatchAllocationMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FeedBatchAllocationService {
    private static final String OPERATION_CODE = "feed:add";
    private static final String GAP_EVENT =
        BatchStatisticsLegacyWriteService.LEGACY_FEED_ALLOCATION_GAP;

    private final FeedLogBatchAllocationMapper allocationMapper;
    private final BatchStatisticsLegacyWriteService legacyWriteService;

    public FeedBatchAllocationService(
        FeedLogBatchAllocationMapper allocationMapper,
        BatchStatisticsLegacyWriteService legacyWriteService
    ) {
        this.allocationMapper = allocationMapper;
        this.legacyWriteService = legacyWriteService;
    }

    public FeedAllocationPreview preview(Long houseId, List<Long> rabbitIds, Date feedTime) {
        Map<GroupKey, Set<Long>> groups = resolveGroups(houseId, rabbitIds, feedTime);
        return new FeedAllocationPreview(groups.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(GROUP_ORDER))
            .map(entry -> new FeedAllocationPreview.Group(
                entry.getKey().batchId(), entry.getKey().phase(), entry.getValue().size()
            ))
            .toList());
    }

    public void assertRequestAllowed(
        Long houseId,
        List<Long> rabbitIds,
        Date feedTime,
        String unit,
        List<FeedBatchAllocationInput> inputs
    ) {
        if (inputs != null && !inputs.isEmpty()) {
            return;
        }
        Map<GroupKey, Set<Long>> groups = resolveGroups(houseId, rabbitIds, feedTime);
        boolean automatic = groups.size() == 1
            && !groups.containsKey(new GroupKey(null, "UNASSIGNED"))
            && isKg(unit);
        if (!automatic) {
            legacyWriteService.requireLegacyWriteEnabled();
        }
    }

    public AllocationPlan prepare(
        Long userId,
        Long houseId,
        List<Long> rabbitIds,
        Date feedTime,
        String unit,
        BigDecimal totalAmount,
        String requestId,
        List<FeedBatchAllocationInput> inputs
    ) {
        Map<GroupKey, Set<Long>> groups = resolveGroups(houseId, rabbitIds, feedTime);
        if (inputs == null || inputs.isEmpty()) {
            if (groups.size() == 1
                && !groups.containsKey(new GroupKey(null, "UNASSIGNED"))
                && isKg(unit)) {
                GroupKey only = groups.keySet().iterator().next();
                return new AllocationPlan(List.of(row(houseId, only, normalize(totalAmount))));
            }
            legacyWriteService.requireLegacyWriteEnabled();
            recordGapForBatches(userId, houseId, groups.keySet(), requestId);
            return new AllocationPlan(List.of());
        }
        if (!isKg(unit)) {
            throw new BizException(400, "批次投喂分配只支持kg");
        }

        Map<GroupKey, FeedBatchAllocationInput> normalizedInputs = new LinkedHashMap<>();
        BigDecimal allocated = BigDecimal.ZERO.setScale(2);
        for (FeedBatchAllocationInput input : inputs) {
            GroupKey key = normalizeKey(input.batchId(), input.phase());
            if (normalizedInputs.putIfAbsent(key, input) != null) {
                throw new BizException(400, "同一批次和阶段不能重复分配");
            }
            allocated = allocated.add(normalize(input.amountKg()));
        }
        if (!normalizedInputs.keySet().equals(groups.keySet())) {
            throw new BizException(409, "投喂分组已变化，请刷新后重试");
        }
        if (allocated.compareTo(normalize(totalAmount)) != 0) {
            throw new BizException(400, "投喂分配合计必须等于总用量");
        }
        List<FeedLogBatchAllocation> rows = normalizedInputs.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(GROUP_ORDER))
            .map(entry -> row(houseId, entry.getKey(), normalize(entry.getValue().amountKg())))
            .toList();
        return new AllocationPlan(rows);
    }

    public void save(Long feedLogId, AllocationPlan plan) {
        if (plan.rows().isEmpty()) {
            return;
        }
        plan.rows().forEach(row -> row.setFeedLogId(feedLogId));
        allocationMapper.insertBatch(plan.rows());
    }

    private Map<GroupKey, Set<Long>> resolveGroups(Long houseId, List<Long> rabbitIds, Date feedTime) {
        List<Long> normalizedIds = rabbitIds.stream().distinct().sorted().toList();
        List<FeedAllocationCandidateRow> candidates = allocationMapper.selectCandidates(
            houseId, normalizedIds, feedTime
        );
        Map<GroupKey, Set<Long>> groups = new HashMap<>();
        Set<Long> assigned = new HashSet<>();
        for (FeedAllocationCandidateRow candidate : candidates) {
            GroupKey key = normalizeKey(candidate.batchId(), candidate.phase());
            groups.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(candidate.rabbitId());
            assigned.add(candidate.rabbitId());
        }
        Set<Long> unassigned = new LinkedHashSet<>(normalizedIds);
        unassigned.removeAll(assigned);
        if (!unassigned.isEmpty()) {
            groups.put(new GroupKey(null, "UNASSIGNED"), unassigned);
        }
        return groups;
    }

    private void recordGapForBatches(
        Long userId,
        Long houseId,
        Set<GroupKey> groups,
        String requestId
    ) {
        groups.stream().map(GroupKey::batchId).filter(java.util.Objects::nonNull).distinct().sorted()
            .forEach(batchId -> legacyWriteService.recordGap(
                userId, houseId, batchId, requestId, OPERATION_CODE, GAP_EVENT
            ));
    }

    private static GroupKey normalizeKey(Long batchId, String phase) {
        String normalizedPhase = phase == null ? "" : phase.trim().toUpperCase();
        if (batchId == null && !"UNASSIGNED".equals(normalizedPhase)) {
            throw new BizException(400, "未归批次分配的phase必须是UNASSIGNED");
        }
        if (batchId != null && !Set.of("BREEDING", "FATTENING").contains(normalizedPhase)) {
            throw new BizException(400, "批次投喂phase必须是BREEDING或FATTENING");
        }
        return new GroupKey(batchId, normalizedPhase);
    }

    private static FeedLogBatchAllocation row(Long houseId, GroupKey key, BigDecimal amount) {
        FeedLogBatchAllocation row = new FeedLogBatchAllocation();
        row.setHouseId(houseId);
        row.setBatchId(key.batchId());
        row.setPhase(key.phase());
        row.setAmountKg(amount);
        return row;
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "投喂分配用量必须大于0");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new BizException(400, "投喂分配用量最多保留两位小数");
        }
    }

    private static boolean isKg(String unit) {
        return unit != null && "kg".equalsIgnoreCase(unit.trim());
    }

    private record GroupKey(Long batchId, String phase) {}

    private static final Comparator<GroupKey> GROUP_ORDER = Comparator
        .comparing(GroupKey::batchId, Comparator.nullsLast(Long::compareTo))
        .thenComparing(GroupKey::phase);

    public record AllocationPlan(List<FeedLogBatchAllocation> rows) {
        public AllocationPlan {
            rows = new ArrayList<>(rows);
        }
    }
}
