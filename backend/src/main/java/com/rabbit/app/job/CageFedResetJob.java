package com.rabbit.app.job;

import com.rabbit.app.mapper.CageMapper;
import com.rabbit.app.mapper.RabbitHouseMapper;
import com.rabbit.app.model.RabbitHouse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CageFedResetJob {
    private final CageMapper cageMapper;
    private final RabbitHouseMapper rabbitHouseMapper;

    public CageFedResetJob(CageMapper cageMapper, RabbitHouseMapper rabbitHouseMapper) {
        this.cageMapper = cageMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void reset() {
        List<RabbitHouse> houses = rabbitHouseMapper.selectAllActive();
        if (houses == null || houses.isEmpty()) {
            return;
        }
        for (RabbitHouse h : houses) {
            if (h == null || h.getId() == null) {
                continue;
            }
            cageMapper.resetAllFed(h.getId(), "job");
        }
    }
}
