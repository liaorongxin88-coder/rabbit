package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchAphrodisiacService {
    private final BatchRabbitMapper batchRabbitMapper;
    private final SettingService settingService;
    private final RequestDedupService requestDedupService;
    private final BatchWorkflowSupport workflowSupport;

    public BatchAphrodisiacService(
            BatchRabbitMapper batchRabbitMapper,
            SettingService settingService,
            RequestDedupService requestDedupService,
            BatchWorkflowSupport workflowSupport
    ) {
        this.batchRabbitMapper = batchRabbitMapper;
        this.settingService = settingService;
        this.requestDedupService = requestDedupService;
        this.workflowSupport = workflowSupport;
    }

    @Transactional
    public void start(
            Long userId,
            Long houseId,
            Long batchId,
            List<Long> rabbitIds,
            String requestId
    ) {
        String api = "batch.aphrodisiacStart";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            workflowSupport.requireActive(houseId, batchId);
            Date now = DateUtil.now();

            for (Long rabbitId : rabbitIds) {
                BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbit(
                        houseId,
                        batchId,
                        rabbitId
                );
                if (link == null) {
                    throw new BizException(400, "兔子不在该批次中");
                }
                String fromStatus = link.getCurrentStatus();
                if (!"待催情".equals(fromStatus) && !"休整期".equals(fromStatus)) {
                    throw new BizException(400, "当前状态不允许催情");
                }
                int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                        houseId,
                        link.getId(),
                        fromStatus,
                        "催情中",
                        now,
                        null,
                        null,
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
                        "催情中",
                        "催情开始",
                        null,
                        null
                );
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void finish(
            Long userId,
            Long houseId,
            Long batchId,
            List<Long> rabbitIds,
            String requestId
    ) {
        String api = "batch.aphrodisiacFinish";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            workflowSupport.requireActive(houseId, batchId);
            GlobalSetting setting = settingService.getEffectiveSetting(userId, houseId);
            Date now = DateUtil.now();
            Date next = DateUtil.plusDays(now, setting.getAphrodisiacDays());

            for (Long rabbitId : rabbitIds) {
                BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbit(
                        houseId,
                        batchId,
                        rabbitId
                );
                if (link == null) {
                    throw new BizException(400, "兔子不在该批次中");
                }
                String fromStatus = link.getCurrentStatus();
                if (!"催情中".equals(fromStatus)) {
                    throw new BizException(400, "当前状态不允许完成催情");
                }
                int rows = batchRabbitMapper.updateStatusAndEventIfStatus(
                        houseId,
                        link.getId(),
                        fromStatus,
                        "待配种",
                        now,
                        next,
                        "配种",
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
                        "待配种",
                        "催情完成",
                        null,
                        null
                );
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }
}
