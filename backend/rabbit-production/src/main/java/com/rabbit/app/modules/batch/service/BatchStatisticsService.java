package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.mapper.BatchStatisticsMapper;
import org.springframework.stereotype.Service;

@Service
public class BatchStatisticsService {
    private final BatchStatisticsMapper batchStatisticsMapper;

    public BatchStatisticsService(BatchStatisticsMapper batchStatisticsMapper) {
        this.batchStatisticsMapper = batchStatisticsMapper;
    }

    public BatchStatistics getStatistics(Long houseId, Long batchId) {
        BatchStatistics statistics = batchStatisticsMapper.selectByBatch(houseId, batchId);
        if (statistics == null) {
            throw new BizException(404, "批次不存在");
        }
        return statistics;
    }
}
