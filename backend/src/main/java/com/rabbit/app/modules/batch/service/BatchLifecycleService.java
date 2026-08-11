package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.util.DateUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchLifecycleService {
    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitMapper rabbitMapper;
    private final RequestDedupService requestDedupService;
    private final BatchWorkflowSupport workflowSupport;

    public BatchLifecycleService(
            BatchMapper batchMapper,
            BatchRabbitMapper batchRabbitMapper,
            RabbitMapper rabbitMapper,
            RequestDedupService requestDedupService,
            BatchWorkflowSupport workflowSupport
    ) {
        this.batchMapper = batchMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitMapper = rabbitMapper;
        this.requestDedupService = requestDedupService;
        this.workflowSupport = workflowSupport;
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
        Batch existing = batchMapper.selectByHouseAndRequestId(houseId, requestId);
        if (existing != null) {
            return existing;
        }
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            Batch done = batchMapper.selectByHouseAndRequestId(houseId, requestId);
            if (done == null) {
                throw new BizException(500, "幂等回查失败");
            }
            return done;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch done = batchMapper.selectByHouseAndRequestId(houseId, requestId);
            if (done != null) {
                requestDedupService.markDone(houseId, userId, api, requestId);
                return done;
            }

            Date now = DateUtil.now();
            Batch batch = new Batch();
            batch.setHouseId(houseId);
            batch.setBatchCode(batchCode);
            batch.setStatus("计划中");
            batch.setRemark(remark);
            batch.setRequestId(requestId);
            batch.setCreateBy(String.valueOf(userId));
            batch.setUpdateBy(String.valueOf(userId));
            try {
                batchMapper.insert(batch);
            } catch (DuplicateKeyException e) {
                Batch duplicate = batchMapper.selectByHouseAndRequestId(houseId, requestId);
                if (duplicate != null) {
                    requestDedupService.markDone(houseId, userId, api, requestId);
                    return duplicate;
                }
                throw e;
            }

            List<BatchRabbit> links = new ArrayList<>();
            for (Long rabbitId : femaleRabbitIds) {
                Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
                if (rabbit == null || !houseId.equals(rabbit.getHouseId())) {
                    throw new BizException(400, "母兔不存在");
                }
                if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
                    throw new BizException(400, "母兔不在场");
                }
                if (!"0".equals(rabbit.getGender())) {
                    throw new BizException(400, "仅母兔可加入繁殖批次");
                }
                if (!"0".equals(rabbit.getType()) && !"1".equals(rabbit.getType())) {
                    throw new BizException(400, "仅种兔/后备兔可加入繁殖批次");
                }
                if (!batchRabbitMapper.selectActiveByRabbit(houseId, rabbitId).isEmpty()) {
                    throw new BizException(400, "母兔已在活跃批次中");
                }

                BatchRabbit link = new BatchRabbit();
                link.setBatchId(batch.getId());
                link.setRabbitId(rabbitId);
                link.setJoinReason("配种");
                link.setBatchRole("breeding");
                link.setCurrentStatus("待催情");
                link.setIsActive(Boolean.TRUE);
                link.setJoinDate(now);
                link.setCreateBy(String.valueOf(userId));
                link.setUpdateBy(String.valueOf(userId));
                links.add(link);

                workflowSupport.insertHistoryAt(
                        userId,
                        houseId,
                        batch.getId(),
                        rabbitId,
                        null,
                        "待催情",
                        "加入批次",
                        now,
                        null,
                        null
                );
            }
            batchRabbitMapper.insertBatch(links);
            requestDedupService.markDone(houseId, userId, api, requestId);
            return batch;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
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
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Batch batch = workflowSupport.require(houseId, batchId);
            Date completedAt = endDate == null ? DateUtil.now() : endDate;
            int active = batchRabbitMapper.countActiveByBatch(batchId);
            if (active != 0) {
                if (!force) {
                    throw new BizException(400, "批次仍有活跃兔，force=true 才能强制结束");
                }
                batchRabbitMapper.deactivateByBatch(
                        houseId,
                        batchId,
                        completedAt,
                        remark == null ? "手动结束批次" : remark,
                        String.valueOf(userId)
                );
            }
            batchMapper.updateStatusAndDates(
                    houseId,
                    batchId,
                    "已完成",
                    batch.getStartDate(),
                    completedAt,
                    String.valueOf(userId)
            );
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }
}
