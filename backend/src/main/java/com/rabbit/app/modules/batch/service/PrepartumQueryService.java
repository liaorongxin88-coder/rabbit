package com.rabbit.app.modules.batch.service;

import com.rabbit.app.modules.batch.entity.PrepartumRecord;
import com.rabbit.app.modules.batch.mapper.PrepartumRecordMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PrepartumQueryService {
    private final PrepartumRecordMapper prepartumRecordMapper;

    public PrepartumQueryService(PrepartumRecordMapper prepartumRecordMapper) {
        this.prepartumRecordMapper = prepartumRecordMapper;
    }

    public List<PrepartumRecord> list(Long houseId, Long batchId, Long rabbitId) {
        return prepartumRecordMapper.selectByHouse(houseId, batchId, rabbitId);
    }
}
