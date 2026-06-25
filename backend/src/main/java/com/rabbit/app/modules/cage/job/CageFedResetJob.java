package com.rabbit.app.modules.cage.job;

import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
