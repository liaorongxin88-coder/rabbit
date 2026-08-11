package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
class BatchWorkflowSupport {
    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;

    BatchWorkflowSupport(
            BatchMapper batchMapper,
            BatchRabbitMapper batchRabbitMapper,
            RabbitStatusHistoryMapper rabbitStatusHistoryMapper
    ) {
        this.batchMapper = batchMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
    }

    Batch require(Long houseId, Long batchId) {
        Batch batch = batchMapper.selectById(houseId, batchId);
        if (batch == null) {
            throw new BizException(400, "批次不存在");
        }
        return batch;
    }

    Batch requireActive(Long houseId, Long batchId) {
        Batch batch = require(houseId, batchId);
        if ("已完成".equals(batch.getStatus())) {
            throw new BizException(400, "批次已完成");
        }
        return batch;
    }

    void insertHistory(
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
        insertHistoryAt(
                userId,
                houseId,
                batchId,
                rabbitId,
                fromStatus,
                toStatus,
                reason,
                DateUtil.now(),
                relatedId,
                relatedTable
        );
    }

    void insertHistoryAt(
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
        RabbitStatusHistory history = new RabbitStatusHistory();
        history.setHouseId(houseId);
        history.setRabbitId(rabbitId);
        history.setBatchId(batchId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangeTime(changeTime == null ? DateUtil.now() : changeTime);
        history.setReason(reason);
        history.setRelatedRecordId(relatedId);
        history.setRelatedRecordTable(relatedTable);
        history.setCreateBy(String.valueOf(userId));
        history.setUpdateBy(String.valueOf(userId));
        rabbitStatusHistoryMapper.insert(history);
    }

    void completeIfEmpty(Long houseId, Long batchId, Long userId, Date endDate) {
        if (batchRabbitMapper.countActiveByBatch(batchId) != 0) {
            return;
        }
        Batch batch = batchMapper.selectById(houseId, batchId);
        if (batch == null) {
            return;
        }
        batchMapper.updateStatusAndDates(
                houseId,
                batchId,
                "已完成",
                batch.getStartDate(),
                endDate,
                String.valueOf(userId)
        );
    }
}
