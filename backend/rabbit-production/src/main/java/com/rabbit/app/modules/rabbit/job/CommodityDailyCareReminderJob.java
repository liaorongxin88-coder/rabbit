package com.rabbit.app.modules.rabbit.job;

import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.rabbit.service.CommodityDailyCareReminderService;
import com.rabbit.app.modules.rabbit.service.CommodityGrowthService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommodityDailyCareReminderJob {
    private final RabbitHouseMapper rabbitHouseMapper;
    private final CommodityGrowthService commodityGrowthService;
    private final CommodityDailyCareReminderService reminderService;

    public CommodityDailyCareReminderJob(
        RabbitHouseMapper rabbitHouseMapper,
        CommodityGrowthService commodityGrowthService,
        CommodityDailyCareReminderService reminderService
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.commodityGrowthService = commodityGrowthService;
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void scanDaily() {
        List<RabbitHouse> houses = rabbitHouseMapper.selectAllActive();
        Date now = DateUtil.now();
        for (RabbitHouse house : houses) {
            Long houseId = house.getId();
            if (houseId == null || houseId <= 0) {
                continue;
            }
            commodityGrowthService.advanceHouse(houseId, now);
            reminderService.scheduleHouse(houseId, now);
        }
    }
}
