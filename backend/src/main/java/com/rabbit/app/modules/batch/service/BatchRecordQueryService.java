package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.mapper.ParturitionRecordMapper;
import com.rabbit.app.modules.batch.mapper.PregnancyCheckRecordMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BatchRecordQueryService {
    private final PregnancyCheckRecordMapper pregnancyCheckRecordMapper;
    private final ParturitionRecordMapper parturitionRecordMapper;
    private final WeaningRecordMapper weaningRecordMapper;

    public BatchRecordQueryService(
            PregnancyCheckRecordMapper pregnancyCheckRecordMapper,
            ParturitionRecordMapper parturitionRecordMapper,
            WeaningRecordMapper weaningRecordMapper
    ) {
        this.pregnancyCheckRecordMapper = pregnancyCheckRecordMapper;
        this.parturitionRecordMapper = parturitionRecordMapper;
        this.weaningRecordMapper = weaningRecordMapper;
    }

    public List<PregnancyCheckRecord> listPregnancyChecks(
            Long houseId,
            Long batchId,
            Long rabbitId,
            Integer limit
    ) {
        int normalizedLimit = normalizeLimit(limit);
        if (isPositive(batchId)) {
            return pregnancyCheckRecordMapper.selectByBatch(houseId, batchId, normalizedLimit);
        }
        if (isPositive(rabbitId)) {
            return pregnancyCheckRecordMapper.selectByRabbit(houseId, rabbitId, normalizedLimit);
        }
        throw missingScope();
    }

    public List<ParturitionRecord> listParturitions(
            Long houseId,
            Long batchId,
            Long rabbitId,
            Integer limit
    ) {
        int normalizedLimit = normalizeLimit(limit);
        if (isPositive(batchId)) {
            return parturitionRecordMapper.selectByBatch(houseId, batchId, normalizedLimit);
        }
        if (isPositive(rabbitId)) {
            return parturitionRecordMapper.selectByRabbit(houseId, rabbitId, normalizedLimit);
        }
        throw missingScope();
    }

    public List<WeaningRecord> listWeanings(
            Long houseId,
            Long batchId,
            Long rabbitId,
            Integer limit
    ) {
        int normalizedLimit = normalizeLimit(limit);
        if (isPositive(batchId)) {
            return weaningRecordMapper.selectByBatch(houseId, batchId, normalizedLimit);
        }
        if (isPositive(rabbitId)) {
            return weaningRecordMapper.selectByRabbit(houseId, rabbitId, normalizedLimit);
        }
        throw missingScope();
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private BizException missingScope() {
        return new BizException(400, "batchId或rabbitId至少提供一个");
    }
}
