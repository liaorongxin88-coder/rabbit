package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommodityDailyCareReminderService {
    private static final String SCHEDULER_OPERATOR = "commodity-care-job";

    private final RabbitMapper rabbitMapper;
    private final WorkTaskWriter workTaskWriter;

    public CommodityDailyCareReminderService(
        RabbitMapper rabbitMapper,
        WorkTaskWriter workTaskWriter
    ) {
        this.rabbitMapper = rabbitMapper;
        this.workTaskWriter = workTaskWriter;
    }

    @Transactional
    public void scheduleHouse(Long houseId, Date now) {
        if (houseId == null || houseId <= 0 || now == null) {
            return;
        }
        for (Rabbit rabbit : rabbitMapper.selectByHouse(houseId, null, "2", true)) {
            if (rabbit == null || rabbit.getId() == null) {
                continue;
            }
            TaskType taskType = TaskType.forCommodityGrowthStage(rabbit.getGrowthStage());
            workTaskWriter.cancelCommodityDailyCareForRabbitExcept(
                houseId,
                rabbit.getId(),
                taskType,
                SCHEDULER_OPERATOR
            );
            if (taskType == null) {
                continue;
            }
            workTaskWriter.scheduleDailyForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
                houseId,
                taskType,
                rabbit.getId(),
                null,
                rabbit.getCageId(),
                now,
                taskType.commodityDailyCareContent(),
                SCHEDULER_OPERATOR
            ));
        }
    }
}
