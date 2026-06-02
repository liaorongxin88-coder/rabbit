package com.rabbit.app.job;

import com.rabbit.app.mapper.RabbitHouseMapper;
import com.rabbit.app.model.RabbitHouse;
import com.rabbit.app.service.EventReminderScanService;
import com.rabbit.app.util.DateUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class EventReminderScanJob {
    private final RabbitHouseMapper rabbitHouseMapper;
    private final EventReminderScanService scanService;

    public EventReminderScanJob(RabbitHouseMapper rabbitHouseMapper, EventReminderScanService scanService) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.scanService = scanService;
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
            scanService.scanHouse(houseId, now);
        }
    }
}
