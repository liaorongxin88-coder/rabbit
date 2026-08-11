package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import com.rabbit.app.modules.batch.mapper.ParturitionRecordMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchParturitionService {
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitMapper rabbitMapper;
    private final SettingService settingService;
    private final ParturitionRecordMapper parturitionRecordMapper;
    private final BreedingPerformanceMapper breedingPerformanceMapper;
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final CageMapper cageMapper;
    private final RequestDedupService requestDedupService;
    private final BatchWorkflowSupport workflowSupport;

    public BatchParturitionService(
            BatchRabbitMapper batchRabbitMapper,
            RabbitMapper rabbitMapper,
            SettingService settingService,
            ParturitionRecordMapper parturitionRecordMapper,
            BreedingPerformanceMapper breedingPerformanceMapper,
            RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper,
            RabbitDepartureRecordMapper rabbitDepartureRecordMapper,
            RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
            CageMapper cageMapper,
            RequestDedupService requestDedupService,
            BatchWorkflowSupport workflowSupport
    ) {
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitMapper = rabbitMapper;
        this.settingService = settingService;
        this.parturitionRecordMapper = parturitionRecordMapper;
        this.breedingPerformanceMapper = breedingPerformanceMapper;
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.cageMapper = cageMapper;
        this.requestDedupService = requestDedupService;
        this.workflowSupport = workflowSupport;
    }

    @Transactional
    public void record(
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
            if (!"怀孕确认".equals(link.getCurrentStatus())) {
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

            String fromStatus = link.getCurrentStatus();
            if (failed) {
                recordFailedParturition(
                        userId,
                        houseId,
                        batchId,
                        rabbitId,
                        remark,
                        requestId,
                        link,
                        record
                );
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }

            Date nextDate = DateUtil.plusDays(birthDate, setting.getWeaningDays());
            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    link.getId(),
                    fromStatus,
                    "哺乳中",
                    birthDate,
                    nextDate,
                    "断奶",
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
                    "哺乳中",
                    "分娩",
                    record.getId(),
                    "parturition_records"
            );
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void recordFailedParturition(
            Long userId,
            Long houseId,
            Long batchId,
            Long rabbitId,
            String remark,
            String requestId,
            BatchRabbit link,
            ParturitionRecord record
    ) {
        Date now = DateUtil.now();
        String operator = String.valueOf(userId);

        RabbitAbnormalCondition condition = new RabbitAbnormalCondition();
        condition.setRabbitId(rabbitId);
        condition.setHouseId(houseId);
        condition.setWarningStatus("流产");
        condition.setWarningTime(now);
        condition.setIsDeal(Boolean.FALSE);
        condition.setRemark(remark);
        condition.setCreateBy(operator);
        condition.setUpdateBy(operator);
        rabbitAbnormalConditionMapper.insert(condition);

        int rows = batchRabbitMapper.deactivateIfActive(
                houseId,
                link.getId(),
                now,
                "分娩失败退出批次",
                operator
        );
        if (rows <= 0) {
            throw new BizException(409, "状态已变化，请刷新后重试");
        }

        Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
        if (rabbit == null) {
            throw new BizException(400, "兔子不存在");
        }
        Cage oldCage = cageMapper.selectById(houseId, rabbit.getCageId());
        if (oldCage != null && houseId.equals(oldCage.getHouseId())) {
            int newCount = (oldCage.getRabbitCount() == null ? 0 : oldCage.getRabbitCount()) - 1;
            if (newCount < 0) {
                newCount = 0;
            }
            String status = newCount == 0 ? "0" : oldCage.getStatus();
            cageMapper.updateRabbitCountAndStatus(
                    houseId,
                    oldCage.getId(),
                    newCount,
                    status,
                    operator
            );
        }

        rabbitMapper.updateDeparture(houseId, rabbitId, now, "parturition_fail", operator);
        RabbitDepartureRecord departure = new RabbitDepartureRecord();
        departure.setHouseId(houseId);
        departure.setRabbitId(rabbitId);
        departure.setDepartureType("parturition_fail");
        departure.setDepartureDate(now);
        departure.setReason("流产");
        departure.setRemark(remark);
        departure.setRequestId(requestId);
        departure.setCreateBy(operator);
        departure.setUpdateBy(operator);
        rabbitDepartureRecordMapper.insert(departure);

        RabbitStatusHistory departureHistory = new RabbitStatusHistory();
        departureHistory.setHouseId(houseId);
        departureHistory.setRabbitId(rabbitId);
        departureHistory.setFromStatus("在栏");
        departureHistory.setToStatus("流产离场");
        departureHistory.setChangeTime(now);
        departureHistory.setReason(remark == null ? "流产离场" : remark);
        departureHistory.setRelatedRecordId(departure.getId());
        departureHistory.setRelatedRecordTable("rabbit_departure_records");
        departureHistory.setCreateBy(operator);
        departureHistory.setUpdateBy(operator);
        rabbitStatusHistoryMapper.insert(departureHistory);

        workflowSupport.insertHistory(
                userId,
                houseId,
                batchId,
                rabbitId,
                link.getCurrentStatus(),
                "分娩失败",
                "分娩",
                record.getId(),
                "parturition_records"
        );
        workflowSupport.completeIfEmpty(houseId, batchId, userId, now);
    }
}
