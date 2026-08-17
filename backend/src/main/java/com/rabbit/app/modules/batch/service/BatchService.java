package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BulkMatingResult;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.BreedingCycle;
import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import com.rabbit.app.modules.batch.entity.PrepartumRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
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
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.service.OpenCycleCommand;
import com.rabbit.app.modules.repro.service.OperatorNameResolver;
import com.rabbit.app.modules.repro.service.ReproRequestIds;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

    /** 建批次时把母兔送进生产流水线；批次本身只是标签，状态在周期上。 */
    private final ReproCycleMapper reproCycleMapper;
    private final ReproStateMachineService reproStateMachineService;
    private final OperatorNameResolver operatorNameResolver;

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
        ReproCycleMapper reproCycleMapper,
        ReproStateMachineService reproStateMachineService,
        OperatorNameResolver operatorNameResolver,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.reproCycleMapper = reproCycleMapper;
        this.reproStateMachineService = reproStateMachineService;
        this.operatorNameResolver = operatorNameResolver;
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

            if (femaleRabbitIds == null || femaleRabbitIds.isEmpty()) {
                throw new BizException(400, "母兔列表不能为空");
            }
            List<Long> requestedIds = new ArrayList<Long>(
                new LinkedHashSet<Long>(femaleRabbitIds)
            );
            if (requestedIds.size() != femaleRabbitIds.size()) {
                throw new BizException(400, "母兔列表包含重复项");
            }
            // The rabbit row is the serialization point for batch membership.
            // Lock every requested mother in a stable order before checking active
            // links, so concurrent batches cannot both observe an empty result.
            requestedIds.sort(Long::compareTo);
            List<Rabbit> females = rabbitMapper.selectByIdsForUpdate(
                houseId,
                requestedIds
            );
            if (females.size() != requestedIds.size()) {
                throw new BizException(400, "母兔不存在");
            }
            // This must be a locking/current read. The idempotency lookups above
            // may already have established a REPEATABLE READ snapshot before a
            // concurrent request releases the mother-row lock.
            Set<Long> activeRabbitIds = new HashSet<Long>(
                batchRabbitMapper.selectActiveRabbitIdsForUpdate(
                    houseId,
                    requestedIds
                )
            );
            if (!activeRabbitIds.isEmpty()) {
                throw new BizException(400, "母兔已在活跃批次中");
            }

            Date now = DateUtil.now();

            Batch b = new Batch();
            b.setHouseId(houseId);
            b.setBatchCode(batchCode);
            b.setStatus("计划中");
            b.setRemark(remark);
            b.setRequestId(requestId);
            b.setCreateBy(String.valueOf(userId));
            b.setUpdateBy(String.valueOf(userId));
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

            List<BatchRabbit> links = new ArrayList<BatchRabbit>(females.size());
            List<RabbitStatusHistory> histories =
                new ArrayList<RabbitStatusHistory>(females.size());
            for (Rabbit r : females) {
                Long rid = r.getId();
                if (r.getIsActive() == null || !r.getIsActive()) {
                    throw new BizException(400, "母兔不在场");
                }
                if (!"0".equals(r.getGender())) {
                    throw new BizException(400, "仅母兔可加入繁殖批次");
                }
                if (!"0".equals(r.getType()) && !"1".equals(r.getType())) {
                    throw new BizException(400, "仅种兔/后备兔可加入繁殖批次");
                }
                BatchRabbit br = new BatchRabbit();
                br.setBatchId(b.getId());
                br.setRabbitId(rid);
                br.setJoinReason("配种");
                br.setBatchRole("breeding");
                br.setCurrentStatus("待催情");
                br.setIsActive(Boolean.TRUE);
                br.setJoinDate(now);
                br.setCreateBy(String.valueOf(userId));
                br.setUpdateBy(String.valueOf(userId));
                links.add(br);

                RabbitStatusHistory h = new RabbitStatusHistory();
                h.setHouseId(houseId);
                h.setRabbitId(rid);
                h.setBatchId(b.getId());
                h.setFromStatus(null);
                h.setToStatus("待催情");
                h.setChangeTime(now);
                h.setReason("加入批次");
                h.setCreateBy(String.valueOf(userId));
                h.setUpdateBy(String.valueOf(userId));
                histories.add(h);
            }
            insertBatchRabbitLinks(links);
            insertStatusHistories(histories);
            openReproCyclesForNewMembers(userId, houseId, b.getId(), females, now, requestId);

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
     * 把刚加入批次的母兔送进生产流水线（待催情）。
     *
     * <p>旧模型里「加入批次」只写 batch_rabbits.current_status = 待催情，那个字段本身就是状态；
     * doe-breeding-v2 之后状态在 breeding_cycles 上，批次只是个标签。如果这里不开周期，
     * 母兔就既没有阶段也没有待办，整条生产流程从界面上根本无法开始。
     *
     * <p>已有进行中流水线周期的母兔会被跳过：她可能正怀着孕只是被重新贴了个标签，
     * 再开一个会撞上 uk_bc_pipeline，也不符合事实。
     */
    private void openReproCyclesForNewMembers(
        Long userId,
        Long houseId,
        Long batchId,
        List<Rabbit> females,
        Date now,
        String requestId
    ) {
        String operatorName = operatorNameResolver.resolve(userId);
        for (Rabbit r : females) {
            if (reproCycleMapper.selectOpenPipelineForUpdate(houseId, r.getId()) != null) {
                continue;
            }
            OpenCycleCommand command = new OpenCycleCommand(
                houseId,
                userId,
                operatorName,
                r.getId(),
                batchId,
                ReproStage.AWAIT_ESTRUS,
                now,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                null,
                ReproRequestIds.derive(requestId, "open-" + r.getId())
            );
            reproStateMachineService.openCycleAt(command);
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




    private List<Long> normalizeBulkMatingIds(List<Long> values) {
        if (values == null || values.isEmpty()) {
            throw new BizException(400, "femaleRabbitIds不能为空");
        }
        if (values.size() > 1000) {
            throw new BizException(400, "单次最多配种1000只母兔");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<Long>();
        for (Long value : values) {
            if (value == null || value <= 0 || !unique.add(value)) {
                throw new BizException(400, "femaleRabbitIds包含无效或重复值");
            }
        }
        List<Long> sorted = new ArrayList<Long>(unique);
        sorted.sort(Long::compareTo);
        return sorted;
    }

    private void validateBulkMatingRequest(Long maleRabbitId, Date matingDate, String requestId) {
        if (maleRabbitId == null || maleRabbitId <= 0) {
            throw new BizException(400, "maleRabbitId不合法");
        }
        if (matingDate == null) {
            throw new BizException(400, "matingDate不能为空");
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new BizException(400, "requestId不能为空");
        }
        if (requestId.length() > 64) {
            throw new BizException(400, "requestId长度不能超过64");
        }
    }

    private void validateMotherForMating(Rabbit mother, Long houseId) {
        if (mother == null || !houseId.equals(mother.getHouseId())) {
            throw new BizException(400, "母兔不存在");
        }
        if (!Boolean.TRUE.equals(mother.getIsActive())) {
            throw new BizException(400, "母兔不在场");
        }
        if (!"0".equals(mother.getGender())) {
            throw new BizException(400, "母兔性别不正确");
        }
        if (!"0".equals(mother.getType()) && !"1".equals(mother.getType())) {
            throw new BizException(400, "母兔类型不正确");
        }
    }

    private void validateMaleForMating(Rabbit male, Long houseId) {
        if (male == null || !houseId.equals(male.getHouseId())) {
            throw new BizException(400, "公兔不存在");
        }
        if (!Boolean.TRUE.equals(male.getIsActive())) {
            throw new BizException(400, "公兔不在场");
        }
        if (!"1".equals(male.getGender())) {
            throw new BizException(400, "公兔性别不正确");
        }
        if (!"0".equals(male.getType())) {
            throw new BizException(400, "仅种公兔可用于配种");
        }
    }

    private String deriveMatingItemRequestId(String requestId, Long motherId) {
        return deriveBoundedRequestId(requestId, String.valueOf(motherId));
    }

    private String bulkMatingPayloadHash(
        Long batchId,
        List<Long> motherIds,
        Long maleRabbitId,
        Date matingDate
    ) {
        StringBuilder canonical = new StringBuilder()
            .append(batchId)
            .append('|')
            .append(maleRabbitId)
            .append('|')
            .append(matingDate.getTime())
            .append('|');
        for (Long motherId : motherIds) {
            canonical.append(motherId).append(',');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                canonical.toString().getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String deriveBoundedRequestId(String requestId, String suffix) {
        String candidate = requestId + "-" + suffix;
        if (candidate.length() <= 64) {
            return candidate;
        }
        return UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8)).toString();
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
                departure.setCreateBy(String.valueOf(userId));
                departure.setUpdateBy(String.valueOf(userId));
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
            }

            checkAndCompleteBatch(houseId, batchId, userId, saleDate);
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
        h.setCreateBy(String.valueOf(userId));
        h.setUpdateBy(String.valueOf(userId));
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
        h.setCreateBy(String.valueOf(userId));
        h.setUpdateBy(String.valueOf(userId));
        rabbitStatusHistoryMapper.insert(h);
    }

    private void checkAndCompleteBatch(
        Long houseId,
        Long batchId,
        Long userId,
        Date endDate
    ) {
        int active = batchRabbitMapper.countActiveByBatch(batchId);
        if (active != 0) {
            return;
        }
        Batch b = batchMapper.selectById(houseId, batchId);
        if (b == null) {
            return;
        }
        batchMapper.updateStatusAndDates(
            houseId,
            batchId,
            "已完成",
            b.getStartDate(),
            endDate,
            String.valueOf(userId)
        );
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
