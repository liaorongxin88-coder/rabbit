package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.BreedingCycle;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import com.rabbit.app.modules.batch.mapper.ParturitionRecordMapper;
import com.rabbit.app.modules.batch.mapper.PregnancyCheckRecordMapper;
import com.rabbit.app.modules.batch.mapper.PrepartumRecordMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordAllocationMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.service.OutboundEligibilityService;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import com.rabbit.app.util.RequestIdUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {

    private static final int BULK_WRITE_SIZE = 500;

    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final BreedingCycleMapper breedingCycleMapper;
    private final RabbitMapper rabbitMapper;
    private final SettingService settingService;
    private final PregnancyCheckRecordMapper pregnancyCheckRecordMapper;
    private final ParturitionRecordMapper parturitionRecordMapper;
    private final PrepartumRecordMapper prepartumRecordMapper;
    private final WeaningRecordMapper weaningRecordMapper;
    private final WeaningRecordAllocationMapper weaningRecordAllocationMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final BreedingPerformanceMapper breedingPerformanceMapper;
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;
    private final CageMapper cageMapper;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final RequestDedupService requestDedupService;
    private final OutboundEligibilityService outboundEligibilityService;
    private final int commodityCageCapacity;

    private final WorkTaskWriter workTaskWriter;

    public BatchService(
        BatchMapper batchMapper,
        BatchRabbitMapper batchRabbitMapper,
        BreedingCycleMapper breedingCycleMapper,
        RabbitMapper rabbitMapper,
        SettingService settingService,
        PregnancyCheckRecordMapper pregnancyCheckRecordMapper,
        ParturitionRecordMapper parturitionRecordMapper,
        PrepartumRecordMapper prepartumRecordMapper,
        WeaningRecordMapper weaningRecordMapper,
        WeaningRecordAllocationMapper weaningRecordAllocationMapper,
        RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
        BreedingPerformanceMapper breedingPerformanceMapper,
        RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper,
        RabbitDepartureRecordMapper rabbitDepartureRecordMapper,
        CageMapper cageMapper,
        ReplacementRecordMapper replacementRecordMapper,
        RequestDedupService requestDedupService,
        OutboundEligibilityService outboundEligibilityService,
        WorkTaskWriter workTaskWriter,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.workTaskWriter = workTaskWriter;
        this.batchMapper = batchMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.breedingCycleMapper = breedingCycleMapper;
        this.rabbitMapper = rabbitMapper;
        this.settingService = settingService;
        this.pregnancyCheckRecordMapper = pregnancyCheckRecordMapper;
        this.parturitionRecordMapper = parturitionRecordMapper;
        this.prepartumRecordMapper = prepartumRecordMapper;
        this.weaningRecordMapper = weaningRecordMapper;
        this.weaningRecordAllocationMapper = weaningRecordAllocationMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.breedingPerformanceMapper = breedingPerformanceMapper;
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
        this.cageMapper = cageMapper;
        this.replacementRecordMapper = replacementRecordMapper;
        this.requestDedupService = requestDedupService;
        this.outboundEligibilityService = outboundEligibilityService;
        this.commodityCageCapacity =
            commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
    }

    public List<Batch> listBatches(Long houseId) {
        return batchMapper.selectByHouse(houseId);
    }

    public List<Batch> listBatchesPage(
        Long houseId,
        String q,
        int page,
        int pageSize
    ) {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
        int offset = (page - 1) * pageSize;
        return batchMapper.selectPageByHouse(houseId, q, offset, pageSize);
    }

    public Batch getBatch(Long houseId, Long id) {
        return batchMapper.selectById(houseId, id);
    }

    public List<BreedingCycle> listBreedingCycles(
        Long houseId,
        Long batchId,
        Long motherRabbitId,
        Boolean activeOnly
    ) {
        requireBatch(houseId, batchId);
        return breedingCycleMapper.selectByBatch(
            houseId,
            batchId,
            motherRabbitId,
            activeOnly
        );
    }

    @TrackedOperation(
        code = "batch.create", eventType = "BATCH_CREATED", targetType = "BATCH",
        targetId = "#result.id"
    )
    @Transactional
    public Batch createBatch(
        Long userId,
        Long houseId,
        String batchCode,
        List<Long> femaleRabbitIds,
        String remark,
        String requestId
    ) {
        String api = "batch.create";
        Batch existing = batchMapper.selectByHouseAndRequestId(
            houseId,
            requestId
        );
        if (existing != null) {
            return existing;
        }
        if (
            requestDedupService.shouldSkipAsDone(
                houseId,
                userId,
                api,
                requestId
            )
        ) {
            Batch done = batchMapper.selectByHouseAndRequestId(
                houseId,
                requestId
            );
            if (done == null) {
                throw new BizException(500, "幂等回查失败");
            }
            return done;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch done = batchMapper.selectByHouseAndRequestId(
                houseId,
                requestId
            );
            if (done != null) {
                requestDedupService.markDone(houseId, userId, api, requestId);
                return done;
            }

            // 新口径：允许先建空批次，母兔随后再追加。
            // 建批时必须凑齐母兔曾是一道无谓的门槛:用户往往先拉一个批次,再陆续把到期的兔只放进去。
            List<Rabbit> members = lockAndValidateRabbits(houseId, femaleRabbitIds);

            Date now = DateUtil.now();

            Batch b = new Batch();
            b.setHouseId(houseId);
            b.setBatchCode(batchCode);
            b.setStatus("进行中");
            b.setStartDate(now);
            b.setRemark(remark);
            b.setRequestId(requestId);
            try {
                batchMapper.insert(b);
            } catch (DuplicateKeyException e) {
                Batch dup = batchMapper.selectByHouseAndRequestId(
                    houseId,
                    requestId
                );
                if (dup != null) {
                    requestDedupService.markDone(
                        houseId,
                        userId,
                        api,
                        requestId
                    );
                    return dup;
                }
                throw e;
            }

            joinMembers(userId, houseId, b.getId(), members, now, requestId);

            requestDedupService.markDone(houseId, userId, api, requestId);
            return b;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(
                houseId,
                userId,
                api,
                requestId,
                e.getMessage()
            );
            throw e;
        }
    }

    /**
     * 锁住并校验一批待加入的兔只；空列表合法，直接返回空。
     */
    private List<Rabbit> lockAndValidateRabbits(Long houseId, List<Long> rabbitIds) {
        if (rabbitIds == null || rabbitIds.isEmpty()) {
            return new ArrayList<Rabbit>();
        }
        List<Long> requestedIds = new ArrayList<Long>(new LinkedHashSet<Long>(rabbitIds));
        if (requestedIds.size() != rabbitIds.size()) {
            throw new BizException(400, "兔只列表包含重复项");
        }
        // Rabbit rows serialize concurrent tag writes. A rabbit may carry
        // several active batch tags, but the same tag cannot be added twice.
        requestedIds.sort(Long::compareTo);
        List<Rabbit> rabbits = rabbitMapper.selectByIdsForUpdate(houseId, requestedIds);
        if (rabbits.size() != requestedIds.size()) {
            throw new BizException(400, "兔只不存在");
        }
        return rabbits;
    }

    private void requireNotAlreadyInBatch(
        Long houseId,
        Long batchId,
        List<Long> rabbitIds
    ) {
        if (!batchRabbitMapper.selectActiveByBatchAndRabbitsForUpdate(
            houseId, batchId, rabbitIds
        ).isEmpty()) {
            throw new BizException(409, "兔只已绑定该批次");
        }
    }

    /**
     * 建立批次成员关系。繁育母兔进入待催情，商品兔只建立养育/售卖关系。
     *
     * <p>建批与追加成员走同一条路径；只有繁育关系会创建生产周期，商品兔绝不能
     * 被误送进母兔状态机。
     */
    private void joinMembers(
        Long userId,
        Long houseId,
        Long batchId,
        List<Rabbit> rabbits,
        Date now,
        String requestId
    ) {
        if (rabbits.isEmpty()) {
            return;
        }
        List<BatchRabbit> links = new ArrayList<BatchRabbit>(rabbits.size());
        List<RabbitStatusHistory> histories =
            new ArrayList<RabbitStatusHistory>(rabbits.size());
        for (Rabbit rabbit : rabbits) {
            Long rabbitId = rabbit.getId();
            if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
                throw new BizException(400, "兔只不在场");
            }
            boolean breedingFemale = "0".equals(rabbit.getGender())
                && ("0".equals(rabbit.getType()) || "1".equals(rabbit.getType()));
            boolean commodityRabbit = "2".equals(rabbit.getType());
            if (!breedingFemale && !commodityRabbit) {
                throw new BizException(400, "仅种母兔、后备母兔或商品兔可加入批次");
            }

            String role = breedingFemale ? "breeding" : "fattening";
            String status = breedingFemale ? "待催情" : "成长期";
            String joinReason = breedingFemale ? "配种" : "养育/售卖";

            BatchRabbit link = new BatchRabbit();
            link.setBatchId(batchId);
            link.setRabbitId(rabbitId);
            link.setJoinReason(joinReason);
            link.setBatchRole(role);
            link.setCurrentStatus(status);
            link.setIsActive(Boolean.TRUE);
            link.setJoinDate(now);
            links.add(link);

            RabbitStatusHistory history = new RabbitStatusHistory();
            history.setHouseId(houseId);
            history.setRabbitId(rabbitId);
            history.setBatchId(batchId);
            history.setFromStatus(null);
            history.setToStatus(status);
            history.setChangeTime(now);
            history.setReason("加入批次");
            histories.add(history);

        }
        insertBatchRabbitLinks(links);
        insertStatusHistories(histories);
    }

    /**
     * 向已存在的批次追加兔只，并按兔只类型派生批次用途。
     */
    @TrackedOperation(
        code = "batch.addMembers", eventType = "BATCH_MEMBERS_ADDED", batchId = "#batchId",
        targetType = "BATCH", targetId = "#batchId"
    )
    @Transactional
    public void addMembers(
        Long userId,
        Long houseId,
        Long batchId,
        List<Long> rabbitIds,
        String requestId
    ) {
        String api = "batch.addMembers";
        if (rabbitIds == null || rabbitIds.isEmpty()) {
            throw new BizException(400, "兔只列表不能为空");
        }
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch batch = batchMapper.selectByIdForUpdate(houseId, batchId);
            if (batch == null) {
                throw new BizException(404, "批次不存在");
            }
            if (!"进行中".equals(batch.getStatus())) {
                throw new BizException(409, "批次已结束，无法再加入兔只");
            }
            List<Rabbit> rabbits = lockAndValidateRabbits(houseId, rabbitIds);
            requireNotAlreadyInBatch(houseId, batchId, rabbitIds);
            joinMembers(userId, houseId, batchId, rabbits, DateUtil.now(), requestId);
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    /**
     * 把刚加入批次的母兔送进生产流水线（待催情）。
     *
     * <p>旧模型里「加入批次」只写 batch_rabbits.current_status = 待催情，那个字段本身就是状态；
     * doe-breeding-v2 之后状态在 breeding_cycles 上，批次只是个标签。如果这里不开周期，
     * 母兔就既没有阶段也没有待办，整条生产流程从界面上根本无法开始。
     *
     * <p>已有未归属批次的流水线周期会原子绑定到新批次并同步其待办；已有其他批次
     * 归属的周期保持不动，避免新标签篡改旧批次的结束语义。
     *
     */
    @TrackedOperation(
        code = "batch.removeMember", eventType = "BATCH_MEMBER_REMOVED", batchId = "#batchId",
        targetType = "BATCH", targetId = "#batchId", rabbitId = "#rabbitId"
    )
    @Transactional
    public void removeMember(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        String requestId
    ) {
        String api = "batch.removeMember";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (batchMapper.selectByIdForUpdate(houseId, batchId) == null) {
                throw new BizException(404, "批次不存在");
            }
            BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId, batchId, rabbitId
            );
            if (link != null) {
                if ("breeding".equals(link.getBatchRole())
                    && breedingCycleMapper.countOpenLifecycleByBatchAndMother(
                        houseId, batchId, rabbitId
                    ) > 0) {
                    throw new BizException(409, "母兔仍有绑定该批次的进行中生产周期，无法移除");
                }
                int updated = batchRabbitMapper.deactivateIfActive(
                    houseId,
                    link.getId(),
                    DateUtil.now(),
                    "手动移除批次标签",
                    String.valueOf(userId)
                );
                if (updated <= 0) {
                    throw new BizException(409, "批次标签已变化，请刷新后重试");
                }
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void insertBatchRabbitLinks(List<BatchRabbit> links) {
        for (int from = 0; from < links.size(); from += BULK_WRITE_SIZE) {
            int to = Math.min(from + BULK_WRITE_SIZE, links.size());
            batchRabbitMapper.insertBatch(links.subList(from, to));
        }
    }

    private void insertStatusHistories(List<RabbitStatusHistory> histories) {
        for (int from = 0; from < histories.size(); from += BULK_WRITE_SIZE) {
            int to = Math.min(from + BULK_WRITE_SIZE, histories.size());
            rabbitStatusHistoryMapper.insertBatch(histories.subList(from, to));
        }
    }

    private void insertRabbitsAndHydrateIds(List<Rabbit> rabbits) {
        for (int from = 0; from < rabbits.size(); from += BULK_WRITE_SIZE) {
            int to = Math.min(from + BULK_WRITE_SIZE, rabbits.size());
            List<Rabbit> chunk = rabbits.subList(from, to);
            if (rabbitMapper.insertBatch(chunk) != chunk.size()) {
                throw new BizException(500, "仔兔批量写入失败");
            }
            List<String> requestIds = new ArrayList<String>(chunk.size());
            for (Rabbit rabbit : chunk) {
                requestIds.add(rabbit.getRequestId());
            }
            Map<String, Rabbit> persisted = new HashMap<String, Rabbit>();
            for (Rabbit rabbit : rabbitMapper.selectByHouseAndRequestIds(
                chunk.get(0).getHouseId(),
                requestIds
            )) {
                persisted.put(rabbit.getRequestId(), rabbit);
            }
            if (persisted.size() != chunk.size()) {
                throw new BizException(500, "仔兔批量写入回查失败");
            }
            for (Rabbit rabbit : chunk) {
                Rabbit saved = persisted.get(rabbit.getRequestId());
                if (saved == null || saved.getId() == null) {
                    throw new BizException(500, "仔兔主键回查失败");
                }
                rabbit.setId(saved.getId());
            }
        }
    }

    private List<WeaningRecordAllocation> pickCommodityCageAllocations(
        Long houseId,
        int count
    ) {
        List<Cage> candidates = cageMapper.selectCommodityCagesForUpdate(houseId);
        List<WeaningRecordAllocation> rows =
            new ArrayList<WeaningRecordAllocation>();
        int left = count;
        for (Cage c : candidates) {
            if (!"0".equals(c.getStatus())) {
                continue;
            }
            left = allocToCage(c, left, rows);
            if (left <= 0) {
                return rows;
            }
        }
        for (Cage c : candidates) {
            if (!"3".equals(c.getStatus())) {
                continue;
            }
            left = allocToCage(c, left, rows);
            if (left <= 0) {
                return rows;
            }
        }
        throw new BizException(400, "没有可用商品兔笼位");
    }

    private int allocToCage(
        Cage c,
        int left,
        List<WeaningRecordAllocation> rows
    ) {
        int used = c.getRabbitCount() == null ? 0 : c.getRabbitCount();
        int cap = commodityCageCapacity;
        int remain = cap - used;
        if (remain <= 0) {
            return left;
        }
        int add = Math.min(remain, left);
        WeaningRecordAllocation a = new WeaningRecordAllocation();
        a.setCageId(c.getId());
        a.setAllocCount(add);
        rows.add(a);
        return left - add;
    }



    @TrackedOperation(
        code = "batch.sale", eventType = "BATCH_SOLD", batchId = "#batchId",
        targetType = "BATCH", targetId = "#batchId"
    )
    @Transactional
    public void sale(
        Long userId,
        Long houseId,
        Long batchId,
        List<Long> rabbitIds,
        Date saleDate,
        String remark,
        String requestId
    ) {
        String api = "batch.sale";
        if (
            requestDedupService.shouldSkipAsDone(
                houseId,
                userId,
                api,
                requestId
            )
        ) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch batch = requireBatchActive(houseId, batchId);

            List<Long> uniqueRabbitIds = rabbitIds.stream().distinct().toList();
            if (uniqueRabbitIds.size() != rabbitIds.size()) {
                throw new BizException(400, "rabbitIds包含重复值");
            }
            List<Long> lockedIds = outboundEligibilityService.lockRabbitIds(
                houseId,
                uniqueRabbitIds
            );
            if (lockedIds.size() != uniqueRabbitIds.size()) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
            List<OutboundDtos.RabbitEligibilityView> eligibility =
                outboundEligibilityService.evaluate(
                    outboundEligibilityService.rowsByIds(
                        houseId,
                        uniqueRabbitIds
                    )
                );
            if (eligibility.size() != uniqueRabbitIds.size()) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
            for (OutboundDtos.RabbitEligibilityView item : eligibility) {
                if (!OutboundEligibilityService.NORMAL.equals(item.eligibility())) {
                    throw new BizException(
                        409,
                        "兔只 #" + item.rabbitId() + " 不可直接出售：" +
                        item.message() + "；请使用安全出库流程"
                    );
                }
            }

            for (Long rabbitId : rabbitIds) {
                Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
                if (r == null) {
                    throw new BizException(400, "兔子不存在");
                }
                if (r.getIsActive() == null || !r.getIsActive()) {
                    throw new BizException(400, "兔子不在场");
                }
                if (!"2".equals(r.getType())) {
                    throw new BizException(400, "仅商品兔可出售");
                }

                BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                    houseId,
                    batchId,
                    rabbitId
                );
                if (br == null) {
                    throw new BizException(400, "兔子不在该批次中");
                }
                if (!"fattening".equals(br.getBatchRole())) {
                    throw new BizException(400, "仅育肥兔可走出售流程");
                }
                if (!"成长期".equals(br.getCurrentStatus())) {
                    throw new BizException(400, "当前状态不允许出售");
                }
                if (
                    br.getNextEventType() != null &&
                    !"出售".equals(br.getNextEventType())
                ) {
                    throw new BizException(400, "当前计划事件不允许出售");
                }

                rabbitMapper.updateDeparture(
                    houseId,
                    rabbitId,
                    saleDate,
                    "0",
                    String.valueOf(userId)
                );
                int rows = batchRabbitMapper.deactivateIfActive(
                    houseId,
                    br.getId(),
                    saleDate,
                    remark == null ? "出售" : remark,
                    String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }

                RabbitDepartureRecord departure = new RabbitDepartureRecord();
                departure.setHouseId(houseId);
                departure.setRabbitId(rabbitId);
                departure.setDepartureType("sale");
                departure.setDepartureDate(saleDate);
                departure.setReason("批次销售出栏");
                departure.setRemark(remark);
                departure.setRequestId(RequestIdUtil.deriveChild(requestId, rabbitId));
                rabbitDepartureRecordMapper.insert(departure);

                Cage cage = cageMapper.selectById(houseId, r.getCageId());
                if (cage != null && houseId.equals(cage.getHouseId())) {
                    int newCount =
                        (cage.getRabbitCount() == null
                            ? 0
                            : cage.getRabbitCount()) - 1;
                    if (newCount < 0) {
                        newCount = 0;
                    }
                    String status = newCount == 0 ? "0" : cage.getStatus();
                    cageMapper.updateRabbitCountAndStatus(
                        houseId,
                        cage.getId(),
                        newCount,
                        status,
                        String.valueOf(userId)
                    );
                }

                insertHistory(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    br.getCurrentStatus(),
                    "出售出栏",
                    "出售",
                    departure.getId(),
                    "rabbit_departure_records"
                );
                workTaskWriter.completeForRabbit(
                    houseId, rabbitId, TaskType.SALE_READY, String.valueOf(userId)
                );
                workTaskWriter.cancelAllForRabbit(
                    houseId, rabbitId, String.valueOf(userId)
                );
            }

            // 新口径：出售后即使批次空了也不自动结束，由用户主动点击。
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(
                houseId,
                userId,
                api,
                requestId,
                e.getMessage()
            );
            throw e;
        }
    }

    @TrackedOperation(
        code = "batch.completeBatch", eventType = "BATCH_COMPLETED", batchId = "#batchId",
        targetType = "BATCH", targetId = "#batchId"
    )
    @Transactional
    public void completeBatch(
        Long userId,
        Long houseId,
        Long batchId,
        Date endDate,
        boolean force,
        String remark,
        String requestId
    ) {
        String api = "batch.completeBatch";
        if (
            requestDedupService.shouldSkipAsDone(
                houseId,
                userId,
                api,
                requestId
            )
        ) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch b = requireBatchForUpdate(houseId, batchId);
            Date x = endDate == null ? DateUtil.now() : endDate;
            int active = batchRabbitMapper.countActiveByBatch(batchId);
            if (active != 0) {
                if (!force) {
                    throw new BizException(
                        400,
                        "批次仍有活跃兔，force=true 才能强制结束"
                    );
                }
            }
            requireNoOpenReproCycles(houseId, batchId);
            if (active != 0) {
                while (
                    batchRabbitMapper.deactivateByBatchLimited(
                        houseId,
                        batchId,
                        x,
                        remark == null ? "手动结束批次" : remark,
                        String.valueOf(userId),
                        BULK_WRITE_SIZE
                    ) > 0
                ) {
                    // Continue until no active member remains in this batch.
                }
            }
            requireNoOpenReproCycles(houseId, batchId);
            batchMapper.updateStatusAndDates(
                houseId,
                batchId,
                "已完成",
                b.getStartDate(),
                x,
                String.valueOf(userId)
            );
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(
                houseId,
                userId,
                api,
                requestId,
                e.getMessage()
            );
            throw e;
        }
    }

    public List<ReplacementRecord> listDueReplacement(Long houseId) {
        return replacementRecordMapper.selectDue(houseId, DateUtil.now());
    }

    public List<ReplacementRecord> listDueReplacement(
        Long houseId,
        boolean onlyUnnotified
    ) {
        if (onlyUnnotified) {
            return replacementRecordMapper.selectDueUnnotified(
                houseId,
                DateUtil.now()
            );
        }
        return replacementRecordMapper.selectDue(houseId, DateUtil.now());
    }

    /**
     * 改批次编号。
     *
     * <p>编号是操作者认批次的唯一凭据，建完才发现打错字、或者养殖计划变了想换个叫法，
     * 以前只能重建一个批次再把兔只搬过去。
     *
     * <p>已完成的批次同样允许改：翻历史记录时把一个错名字改对是正当需求，而且改名
     * 不影响任何生产数据，批次的身份始终是主键。编号没有唯一约束，这里也不额外拦重名，
     * 与建批次时的口径保持一致。
     */
    @TrackedOperation(
        code = "batch.rename", eventType = "BATCH_RENAMED", batchId = "#batchId",
        targetType = "BATCH", targetId = "#batchId"
    )
    @Transactional
    public Batch renameBatch(
        Long userId,
        Long houseId,
        Long batchId,
        String batchCode,
        String requestId
    ) {
        String api = "batch.rename";
        String code = batchCode == null ? "" : batchCode.trim();
        if (code.isEmpty()) {
            throw new BizException(400, "批次编号不能为空");
        }
        if (
            requestDedupService.shouldSkipAsDone(
                houseId,
                userId,
                api,
                requestId
            )
        ) {
            return requireBatch(houseId, batchId);
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch b = requireBatchForUpdate(houseId, batchId);
            if (!code.equals(b.getBatchCode())) {
                batchMapper.updateBatchCode(
                    houseId,
                    batchId,
                    code,
                    String.valueOf(userId)
                );
                b.setBatchCode(code);
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
            return b;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(
                houseId,
                userId,
                api,
                requestId,
                e.getMessage()
            );
            throw e;
        }
    }

    private Batch requireBatch(Long houseId, Long batchId) {
        Batch b = batchMapper.selectById(houseId, batchId);
        if (b == null) {
            throw new BizException(400, "批次不存在");
        }
        return b;
    }

    private Batch requireBatchForUpdate(Long houseId, Long batchId) {
        Batch b = batchMapper.selectByIdForUpdate(houseId, batchId);
        if (b == null) {
            throw new BizException(400, "批次不存在");
        }
        return b;
    }

    private Batch requireBatchActive(Long houseId, Long batchId) {
        Batch b = requireBatch(houseId, batchId);
        if ("已完成".equals(b.getStatus())) {
            throw new BizException(400, "批次已完成");
        }
        return b;
    }

    private Batch requireBatchActiveForUpdate(Long houseId, Long batchId) {
        Batch b = requireBatchForUpdate(houseId, batchId);
        if ("已完成".equals(b.getStatus())) {
            throw new BizException(400, "批次已完成");
        }
        return b;
    }

    private GlobalSetting requireSetting(Long userId, Long houseId) {
        return settingService.getEffectiveSetting(userId, houseId);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 结束批次前的守门：还有未结束的生产周期就不允许结束。
     *
     * <p>旧实现在这里直接 UPDATE 把周期置为「已终止」。doe-breeding-v2 之后这么做是错的，
     * 而且是静默错：那条 SQL 不认识 lifecycle / result / stage，于是周期在旧视角已终止、
     * 在新视角仍是 OPEN——母兔会被 uk_bc_pipeline 卡住再也开不了新周期，待办也永远 PENDING。
     *
     * <p>更根本的问题是旧模型把「批次生命周期」和「生产周期」混为一谈。批次现在只是个标签，
     * 归档一个标签不应该能终止母兔的生理过程。所以改成拒绝：要结束周期就去走
     * repro 接口显式操作（空怀/流产/离场），那条路径才会同时维护阶段、待办和母兔投影。
     */
    private void requireNoOpenReproCycles(Long houseId, Long batchId) {
        int open = breedingCycleMapper.countOpenLifecycleByBatch(houseId, batchId);
        if (open > 0) {
            throw new BizException(
                409,
                "批次仍有 " + open + " 个未结束的生产周期，请先在生产流程中处理完再结束批次"
            );
        }
    }

    private void insertHistory(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        String fromStatus,
        String toStatus,
        String reason,
        Long relatedId,
        String relatedTable
    ) {
        RabbitStatusHistory h = new RabbitStatusHistory();
        h.setHouseId(houseId);
        h.setRabbitId(rabbitId);
        h.setBatchId(batchId);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setChangeTime(DateUtil.now());
        h.setReason(reason);
        h.setRelatedRecordId(relatedId);
        h.setRelatedRecordTable(relatedTable);
        rabbitStatusHistoryMapper.insert(h);
    }

    private void insertHistoryAt(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        String fromStatus,
        String toStatus,
        String reason,
        Date changeTime,
        Long relatedId,
        String relatedTable
    ) {
        RabbitStatusHistory h = new RabbitStatusHistory();
        h.setHouseId(houseId);
        h.setRabbitId(rabbitId);
        h.setBatchId(batchId);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setChangeTime(changeTime == null ? DateUtil.now() : changeTime);
        h.setReason(reason);
        h.setRelatedRecordId(relatedId);
        h.setRelatedRecordTable(relatedTable);
        rabbitStatusHistoryMapper.insert(h);
    }

    private Cage pickCommodityCage(Long houseId) {
        List<Cage> cages = cageMapper.selectByHouseId(houseId);
        for (Cage c : cages) {
            if (c.getIsEnabled() != null && !c.getIsEnabled()) {
                continue;
            }
            if ("0".equals(c.getStatus())) {
                return c;
            }
        }
        for (Cage c : cages) {
            if (c.getIsEnabled() != null && !c.getIsEnabled()) {
                continue;
            }
            if ("3".equals(c.getStatus())) {
                return c;
            }
        }
        throw new BizException(400, "没有可用商品兔笼位");
    }

    private String pickKidGender(
        int index,
        int total,
        int maleCount,
        int femaleCount
    ) {
        if (maleCount + femaleCount == 0) {
            return "0";
        }
        if (index < maleCount) {
            return "1";
        }
        return "0";
    }
}
