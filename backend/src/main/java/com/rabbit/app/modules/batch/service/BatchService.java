package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import com.rabbit.app.modules.batch.entity.PrepartumRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {

    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;
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

            List<BatchRabbit> links = new ArrayList<BatchRabbit>();
            for (Long rid : femaleRabbitIds) {
                Rabbit r = rabbitMapper.selectById(houseId, rid);
                if (r == null || !houseId.equals(r.getHouseId())) {
                    throw new BizException(400, "母兔不存在");
                }
                if (r.getIsActive() == null || !r.getIsActive()) {
                    throw new BizException(400, "母兔不在场");
                }
                if (!"0".equals(r.getGender())) {
                    throw new BizException(400, "仅母兔可加入繁殖批次");
                }
                if (!"0".equals(r.getType()) && !"1".equals(r.getType())) {
                    throw new BizException(400, "仅种兔/后备兔可加入繁殖批次");
                }
                if (
                    !batchRabbitMapper
                        .selectActiveByRabbit(houseId, rid)
                        .isEmpty()
                ) {
                    throw new BizException(400, "母兔已在活跃批次中");
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
                rabbitStatusHistoryMapper.insert(h);
            }
            batchRabbitMapper.insertBatch(links);

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
            Batch batch = requireBatchActive(houseId, batchId);
            GlobalSetting gs = requireSetting(userId, houseId);

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                houseId,
                batchId,
                femaleRabbitId
            );
            if (br == null) {
                throw new BizException(400, "母兔不在该批次中");
            }
            if (!"待配种".equals(br.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许配种");
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

            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                houseId,
                br.getId(),
                fromStatus,
                "已配种",
                matingDate,
                nextDate,
                "摸胎",
                maleRabbitId,
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

    @Transactional
    public void pregnancyCheck(
        Long userId,
        Long houseId,
        Long batchId,
        Long rabbitId,
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

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (!"已配种".equals(br.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许摸胎");
            }
            if (
                !"怀孕".equals(result) &&
                !"空怀".equals(result) &&
                !"不确定".equals(result)
            ) {
                throw new BizException(400, "摸胎结果不正确");
            }

            PregnancyCheckRecord record = new PregnancyCheckRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setRabbitId(rabbitId);
            record.setCheckDate(checkDate);
            record.setResult(result);
            record.setRemark(remark);
            record.setCreateBy(String.valueOf(userId));
            record.setUpdateBy(String.valueOf(userId));
            pregnancyCheckRecordMapper.insert(record);

            breedingPerformanceMapper.ensureExists(houseId, rabbitId);

            String fromStatus = br.getCurrentStatus();
            if ("怀孕".equals(result)) {
                if (br.getLastEventDate() == null) {
                    throw new BizException(400, "缺少配种日期");
                }
                Date dueDate = DateUtil.plusDays(br.getLastEventDate(), 30);
                Date nextDate = DateUtil.minusDays(
                    dueDate,
                    gs.getPrepartumDays()
                );
                int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    br.getId(),
                    fromStatus,
                    "怀孕确认",
                    br.getLastEventDate(),
                    nextDate,
                    "备产",
                    br.getMaleRabbitId(),
                    String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
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
                int rows = batchRabbitMapper.deactivateIfActive(
                    houseId,
                    br.getId(),
                    DateUtil.now(),
                    "空怀退出批次",
                    String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
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

            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                houseId,
                br.getId(),
                fromStatus,
                "不确定",
                br.getLastEventDate(),
                br.getNextEventDate(),
                br.getNextEventType(),
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

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (!"怀孕确认".equals(br.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许备产");
            }
            if (!"备产".equals(br.getNextEventType())) {
                throw new BizException(400, "当前无需备产");
            }
            if (br.getLastEventDate() == null) {
                throw new BizException(400, "缺少配种日期");
            }

            PrepartumRecord record = new PrepartumRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setRabbitId(rabbitId);
            record.setActionDate(actionDate);
            record.setRemark(remark);
            record.setCreateBy(String.valueOf(userId));
            record.setUpdateBy(String.valueOf(userId));
            prepartumRecordMapper.insert(record);

            Date dueDate = DateUtil.plusDays(br.getLastEventDate(), 30);
            int rows = batchRabbitMapper.updateNextEventIfStatus(
                houseId,
                br.getId(),
                br.getCurrentStatus(),
                dueDate,
                "分娩",
                String.valueOf(userId)
            );
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
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

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (!"怀孕确认".equals(br.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许分娩");
            }

            ParturitionRecord record = new ParturitionRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
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
            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                houseId,
                br.getId(),
                fromStatus,
                "哺乳中",
                birthDate,
                nextDate,
                "断奶",
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

            BatchRabbit br = batchRabbitMapper.selectActiveByBatchAndRabbit(
                houseId,
                batchId,
                rabbitId
            );
            if (br == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (!"哺乳中".equals(br.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许断奶");
            }
            if (weaningCount < 0) {
                throw new BizException(400, "断奶数量错误");
            }
            int m = maleCount == null ? 0 : maleCount;
            int f = femaleCount == null ? 0 : femaleCount;
            if (m + f != 0 && m + f != weaningCount) {
                throw new BizException(400, "公母数量之和需等于断奶数量");
            }

            Long targetId =
                targetCageId != null && targetCageId > 0 ? targetCageId : null;
            Cage cage = null;
            List<WeaningRecordAllocation> allocations =
                new ArrayList<WeaningRecordAllocation>();
            if (weaningCount > 0) {
                if (targetId != null) {
                    cage = cageMapper.selectById(houseId, targetId);
                    if (cage == null) {
                        throw new BizException(400, "目标笼位不存在");
                    }
                    if (cage.getIsEnabled() != null && !cage.getIsEnabled()) {
                        throw new BizException(400, "目标笼位已停用");
                    }
                    String st = cage.getStatus();
                    if (st != null && !"0".equals(st) && !"3".equals(st)) {
                        throw new BizException(400, "目标笼位不是商品兔笼位");
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
                    cage = cageMapper.selectById(
                        houseId,
                        allocations.get(0).getCageId()
                    );
                }
            }

            WeaningRecord record = new WeaningRecord();
            record.setHouseId(houseId);
            record.setBatchId(batchId);
            record.setRabbitId(rabbitId);
            record.setTargetCageId(targetId);
            record.setInCageId(cage == null ? null : cage.getId());
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
            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                houseId,
                br.getId(),
                fromStatus,
                "休整期",
                weaningDate,
                nextDate,
                "催情",
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
                "休整期",
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
            int idx = 0;
            for (WeaningRecordAllocation a : allocations) {
                Cage c = cageMapper.selectById(houseId, a.getCageId());
                if (c == null) {
                    throw new BizException(400, "笼位不存在");
                }
                int add = a.getAllocCount() == null ? 0 : a.getAllocCount();
                if (add <= 0) {
                    continue;
                }
                int oldCount =
                    c.getRabbitCount() == null ? 0 : c.getRabbitCount();
                String newStatus = "0".equals(c.getStatus())
                    ? "3"
                    : c.getStatus();
                cageMapper.updateRabbitCountAndStatus(
                    houseId,
                    c.getId(),
                    oldCount + add,
                    newStatus,
                    String.valueOf(userId)
                );

                for (int i = 0; i < add; i++) {
                    Rabbit kid = new Rabbit();
                    kid.setHouseId(houseId);
                    kid.setCageId(c.getId());
                    kid.setMotherId(rabbitId);
                    kid.setType("2");
                    kid.setGender(pickKidGender(idx, weaningCount, m, f));
                    kid.setArrivalMethod("1");
                    kid.setArrivalDate(weaningDate);
                    kid.setWeight(avgWeight);
                    kid.setIsActive(Boolean.TRUE);
                    kid.setIsQuarantined(Boolean.FALSE);
                    kid.setCreateBy(String.valueOf(userId));
                    kid.setUpdateBy(String.valueOf(userId));
                    rabbitMapper.insert(kid);
                    kits.add(kid);
                    idx++;
                }
            }

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
                rabbitStatusHistoryMapper.insert(h);
            }
            batchRabbitMapper.insertBatch(kitLinks);
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
        List<Cage> cages = cageMapper.selectByHouseId(houseId);
        List<Cage> candidates = new ArrayList<Cage>();
        for (Cage c : cages) {
            if (c.getIsEnabled() != null && !c.getIsEnabled()) {
                continue;
            }
            String st = c.getStatus();
            if (!"0".equals(st) && !"3".equals(st)) {
                continue;
            }
            candidates.add(c);
        }
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
                    !"待催情".equals(fromStatus) && !"休整期".equals(fromStatus)
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
                    "已出售",
                    "出售",
                    null,
                    null
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
            Batch b = requireBatch(houseId, batchId);
            Date x = endDate == null ? DateUtil.now() : endDate;
            int active = batchRabbitMapper.countActiveByBatch(batchId);
            if (active != 0) {
                if (!force) {
                    throw new BizException(
                        400,
                        "批次仍有活跃兔，force=true 才能强制结束"
                    );
                }
                batchRabbitMapper.deactivateByBatch(
                    houseId,
                    batchId,
                    x,
                    remark == null ? "手动结束批次" : remark,
                    String.valueOf(userId)
                );
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

    private Batch requireBatchActive(Long houseId, Long batchId) {
        Batch b = requireBatch(houseId, batchId);
        if ("已完成".equals(b.getStatus())) {
            throw new BizException(400, "批次已完成");
        }
        return b;
    }

    private GlobalSetting requireSetting(Long userId, Long houseId) {
        return settingService.getEffectiveSetting(userId, houseId);
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
