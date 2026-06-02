package com.rabbit.app.service;

import com.rabbit.app.dto.BreedingPerformanceAggRow;
import com.rabbit.app.dto.BreedingPerformanceRecalcResult;
import com.rabbit.app.mapper.BreedingPerformanceAggMapper;
import com.rabbit.app.mapper.BreedingPerformanceMapper;
import com.rabbit.app.mapper.RabbitMapper;
import com.rabbit.app.model.Rabbit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BreedingPerformanceRecalcService {
    private final RabbitMapper rabbitMapper;
    private final BreedingPerformanceAggMapper aggMapper;
    private final BreedingPerformanceMapper breedingPerformanceMapper;

    public BreedingPerformanceRecalcService(RabbitMapper rabbitMapper,
                                           BreedingPerformanceAggMapper aggMapper,
                                           BreedingPerformanceMapper breedingPerformanceMapper) {
        this.rabbitMapper = rabbitMapper;
        this.aggMapper = aggMapper;
        this.breedingPerformanceMapper = breedingPerformanceMapper;
    }

    @Transactional
    public BreedingPerformanceRecalcResult recalcHouse(Long houseId) {
        Map<Long, BreedingPerformanceAggRow> part = toMap(aggMapper.selectParturitionAggByHouse(houseId));
        Map<Long, BreedingPerformanceAggRow> wean = toMap(aggMapper.selectWeaningAggByHouse(houseId));
        Map<Long, BreedingPerformanceAggRow> preg = toMap(aggMapper.selectPregnancyAggByHouse(houseId));

        int offset = 0;
        int limit = 1000;
        int total = 0;
        int rows = 0;
        while (true) {
            List<Rabbit> rabbits = rabbitMapper.selectPageByHouse(houseId, null, null, null, offset, limit);
            if (rabbits == null || rabbits.isEmpty()) {
                break;
            }
            for (Rabbit r : rabbits) {
                if (r == null || r.getId() == null || r.getId() <= 0) {
                    continue;
                }
                Long rabbitId = r.getId();
                BreedingPerformanceAggRow pr = part.get(rabbitId);
                BreedingPerformanceAggRow wr = wean.get(rabbitId);
                BreedingPerformanceAggRow pgr = preg.get(rabbitId);

                int totalLitters = pr == null || pr.getTotalLitters() == null ? 0 : pr.getTotalLitters();
                int totalKits = pr == null || pr.getTotalKits() == null ? 0 : pr.getTotalKits();
                int totalLiveKits = pr == null || pr.getTotalLiveKits() == null ? 0 : pr.getTotalLiveKits();
                int totalWeaned = wr == null || wr.getTotalWeaned() == null ? 0 : wr.getTotalWeaned();
                int successBreedingCount = pgr == null || pgr.getSuccessBreedingCount() == null ? 0 : pgr.getSuccessBreedingCount();
                int failedBreedingCount = pgr == null || pgr.getFailedBreedingCount() == null ? 0 : pgr.getFailedBreedingCount();

                BigDecimal avgLitterSize = totalLitters <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf((double) totalKits / (double) totalLitters).setScale(2, RoundingMode.HALF_UP);
                BigDecimal avgWeaningSize = totalLitters <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf((double) totalWeaned / (double) totalLitters).setScale(2, RoundingMode.HALF_UP);

                rows += breedingPerformanceMapper.upsertRecalc(
                        houseId,
                        rabbitId,
                        totalLitters,
                        totalKits,
                        totalLiveKits,
                        totalWeaned,
                        successBreedingCount,
                        failedBreedingCount,
                        avgLitterSize,
                        avgWeaningSize,
                        pr == null ? null : pr.getLastLitterDate()
                );
                total++;
            }
            if (rabbits.size() < limit) {
                break;
            }
            offset += rabbits.size();
        }
        BreedingPerformanceRecalcResult out = new BreedingPerformanceRecalcResult();
        out.setTotalRabbits(total);
        out.setUpdatedRows(rows);
        return out;
    }

    private Map<Long, BreedingPerformanceAggRow> toMap(List<BreedingPerformanceAggRow> rows) {
        Map<Long, BreedingPerformanceAggRow> map = new HashMap<Long, BreedingPerformanceAggRow>();
        if (rows == null) {
            return map;
        }
        for (BreedingPerformanceAggRow r : rows) {
            if (r == null || r.getRabbitId() == null || r.getRabbitId() <= 0) {
                continue;
            }
            map.put(r.getRabbitId(), r);
        }
        return map;
    }
}

