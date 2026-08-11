package com.rabbit.app.modules.batch.service;

import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.util.DateUtil;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BatchQueryService {
    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;

    public BatchQueryService(
            BatchMapper batchMapper,
            BatchRabbitMapper batchRabbitMapper
    ) {
        this.batchMapper = batchMapper;
        this.batchRabbitMapper = batchRabbitMapper;
    }

    public List<Batch> listBatches(Long houseId) {
        return batchMapper.selectByHouse(houseId);
    }

    public List<Batch> listBatchesPage(Long houseId, String query, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        return batchMapper.selectPageByHouse(houseId, query, offset, normalizedPageSize);
    }

    public Batch getBatch(Long houseId, Long batchId) {
        return batchMapper.selectById(houseId, batchId);
    }

    public List<BatchRabbitItem> listBatchRabbits(Long batchId, String role, Boolean active) {
        return batchRabbitMapper.selectItemsByBatch(batchId, role, active);
    }

    public List<BatchRabbit> listDueBatchEvents(Long houseId, boolean onlyUnnotified) {
        if (onlyUnnotified) {
            return batchRabbitMapper.selectDueUnnotifiedEventsByHouse(houseId, DateUtil.now());
        }
        return batchRabbitMapper.selectDueEventsByHouse(houseId, DateUtil.now());
    }
}
