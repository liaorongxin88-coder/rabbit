package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import com.rabbit.app.modules.batch.entity.PrepartumRecord;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import com.rabbit.app.modules.batch.mapper.PregnancyCheckRecordMapper;
import com.rabbit.app.modules.batch.mapper.PrepartumRecordMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchBreedingService {
    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitMapper rabbitMapper;
    private final SettingService settingService;
    private final PregnancyCheckRecordMapper pregnancyCheckRecordMapper;
    private final PrepartumRecordMapper prepartumRecordMapper;
    private final BreedingPerformanceMapper breedingPerformanceMapper;
    private final RequestDedupService requestDedupService;
    private final BatchWorkflowSupport workflowSupport;

    public BatchBreedingService(
            BatchMapper batchMapper,
            BatchRabbitMapper batchRabbitMapper,
            RabbitMapper rabbitMapper,
            SettingService settingService,
            PregnancyCheckRecordMapper pregnancyCheckRecordMapper,
            PrepartumRecordMapper prepartumRecordMapper,
            BreedingPerformanceMapper breedingPerformanceMapper,
            RequestDedupService requestDedupService,
            BatchWorkflowSupport workflowSupport
    ) {
        this.batchMapper = batchMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitMapper = rabbitMapper;
        this.settingService = settingService;
        this.pregnancyCheckRecordMapper = pregnancyCheckRecordMapper;
        this.prepartumRecordMapper = prepartumRecordMapper;
        this.breedingPerformanceMapper = breedingPerformanceMapper;
        this.requestDedupService = requestDedupService;
        this.workflowSupport = workflowSupport;
    }

    @Transactional
    public void mate(
            Long userId,
            Long houseId,
            Long batchId,
            Long femaleRabbitId,
            Long maleRabbitId,
            Date matingDate,
            String requestId
    ) {
        String api = "batch.mating";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch batch = workflowSupport.requireActive(houseId, batchId);
            GlobalSetting setting = settingService.getEffectiveSetting(userId, houseId);

            BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbit(
                    houseId,
                    batchId,
                    femaleRabbitId
            );
            if (link == null) {
                throw new BizException(400, "母兔不在该批次中");
            }
            if (!"待配种".equals(link.getCurrentStatus())) {
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
            if (!"0".equals(female.getType()) && !"1".equals(female.getType())) {
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

            String fromStatus = link.getCurrentStatus();
            Date nextDate = DateUtil.plusDays(matingDate, setting.getPalpationDays());
            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    link.getId(),
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

            workflowSupport.insertHistory(
                    userId,
                    houseId,
                    batchId,
                    femaleRabbitId,
                    fromStatus,
                    "已配种",
                    "配种",
                    null,
                    null
            );
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void checkPregnancy(
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
            if (!"已配种".equals(link.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许摸胎");
            }
            if (!"怀孕".equals(result) && !"空怀".equals(result) && !"不确定".equals(result)) {
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
            String fromStatus = link.getCurrentStatus();
            if ("怀孕".equals(result)) {
                if (link.getLastEventDate() == null) {
                    throw new BizException(400, "缺少配种日期");
                }
                Date dueDate = DateUtil.plusDays(link.getLastEventDate(), 30);
                Date nextDate = DateUtil.minusDays(dueDate, setting.getPrepartumDays());
                int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                        houseId,
                        link.getId(),
                        fromStatus,
                        "怀孕确认",
                        link.getLastEventDate(),
                        nextDate,
                        "备产",
                        link.getMaleRabbitId(),
                        String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                breedingPerformanceMapper.incBreedingResult(houseId, rabbitId, true);
                workflowSupport.insertHistory(
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
                int rows = batchRabbitMapper.deactivateIfActive(
                        houseId,
                        link.getId(),
                        now,
                        "空怀退出批次",
                        String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }
                breedingPerformanceMapper.incBreedingResult(houseId, rabbitId, false);
                workflowSupport.insertHistory(
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
                workflowSupport.completeIfEmpty(houseId, batchId, userId, now);
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }

            int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                    houseId,
                    link.getId(),
                    fromStatus,
                    "不确定",
                    link.getLastEventDate(),
                    link.getNextEventDate(),
                    link.getNextEventType(),
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
                    "不确定",
                    "摸胎",
                    record.getId(),
                    "pregnancy_check_records"
            );
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void finishPrepartum(
            Long userId,
            Long houseId,
            Long batchId,
            Long rabbitId,
            Date actionDate,
            String remark,
            String requestId
    ) {
        String api = "batch.prepartumFinish";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            workflowSupport.requireActive(houseId, batchId);
            settingService.getEffectiveSetting(userId, houseId);

            BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbit(
                    houseId,
                    batchId,
                    rabbitId
            );
            if (link == null) {
                throw new BizException(400, "兔子不在该批次中");
            }
            if (!"怀孕确认".equals(link.getCurrentStatus())) {
                throw new BizException(400, "当前状态不允许备产");
            }
            if (!"备产".equals(link.getNextEventType())) {
                throw new BizException(400, "当前无需备产");
            }
            if (link.getLastEventDate() == null) {
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

            Date dueDate = DateUtil.plusDays(link.getLastEventDate(), 30);
            int rows = batchRabbitMapper.updateNextEventIfStatus(
                    houseId,
                    link.getId(),
                    link.getCurrentStatus(),
                    dueDate,
                    "分娩",
                    String.valueOf(userId)
            );
            if (rows <= 0) {
                throw new BizException(409, "状态已变化，请刷新后重试");
            }
            workflowSupport.insertHistoryAt(
                    userId,
                    houseId,
                    batchId,
                    rabbitId,
                    link.getCurrentStatus(),
                    link.getCurrentStatus(),
                    "备产完成",
                    actionDate,
                    record.getId(),
                    "prepartum_records"
            );
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }
}
