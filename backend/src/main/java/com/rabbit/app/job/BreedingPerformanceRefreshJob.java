package com.rabbit.app.job;

import com.rabbit.app.mapper.RabbitHouseMapper;
import com.rabbit.app.model.RabbitHouse;
import com.rabbit.app.service.BreedingPerformanceRecalcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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

