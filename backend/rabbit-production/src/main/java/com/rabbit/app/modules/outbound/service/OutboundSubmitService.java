package com.rabbit.app.modules.outbound.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import com.rabbit.app.modules.outbound.entity.OutboundTask;
import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskItemMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskMapper;
import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import com.rabbit.app.modules.sale.entity.SaleOrder;
import com.rabbit.app.modules.sale.entity.SaleOrderItem;
import com.rabbit.app.modules.sale.mapper.SaleOrderItemMapper;
import com.rabbit.app.modules.sale.mapper.SaleOrderMapper;
import com.rabbit.app.util.RequestIdUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundSubmitService {
    private static final int SALE_ITEM_WRITE_CHUNK_SIZE = 1_000;

    private final OutboundTaskMapper taskMapper;
    private final OutboundTaskItemMapper taskItemMapper;
    private final OutboundEligibilityService eligibilityService;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final RabbitMapper rabbitMapper;
    private final RabbitDepartureRecordMapper departureMapper;
    private final RabbitStatusHistoryMapper historyMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final BatchMapper batchMapper;
    private final CageMapper cageMapper;
    private final HouseService houseService;
    private final ObjectMapper objectMapper;
    private final WorkTaskWriter workTaskWriter;

    public OutboundSubmitService(OutboundTaskMapper taskMapper, OutboundTaskItemMapper taskItemMapper,
                                 OutboundEligibilityService eligibilityService,
                                 SaleOrderMapper saleOrderMapper, SaleOrderItemMapper saleOrderItemMapper,
                                 RabbitMapper rabbitMapper, RabbitDepartureRecordMapper departureMapper,
                                 RabbitStatusHistoryMapper historyMapper, BatchRabbitMapper batchRabbitMapper,
                                 BatchMapper batchMapper, CageMapper cageMapper, HouseService houseService,
                                 ObjectMapper objectMapper, WorkTaskWriter workTaskWriter) {
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.eligibilityService = eligibilityService;
        this.saleOrderMapper = saleOrderMapper;
        this.saleOrderItemMapper = saleOrderItemMapper;
        this.rabbitMapper = rabbitMapper;
        this.departureMapper = departureMapper;
        this.historyMapper = historyMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.batchMapper = batchMapper;
        this.cageMapper = cageMapper;
        this.houseService = houseService;
        this.objectMapper = objectMapper;
        this.workTaskWriter = workTaskWriter;
    }

    @Transactional
    public OutboundDtos.SubmitResult executeClaimed(Long userId, Long houseId, String taskId,
                                                    OutboundDtos.SubmitRequest input) {
        validateForm(input);
        List<Long> rabbitIds = normalizedIds(input.rabbitIds());

        OutboundTask task = taskMapper.selectByIdForUpdate(houseId, userId, taskId);
        if (task == null) throw new BizException(404, "OUTBOUND_TASK_NOT_FOUND");
        if (!"WAITING_CONFIRMATION".equals(task.getStatus())) {
            if ("COMPLETED".equals(task.getStatus()) && task.getSaleOrderId() != null) {
                throw new BizException(409, "OUTBOUND_TASK_COMPLETED_USE_ORIGINAL_REQUEST");
            }
            throw new BizException(409, "任务尚未冻结或正在处理");
        }
        if (taskMapper.markSubmitting(houseId, userId, taskId, input.requestId()) == 0) {
            throw new BizException(409, "任务状态已变化");
        }

        List<OutboundTaskItem> frozenItems = taskItemMapper.selectByTask(taskId);
        assertFrozenPayload(frozenItems, rabbitIds, input);
        assertSpecialActionPermission(userId, houseId, frozenItems);
        List<Long> locked = eligibilityService.lockRabbitIds(houseId, rabbitIds);
        List<Long> cageIds = frozenItems.stream().map(OutboundTaskItem::getCageIdSnapshot)
                .filter(Objects::nonNull).distinct().sorted().toList();
        List<Long> lockedCages = cageMapper.lockIds(houseId, cageIds);
        List<OutboundDtos.RabbitConflict> conflicts = new ArrayList<>();
        if (locked.size() != rabbitIds.size()) {
            Set<Long> lockedSet = new HashSet<>(locked);
            for (Long rabbitId : rabbitIds) {
                if (!lockedSet.contains(rabbitId)) {
                    conflicts.add(conflict(rabbitId, "RABBIT_NOT_PRESENT", "不存在", "兔只不存在或不在当前兔舍", "移出本次出库"));
                }
            }
        }
        if (lockedCages.size() != cageIds.size()) {
            Set<Long> lockedCageIds = new HashSet<>(lockedCages);
            for (OutboundTaskItem item : frozenItems) {
                if (!lockedCageIds.contains(item.getCageIdSnapshot())) {
                    conflicts.add(conflict(item.getRabbitId(), "CAGE_NOT_PRESENT", "笼位不存在",
                            "冻结快照中的笼位已不存在", "刷新并确认当前位置"));
                }
            }
        }

        Map<Long, OutboundCandidateRow> candidateById = new LinkedHashMap<>();
        for (OutboundCandidateRow row : eligibilityService.rowsByIds(houseId, rabbitIds)) {
            candidateById.put(row.getRabbitId(), row);
        }
        Map<Long, OutboundTaskItem> frozenById = new LinkedHashMap<>();
        for (OutboundTaskItem item : frozenItems) frozenById.put(item.getRabbitId(), item);

        for (Long rabbitId : rabbitIds) {
            OutboundTaskItem frozen = frozenById.get(rabbitId);
            OutboundCandidateRow row = candidateById.get(rabbitId);
            if (frozen == null || row == null) continue;
            OutboundDtos.RabbitEligibilityView current = eligibilityService.evaluate(row);
            Long requestedVersion = input.stateVersions().get(String.valueOf(rabbitId));
            if (!Objects.equals(frozen.getStateVersion(), requestedVersion)
                    || !Objects.equals(row.getStateVersion(), requestedVersion)) {
                conflicts.add(conflict(rabbitId, "RABBIT_STATE_CHANGED", current.stage(),
                        "兔只状态版本已变化", "刷新并重新选择"));
                continue;
            }
            if (!Objects.equals(frozen.getCageIdSnapshot(), row.getCageId())
                    || !Objects.equals(frozen.getCageNumberSnapshot(), row.getCageNumber())
                    || !Objects.equals(frozen.getRowCodeSnapshot(), row.getRowCode())
                    || !Objects.equals(frozen.getLayerIndexSnapshot(), row.getLayerIndex())
                    || !Objects.equals(frozen.getPositionIndexSnapshot(), row.getPositionIndex())) {
                conflicts.add(conflict(rabbitId, "RABBIT_LOCATION_CHANGED", current.stage(),
                        "兔只笼位或笼位坐标已变化", "刷新并确认当前位置"));
                continue;
            }
            if ("NORMAL".equals(frozen.getSelectionType())
                    && !OutboundEligibilityService.NORMAL.equals(current.eligibility())) {
                conflicts.add(conflict(rabbitId, current.reasonCode(), current.stage(), current.message(), current.recommendedAction()));
            } else if ("EARLY_SALE".equals(frozen.getSelectionType())
                    && !Set.of(OutboundEligibilityService.EARLY_SALE, OutboundEligibilityService.NORMAL).contains(current.eligibility())) {
                conflicts.add(conflict(rabbitId, current.reasonCode(), current.stage(), current.message(), current.recommendedAction()));
            }
        }
        if (!conflicts.isEmpty()) {
            if (taskMapper.restoreWaiting(houseId, userId, taskId, input.requestId()) == 0) {
                throw new BizException(409, "任务状态已变化");
            }
            return conflictResult(input.requestId(), taskId, conflicts);
        }

        SaleOrder order = createOrder(userId, houseId, input);
        List<SaleOrderItem> saleItems = createSaleItems(userId, order.getId(), frozenItems, candidateById, input.unitPrice());
        insertSaleItems(saleItems);

        String operator = String.valueOf(userId);
        Date now = input.saleTime();
        Map<Long, Integer> cageDeltas = new HashMap<>();
        Set<Long> touchedBatches = new LinkedHashSet<>();
        for (OutboundTaskItem frozen : frozenItems) {
            Long rabbitId = frozen.getRabbitId();
            if (rabbitMapper.updateDepartureIfVersion(houseId, rabbitId, frozen.getStateVersion(), now, "sale", operator) == 0) {
                throw new BizException(409, "RABBIT_STATE_CHANGED: " + rabbitId);
            }
            List<BatchRabbit> links = batchRabbitMapper.selectActiveByRabbitForUpdate(houseId, rabbitId);
            for (BatchRabbit link : links) {
                if (batchRabbitMapper.deactivateIfActive(houseId, link.getId(), now, "批量出库", operator) == 0) {
                    throw new BizException(409, "批次状态已变化: " + rabbitId);
                }
                touchedBatches.add(link.getBatchId());
            }
            cageDeltas.merge(frozen.getCageIdSnapshot(), 1, Integer::sum);
            insertDepartureAndHistory(houseId, rabbitId, frozen, order.getId(), now, operator, input.requestId());
            workTaskWriter.completeForRabbit(houseId, rabbitId, TaskType.SALE_READY, operator);
            workTaskWriter.cancelAllForRabbit(houseId, rabbitId, operator);
        }
        for (Map.Entry<Long, Integer> entry : cageDeltas.entrySet()) {
            if (cageMapper.decrementRabbitCount(houseId, entry.getKey(), entry.getValue(), operator) == 0) {
                throw new BizException(409, "笼位状态已变化: " + entry.getKey());
            }
        }
        for (Long batchId : touchedBatches) {
            if (batchRabbitMapper.countActiveByBatch(batchId) == 0) {
                Batch batch = batchMapper.selectById(houseId, batchId);
                if (batch != null) {
                    batchMapper.updateStatusAndDates(houseId, batchId, "已完成", batch.getStartDate(), now, operator);
                }
            }
        }

        if (taskMapper.markCompleted(houseId, userId, taskId, input.requestId(), order.getId(), new Date()) == 0) {
            throw new BizException(409, "任务完成状态写入失败");
        }
        OutboundTask completed = taskMapper.selectById(houseId, userId, taskId);
        return completedResult(completed, order, frozenItems);
    }

    private void assertFrozenPayload(List<OutboundTaskItem> items, List<Long> rabbitIds, OutboundDtos.SubmitRequest input) {
        Set<Long> frozenIds = new LinkedHashSet<>();
        for (OutboundTaskItem item : items) frozenIds.add(item.getRabbitId());
        if (!frozenIds.equals(new LinkedHashSet<>(rabbitIds))) {
            throw new BizException(409, "提交兔只与冻结快照不一致");
        }
        Set<String> expectedVersionKeys = new HashSet<>();
        for (Long rabbitId : rabbitIds) expectedVersionKeys.add(String.valueOf(rabbitId));
        if (!expectedVersionKeys.equals(input.stateVersions().keySet())) {
            throw new BizException(400, "stateVersions必须与rabbitIds完全一致");
        }
        if (input.earlySaleReasons() != null && !expectedVersionKeys.containsAll(input.earlySaleReasons().keySet())) {
            throw new BizException(400, "earlySaleReasons包含未选择的兔只");
        }
        for (OutboundTaskItem item : items) {
            Long version = input.stateVersions().get(String.valueOf(item.getRabbitId()));
            if (version == null) throw new BizException(400, "缺少stateVersion: " + item.getRabbitId());
            String reason = trim(input.earlySaleReasons() == null ? null
                    : input.earlySaleReasons().get(String.valueOf(item.getRabbitId())));
            if ("EARLY_SALE".equals(item.getSelectionType())) {
                if (reason == null) throw new BizException(400, "EARLY_SALE_REASON_REQUIRED: " + item.getRabbitId());
                if (!reason.equals(trim(item.getEarlySaleReason()))) {
                    throw new BizException(409, "提前出售原因与冻结快照不一致: " + item.getRabbitId());
                }
            } else if (reason != null) {
                throw new BizException(400, "正常出售兔只不能携带提前出售原因: " + item.getRabbitId());
            }
        }
    }

    private SaleOrder createOrder(Long userId, Long houseId, OutboundDtos.SubmitRequest input) {
        SaleOrder order = new SaleOrder();
        order.setHouseId(houseId);
        order.setSaleTime(input.saleTime());
        order.setCustomer(trim(input.customer()));
        order.setTotalWeight(input.totalWeight());
        order.setUnitPrice(input.unitPrice());
        if (input.unitPrice() != null) {
            order.setTotalAmount(input.unitPrice().multiply(BigDecimal.valueOf(input.totalWeight())));
        }
        order.setRemark(trim(input.remark()));
        order.setRequestId(input.requestId());
        saleOrderMapper.insert(order);
        return order;
    }

    private List<SaleOrderItem> createSaleItems(Long userId, Long orderId, List<OutboundTaskItem> frozenItems,
                                                Map<Long, OutboundCandidateRow> rows, BigDecimal unitPrice) {
        List<SaleOrderItem> result = new ArrayList<>();
        String operator = String.valueOf(userId);
        for (OutboundTaskItem frozen : frozenItems) {
            OutboundCandidateRow row = rows.get(frozen.getRabbitId());
            SaleOrderItem item = new SaleOrderItem();
            item.setSaleOrderId(orderId);
            item.setRabbitId(frozen.getRabbitId());
            item.setCageIdSnapshot(frozen.getCageIdSnapshot());
            item.setCageNumberSnapshot(frozen.getCageNumberSnapshot());
            item.setRowCodeSnapshot(frozen.getRowCodeSnapshot());
            item.setLayerIndexSnapshot(frozen.getLayerIndexSnapshot());
            item.setPositionIndexSnapshot(frozen.getPositionIndexSnapshot());
            item.setRabbitTypeSnapshot(row.getRabbitType());
            item.setStageSnapshot(frozen.getStageSnapshot());
            item.setParallelStatusSnapshot("quarantine=" + Boolean.TRUE.equals(row.getQuarantined())
                    + ";treatment=" + Boolean.TRUE.equals(row.getOpenTreatment())
                    + ";abnormal=" + Boolean.TRUE.equals(row.getUnresolvedAbnormal()));
            item.setStateVersionSnapshot(frozen.getStateVersion());
            item.setEarlySale("EARLY_SALE".equals(frozen.getSelectionType()));
            item.setEarlySaleReason(frozen.getEarlySaleReason());
            item.setBatchIdSnapshot(frozen.getBatchIdSnapshot());
            item.setWeight(row.getWeight());
            item.setPrice(unitPrice);
            result.add(item);
        }
        return result;
    }

    private void insertSaleItems(List<SaleOrderItem> items) {
        for (int start = 0; start < items.size(); start += SALE_ITEM_WRITE_CHUNK_SIZE) {
            int end = Math.min(start + SALE_ITEM_WRITE_CHUNK_SIZE, items.size());
            saleOrderItemMapper.insertBatch(items.subList(start, end));
        }
    }

    private void insertDepartureAndHistory(Long houseId, Long rabbitId, OutboundTaskItem item, Long orderId,
                                           Date saleTime, String operator, String requestId) {
        RabbitDepartureRecord departure = new RabbitDepartureRecord();
        departure.setHouseId(houseId);
        departure.setRabbitId(rabbitId);
        departure.setDepartureType("sale");
        departure.setDepartureDate(saleTime);
        departure.setReason("EARLY_SALE".equals(item.getSelectionType()) ? item.getEarlySaleReason() : "批量销售出栏");
        departure.setRemark("saleOrder#" + orderId);
        departure.setRequestId(RequestIdUtil.deriveChild(requestId, rabbitId));
        departureMapper.insert(departure);

        RabbitStatusHistory history = new RabbitStatusHistory();
        history.setHouseId(houseId);
        history.setRabbitId(rabbitId);
        history.setBatchId(item.getBatchIdSnapshot());
        history.setFromStatus(item.getStageSnapshot());
        history.setToStatus("出售出栏");
        history.setChangeTime(saleTime);
        history.setReason(departure.getReason());
        history.setRelatedRecordId(departure.getId());
        history.setRelatedRecordTable("rabbit_departure_records");
        historyMapper.insert(history);
    }

    OutboundDtos.SubmitResult completedResult(OutboundTask task, SaleOrder order, List<OutboundTaskItem> items) {
        if (order == null) throw new BizException(500, "销售单回查失败");
        Set<Long> cages = new HashSet<>();
        Set<String> rows = new HashSet<>();
        for (OutboundTaskItem item : items) {
            cages.add(item.getCageIdSnapshot());
            rows.add(item.getRowCodeSnapshot());
        }
        return new OutboundDtos.SubmitResult("COMPLETED", order.getRequestId(), task.getTaskId(), order.getId(),
                "SO-" + order.getId(), order.getSaleTime(), items.size(), cages.size(), rows.size(),
                order.getTotalWeight(), order.getTotalAmount(), null, "本次出库已完成", List.of());
    }

    private OutboundDtos.SubmitResult conflictResult(String requestId, String taskId, List<OutboundDtos.RabbitConflict> conflicts) {
        return new OutboundDtos.SubmitResult("CONFLICT", requestId, taskId, null, null, null, 0, 0, 0,
                null, null, "RABBIT_PRECHECK_CONFLICT", "本次出库未生效，草稿已保留", conflicts);
    }

    private OutboundDtos.RabbitConflict conflict(Long rabbitId, String code, String state, String message, String action) {
        return new OutboundDtos.RabbitConflict(rabbitId, code, state, message, action);
    }

    PreparedSubmission prepare(String taskId, OutboundDtos.SubmitRequest input) {
        validateForm(input);
        List<Long> rabbitIds = normalizedIds(input.rabbitIds());
        return new PreparedSubmission(rabbitIds, payloadHash(taskId, rabbitIds, input));
    }

    public void assertRequestPermission(Long userId, Long houseId, String taskId) {
        OutboundTask task = taskMapper.selectById(houseId, userId, taskId);
        if (task == null) {
            throw new BizException(404, "OUTBOUND_TASK_NOT_FOUND");
        }
        assertSpecialActionPermission(userId, houseId, taskItemMapper.selectByTask(taskId));
    }

    private void validateForm(OutboundDtos.SubmitRequest input) {
        validateRequestId(input.requestId());
        if (input.totalWeight() == null || !Double.isFinite(input.totalWeight())
                || input.totalWeight() <= 0 || input.totalWeight() > 100000) {
            throw new BizException(400, "totalWeight不合法");
        }
        if (input.unitPrice() != null) {
            if (input.unitPrice().compareTo(BigDecimal.ZERO) < 0
                    || input.unitPrice().compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new BizException(400, "unitPrice不合法");
            }
            BigDecimal amount = input.unitPrice().multiply(BigDecimal.valueOf(input.totalWeight()));
            if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw new BizException(400, "预计总金额超出允许范围");
            }
        }
        LocalDate saleDate = input.saleTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        if (saleDate.isAfter(today) || saleDate.isBefore(today.minusDays(30))) {
            throw new BizException(400, "出库日期仅允许今天及过去30天");
        }
        if (input.customer() != null && input.customer().trim().length() > 100) throw new BizException(400, "客户名称过长");
        if (input.remark() != null && input.remark().trim().length() > 2000) throw new BizException(400, "备注过长");
    }

    private List<Long> normalizedIds(List<Long> values) {
        if (values == null || values.isEmpty()) throw new BizException(400, "rabbitIds不能为空");
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null || value <= 0 || !unique.add(value)) throw new BizException(400, "rabbitIds包含无效或重复值");
        }
        return unique.stream().sorted().toList();
    }

    private String payloadHash(String taskId, List<Long> ids, OutboundDtos.SubmitRequest input) {
        StringBuilder canonical = new StringBuilder(taskId).append('|');
        for (Long id : ids) {
            canonical.append(id).append(':').append(input.stateVersions().get(String.valueOf(id))).append(':')
                    .append(trim(input.earlySaleReasons() == null ? null : input.earlySaleReasons().get(String.valueOf(id))))
                    .append(';');
        }
        canonical.append('|').append(input.saleTime().getTime()).append('|').append(input.totalWeight())
                .append('|').append(input.unitPrice()).append('|').append(trim(input.customer())).append('|').append(trim(input.remark()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void assertSpecialActionPermission(Long userId, Long houseId, List<OutboundTaskItem> items) {
        if (requiresControl(items)) {
            houseService.assertHousePermission(userId, houseId, "control");
        }
    }

    static boolean requiresControl(List<OutboundTaskItem> items) {
        return items.stream().anyMatch(item -> "EARLY_SALE".equals(item.getSelectionType()));
    }

    static void validateRequestId(String requestId) {
        if (requestId == null) throw new BizException(400, "requestId不能为空");
        try {
            if (!UUID.fromString(requestId).toString().equals(requestId)) {
                throw new BizException(400, "requestId必须是规范UUID");
            }
        } catch (IllegalArgumentException error) {
            throw new BizException(400, "requestId必须是规范UUID");
        }
    }

    String serializeConflicts(List<OutboundDtos.RabbitConflict> conflicts) {
        try {
            return objectMapper.writeValueAsString(conflicts);
        } catch (JsonProcessingException error) {
            throw new BizException(500, "出库冲突结果序列化失败");
        }
    }

    List<OutboundDtos.RabbitConflict> deserializeConflicts(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(500, "出库冲突结果缺失");
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<OutboundDtos.RabbitConflict>>() {});
        } catch (JsonProcessingException error) {
            throw new BizException(500, "出库冲突结果解析失败");
        }
    }

    record PreparedSubmission(List<Long> rabbitIds, String payloadHash) {}
}
