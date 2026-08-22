package com.rabbit.app.modules.event.job;

import com.rabbit.app.modules.event.service.EventReminderScanService;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.rabbit.service.CommodityGrowthService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventReminderScanJob {
    private final RabbitHouseMapper rabbitHouseMapper;
    private final EventReminderScanService scanService;
    private final CommodityGrowthService commodityGrowthService;

    public EventReminderScanJob(
        RabbitHouseMapper rabbitHouseMapper,
        EventReminderScanService scanService,
        CommodityGrowthService commodityGrowthService
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.scanService = scanService;
        this.commodityGrowthService = commodityGrowthService;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void scanDaily() {
        List<RabbitHouse> houses = rabbitHouseMapper.selectAllActive();
        Date now = DateUtil.now();
        for (RabbitHouse h : houses) {
            Long houseId = h.getId();
            if (houseId == null || houseId <= 0) {
                continue;
            }
            commodityGrowthService.advanceHouse(houseId, now);
            scanService.scanHouse(houseId, now);
        }
    }
}
