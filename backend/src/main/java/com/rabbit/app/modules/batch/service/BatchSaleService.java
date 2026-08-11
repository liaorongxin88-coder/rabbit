package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.service.OutboundEligibilityService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchSaleService {
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitMapper rabbitMapper;
    private final CageMapper cageMapper;
    private final RequestDedupService requestDedupService;
    private final OutboundEligibilityService outboundEligibilityService;
    private final BatchWorkflowSupport workflowSupport;

    public BatchSaleService(
            BatchRabbitMapper batchRabbitMapper,
            RabbitMapper rabbitMapper,
            CageMapper cageMapper,
            RequestDedupService requestDedupService,
            OutboundEligibilityService outboundEligibilityService,
            BatchWorkflowSupport workflowSupport
    ) {
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitMapper = rabbitMapper;
        this.cageMapper = cageMapper;
        this.requestDedupService = requestDedupService;
        this.outboundEligibilityService = outboundEligibilityService;
        this.workflowSupport = workflowSupport;
    }

    @Transactional
    public void sell(
            Long userId,
            Long houseId,
            Long batchId,
            List<Long> rabbitIds,
            Date saleDate,
            String remark,
            String requestId
    ) {
        String api = "batch.sale";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            workflowSupport.requireActive(houseId, batchId);

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
                            outboundEligibilityService.rowsByIds(houseId, uniqueRabbitIds)
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
                Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
                if (rabbit == null) {
                    throw new BizException(400, "兔子不存在");
                }
                if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
                    throw new BizException(400, "兔子不在场");
                }
                if (!"2".equals(rabbit.getType())) {
                    throw new BizException(400, "仅商品兔可出售");
                }

                BatchRabbit link = batchRabbitMapper.selectActiveByBatchAndRabbit(
                        houseId,
                        batchId,
                        rabbitId
                );
                if (link == null) {
                    throw new BizException(400, "兔子不在该批次中");
                }
                if (!"fattening".equals(link.getBatchRole())) {
                    throw new BizException(400, "仅育肥兔可走出售流程");
                }
                if (!"成长期".equals(link.getCurrentStatus())) {
                    throw new BizException(400, "当前状态不允许出售");
                }
                if (link.getNextEventType() != null && !"出售".equals(link.getNextEventType())) {
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
                        link.getId(),
                        saleDate,
                        remark == null ? "出售" : remark,
                        String.valueOf(userId)
                );
                if (rows <= 0) {
                    throw new BizException(409, "状态已变化，请刷新后重试");
                }

                Cage cage = cageMapper.selectById(houseId, rabbit.getCageId());
                if (cage != null && houseId.equals(cage.getHouseId())) {
                    int newCount = (cage.getRabbitCount() == null ? 0 : cage.getRabbitCount()) - 1;
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

                workflowSupport.insertHistory(
                        userId,
                        houseId,
                        batchId,
                        rabbitId,
                        link.getCurrentStatus(),
                        "已出售",
                        "出售",
                        null,
                        null
                );
            }

            workflowSupport.completeIfEmpty(houseId, batchId, userId, saleDate);
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }
}
