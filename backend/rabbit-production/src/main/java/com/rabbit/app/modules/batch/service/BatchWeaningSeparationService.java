package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.SeparateWeaningRecordRequest;
import com.rabbit.app.modules.batch.dto.WeaningSeparationResult;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordAllocationMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.service.KitPlacementService;
import com.rabbit.app.modules.repro.service.KitSeparationCommand;
import com.rabbit.app.modules.repro.service.OperatorNameResolver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Performs the inventory half of deferred weaning for one production batch. */
@Service
public class BatchWeaningSeparationService {

    private static final String API = "batch.weaning.separate";

    private final BatchMapper batchMapper;
    private final WeaningRecordMapper weaningRecordMapper;
    private final WeaningRecordAllocationMapper allocationMapper;
    private final CageMapper cageMapper;
    private final ReproCycleMapper reproCycleMapper;
    private final KitPlacementService kitPlacementService;
    private final RequestDedupService requestDedupService;
    private final OperatorNameResolver operatorNames;
    private final int commodityCageCapacity;

    public BatchWeaningSeparationService(
        BatchMapper batchMapper,
        WeaningRecordMapper weaningRecordMapper,
        WeaningRecordAllocationMapper allocationMapper,
        CageMapper cageMapper,
        ReproCycleMapper reproCycleMapper,
        KitPlacementService kitPlacementService,
        RequestDedupService requestDedupService,
        OperatorNameResolver operatorNames,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.batchMapper = batchMapper;
        this.weaningRecordMapper = weaningRecordMapper;
        this.allocationMapper = allocationMapper;
        this.cageMapper = cageMapper;
        this.reproCycleMapper = reproCycleMapper;
        this.kitPlacementService = kitPlacementService;
        this.requestDedupService = requestDedupService;
        this.operatorNames = operatorNames;
        this.commodityCageCapacity = commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
    }

    @Transactional
    public WeaningSeparationResult separate(
        Long userId,
        Long houseId,
        Long batchId,
        Long weaningRecordId,
        SeparateWeaningRecordRequest request
    ) {
        Batch batch = batchMapper.selectByIdForUpdate(houseId, batchId);
        if (batch == null) {
            throw new BizException(404, "批次不存在");
        }
        if ("已完成".equals(batch.getStatus())) {
            throw new BizException(400, "批次已完成");
        }

        List<WeaningRecordAllocation> allocations = allocations(weaningRecordId, request);
        RequestDedupService.BeginResult dedup = requestDedupService.begin(
            houseId,
            userId,
            API,
            request.getRequestId().trim(),
            payloadHash(weaningRecordId, allocations)
        );
        if (dedup == RequestDedupService.BeginResult.DONE) {
            WeaningRecord existing = weaningRecordMapper.selectById(
                houseId, batchId, weaningRecordId
            );
            if (existing == null) {
                throw new BizException(404, "待分笼记录不存在");
            }
            return new WeaningSeparationResult(
                existing.getId(), 0, orZero(existing.getWaitingCount()), List.of(), true
            );
        }

        WeaningRecord record = weaningRecordMapper.selectByIdForUpdate(
            houseId, batchId, weaningRecordId
        );
        if (record == null) {
            throw new BizException(404, "待分笼记录不存在");
        }
        int separatedCount = allocationCount(allocations);
        if (separatedCount > orZero(record.getWaitingCount())) {
            throw new BizException(400, "分笼数量超过待分笼数量");
        }

        lockAndValidateCages(houseId, allocations);
        String operator = operatorNames.resolve(userId);
        Date separatedAt = new Date();
        ReproCycle cycle = record.getBreedingCycleId() == null
            ? null
            : reproCycleMapper.selectById(houseId, record.getBreedingCycleId());
        List<Long> rabbitIds = kitPlacementService.separate(new KitSeparationCommand(
            userId,
            operator,
            record,
            cycle == null ? null : cycle.getMaleRabbitId(),
            allocations,
            separatedAt,
            request.getRequestId().trim()
        ));

        allocationMapper.insertBatch(allocations);
        if (weaningRecordMapper.decrementWaitingCount(
            houseId, batchId, weaningRecordId, separatedCount, operator
        ) != 1) {
            throw new BizException(409, "待分笼数量已变化，请刷新后重试");
        }
        int waitingCount = orZero(record.getWaitingCount()) - separatedCount;
        requestDedupService.markDone(houseId, userId, API, request.getRequestId().trim());
        return new WeaningSeparationResult(
            record.getId(), separatedCount, waitingCount, rabbitIds, false
        );
    }

    public List<WeaningRecord> listPending(Long houseId, Long batchId) {
        if (batchMapper.selectById(houseId, batchId) == null) {
            throw new BizException(404, "批次不存在");
        }
        return weaningRecordMapper.selectPendingByBatch(houseId, batchId, 200);
    }

    private List<WeaningRecordAllocation> allocations(
        Long weaningRecordId,
        SeparateWeaningRecordRequest request
    ) {
        Map<Long, Integer> counts = new TreeMap<>();
        for (SeparateWeaningRecordRequest.Allocation item : request.getAllocations()) {
            if (item.getCageId() == null || item.getCageId() <= 0 || item.getCount() == null
                || item.getCount() <= 0) {
                throw new BizException(400, "分笼笼位和数量错误");
            }
            counts.merge(item.getCageId(), item.getCount(), Math::addExact);
        }
        List<WeaningRecordAllocation> rows = new ArrayList<>(counts.size());
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            WeaningRecordAllocation row = new WeaningRecordAllocation();
            row.setWeaningRecordId(weaningRecordId);
            row.setCageId(entry.getKey());
            row.setAllocCount(entry.getValue());
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new BizException(400, "至少选择一个商品兔笼位");
        }
        return rows;
    }

    private void lockAndValidateCages(Long houseId, List<WeaningRecordAllocation> allocations) {
        List<Long> cageIds = allocations.stream().map(WeaningRecordAllocation::getCageId).toList();
        Map<Long, Cage> cages = new HashMap<>();
        for (Cage cage : cageMapper.selectByIdsForUpdate(houseId, cageIds)) {
            cages.put(cage.getId(), cage);
        }
        if (cages.size() != cageIds.size()) {
            throw new BizException(400, "目标笼位不存在");
        }
        for (WeaningRecordAllocation allocation : allocations) {
            Cage cage = cages.get(allocation.getCageId());
            if (!Boolean.TRUE.equals(cage.getIsEnabled())) {
                throw new BizException(400, "目标笼位已停用");
            }
            if (!"0".equals(cage.getStatus()) && !"3".equals(cage.getStatus())) {
                throw new BizException(400, "目标笼位不是商品兔笼位");
            }
            if (orZero(cage.getRabbitCount()) + allocation.getAllocCount()
                > commodityCageCapacity) {
                throw new BizException(400, "目标笼位容量不足");
            }
        }
    }

    private static String payloadHash(Long weaningRecordId, List<WeaningRecordAllocation> allocations) {
        StringBuilder value = new StringBuilder().append(weaningRecordId);
        for (WeaningRecordAllocation allocation : allocations) {
            value.append(':').append(allocation.getCageId()).append('x').append(allocation.getAllocCount());
        }
        return UUID.nameUUIDFromBytes(value.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static int allocationCount(List<WeaningRecordAllocation> allocations) {
        int total = 0;
        for (WeaningRecordAllocation allocation : allocations) {
            total = Math.addExact(total, allocation.getAllocCount());
        }
        return total;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
