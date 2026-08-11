package com.rabbit.app.modules.batch.service;

import com.rabbit.app.modules.batch.dto.BreedingSummary;
import com.rabbit.app.modules.batch.entity.BreedingPerformance;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BreedingPerformanceQueryService {
    private final BreedingPerformanceMapper breedingPerformanceMapper;

    public BreedingPerformanceQueryService(BreedingPerformanceMapper breedingPerformanceMapper) {
        this.breedingPerformanceMapper = breedingPerformanceMapper;
    }

    public BreedingPerformance getByRabbit(Long houseId, Long rabbitId) {
        return breedingPerformanceMapper.selectByRabbit(houseId, rabbitId);
    }

    public List<BreedingPerformance> listByHouse(Long houseId) {
        return breedingPerformanceMapper.selectByHouse(houseId);
    }

    public BreedingSummary summarize(Long houseId) {
        return breedingPerformanceMapper.selectSummary(houseId);
    }
}
