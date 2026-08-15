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
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
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

    @Transactional
    public void mating(
        Long userId,
        Long houseId,
        Long batchId,
        Long femaleRabbitId,
        Long maleRabbitId,
        Date matingDate,
        String requestId
    ) {
        matingInternal(
            userId,
            houseId,
            batchId,
            femaleRabbitId,
            maleRabbitId,
            matingDate,
            requestId,
            requestId,
            null,
            null
        );
    }

    private void matingInternal(
        Long userId,
        Long houseId,
        Long batchId,
        Long femaleRabbitId,
        Long maleRabbitId,
        Date matingDate,
        String requestId,
        String cycleRequestId,
        Batch lockedBatch,
        GlobalSetting lockedSetting
    ) {
        String api = "batch.mating";
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
            Batch batch = lockedBatch == null
                ? requireBatchActiveForUpdate(houseId, batchId)
                : lockedBatch;
            GlobalSetting gs = lockedSetting == null
                ? requireSetting(userId, houseId)
                : lockedSetting;

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId,
                batchId,
                femaleRabbitId
            );
            if (br == null) {
                throw new BizException(400, "母兔不在该批次中");
            }
            if (
                !"待配种".equals(br.getCurrentStatus()) &&
                !"哺乳中".equals(br.getCurrentStatus())
            ) {
                throw new BizException(400, "当前状态不允许配种");
            }
            if (
                breedingCycleMapper.countOpenGestations(
                    houseId,
                    batchId,
                    femaleRabbitId
                ) > 0
            ) {
                throw new BizException(409, "母兔已有进行中的配种周期");
            }
            Rabbit female = rabbitMapper.selectById(houseId, femaleRabbitId);
            if (female == null || !houseId.equals(female.getHouseId())) {
                throw new BizException(400, "母兔不存在");
            }
            if (female.getIsActive() == null || !female.getIsActive()) {
                throw new BizException(400, "母兔不在场");
            }
            if (!"0".equals(female.getGender())) {
                throw new BizException(400, "母兔性别不正确");
            }
            if (
                !"0".equals(female.getType()) && !"1".equals(female.getType())
            ) {
                throw new BizException(400, "母兔类型不正确");
            }

            Rabbit male = rabbitMapper.selectById(houseId, maleRabbitId);
            if (male == null || !houseId.equals(male.getHouseId())) {
                throw new BizException(400, "公兔不存在");
            }
            if (male.getIsActive() == null || !male.getIsActive()) {
                throw new BizException(400, "公兔不在场");
            }
            if (!"1".equals(male.getGender())) {
                throw new BizException(400, "公兔性别不正确");
            }
            if (!"0".equals(male.getType())) {
                throw new BizException(400, "仅种公兔可用于配种");
            }

            String fromStatus = br.getCurrentStatus();
            Date nextDate = DateUtil.plusDays(
                matingDate,
                gs.getPalpationDays()
            );
            BreedingCycle nursingCycle = breedingCycleMapper
                .selectLatestByStatusesForUpdate(
                    houseId,
                    batchId,
                    femaleRabbitId,
                    List.of("哺乳中")
                );
            if (
                nursingCycle != null &&
                nursingCycle.getBirthDate() != null &&
                DateUtil.daysBetween(nursingCycle.getBirthDate(), matingDate) < 0
            ) {
                throw new BizException(400, "二次配种日期不能早于上一窝产仔日期");
            }
            BreedingCycle latest = breedingCycleMapper.selectLatest(
                houseId,
                batchId,
                femaleRabbitId
            );
            BreedingCycle cycle = new BreedingCycle();
            cycle.setHouseId(houseId);
            cycle.setBatchId(batchId);
            cycle.setMotherRabbitId(femaleRabbitId);
            cycle.setMaleRabbitId(maleRabbitId);
            cycle.setCycleNo(latest == null ? 1 : latest.getCycleNo() + 1);
            cycle.setStatus("已配种");
            cycle.setMatingDate(matingDate);
            cycle.setExpectedBirthDate(DateUtil.plusDays(matingDate, 30));
            cycle.setNextEventDate(nextDate);
            cycle.setNextEventType("摸胎");
            cycle.setRequestId(cycleRequestId);
            cycle.setCreateBy(String.valueOf(userId));
            cycle.setUpdateBy(String.valueOf(userId));
            if (nursingCycle != null) {
                cycle.setPostpartumRematingDays(
                    DateUtil.daysBetween(nursingCycle.getBirthDate(), matingDate)
                );
                cycle.setOverlapLitterCycleNo(nursingCycle.getCycleNo());
                cycle.setOverlapStartDate(matingDate);
            }
            breedingCycleMapper.insert(cycle);

            int rows = batchRabbitMapper.updateBreedingSummary(
                houseId,
                br.getId(),
                "已配种",
                matingDate,
                nextDate,
                "摸胎",
                maleRabbitId,
                cycle.getId(),
                breedingCycleMapper.sumCurrentNursingKits(
                    houseId,
                    batchId,
                    femaleRabbitId
                ),
                breedingCycleMapper.countNursingLitters(
                    houseId,
                    batchId,
                    femaleRabbitId
                ),
                String.valueOf(userId)
            );
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }

            if (batch.getStartDate() == null) {
                batchMapper.updateStatusAndDates(
                    houseId,
                    batchId,
                    "进行中",
                    matingDate,
                    batch.getEndDate(),
                    String.valueOf(userId)
                );
            }

            RabbitStatusHistory h = new RabbitStatusHistory();
            h.setHouseId(houseId);
            h.setRabbitId(femaleRabbitId);
            h.setBatchId(batchId);
            h.setFromStatus(fromStatus);
            h.setToStatus("已配种");
            h.setChangeTime(DateUtil.now());
            h.setReason("配种");
            h.setCreateBy(String.valueOf(userId));
            h.setUpdateBy(String.valueOf(userId));
            rabbitStatusHistoryMapper.insert(h);
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

    /**
     * Mates one bounded house round in one transaction. The per-row core keeps
     * the legacy state machine and history semantics identical to the single
     * endpoint while this method removes 1,000 HTTP transactions and validates
     * the complete payload before the first cycle is written.
     */
    @Transactional
    public BulkMatingResult matingBulk(
        Long userId,
        Long houseId,
        Long batchId,
        List<Long> femaleRabbitIds,
        Long maleRabbitId,
        Date matingDate,
        String requestId
    ) {
        List<Long> motherIds = normalizeBulkMatingIds(femaleRabbitIds);
        validateBulkMatingRequest(maleRabbitId, matingDate, requestId);
        String api = "batch.mating.bulk";
        String payloadHash = bulkMatingPayloadHash(batchId, motherIds, maleRabbitId, matingDate);
        if (requestDedupService.begin(houseId, userId, api, requestId, payloadHash)
            == RequestDedupService.BeginResult.DONE) {
            return new BulkMatingResult(requestId, motherIds.size());
        }
        try {
            Batch lockedBatch = requireBatchActiveForUpdate(houseId, batchId);
            GlobalSetting lockedSetting = requireSetting(userId, houseId);

            // The batch row is the lifecycle mutex; rabbit and member rows then
            // follow stable ID order for all mothers in the round.
            List<Rabbit> mothers = rabbitMapper.selectByIdsForUpdate(houseId, motherIds);
            if (mothers.size() != motherIds.size()) {
                throw new BizException(400, "母兔不存在");
            }
            for (Rabbit mother : mothers) {
                validateMotherForMating(mother, houseId);
            }

            List<BatchRabbit> links = batchRabbitMapper
                .selectActiveByBatchAndRabbitsForUpdate(houseId, batchId, motherIds);
            if (links.size() != motherIds.size()) {
                throw new BizException(400, "母兔不在该批次中");
            }
            Map<Long, BatchRabbit> linkByMother = new HashMap<Long, BatchRabbit>();
            for (BatchRabbit link : links) {
                linkByMother.put(link.getRabbitId(), link);
            }
            if (linkByMother.size() != motherIds.size()) {
                throw new BizException(400, "母兔不在该批次中");
            }

            Rabbit male = rabbitMapper.selectByIdsForUpdate(houseId, List.of(maleRabbitId))
                .stream()
                .findFirst()
                .orElse(null);
            validateMaleForMating(male, houseId);

            // All checks happen before the first insert/update. A bad row
            // therefore rolls back the entire round without a partial mating.
            for (Long motherId : motherIds) {
                BatchRabbit link = linkByMother.get(motherId);
                if (link == null) {
                    throw new BizException(400, "母兔不在该批次中");
                }
                if (!"待配种".equals(link.getCurrentStatus())
                    && !"哺乳中".equals(link.getCurrentStatus())) {
                    throw new BizException(400, "当前状态不允许配种");
                }
                if (breedingCycleMapper.countOpenGestations(houseId, batchId, motherId) > 0) {
                    throw new BizException(409, "母兔已有进行中的配种周期");
                }
                BreedingCycle nursingCycle = breedingCycleMapper
                    .selectLatestByStatusesForUpdate(
                        houseId,
                        batchId,
                        motherId,
                        List.of("哺乳中")
                    );
                if (nursingCycle != null && nursingCycle.getBirthDate() != null
                    && DateUtil.daysBetween(nursingCycle.getBirthDate(), matingDate) < 0) {
                    throw new BizException(400, "二次配种日期不能早于上一窝产仔日期");
                }
            }

            for (Long motherId : motherIds) {
                String itemRequestId = deriveMatingItemRequestId(requestId, motherId);
                matingInternal(
                    userId,
                    houseId,
                    batchId,
                    motherId,
                    maleRabbitId,
                    matingDate,
                    itemRequestId,
                    itemRequestId,
                    lockedBatch,
                    lockedSetting
                );
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
            return new BulkMatingResult(requestId, motherIds.size());
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
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

    @Transactional
    public void pregnancyCheck(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        Long breedingCycleId,
        Date checkDate,
        String result,
        String remark,
        String requestId
    ) {
        String api = "batch.pregnancyCheck";
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
            requireBatchActive(houseId, batchId);
            GlobalSetting gs = requireSetting(userId, houseId);

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (
                !"怀孕".equals(result) &&
                !"空怀".equals(result) &&
                !"不确定".equals(result)
            ) {
                throw new BizException(400, "摸胎结果不正确");
            }
            BreedingCycle cycle = requireCycleForUpdate(
                houseId,
                batchId,
                rabbitId,
                breedingCycleId,
                List.of("已配种", "不确定"),
                false,
                "当前没有可摸胎的繁殖周期"
            );

            PregnancyCheckRecord record = new PregnancyCheckRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setBreedingCycleId(cycle.getId());
            record.setRabbitId(rabbitId);
            record.setCheckDate(checkDate);
            record.setResult(result);
            record.setRemark(remark);
            record.setCreateBy(String.valueOf(userId));
            record.setUpdateBy(String.valueOf(userId));
            pregnancyCheckRecordMapper.insert(record);

            breedingPerformanceMapper.ensureExists(houseId, rabbitId);

            String fromStatus = br.getCurrentStatus();
            String cycleFromStatus = cycle.getStatus();
            cycle.setPregnancyCheckDate(checkDate);
            cycle.setPregnancyResult(result);
            cycle.setUpdateBy(String.valueOf(userId));
            if ("怀孕".equals(result)) {
                if (cycle.getMatingDate() == null) {
                    throw new BizException(400, "缺少配种日期");
                }
                Date dueDate = DateUtil.plusDays(cycle.getMatingDate(), 30);
                Date nextDate = DateUtil.minusDays(
                    dueDate,
                    gs.getPrepartumDays()
                );
                cycle.setStatus("怀孕确认");
                cycle.setExpectedBirthDate(dueDate);
                cycle.setNextEventDate(nextDate);
                cycle.setNextEventType("备产");
                int rows = breedingCycleMapper.update(cycle, cycleFromStatus);
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                syncBreedingSummary(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    br,
                    null,
                    null,
                    null,
                    null,
                    null
                );
                breedingPerformanceMapper.incBreedingResult(
                    houseId,
                    rabbitId,
                    true
                );
                insertHistory(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    fromStatus,
                    "怀孕确认",
                    "摸胎",
                    record.getId(),
                    "pregnancy_check_records"
                );
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }
            if ("空怀".equals(result)) {
                Date now = DateUtil.now();
                cycle.setStatus("空怀");
                cycle.setNextEventDate(null);
                cycle.setNextEventType(null);
                cycle.setClosedAt(now);
                cycle.setCloseReason("孕检空怀");
                int rows = breedingCycleMapper.update(cycle, cycleFromStatus);
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                BreedingCycle open = breedingCycleMapper.selectDisplayOpen(
                    houseId,
                    batchId,
                    rabbitId
                );
                if (open == null) {
                    rows = batchRabbitMapper.deactivateIfActive(
                        houseId,
                        br.getId(),
                        now,
                        "空怀退出批次",
                        String.valueOf(userId)
                    );
                    if (rows <= 0) {
                        throw new BizException(409, "状态已变化，请刷新后重试");
                    }
                } else {
                    syncBreedingSummary(
                        userId,
                        houseId,
                        batchId,
                        rabbitId,
                        br,
                        null,
                        null,
                        null,
                        null,
                        null
                    );
                }
                breedingPerformanceMapper.incBreedingResult(
                    houseId,
                    rabbitId,
                    false
                );
                insertHistory(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    fromStatus,
                    "空怀",
                    "摸胎",
                    record.getId(),
                    "pregnancy_check_records"
                );
                checkAndCompleteBatch(houseId, batchId, userId, DateUtil.now());
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }

            cycle.setStatus("不确定");
            int rows = breedingCycleMapper.update(cycle, cycleFromStatus);
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
            syncBreedingSummary(
                userId,
                houseId,
                batchId,
                rabbitId,
                br,
                null,
                null,
                null,
                null,
                null
            );
            insertHistory(
                userId,
                houseId,
                batchId,
                rabbitId,
                fromStatus,
                "不确定",
                "摸胎",
                record.getId(),
                "pregnancy_check_records"
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

    @Transactional
    public void prepartumFinish(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        Long breedingCycleId,
        Date actionDate,
        String remark,
        String requestId
    ) {
        String api = "batch.prepartumFinish";
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
            requireBatchActive(houseId, batchId);
            requireSetting(userId, houseId);

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            BreedingCycle cycle = requireCycleForUpdate(
                houseId,
                batchId,
                rabbitId,
                breedingCycleId,
                List.of("怀孕确认"),
                false,
                "当前没有可备产的繁殖周期"
            );
            if (!"备产".equals(cycle.getNextEventType())) {
                throw new BizException(400, "当前无需备产");
            }
            if (cycle.getMatingDate() == null) {
                throw new BizException(400, "缺少配种日期");
            }

            PrepartumRecord record = new PrepartumRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setBreedingCycleId(cycle.getId());
            record.setRabbitId(rabbitId);
            record.setActionDate(actionDate);
            record.setRemark(remark);
            record.setCreateBy(String.valueOf(userId));
            record.setUpdateBy(String.valueOf(userId));
            prepartumRecordMapper.insert(record);

            Date dueDate = DateUtil.plusDays(cycle.getMatingDate(), 30);
            cycle.setExpectedBirthDate(dueDate);
            cycle.setNextEventDate(dueDate);
            cycle.setNextEventType("分娩");
            cycle.setUpdateBy(String.valueOf(userId));
            int rows = breedingCycleMapper.update(cycle, cycle.getStatus());
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
            syncBreedingSummary(
                userId,
                houseId,
                batchId,
                rabbitId,
                br,
                null,
                null,
                null,
                null,
                null
            );
            insertHistoryAt(
                userId,
                houseId,
                batchId,
                rabbitId,
                br.getCurrentStatus(),
                br.getCurrentStatus(),
                "备产完成",
                actionDate,
                record.getId(),
                "prepartum_records"
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

    @Transactional
    public void parturition(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        Long breedingCycleId,
        Date birthDate,
        int totalKits,
        int liveKits,
        boolean failed,
        String remark,
        String requestId
    ) {
        String api = "batch.parturition";
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
            requireBatchActive(houseId, batchId);
            GlobalSetting gs = requireSetting(userId, houseId);

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (totalKits < 0 || liveKits < 0) {
                throw new BizException(400, "产仔数量不能小于0");
            }
            if (liveKits > totalKits) {
                throw new BizException(400, "活仔数不能大于总产仔数");
            }
            if (failed && (totalKits != 0 || liveKits != 0)) {
                throw new BizException(400, "失败产的总仔数和活仔数必须为0");
            }
            BreedingCycle cycle = requireCycleForUpdate(
                houseId,
                batchId,
                rabbitId,
                breedingCycleId,
                List.of("怀孕确认"),
                false,
                "当前没有可分娩的繁殖周期"
            );

            ParturitionRecord record = new ParturitionRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setBreedingCycleId(cycle.getId());
            record.setRabbitId(rabbitId);
            record.setBirthDate(birthDate);
            record.setTotalKits(totalKits);
            record.setLiveKits(liveKits);
            record.setRemark(remark);
            record.setCreateBy(String.valueOf(userId));
            record.setUpdateBy(String.valueOf(userId));
            parturitionRecordMapper.insert(record);

            breedingPerformanceMapper.ensureExists(houseId, rabbitId);
            breedingPerformanceMapper.addParturition(
                houseId,
                rabbitId,
                totalKits,
                liveKits,
                birthDate
            );

            String fromStatus = br.getCurrentStatus();
            if (failed) {
                Date now = DateUtil.now();
                String op = String.valueOf(userId);
                cycle.setStatus("分娩失败");
                cycle.setBirthDate(birthDate);
                cycle.setTotalKits(totalKits);
                cycle.setLiveKits(liveKits);
                cycle.setCurrentNursingKits(0);
                cycle.setNextEventDate(null);
                cycle.setNextEventType(null);
                cycle.setClosedAt(now);
                cycle.setCloseReason("分娩失败");
                cycle.setRemark(remark);
                cycle.setUpdateBy(op);
                if (breedingCycleMapper.update(cycle, "怀孕确认") <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                RabbitAbnormalCondition c = new RabbitAbnormalCondition();
                c.setRabbitId(rabbitId);
                c.setHouseId(houseId);
                c.setWarningStatus("流产");
                c.setWarningTime(now);
                c.setIsDeal(Boolean.FALSE);
                c.setRemark(remark);
                c.setCreateBy(op);
                c.setUpdateBy(op);
                rabbitAbnormalConditionMapper.insert(c);
                if (
                    breedingCycleMapper.countNursingLitters(
                        houseId,
                        batchId,
                        rabbitId
                    ) > 0
                ) {
                    syncBreedingSummary(
                        userId,
                        houseId,
                        batchId,
                        rabbitId,
                        br,
                        null,
                        null,
                        null,
                        null,
                        null
                    );
                    insertHistory(
                        userId,
                        houseId,
                        batchId,
                        rabbitId,
                        fromStatus,
                        "分娩失败",
                        "分娩",
                        record.getId(),
                        "parturition_records"
                    );
                    requestDedupService.markDone(
                        houseId,
                        userId,
                        api,
                        requestId
                    );
                    return;
                }
                int rows = batchRabbitMapper.deactivateIfActive(
                    houseId,
                    br.getId(),
                    now,
                    "分娩失败退出批次",
                    op
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
                if (r == null) {
                    throw new BizException(400, "兔子不存在");
                }
                Cage oldCage = cageMapper.selectById(houseId, r.getCageId());
                if (oldCage != null && houseId.equals(oldCage.getHouseId())) {
                    int newCount =
                        (oldCage.getRabbitCount() == null
                            ? 0
                            : oldCage.getRabbitCount()) - 1;
                    if (newCount < 0) {
                        newCount = 0;
                    }
                    String status = newCount == 0 ? "0" : oldCage.getStatus();
                    cageMapper.updateRabbitCountAndStatus(
                        houseId,
                        oldCage.getId(),
                        newCount,
                        status,
                        op
                    );
                }

                rabbitMapper.updateDeparture(
                    houseId,
                    rabbitId,
                    now,
                    "parturition_fail",
                    op
                );
                RabbitDepartureRecord dr = new RabbitDepartureRecord();
                dr.setHouseId(houseId);
                dr.setRabbitId(rabbitId);
                dr.setDepartureType("parturition_fail");
                dr.setDepartureDate(now);
                dr.setReason("流产");
                dr.setRemark(remark);
                dr.setRequestId(requestId);
                dr.setCreateBy(op);
                dr.setUpdateBy(op);
                rabbitDepartureRecordMapper.insert(dr);

                RabbitStatusHistory rh = new RabbitStatusHistory();
                rh.setHouseId(houseId);
                rh.setRabbitId(rabbitId);
                rh.setFromStatus("在栏");
                rh.setToStatus("流产离场");
                rh.setChangeTime(now);
                rh.setReason(remark == null ? "流产离场" : remark);
                rh.setRelatedRecordId(dr.getId());
                rh.setRelatedRecordTable("rabbit_departure_records");
                rh.setCreateBy(op);
                rh.setUpdateBy(op);
                rabbitStatusHistoryMapper.insert(rh);

                insertHistory(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    fromStatus,
                    "分娩失败",
                    "分娩",
                    record.getId(),
                    "parturition_records"
                );
                checkAndCompleteBatch(houseId, batchId, userId, now);
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }

            Date nextDate = DateUtil.plusDays(birthDate, gs.getWeaningDays());
            cycle.setStatus("哺乳中");
            cycle.setBirthDate(birthDate);
            cycle.setTotalKits(totalKits);
            cycle.setLiveKits(liveKits);
            cycle.setCurrentNursingKits(liveKits);
            cycle.setNextEventDate(nextDate);
            cycle.setNextEventType("断奶");
            cycle.setRemark(remark);
            cycle.setUpdateBy(String.valueOf(userId));
            int rows = breedingCycleMapper.update(cycle, "怀孕确认");
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
            syncBreedingSummary(
                userId,
                houseId,
                batchId,
                rabbitId,
                br,
                null,
                null,
                null,
                null,
                null
            );
            insertHistory(
                userId,
                houseId,
                batchId,
                rabbitId,
                fromStatus,
                "哺乳中",
                "分娩",
                record.getId(),
                "parturition_records"
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

    @Transactional
    public void weaning(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
        Long breedingCycleId,
        Date weaningDate,
        int weaningCount,
        Integer maleCount,
        Integer femaleCount,
        Long targetCageId,
        Double avgWeight,
        String remark,
        String requestId
    ) {
        String api = "batch.weaning";
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
            requireBatchActive(houseId, batchId);
            GlobalSetting gs = requireSetting(userId, houseId);

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            BreedingCycle cycle = requireCycleForUpdate(
                houseId,
                batchId,
                rabbitId,
                breedingCycleId,
                List.of("哺乳中"),
                true,
                "当前没有可断奶的哺乳窝次"
            );
            if (weaningCount < 0) {
                throw new BizException(400, "断奶数量错误");
            }
            int currentNursing = valueOrZero(cycle.getCurrentNursingKits());
            if (weaningCount > currentNursing) {
                throw new BizException(400, "断奶数量不能大于当前带仔数");
            }
            int m = maleCount == null ? 0 : maleCount;
            int f = femaleCount == null ? 0 : femaleCount;
            if (m + f != 0 && m + f != weaningCount) {
                throw new BizException(400, "公母数量之和需等于断奶数量");
            }

            Long targetId =
                targetCageId != null && targetCageId > 0 ? targetCageId : null;
            List<WeaningRecordAllocation> allocations =
                new ArrayList<WeaningRecordAllocation>();
            if (weaningCount > 0) {
                if (targetId != null) {
                    Cage cage = cageMapper.selectByIdForUpdate(houseId, targetId);
                    if (cage == null) {
                        throw new BizException(400, "目标笼位不存在");
                    }
                    if (!Boolean.TRUE.equals(cage.getIsEnabled())) {
                        throw new BizException(400, "目标笼位已停用");
                    }
                    String st = cage.getStatus();
                    if (!"0".equals(st) && !"3".equals(st)) {
                        throw new BizException(400, "目标笼位不是商品兔笼位");
                    }
                    int used = valueOrZero(cage.getRabbitCount());
                    if (used + weaningCount > commodityCageCapacity) {
                        throw new BizException(400, "目标笼位容量不足");
                    }
                    WeaningRecordAllocation a = new WeaningRecordAllocation();
                    a.setCageId(cage.getId());
                    a.setAllocCount(weaningCount);
                    allocations.add(a);
                } else {
                    allocations.addAll(
                        pickCommodityCageAllocations(houseId, weaningCount)
                    );
                    if (allocations.isEmpty()) {
                        throw new BizException(400, "没有可用商品兔笼位");
                    }
                }
            }

            WeaningRecord record = new WeaningRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setBreedingCycleId(cycle.getId());
            record.setRabbitId(rabbitId);
            record.setTargetCageId(targetId);
            record.setInCageId(
                allocations.isEmpty() ? null : allocations.get(0).getCageId()
            );
            record.setWeaningDate(weaningDate);
            record.setWeaningCount(weaningCount);
            record.setWaitingCount(0);
            record.setAvgWeight(avgWeight);
            record.setRemark(remark);
            record.setCreateBy(String.valueOf(userId));
            record.setUpdateBy(String.valueOf(userId));
            weaningRecordMapper.insert(record);

            breedingPerformanceMapper.ensureExists(houseId, rabbitId);
            breedingPerformanceMapper.addWeaning(
                houseId,
                rabbitId,
                weaningCount
            );

            String fromStatus = br.getCurrentStatus();
            Date nextDate = DateUtil.plusDays(
                weaningDate,
                gs.getPostpartumDays()
            );
            cycle.setStatus("已断奶");
            cycle.setCurrentNursingKits(0);
            cycle.setWeanedKits(weaningCount);
            int careBase = Math.max(
                0,
                valueOrZero(cycle.getLiveKits()) +
                valueOrZero(cycle.getFosterInKits()) -
                valueOrZero(cycle.getFosterOutKits())
            );
            cycle.setPreweaningLossKits(Math.max(0, careBase - weaningCount));
            cycle.setWeaningDate(weaningDate);
            cycle.setAvgWeaningWeight(avgWeight);
            cycle.setLactationDays(
                DateUtil.daysBetween(cycle.getBirthDate(), weaningDate)
            );
            cycle.setNextEventDate(null);
            cycle.setNextEventType(null);
            cycle.setClosedAt(weaningDate);
            cycle.setCloseReason("断奶完成");
            cycle.setRemark(remark);
            cycle.setUpdateBy(String.valueOf(userId));
            int rows = breedingCycleMapper.update(cycle, "哺乳中");
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
            breedingCycleMapper.closeOverlaps(
                houseId,
                batchId,
                rabbitId,
                cycle.getCycleNo(),
                weaningDate,
                String.valueOf(userId)
            );
            BreedingCycle display = syncBreedingSummary(
                userId,
                houseId,
                batchId,
                rabbitId,
                br,
                "休整期",
                weaningDate,
                nextDate,
                "催情",
                cycle.getMaleRabbitId()
            );
            insertHistory(
                userId,
                houseId,
                batchId,
                rabbitId,
                fromStatus,
                display == null ? "休整期" : display.getStatus(),
                "断奶",
                record.getId(),
                "weaning_records"
            );

            if (weaningCount == 0) {
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }
            for (WeaningRecordAllocation a : allocations) {
                a.setWeaningRecordId(record.getId());
            }
            weaningRecordAllocationMapper.insertBatch(allocations);

            List<Rabbit> kits = new ArrayList<Rabbit>();
            List<RabbitStatusHistory> kitHistories = new ArrayList<RabbitStatusHistory>();
            int idx = 0;
            for (WeaningRecordAllocation a : allocations) {
                int add = a.getAllocCount() == null ? 0 : a.getAllocCount();
                if (add <= 0) {
                    continue;
                }
                if (
                    cageMapper.incrementCommodityRabbitCountWithinCapacity(
                        houseId,
                        a.getCageId(),
                        add,
                        commodityCageCapacity,
                        String.valueOf(userId)
                    ) != 1
                ) {
                    throw new BizException(
                        409,
                        "笼位状态或容量已变化，请刷新后重试"
                    );
                }

                for (int i = 0; i < add; i++) {
                    Rabbit kid = new Rabbit();
                    kid.setHouseId(houseId);
                    kid.setCageId(a.getCageId());
                    kid.setMotherId(rabbitId);
                    kid.setFatherId(cycle.getMaleRabbitId());
                    kid.setBirthBatchId(batchId);
                    kid.setBirthCycleId(cycle.getId());
                    kid.setType("2");
                    kid.setGender(pickKidGender(idx, weaningCount, m, f));
                    kid.setArrivalMethod("1");
                    kid.setArrivalDate(weaningDate);
                    kid.setWeight(avgWeight);
                    kid.setGrowthStage("GROWING");
                    kid.setIsActive(Boolean.TRUE);
                    kid.setIsQuarantined(Boolean.FALSE);
                    kid.setRequestId(deriveBoundedRequestId(requestId, "kit-" + idx));
                    kid.setCreateBy(String.valueOf(userId));
                    kid.setUpdateBy(String.valueOf(userId));
                    kits.add(kid);
                    idx++;
                }
            }

            insertRabbitsAndHydrateIds(kits);

            Date saleDate = DateUtil.plusDays(weaningDate, gs.getSaleDays());
            List<BatchRabbit> kitLinks = new ArrayList<BatchRabbit>();
            for (Rabbit kid : kits) {
                BatchRabbit kbr = new BatchRabbit();
                kbr.setBatchId(batchId);
                kbr.setRabbitId(kid.getId());
                kbr.setJoinReason("断奶");
                kbr.setBatchRole("fattening");
                kbr.setCurrentStatus("成长期");
                kbr.setLastEventDate(weaningDate);
                kbr.setNextEventDate(saleDate);
                kbr.setNextEventType("出售");
                kbr.setIsActive(Boolean.TRUE);
                kbr.setJoinDate(weaningDate);
                kbr.setCreateBy(String.valueOf(userId));
                kbr.setUpdateBy(String.valueOf(userId));
                kitLinks.add(kbr);

                RabbitStatusHistory h = new RabbitStatusHistory();
                h.setHouseId(houseId);
                h.setRabbitId(kid.getId());
                h.setBatchId(batchId);
                h.setFromStatus(null);
                h.setToStatus("成长期");
                h.setChangeTime(DateUtil.now());
                h.setReason("断奶生成仔兔");
                h.setRelatedRecordId(record.getId());
                h.setRelatedRecordTable("weaning_records");
                h.setCreateBy(String.valueOf(userId));
                h.setUpdateBy(String.valueOf(userId));
                kitHistories.add(h);
            }
            insertBatchRabbitLinks(kitLinks);
            insertStatusHistories(kitHistories);
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
    public void aphrodisiacStart(
        Long userId,
        Long houseId,
        Long batchId,
        List<Long> rabbitIds,
        String requestId
    ) {
        String api = "batch.aphrodisiacStart";
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
            requireBatchActive(houseId, batchId);
            Date now = DateUtil.now();

            for (Long rabbitId : rabbitIds) {
                BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                    houseId,
                    batchId,
                    rabbitId
                );
                if (br == null) {
                    throw new BizException(400, "兔子不在该批次中");
                }
                String fromStatus = br.getCurrentStatus();
                if (
                    !"待催情".equals(fromStatus) &&
                    !"休整期".equals(fromStatus) &&
                    !"哺乳中".equals(fromStatus)
                ) {
                    throw new BizException(400, "当前状态不允许催情");
                }
                int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    br.getId(),
                    fromStatus,
                    "催情中",
                    now,
                    null,
                    null,
                    br.getMaleRabbitId(),
                    String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                insertHistory(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    fromStatus,
                    "催情中",
                    "催情开始",
                    null,
                    null
                );
            }
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
    public void aphrodisiacFinish(
        Long userId,
        Long houseId,
        Long batchId,
        List<Long> rabbitIds,
        String requestId
    ) {
        String api = "batch.aphrodisiacFinish";
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
            requireBatchActive(houseId, batchId);
            GlobalSetting gs = requireSetting(userId, houseId);
            Date now = DateUtil.now();
            Date next = DateUtil.plusDays(now, gs.getAphrodisiacDays());

            for (Long rabbitId : rabbitIds) {
                BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                    houseId,
                    batchId,
                    rabbitId
                );
                if (br == null) {
                    throw new BizException(400, "兔子不在该批次中");
                }
                String fromStatus = br.getCurrentStatus();
                if (!"催情中".equals(fromStatus)) {
                    throw new BizException(400, "当前状态不允许完成催情");
                }
                int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    br.getId(),
                    fromStatus,
                    "待配种",
                    now,
                    next,
                    "配种",
                    br.getMaleRabbitId(),
                    String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                insertHistory(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    fromStatus,
                    "待配种",
                    "催情完成",
                    null,
                    null
                );
            }
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
            closeOpenCyclesByBatch(
                houseId,
                batchId,
                x,
                force ? "批次强制结束" : "批次结束",
                String.valueOf(userId)
            );
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
            closeOpenCyclesByBatch(
                houseId,
                batchId,
                x,
                force ? "批次强制结束" : "批次结束",
                String.valueOf(userId)
            );
            if (breedingCycleMapper.countOpenByBatch(houseId, batchId) != 0) {
                throw new BizException(409, "批次仍有进行中的繁殖周期");
            }
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

    public List<BatchRabbit> listDueBatchEvents(Long houseId) {
        return batchRabbitMapper.selectDueEventsByHouse(
            houseId,
            DateUtil.now()
        );
    }

    public List<BatchRabbit> listDueBatchEvents(
        Long houseId,
        boolean onlyUnnotified
    ) {
        if (onlyUnnotified) {
            return batchRabbitMapper.selectDueUnnotifiedEventsByHouse(
                houseId,
                DateUtil.now()
            );
        }
        return batchRabbitMapper.selectDueEventsByHouse(
            houseId,
            DateUtil.now()
        );
    }

    public List<BreedingCycle> listDueBreedingCycleEvents(
        Long houseId,
        boolean onlyUnnotified
    ) {
        return breedingCycleMapper.selectDueEventsByHouse(
            houseId,
            DateUtil.now(),
            onlyUnnotified
        );
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

    private BreedingCycle requireCycleForUpdate(
        Long houseId,
        Long batchId,
        Long motherRabbitId,
        Long breedingCycleId,
        List<String> statuses,
        boolean oldest,
        String missingMessage
    ) {
        BreedingCycle cycle;
        if (breedingCycleId != null) {
            cycle = breedingCycleMapper.selectByIdForUpdate(
                houseId,
                batchId,
                motherRabbitId,
                breedingCycleId
            );
            if (cycle != null && !statuses.contains(cycle.getStatus())) {
                throw new BizException(400, "所选繁殖周期状态不允许当前操作");
            }
        } else if (oldest) {
            cycle = breedingCycleMapper.selectOldestByStatusesForUpdate(
                houseId,
                batchId,
                motherRabbitId,
                statuses
            );
        } else {
            cycle = breedingCycleMapper.selectLatestByStatusesForUpdate(
                houseId,
                batchId,
                motherRabbitId,
                statuses
            );
        }
        if (cycle == null) {
            throw new BizException(400, missingMessage);
        }
        return cycle;
    }

    private BreedingCycle syncBreedingSummary(
        Long userId,
        Long houseId,
        Long batchId,
        Long motherRabbitId,
        BatchRabbit batchRabbit,
        String fallbackStatus,
        Date fallbackLastEventDate,
        Date fallbackNextEventDate,
        String fallbackNextEventType,
        Long fallbackMaleRabbitId
    ) {
        BreedingCycle display = breedingCycleMapper.selectDisplayOpen(
            houseId,
            batchId,
            motherRabbitId
        );
        BreedingCycle latest = breedingCycleMapper.selectLatest(
            houseId,
            batchId,
            motherRabbitId
        );
        String status = fallbackStatus;
        Date lastEventDate = fallbackLastEventDate;
        Date nextEventDate = fallbackNextEventDate;
        String nextEventType = fallbackNextEventType;
        Long maleRabbitId = fallbackMaleRabbitId;
        if (display != null) {
            status = display.getStatus();
            lastEventDate = "哺乳中".equals(display.getStatus())
                ? display.getBirthDate()
                : display.getMatingDate();
            nextEventDate = display.getNextEventDate();
            nextEventType = display.getNextEventType();
            maleRabbitId = display.getMaleRabbitId();
        }
        if (status == null) {
            throw new BizException(500, "无法生成母兔繁殖状态摘要");
        }
        int rows = batchRabbitMapper.updateBreedingSummary(
            houseId,
            batchRabbit.getId(),
            status,
            lastEventDate,
            nextEventDate,
            nextEventType,
            maleRabbitId,
            latest == null ? null : latest.getId(),
            breedingCycleMapper.sumCurrentNursingKits(
                houseId,
                batchId,
                motherRabbitId
            ),
            breedingCycleMapper.countNursingLitters(
                houseId,
                batchId,
                motherRabbitId
            ),
            String.valueOf(userId)
        );
        if (rows <= 0) {
            throw new BizException(409, "母兔状态已变化，请刷新后重试");
        }
        return display;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void closeOpenCyclesByBatch(
        Long houseId,
        Long batchId,
        Date closedAt,
        String reason,
        String updateBy
    ) {
        int rows;
        do {
            rows = breedingCycleMapper.closeOpenByBatch(
                houseId,
                batchId,
                closedAt,
                reason,
                updateBy,
                BULK_WRITE_SIZE
            );
        } while (rows == BULK_WRITE_SIZE);
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
