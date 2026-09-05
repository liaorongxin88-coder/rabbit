package com.rabbit.app.modules.sale.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import com.rabbit.app.modules.sale.entity.SaleOrderBatchAllocation;
import com.rabbit.app.modules.sale.mapper.SaleOrderBatchAllocationMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SaleBatchAllocationService {
    private static final String ALLOCATION_GAP =
        BatchStatisticsLegacyWriteService.LEGACY_SALE_ALLOCATION_GAP;
    private static final String PRICE_GAP =
        BatchStatisticsLegacyWriteService.LEGACY_SALE_PRICE_GAP;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("100000.000");

    private final SaleOrderBatchAllocationMapper allocationMapper;
    private final BatchStatisticsLegacyWriteService legacyWriteService;
    private final BatchMapper batchMapper;

    public SaleBatchAllocationService(
        SaleOrderBatchAllocationMapper allocationMapper,
        BatchStatisticsLegacyWriteService legacyWriteService
    ) {
        this(allocationMapper, legacyWriteService, null);
    }

    @Autowired
    public SaleBatchAllocationService(
        SaleOrderBatchAllocationMapper allocationMapper,
        BatchStatisticsLegacyWriteService legacyWriteService,
        BatchMapper batchMapper
    ) {
        this.allocationMapper = allocationMapper;
        this.legacyWriteService = legacyWriteService;
        this.batchMapper = batchMapper;
    }

    public void assertRequestAllowed(
        Map<Long, Integer> batchCounts,
        BigDecimal unitPrice,
        List<SaleBatchAllocationInput> requested
    ) {
        boolean explicitAllocations = requested != null && !requested.isEmpty();
        if (explicitAllocations) {
            requireSnapshotPrice(unitPrice);
        }
        if ((!explicitAllocations && batchCounts.size() > 1) || !validPrice(unitPrice)) {
            legacyWriteService.requireLegacyWriteEnabled();
        }
    }

    public List<SaleOrderBatchAllocation> allocateAndSave(
        Long userId,
        Long houseId,
        Long saleOrderId,
        String requestId,
        BigDecimal totalWeightKg,
        BigDecimal unitPrice,
        List<Long> rabbitBatchIds,
        List<SaleBatchAllocationInput> requested,
        String operationCode
    ) {
        AllocationPlan plan = prepare(
            userId,
            houseId,
            countsByBatch(rabbitBatchIds),
            totalWeightKg,
            unitPrice,
            requested,
            requestId,
            operationCode
        );
        save(saleOrderId, plan);
        return plan.rows();
    }

    public AllocationPlan prepareOutbound(
        Long userId,
        Long houseId,
        Map<Long, Integer> batchCounts,
        BigDecimal totalWeightKg,
        BigDecimal unitPrice,
        List<SaleBatchAllocationInput> requested,
        String requestId
    ) {
        return prepare(
            userId,
            houseId,
            batchCounts,
            totalWeightKg,
            unitPrice,
            requested,
            requestId,
            "outbound:submit"
        );
    }

    public void save(Long saleOrderId, AllocationPlan plan) {
        if (plan == null || plan.rows().isEmpty()) {
            return;
        }
        plan.rows().forEach(row -> row.setSaleOrderId(saleOrderId));
        allocationMapper.insertBatch(plan.rows());
    }

    public static BigDecimal orderAmount(BigDecimal totalWeightKg, BigDecimal unitPrice) {
        if (unitPrice == null) {
            return null;
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0 || unitPrice.compareTo(MAX_PRICE) > 0) {
            throw new BizException(400, "unitPrice不合法");
        }
        return money(normalizeWeight(totalWeightKg), unitPrice);
    }

    public static boolean supportsSnapshotPrice(BigDecimal value) {
        if (value == null
            || value.compareTo(BigDecimal.ZERO) <= 0
            || value.compareTo(MAX_PRICE) > 0) {
            return false;
        }
        try {
            value.setScale(2, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException error) {
            return false;
        }
    }

    public static BigDecimal normalizeSnapshotPrice(BigDecimal value) {
        requireSnapshotPrice(value);
        return normalizePrice(value);
    }

    public static List<SaleBatchAllocationInput> normalizeDraftAllocations(
        Map<Long, Integer> counts,
        BigDecimal totalWeightKg,
        BigDecimal unitPrice,
        List<SaleBatchAllocationInput> requested,
        boolean requireComplete
    ) {
        if (requested == null || requested.isEmpty()) {
            if (requireComplete) {
                throw new BizException(400, "销售批次分配必须覆盖全部批次和未归批次组");
            }
            return List.of();
        }
        if (unitPrice != null) {
            normalizeSnapshotPrice(unitPrice);
        }

        BigDecimal normalizedTotal = totalWeightKg == null ? null : normalizeWeight(totalWeightKg);
        if (normalizedTotal != null && normalizedTotal.compareTo(MAX_WEIGHT) > 0) {
            throw new BizException(400, "totalWeight不合法");
        }
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        for (SaleBatchAllocationInput input : requested) {
            if (input == null) {
                throw new BizException(400, "batchAllocations不能包含空项");
            }
            if (input.batchId() != null && input.batchId() <= 0) {
                throw new BizException(400, "batchId不合法");
            }
            if (!counts.containsKey(input.batchId())) {
                throw new BizException(400, "销售批次分配包含未选择的批次");
            }
            BigDecimal weight = normalizeWeight(input.actualWeightKg());
            if (weight.compareTo(MAX_WEIGHT) > 0) {
                throw new BizException(400, "actualWeightKg不能超过100000");
            }
            if (weights.putIfAbsent(input.batchId(), weight) != null) {
                throw new BizException(400, "同一销售批次不能重复分配");
            }
        }

        if (requireComplete) {
            if (!weights.keySet().equals(counts.keySet())) {
                throw new BizException(400, "销售批次分配必须覆盖全部批次和未归批次组");
            }
            if (normalizedTotal == null) {
                throw new BizException(400, "totalWeight不能为空");
            }
            BigDecimal sum = weights.values().stream()
                .reduce(BigDecimal.ZERO.setScale(3), BigDecimal::add);
            if (sum.compareTo(normalizedTotal) != 0) {
                throw new BizException(400, "销售批次分配重量合计必须等于订单总重量");
            }
            normalizeSnapshotPrice(unitPrice);
        }

        List<SaleBatchAllocationInput> normalized = weights.entrySet().stream()
            .map(entry -> new SaleBatchAllocationInput(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(
                SaleBatchAllocationInput::batchId,
                Comparator.nullsLast(Long::compareTo)
            ))
            .toList();
        return List.copyOf(normalized);
    }

    private AllocationPlan prepare(
        Long userId,
        Long houseId,
        Map<Long, Integer> counts,
        BigDecimal totalWeightKg,
        BigDecimal unitPrice,
        List<SaleBatchAllocationInput> requested,
        String requestId,
        String operationCode
    ) {
        BigDecimal normalizedTotal = normalizeWeight(totalWeightKg);
        if (counts.isEmpty()) {
            throw new BizException(400, "销售批次分组不能为空");
        }
        assertHouseBatches(houseId, counts);
        Map<Long, BigDecimal> weights = requestedWeights(
            requested,
            counts,
            normalizedTotal,
            !"outbound:submit".equals(operationCode)
        );
        if (weights == null) {
            legacyWriteService.requireLegacyWriteEnabled();
            recordForBatches(
                userId, houseId, counts, requestId, operationCode, ALLOCATION_GAP
            );
            if (!validPrice(unitPrice)) {
                recordForBatches(userId, houseId, counts, requestId, operationCode, PRICE_GAP);
            }
            BigDecimal totalAmount = validPrice(unitPrice)
                ? money(normalizedTotal, normalizePrice(unitPrice))
                : null;
            return new AllocationPlan(List.of(), totalAmount);
        }

        if (requested != null && !requested.isEmpty()) {
            requireSnapshotPrice(unitPrice);
        }

        BigDecimal normalizedPrice = null;
        if (validPrice(unitPrice)) {
            normalizedPrice = normalizePrice(unitPrice);
        } else {
            legacyWriteService.requireLegacyWriteEnabled();
            recordForBatches(userId, houseId, counts, requestId, operationCode, PRICE_GAP);
        }

        List<SaleOrderBatchAllocation> rows = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : weights.entrySet()) {
            SaleOrderBatchAllocation row = new SaleOrderBatchAllocation();
            row.setHouseId(houseId);
            row.setBatchId(entry.getKey());
            row.setRabbitCount(counts.get(entry.getKey()));
            row.setActualWeightKg(entry.getValue());
            row.setUnitPricePerKg(normalizedPrice);
            row.setAmount(normalizedPrice == null ? null : money(entry.getValue(), normalizedPrice));
            rows.add(row);
        }
        rows.sort(BATCH_ORDER);
        BigDecimal totalAmount = normalizedPrice == null
            ? null
            : money(normalizedTotal, normalizedPrice);
        if (normalizedPrice != null) {
            applyRoundingTail(rows, totalAmount);
        }
        return new AllocationPlan(rows, totalAmount);
    }

    private void assertHouseBatches(Long houseId, Map<Long, Integer> counts) {
        if (batchMapper == null) {
            return;
        }
        for (Long batchId : counts.keySet()) {
            if (batchId != null && batchMapper.selectById(houseId, batchId) == null) {
                throw new BizException(409, "销售批次不属于当前兔舍: " + batchId);
            }
        }
    }

    private Map<Long, BigDecimal> requestedWeights(
        List<SaleBatchAllocationInput> requested,
        Map<Long, Integer> counts,
        BigDecimal totalWeight,
        boolean autoAssignUnassignedGroup
    ) {
        if (requested == null || requested.isEmpty()) {
            if (counts.size() != 1
                || (!autoAssignUnassignedGroup && hasUnassignedGroup(counts))) {
                return null;
            }
            Map<Long, BigDecimal> automatic = new LinkedHashMap<>();
            automatic.put(counts.keySet().iterator().next(), totalWeight);
            return automatic;
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        BigDecimal sum = BigDecimal.ZERO.setScale(3);
        for (SaleBatchAllocationInput input : requested) {
            if (!counts.containsKey(input.batchId())) {
                throw new BizException(400, "销售批次分配包含未选择的批次");
            }
            BigDecimal weight = normalizeWeight(input.actualWeightKg());
            if (result.putIfAbsent(input.batchId(), weight) != null) {
                throw new BizException(400, "同一销售批次不能重复分配");
            }
            sum = sum.add(weight);
        }
        if (!result.keySet().equals(counts.keySet())) {
            throw new BizException(400, "销售批次分配必须覆盖全部批次和未归批次组");
        }
        if (sum.compareTo(totalWeight) != 0) {
            throw new BizException(400, "销售批次分配重量合计必须等于订单总重量");
        }
        return result;
    }

    private static Map<Long, Integer> countsByBatch(List<Long> rabbitBatchIds) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long batchId : rabbitBatchIds) {
            counts.merge(batchId, 1, Integer::sum);
        }
        return counts;
    }

    private void recordForBatches(
        Long userId,
        Long houseId,
        Map<Long, Integer> counts,
        String requestId,
        String operationCode,
        String eventType
    ) {
        counts.keySet().stream().filter(Objects::nonNull).sorted().forEach(batchId ->
            legacyWriteService.recordGap(
                userId, houseId, batchId, requestId, operationCode, eventType
            )
        );
    }

    private static void applyRoundingTail(
        List<SaleOrderBatchAllocation> rows,
        BigDecimal orderAmount
    ) {
        BigDecimal allocated = rows.stream().map(SaleOrderBatchAllocation::getAmount)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal tail = orderAmount.subtract(allocated);
        if (tail.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        SaleOrderBatchAllocation target = rows.stream().sorted(WEIGHT_DESC_BATCH_ORDER)
            .findFirst()
            .orElseThrow();
        target.setAmount(target.getAmount().add(tail));
    }

    private static BigDecimal normalizeWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "销售重量必须大于0");
        }
        try {
            return value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new BizException(400, "销售重量最多保留三位小数");
        }
    }

    public static BigDecimal normalizeSnapshotWeight(BigDecimal value) {
        return normalizeWeight(value);
    }

    private static boolean hasUnassignedGroup(Map<Long, Integer> counts) {
        return counts.keySet().stream().anyMatch(Objects::isNull);
    }

    private static BigDecimal normalizePrice(BigDecimal value) {
        if (!validPrice(value)) {
            throw new BizException(400, "unitPrice不合法");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new BizException(400, "unitPrice最多保留两位小数");
        }
    }

    private static boolean validPrice(BigDecimal value) {
        return supportsSnapshotPrice(value);
    }

    private static void requireSnapshotPrice(BigDecimal value) {
        if (supportsSnapshotPrice(value)) {
            return;
        }
        if (value != null
            && value.compareTo(BigDecimal.ZERO) > 0
            && value.compareTo(MAX_PRICE) <= 0) {
            throw new BizException(400, "统一重量单价最多保留两位小数");
        }
        throw new BizException(400, "统一重量单价必须大于0");
    }

    private static BigDecimal money(BigDecimal weight, BigDecimal price) {
        return weight.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private static final Comparator<SaleOrderBatchAllocation> BATCH_ORDER = Comparator
        .comparing(SaleOrderBatchAllocation::getBatchId, Comparator.nullsLast(Long::compareTo));

    private static final Comparator<SaleOrderBatchAllocation> WEIGHT_DESC_BATCH_ORDER = Comparator
        .comparing(SaleOrderBatchAllocation::getActualWeightKg, Comparator.reverseOrder())
        .thenComparing(SaleOrderBatchAllocation::getBatchId, Comparator.nullsLast(Long::compareTo));

    public record AllocationPlan(
        List<SaleOrderBatchAllocation> rows,
        BigDecimal totalAmount
    ) {
        public AllocationPlan {
            rows = List.copyOf(rows);
        }
    }
}
