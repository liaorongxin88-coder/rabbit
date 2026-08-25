package com.rabbit.app.modules.batch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
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
    private final RabbitMapper rabbitMapper;
    private final KitPlacementService kitPlacementService;
    private final RequestDedupService requestDedupService;
    private final OperatorNameResolver operatorNames;
    private final ObjectMapper objectMapper;
    private final int commodityCageCapacity;

    public BatchWeaningSeparationService(
        BatchMapper batchMapper,
        WeaningRecordMapper weaningRecordMapper,
        WeaningRecordAllocationMapper allocationMapper,
        CageMapper cageMapper,
        RabbitMapper rabbitMapper,
        KitPlacementService kitPlacementService,
        RequestDedupService requestDedupService,
        OperatorNameResolver operatorNames,
        ObjectMapper objectMapper,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.batchMapper = batchMapper;
        this.weaningRecordMapper = weaningRecordMapper;
        this.allocationMapper = allocationMapper;
        this.cageMapper = cageMapper;
        this.rabbitMapper = rabbitMapper;
        this.kitPlacementService = kitPlacementService;
        this.requestDedupService = requestDedupService;
        this.operatorNames = operatorNames;
        this.objectMapper = objectMapper;
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

        List<WeaningRecordAllocation> allocations = allocations(weaningRecordId, request);
        String requestId = request.getRequestId().trim();
        RequestDedupService.BeginResult dedup = requestDedupService.begin(
            houseId,
            userId,
            API,
            requestId,
            payloadHash(batchId, weaningRecordId, request, allocations)
        );
        if (dedup == RequestDedupService.BeginResult.DONE) {
            return replayedResult(houseId, userId, requestId);
        }
        if ("已完成".equals(batch.getStatus())) {
            throw new BizException(400, "批次已完成");
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

        validateExplicitSexCounts(
            record,
            allocationMapper.selectByWeaningRecordId(weaningRecordId),
            allocations
        );
        lockAndValidateParents(
            houseId, request.getMotherRabbitId(), request.getFatherRabbitId()
        );
        lockAndValidateCages(houseId, allocations);

        String operator = operatorNames.resolve(userId);
        Date separatedAt = new Date();
        List<Long> rabbitIds = kitPlacementService.separate(new KitSeparationCommand(
            userId,
            operator,
            record,
            request.getMotherRabbitId(),
            request.getFatherRabbitId(),
            allocations,
            separatedAt,
            requestId
        ));

        allocationMapper.insertBatch(allocations);
        if (weaningRecordMapper.decrementWaitingCount(
            houseId, batchId, weaningRecordId, separatedCount, operator
        ) != 1) {
            throw new BizException(409, "待分笼数量已变化，请刷新后重试");
        }
        int waitingCount = orZero(record.getWaitingCount()) - separatedCount;
        WeaningSeparationResult result = new WeaningSeparationResult(
            record.getId(), separatedCount, waitingCount, rabbitIds, false
        );
        requestDedupService.markDone(
            houseId, userId, API, requestId, responsePayload(result)
        );
        return result;
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
        Map<Long, WeaningRecordAllocation> rows = new TreeMap<>();
        if (request.getAllocations() == null) {
            throw new BizException(400, "至少选择一个商品兔笼位");
        }
        try {
            for (SeparateWeaningRecordRequest.Allocation item : request.getAllocations()) {
                validateAllocation(item);
                boolean explicitSex = item.getMaleCount() != null;
                WeaningRecordAllocation row = rows.get(item.getCageId());
                if (row == null) {
                    row = new WeaningRecordAllocation();
                    row.setWeaningRecordId(weaningRecordId);
                    row.setCageId(item.getCageId());
                    row.setAllocCount(item.getCount());
                    row.setMaleCount(item.getMaleCount());
                    row.setFemaleCount(item.getFemaleCount());
                    rows.put(item.getCageId(), row);
                    continue;
                }
                if (explicitSex != (row.getMaleCount() != null)) {
                    throw new BizException(400, "同一笼位的公母数量提供方式必须一致");
                }
                row.setAllocCount(Math.addExact(row.getAllocCount(), item.getCount()));
                if (explicitSex) {
                    row.setMaleCount(Math.addExact(row.getMaleCount(), item.getMaleCount()));
                    row.setFemaleCount(Math.addExact(row.getFemaleCount(), item.getFemaleCount()));
                }
            }
        } catch (ArithmeticException exception) {
            throw new BizException(400, "分笼数量过大");
        }
        if (rows.isEmpty()) {
            throw new BizException(400, "至少选择一个商品兔笼位");
        }
        return new ArrayList<>(rows.values());
    }

    private void validateAllocation(SeparateWeaningRecordRequest.Allocation item) {
        if (item == null || item.getCageId() == null || item.getCageId() <= 0
            || item.getCount() == null || item.getCount() <= 0) {
            throw new BizException(400, "分笼笼位和数量错误");
        }
        if ((item.getMaleCount() == null) != (item.getFemaleCount() == null)) {
            throw new BizException(400, "maleCount和femaleCount必须同时提供");
        }
        if (item.getMaleCount() != null && (item.getMaleCount() < 0
            || item.getFemaleCount() < 0
            || (long) item.getMaleCount() + item.getFemaleCount() != item.getCount())) {
            throw new BizException(400, "公母数量之和必须等于分笼数量");
        }
    }

    private void validateExplicitSexCounts(
        WeaningRecord record,
        List<WeaningRecordAllocation> existing,
        List<WeaningRecordAllocation> requested
    ) {
        int requestedMale = 0;
        int requestedFemale = 0;
        boolean hasExplicitSex = false;
        for (WeaningRecordAllocation allocation : requested) {
            if (allocation.getMaleCount() == null) {
                continue;
            }
            hasExplicitSex = true;
            requestedMale = Math.addExact(requestedMale, allocation.getMaleCount());
            requestedFemale = Math.addExact(requestedFemale, allocation.getFemaleCount());
        }
        if (!hasExplicitSex) {
            return;
        }
        RemainingSex remaining = remainingSex(record, existing);
        if (remaining == null) {
            throw new BizException(400, "待分笼记录的剩余公母数量不可信，请仅填写总数");
        }
        if (requestedMale > remaining.male() || requestedFemale > remaining.female()) {
            throw new BizException(400, "分笼公母数量超过剩余数量");
        }
    }

    private RemainingSex remainingSex(
        WeaningRecord record,
        List<WeaningRecordAllocation> existing
    ) {
        if (record.getMaleCount() == null || record.getFemaleCount() == null
            || record.getMaleCount() < 0 || record.getFemaleCount() < 0
            || (long) record.getMaleCount() + record.getFemaleCount()
                != orZero(record.getWeaningCount())) {
            return null;
        }
        int allocated = 0;
        int allocatedMale = 0;
        int allocatedFemale = 0;
        try {
            for (WeaningRecordAllocation allocation : existing) {
                if (allocation.getMaleCount() == null || allocation.getFemaleCount() == null
                    || allocation.getMaleCount() < 0 || allocation.getFemaleCount() < 0
                    || allocation.getMaleCount() + allocation.getFemaleCount()
                        != orZero(allocation.getAllocCount())) {
                    return null;
                }
                allocated = Math.addExact(allocated, orZero(allocation.getAllocCount()));
                allocatedMale = Math.addExact(allocatedMale, allocation.getMaleCount());
                allocatedFemale = Math.addExact(allocatedFemale, allocation.getFemaleCount());
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        if (allocated != orZero(record.getWeaningCount()) - orZero(record.getWaitingCount())) {
            return null;
        }
        int male = record.getMaleCount() - allocatedMale;
        int female = record.getFemaleCount() - allocatedFemale;
        if (male < 0 || female < 0 || male + female != orZero(record.getWaitingCount())) {
            return null;
        }
        return new RemainingSex(male, female);
    }

    private void lockAndValidateParents(Long houseId, Long motherId, Long fatherId) {
        List<Long> parentIds = new ArrayList<>(2);
        if (motherId != null) {
            parentIds.add(motherId);
        }
        if (fatherId != null && !fatherId.equals(motherId)) {
            parentIds.add(fatherId);
        }
        parentIds.sort(Long::compareTo);
        Map<Long, Rabbit> parents = new HashMap<>();
        for (Rabbit parent : rabbitMapper.selectByIdsForUpdate(houseId, parentIds)) {
            parents.put(parent.getId(), parent);
        }
        if (parents.size() != parentIds.size()) {
            throw new BizException(400, "关联父母兔必须属于当前兔舍");
        }
        if (motherId != null) {
            Rabbit mother = parents.get(motherId);
            if (!"0".equals(mother.getType())) {
                throw new BizException(400, "关联母兔必须是种兔");
            }
            if (!"0".equals(mother.getGender())) {
                throw new BizException(400, "关联母兔性别不正确");
            }
        }
        if (fatherId != null) {
            Rabbit father = parents.get(fatherId);
            if (!"0".equals(father.getType())) {
                throw new BizException(400, "关联公兔必须是种兔");
            }
            if (!"1".equals(father.getGender())) {
                throw new BizException(400, "关联公兔性别不正确");
            }
        }
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

    private static String payloadHash(
        Long batchId,
        Long weaningRecordId,
        SeparateWeaningRecordRequest request,
        List<WeaningRecordAllocation> allocations
    ) {
        StringBuilder value = new StringBuilder()
            .append(batchId)
            .append('|').append(weaningRecordId)
            .append('|').append(request.getMotherRabbitId())
            .append('|').append(request.getFatherRabbitId());
        for (WeaningRecordAllocation allocation : allocations) {
            value.append('|').append(allocation.getCageId())
                .append('x').append(allocation.getAllocCount())
                .append('m').append(allocation.getMaleCount())
                .append('f').append(allocation.getFemaleCount());
        }
        return UUID.nameUUIDFromBytes(value.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private WeaningSeparationResult replayedResult(
        Long houseId,
        Long userId,
        String requestId
    ) {
        String payload = requestDedupService.getResponsePayload(houseId, userId, API, requestId);
        if (payload == null || payload.isBlank()) {
            throw new BizException(409, "首次分笼结果不可用，请使用新的requestId");
        }
        try {
            WeaningSeparationResult original = objectMapper.readValue(
                payload, WeaningSeparationResult.class
            );
            return new WeaningSeparationResult(
                original.weaningRecordId(),
                original.separatedCount(),
                original.waitingCount(),
                original.generatedRabbitIds(),
                true
            );
        } catch (JsonProcessingException exception) {
            throw new BizException(500, "首次分笼结果解析失败");
        }
    }

    private String responsePayload(WeaningSeparationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BizException(500, "分笼结果保存失败");
        }
    }

    private static int allocationCount(List<WeaningRecordAllocation> allocations) {
        int total = 0;
        try {
            for (WeaningRecordAllocation allocation : allocations) {
                total = Math.addExact(total, allocation.getAllocCount());
            }
        } catch (ArithmeticException exception) {
            throw new BizException(400, "分笼数量过大");
        }
        return total;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record RemainingSex(int male, int female) {
    }
}
