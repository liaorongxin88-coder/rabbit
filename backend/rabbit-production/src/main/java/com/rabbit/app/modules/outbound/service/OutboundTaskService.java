package com.rabbit.app.modules.outbound.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import com.rabbit.app.modules.outbound.entity.OutboundTask;
import com.rabbit.app.modules.outbound.entity.OutboundTaskBatchAllocation;
import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskBatchAllocationMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskItemMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskMapper;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import com.rabbit.app.modules.sale.service.SaleBatchAllocationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundTaskService {
    private static final int TASK_ITEM_WRITE_CHUNK_SIZE = 1_000;

    private final OutboundTaskMapper taskMapper;
    private final OutboundTaskItemMapper itemMapper;
    private final OutboundTaskBatchAllocationMapper allocationMapper;
    private final OutboundEligibilityService eligibilityService;
    private final HouseService houseService;

    public OutboundTaskService(OutboundTaskMapper taskMapper, OutboundTaskItemMapper itemMapper,
                               OutboundTaskBatchAllocationMapper allocationMapper,
                               OutboundEligibilityService eligibilityService, HouseService houseService) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.allocationMapper = allocationMapper;
        this.eligibilityService = eligibilityService;
        this.houseService = houseService;
    }

    @Transactional
    public OutboundDtos.TaskView create(Long userId, Long houseId, OutboundDtos.CreateTaskRequest request) {
        boolean resume = request.resumeExisting() == null || request.resumeExisting();
        if (resume) {
            OutboundTask existing = taskMapper.selectLatestEditable(houseId, userId);
            if (existing != null) {
                return view(existing, true);
            }
        }
        String entryType = normalizeEntryType(request.entryType());
        validateSource(entryType, request.rabbitId(), request.cageId(), request.rowCode());
        List<OutboundCandidateRow> candidates = eligibilityService.scopeRows(
                houseId, entryType, request.rabbitId(), request.cageId(), trim(request.rowCode()));
        if ("RABBIT".equals(entryType) && candidates.isEmpty()) {
            throw new BizException(404, "目标兔只不存在");
        }

        OutboundTask task = new OutboundTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setHouseId(houseId);
        task.setOperatorId(userId);
        task.setEntryType(entryType);
        task.setSourceRabbitId(request.rabbitId());
        task.setSourceCageId(request.cageId());
        task.setSourceRowCode(trim(request.rowCode()));
        task.setStatus("SELECTING");
        task.setRevision(0L);
        taskMapper.insert(task);

        List<OutboundTaskItem> defaults = new ArrayList<>();
        for (OutboundCandidateRow candidate : candidates) {
            OutboundDtos.RabbitEligibilityView evaluated = eligibilityService.evaluate(candidate);
            if (evaluated.defaultSelected()) {
                defaults.add(toItem(task.getTaskId(), candidate, "NORMAL", null, evaluated.stateVersion()));
            }
        }
        insertTaskItems(defaults);
        return view(taskMapper.selectById(houseId, userId, task.getTaskId()), false);
    }

    public OutboundDtos.TaskView get(Long userId, Long houseId, String taskId) {
        OutboundTask task = requireTask(userId, houseId, taskId);
        return view(task, false);
    }

    public OutboundDtos.TaskView precheck(Long userId, Long houseId, String taskId) {
        return get(userId, houseId, taskId);
    }

    @Transactional
    public OutboundDtos.TaskView save(Long userId, Long houseId, String taskId, OutboundDtos.SaveDraftRequest request) {
        OutboundTask task = taskMapper.selectByIdForUpdate(houseId, userId, taskId);
        if (task == null) throw new BizException(404, "OUTBOUND_TASK_NOT_FOUND");
        String status = normalizeDraftStatus(request.status());
        List<OutboundDtos.SelectedRabbitInput> inputs = request.items() == null ? List.of() : request.items();
        if (inputs.stream().anyMatch(Objects::isNull)) {
            throw new BizException(400, "items不能包含空项");
        }
        if (inputs.stream().anyMatch(input -> "EARLY_SALE".equalsIgnoreCase(trim(input.selectionType())))) {
            houseService.assertHousePermission(userId, houseId, "control");
        }
        if ("WAITING_CONFIRMATION".equals(status) && inputs.isEmpty()) {
            throw new BizException(400, "请选择兔只");
        }
        List<Long> ids = uniqueIds(inputs);
        Map<Long, OutboundCandidateRow> candidates = byId(eligibilityService.rowsByIds(houseId, ids));
        List<OutboundTaskItem> items = new ArrayList<>();
        for (OutboundDtos.SelectedRabbitInput input : inputs) {
            OutboundCandidateRow candidate = candidates.get(input.rabbitId());
            if (candidate == null) throw new BizException(409, "RABBIT_NOT_PRESENT: " + input.rabbitId());
            OutboundDtos.RabbitEligibilityView evaluated = eligibilityService.evaluate(candidate);
            String selectionType = normalizeSelectionType(input.selectionType());
            if ("NORMAL".equals(selectionType) && !OutboundEligibilityService.NORMAL.equals(evaluated.eligibility())) {
                throw new BizException(409, "RABBIT_NOT_ELIGIBLE: " + input.rabbitId());
            }
            if ("EARLY_SALE".equals(selectionType)) {
                if (!OutboundEligibilityService.EARLY_SALE.equals(evaluated.eligibility())) {
                    throw new BizException(409, "EARLY_SALE_NOT_ALLOWED: " + input.rabbitId());
                }
                if (trim(input.earlySaleReason()) == null) {
                    throw new BizException(400, "EARLY_SALE_REASON_REQUIRED: " + input.rabbitId());
                }
            }
            if (!evaluated.stateVersion().equals(input.stateVersion())) {
                throw new BizException(409, "RABBIT_STATE_CHANGED: " + input.rabbitId());
            }
            items.add(toItem(taskId, candidate, selectionType, trim(input.earlySaleReason()), input.stateVersion()));
        }

        BigDecimal unitPrice = draftUnitPrice(request);
        BigDecimal totalWeight = draftTotalWeight(request.totalWeight());
        List<SaleBatchAllocationInput> allocations = request.batchAllocations() == null
            ? null
            : SaleBatchAllocationService.normalizeDraftAllocations(
                batchGroupCounts(items),
                totalWeight,
                unitPrice,
                request.batchAllocations(),
                false
            );
        List<OutboundTaskItem> existingItems = allocations == null
            ? itemMapper.selectByTask(taskId)
            : List.of();
        boolean preserveExistingAllocations = allocations == null
            && hasEquivalentAllocationSnapshot(existingItems, items);

        int updated = taskMapper.updateDraft(houseId, userId, taskId, request.revision(), status,
                request.saleTime(), request.totalWeight(), unitPrice, trim(request.customer()), trim(request.remark()));
        if (updated == 0) {
            throw new BizException(409, "OUTBOUND_REVISION_CONFLICT");
        }
        replaceTaskItems(taskId, items);
        if (allocations != null) {
            replaceTaskAllocations(houseId, taskId, allocations);
        } else if (!preserveExistingAllocations) {
            replaceTaskAllocations(houseId, taskId, List.of());
        }
        return view(taskMapper.selectById(houseId, userId, taskId), false);
    }

    @Transactional
    public void cancel(Long userId, Long houseId, String taskId) {
        if (taskMapper.markCancelled(houseId, userId, taskId) == 0) {
            OutboundTask task = taskMapper.selectById(houseId, userId, taskId);
            if (task == null) throw new BizException(404, "OUTBOUND_TASK_NOT_FOUND");
            throw new BizException(409, "当前任务状态不可取消");
        }
    }

    public OutboundTask requireTask(Long userId, Long houseId, String taskId) {
        OutboundTask task = taskMapper.selectById(houseId, userId, taskId);
        if (task == null) throw new BizException(404, "OUTBOUND_TASK_NOT_FOUND");
        return task;
    }

    public List<OutboundTaskItem> taskItems(String taskId) {
        return itemMapper.selectByTask(taskId);
    }

    private void replaceTaskItems(String taskId, List<OutboundTaskItem> items) {
        int deleted;
        do {
            deleted = itemMapper.deleteByTaskLimited(taskId, TASK_ITEM_WRITE_CHUNK_SIZE);
        } while (deleted == TASK_ITEM_WRITE_CHUNK_SIZE);
        insertTaskItems(items);
    }

    private void insertTaskItems(List<OutboundTaskItem> items) {
        for (int start = 0; start < items.size(); start += TASK_ITEM_WRITE_CHUNK_SIZE) {
            int end = Math.min(start + TASK_ITEM_WRITE_CHUNK_SIZE, items.size());
            itemMapper.insertBatch(items.subList(start, end));
        }
    }

    private void replaceTaskAllocations(
        Long houseId,
        String taskId,
        List<SaleBatchAllocationInput> allocations
    ) {
        int deleted;
        do {
            deleted = allocationMapper.deleteByTaskLimited(
                houseId, taskId, TASK_ITEM_WRITE_CHUNK_SIZE
            );
        } while (deleted == TASK_ITEM_WRITE_CHUNK_SIZE);
        for (int start = 0; start < allocations.size(); start += TASK_ITEM_WRITE_CHUNK_SIZE) {
            int end = Math.min(start + TASK_ITEM_WRITE_CHUNK_SIZE, allocations.size());
            List<OutboundTaskBatchAllocation> rows = allocations.subList(start, end).stream()
                .map(allocation -> toAllocation(houseId, taskId, allocation))
                .toList();
            allocationMapper.insertBatch(rows);
        }
    }

    private boolean hasEquivalentAllocationSnapshot(
        List<OutboundTaskItem> existingItems,
        List<OutboundTaskItem> newItems
    ) {
        if (existingItems.size() != newItems.size()) {
            return false;
        }
        Map<Long, OutboundTaskItem> existingByRabbit = new LinkedHashMap<>();
        for (OutboundTaskItem item : existingItems) {
            if (item == null || existingByRabbit.put(item.getRabbitId(), item) != null) {
                return false;
            }
        }
        for (OutboundTaskItem item : newItems) {
            OutboundTaskItem existing = existingByRabbit.remove(item.getRabbitId());
            if (existing == null
                    || !Objects.equals(existing.getBatchIdSnapshot(), item.getBatchIdSnapshot())
                    || !Objects.equals(existing.getStateVersion(), item.getStateVersion())
                    || !Objects.equals(comparableSelectionType(existing.getSelectionType()),
                        comparableSelectionType(item.getSelectionType()))
                    || !Objects.equals(trim(existing.getEarlySaleReason()),
                        trim(item.getEarlySaleReason()))) {
                return false;
            }
        }
        return existingByRabbit.isEmpty();
    }

    private String comparableSelectionType(String value) {
        String normalized = trim(value);
        return normalized == null ? "NORMAL" : normalized.toUpperCase(Locale.ROOT);
    }

    private OutboundTaskBatchAllocation toAllocation(
        Long houseId,
        String taskId,
        SaleBatchAllocationInput input
    ) {
        OutboundTaskBatchAllocation allocation = new OutboundTaskBatchAllocation();
        allocation.setTaskId(taskId);
        allocation.setHouseId(houseId);
        allocation.setBatchId(input.batchId());
        allocation.setActualWeightKg(input.actualWeightKg());
        return allocation;
    }

    private OutboundDtos.TaskView view(OutboundTask task, boolean resumed) {
        List<OutboundCandidateRow> rows = eligibilityService.scopeRows(task.getHouseId(), task.getEntryType(),
                task.getSourceRabbitId(), task.getSourceCageId(), task.getSourceRowCode());
        List<OutboundDtos.RabbitEligibilityView> rabbits = eligibilityService.evaluate(rows);
        List<OutboundDtos.TaskItemView> selected = itemMapper.selectByTask(task.getTaskId()).stream()
                .map(item -> new OutboundDtos.TaskItemView(item.getRabbitId(), item.getStateVersion(),
                        item.getSelectionType(), item.getEarlySaleReason()))
                .toList();
        List<SaleBatchAllocationInput> allocations = allocationMapper.selectByTask(
                task.getHouseId(), task.getTaskId()).stream()
                .map(allocation -> new SaleBatchAllocationInput(
                    allocation.getBatchId(), allocation.getActualWeightKg()))
                .toList();
        return new OutboundDtos.TaskView(task.getTaskId(), task.getHouseId(), task.getEntryType(),
                task.getSourceRabbitId(), task.getSourceCageId(), task.getSourceRowCode(), task.getStatus(),
                task.getRevision(), task.getSaleTime(), task.getTotalWeight(), task.getUnitPrice(),
                task.getUnitPrice(), allocations, task.getCustomer(), task.getRemark(),
                task.getSaleOrderId(), resumed, eligibilityService.summary(rabbits), rabbits, selected);
    }

    private OutboundTaskItem toItem(String taskId, OutboundCandidateRow row, String selectionType,
                                    String reason, Long stateVersion) {
        OutboundTaskItem item = new OutboundTaskItem();
        item.setTaskId(taskId);
        item.setRabbitId(row.getRabbitId());
        item.setStateVersion(stateVersion);
        item.setSelectionType(selectionType);
        item.setEarlySaleReason(reason);
        item.setCageIdSnapshot(row.getCageId());
        item.setCageNumberSnapshot(row.getCageNumber() == null ? "#" + row.getCageId() : row.getCageNumber());
        item.setRowCodeSnapshot(row.getRowCode() == null ? "LEGACY" : row.getRowCode());
        item.setLayerIndexSnapshot(row.getLayerIndex());
        item.setPositionIndexSnapshot(row.getPositionIndex());
        item.setStageSnapshot(eligibilityService.displayStage(row));
        item.setBatchIdSnapshot(row.getBatchId());
        return item;
    }

    private Map<Long, OutboundCandidateRow> byId(List<OutboundCandidateRow> rows) {
        Map<Long, OutboundCandidateRow> result = new LinkedHashMap<>();
        for (OutboundCandidateRow row : rows) result.put(row.getRabbitId(), row);
        return result;
    }

    private Map<Long, Integer> batchGroupCounts(List<OutboundTaskItem> items) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (OutboundTaskItem item : items) {
            counts.merge(item.getBatchIdSnapshot(), 1, Integer::sum);
        }
        return counts;
    }

    private BigDecimal draftUnitPrice(OutboundDtos.SaveDraftRequest request) {
        BigDecimal legacy = request.unitPrice();
        BigDecimal current = request.unitPricePerKg();
        if (legacy != null && current != null && legacy.compareTo(current) != 0) {
            throw new BizException(400, "unitPrice与unitPricePerKg不一致");
        }
        BigDecimal value = current == null ? legacy : current;
        if (value == null) {
            return null;
        }
        boolean snapshotContract = current != null || request.batchAllocations() != null;
        if (snapshotContract) {
            return SaleBatchAllocationService.normalizeSnapshotPrice(value);
        }
        SaleBatchAllocationService.orderAmount(BigDecimal.ONE, value);
        return value;
    }

    private BigDecimal draftTotalWeight(Double value) {
        if (value == null) {
            return null;
        }
        if (!Double.isFinite(value) || value <= 0 || value > 100000) {
            throw new BizException(400, "totalWeight不合法");
        }
        return BigDecimal.valueOf(value);
    }

    private List<Long> uniqueIds(List<OutboundDtos.SelectedRabbitInput> inputs) {
        Set<Long> ids = new LinkedHashSet<>();
        for (OutboundDtos.SelectedRabbitInput input : inputs) {
            if (input.rabbitId() == null || input.rabbitId() <= 0 || !ids.add(input.rabbitId())) {
                throw new BizException(400, "rabbitIds包含无效或重复值");
            }
        }
        return new ArrayList<>(ids);
    }

    private String normalizeEntryType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("RABBIT", "CAGE", "ROW", "HOUSE").contains(normalized)) {
            throw new BizException(400, "entryType不支持");
        }
        return normalized;
    }

    private void validateSource(String type, Long rabbitId, Long cageId, String rowCode) {
        if ("RABBIT".equals(type) && (rabbitId == null || rabbitId <= 0)) throw new BizException(400, "rabbitId不能为空");
        if ("CAGE".equals(type) && (cageId == null || cageId <= 0)) throw new BizException(400, "cageId不能为空");
        if ("ROW".equals(type) && trim(rowCode) == null) throw new BizException(400, "rowCode不能为空");
    }

    private String normalizeDraftStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SELECTING", "WAITING_CONFIRMATION").contains(normalized)) {
            throw new BizException(400, "status不支持");
        }
        return normalized;
    }

    private String normalizeSelectionType(String value) {
        String normalized = value == null || value.isBlank() ? "NORMAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NORMAL", "EARLY_SALE").contains(normalized)) throw new BizException(400, "selectionType不支持");
        return normalized;
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
