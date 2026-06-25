package com.rabbit.app.modules.batch.job;

import com.rabbit.app.modules.batch.service.BreedingPerformanceRecalcService;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BreedingPerformanceRefreshJob {
    private final RabbitHouseMapper rabbitHouseMapper;
    private final BreedingPerformanceRecalcService recalcService;
    private final boolean enabled;

    public BreedingPerformanceRefreshJob(RabbitHouseMapper rabbitHouseMapper,
                                         BreedingPerformanceRecalcService recalcService,
                                         @Value("${app.breeding-performance.recalc.enabled:false}") boolean enabled) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.recalcService = recalcService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "0 10 3 * * ?")
    public void recalcDaily() {
        if (!enabled) {
            return;
        }
        List<RabbitHouse> houses = rabbitHouseMapper.selectAllActive();
        for (RabbitHouse h : houses) {
            Long houseId = h.getId();
            if (houseId == null || houseId <= 0) {
                continue;
            }
            recalcService.recalcHouse(houseId);
        }
    }
}

