package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordAllocationMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchWeaningService {
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitMapper rabbitMapper;
    private final SettingService settingService;
    private final WeaningRecordMapper weaningRecordMapper;
    private final WeaningRecordAllocationMapper weaningRecordAllocationMapper;
    private final BreedingPerformanceMapper breedingPerformanceMapper;
    private final CageMapper cageMapper;
    private final RequestDedupService requestDedupService;
    private final BatchWorkflowSupport workflowSupport;
    private final int commodityCageCapacity;

    public BatchWeaningService(
            BatchRabbitMapper batchRabbitMapper,
            RabbitMapper rabbitMapper,
            SettingService settingService,
            WeaningRecordMapper weaningRecordMapper,
            WeaningRecordAllocationMapper weaningRecordAllocationMapper,
            BreedingPerformanceMapper breedingPerformanceMapper,
            CageMapper cageMapper,
            RequestDedupService requestDedupService,
            BatchWorkflowSupport workflowSupport,
            @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitMapper = rabbitMapper;
        this.settingService = settingService;
        this.weaningRecordMapper = weaningRecordMapper;
        this.weaningRecordAllocationMapper = weaningRecordAllocationMapper;
        this.breedingPerformanceMapper = breedingPerformanceMapper;
        this.cageMapper = cageMapper;
        this.requestDedupService = requestDedupService;
        this.workflowSupport = workflowSupport;
        this.commodityCageCapacity = commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
    }

    @Transactional
    public void wean(
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
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            workflowSupport.requireActive(houseId, batchId);
            GlobalSetting setting = settingService.getEffectiveSetting(userId, houseId);

            BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbit(
                    houseId,
                    batchId,
                    rabbitId
            );
            if (link == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (!"哺乳中".equals(link.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许断奶");
            }
            if (weaningCount < 0) {
                throw new BizException(400, "断奶数量错误");
            }
            int males = maleCount == null ? 0 : maleCount;
            int females = femaleCount == null ? 0 : femaleCount;
            if (males + females != 0 && males + females != weaningCount) {
                throw new BizException(400, "公母数量之和需等于断奶数量");
            }

            Long targetId = targetCageId != null && targetCageId > 0 ? targetCageId : null;
            Cage cage = null;
            List<WeaningRecordAllocation> allocations = new ArrayList<>();
            if (weaningCount > 0) {
                if (targetId != null) {
                    cage = cageMapper.selectById(houseId, targetId);
                    if (cage == null) {
                        throw new BizException(400, "目标笼位不存在");
                    }
                    if (cage.getIsEnabled() != null && !cage.getIsEnabled()) {
                        throw new BizException(400, "目标笼位已停用");
                    }
                    String status = cage.getStatus();
                    if (status != null && !"0".equals(status) && !"3".equals(status)) {
                        throw new BizException(400, "目标笼位不是商品兔笼位");
                    }
                    WeaningRecordAllocation allocation = new WeaningRecordAllocation();
                    allocation.setCageId(cage.getId());
                    allocation.setAllocCount(weaningCount);
                    allocations.add(allocation);
                } else {
                    allocations.addAll(pickCommodityCageAllocations(houseId, weaningCount));
                    if (allocations.isEmpty()) {
                        throw new BizException(400, "没有可用商品兔笼位");
                    }
                    cage = cageMapper.selectById(houseId, allocations.get(0).getCageId());
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
            breedingPerformanceMapper.addWeaning(houseId, rabbitId, weaningCount);

            String fromStatus = link.getCurrentStatus();
            Date nextDate = DateUtil.plusDays(weaningDate, setting.getPostpartumDays());
            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    link.getId(),
                    fromStatus,
                    "休整期",
                    weaningDate,
                    nextDate,
                    "催情",
                    link.getMaleRabbitId(),
                    String.valueOf(userId)
            );
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
            workflowSupport.insertHistory(
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
            for (WeaningRecordAllocation allocation : allocations) {
                allocation.setWeaningRecordId(record.getId());
            }
            weaningRecordAllocationMapper.insertBatch(allocations);

            List<Rabbit> kits = new ArrayList<>();
            int index = 0;
            for (WeaningRecordAllocation allocation : allocations) {
                Cage targetCage = cageMapper.selectById(houseId, allocation.getCageId());
                if (targetCage == null) {
                    throw new BizException(400, "笼位不存在");
                }
                int add = allocation.getAllocCount() == null ? 0 : allocation.getAllocCount();
                if (add <= 0) {
                    continue;
                }
                int oldCount = targetCage.getRabbitCount() == null ? 0 : targetCage.getRabbitCount();
                String newStatus = "0".equals(targetCage.getStatus()) ? "3" : targetCage.getStatus();
                cageMapper.updateRabbitCountAndStatus(
                        houseId,
                        targetCage.getId(),
                        oldCount + add,
                        newStatus,
                        String.valueOf(userId)
                );

                for (int i = 0; i < add; i++) {
                    Rabbit kit = new Rabbit();
                    kit.setHouseId(houseId);
                    kit.setCageId(targetCage.getId());
                    kit.setMotherId(rabbitId);
                    kit.setType("2");
                    kit.setGender(pickKitGender(index, males, females));
                    kit.setArrivalMethod("1");
                    kit.setArrivalDate(weaningDate);
                    kit.setWeight(avgWeight);
                    kit.setIsActive(Boolean.TRUE);
                    kit.setIsQuarantined(Boolean.FALSE);
                    kit.setCreateBy(String.valueOf(userId));
                    kit.setUpdateBy(String.valueOf(userId));
                    rabbitMapper.insert(kit);
                    kits.add(kit);
                    index++;
                }
            }

            Date saleDate = DateUtil.plusDays(weaningDate, setting.getSaleDays());
            List<BatchRabbit> kitLinks = new ArrayList<>();
            for (Rabbit kit : kits) {
                BatchRabbit kitLink = new BatchRabbit();
                kitLink.setBatchId(batchId);
                kitLink.setRabbitId(kit.getId());
                kitLink.setJoinReason("断奶");
                kitLink.setBatchRole("fattening");
                kitLink.setCurrentStatus("成长期");
                kitLink.setLastEventDate(weaningDate);
                kitLink.setNextEventDate(saleDate);
                kitLink.setNextEventType("出售");
                kitLink.setIsActive(Boolean.TRUE);
                kitLink.setJoinDate(weaningDate);
                kitLink.setCreateBy(String.valueOf(userId));
                kitLink.setUpdateBy(String.valueOf(userId));
                kitLinks.add(kitLink);

                workflowSupport.insertHistory(
                        userId,
                        houseId,
                        batchId,
                        kit.getId(),
                        null,
                        "成长期",
                        "断奶生成仔兔",
                        record.getId(),
                        "weaning_records"
                );
            }
            batchRabbitMapper.insertBatch(kitLinks);
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private List<WeaningRecordAllocation> pickCommodityCageAllocations(Long houseId, int count) {
        List<Cage> candidates = new ArrayList<>();
        for (Cage cage : cageMapper.selectByHouseId(houseId)) {
            if (cage.getIsEnabled() != null && !cage.getIsEnabled()) {
                continue;
            }
            if (!"0".equals(cage.getStatus()) && !"3".equals(cage.getStatus())) {
                continue;
            }
            candidates.add(cage);
        }

        List<WeaningRecordAllocation> rows = new ArrayList<>();
        int left = count;
        for (Cage cage : candidates) {
            if (!"0".equals(cage.getStatus())) {
                continue;
            }
            left = allocateToCage(cage, left, rows);
            if (left <= 0) {
                return rows;
            }
        }
        for (Cage cage : candidates) {
            if (!"3".equals(cage.getStatus())) {
                continue;
            }
            left = allocateToCage(cage, left, rows);
            if (left <= 0) {
                return rows;
            }
        }
        throw new BizException(400, "没有可用商品兔笼位");
    }

    private int allocateToCage(
            Cage cage,
            int left,
            List<WeaningRecordAllocation> rows
    ) {
        int used = cage.getRabbitCount() == null ? 0 : cage.getRabbitCount();
        int remain = commodityCageCapacity - used;
        if (remain <= 0) {
            return left;
        }
        int add = Math.min(remain, left);
        WeaningRecordAllocation allocation = new WeaningRecordAllocation();
        allocation.setCageId(cage.getId());
        allocation.setAllocCount(add);
        rows.add(allocation);
        return left - add;
    }

    private String pickKitGender(int index, int maleCount, int femaleCount) {
        if (maleCount + femaleCount == 0) {
            return "0";
        }
        return index < maleCount ? "1" : "0";
    }
}
