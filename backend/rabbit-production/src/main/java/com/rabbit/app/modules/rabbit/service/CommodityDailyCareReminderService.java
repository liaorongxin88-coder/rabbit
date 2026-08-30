package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.modules.rabbit.domain.CommodityGrowthStage;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.mapper.GlobalSettingMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommodityDailyCareReminderService {
    private static final String SCHEDULER_OPERATOR = "commodity-care-job";

    private final RabbitMapper rabbitMapper;
    private final GlobalSettingMapper globalSettingMapper;
    private final WorkTaskWriter workTaskWriter;

    public CommodityDailyCareReminderService(
        RabbitMapper rabbitMapper,
        GlobalSettingMapper globalSettingMapper,
        WorkTaskWriter workTaskWriter
    ) {
        this.rabbitMapper = rabbitMapper;
        this.globalSettingMapper = globalSettingMapper;
        this.workTaskWriter = workTaskWriter;
    }

    @Transactional
    public void scheduleHouse(Long houseId, Date now) {
        if (houseId == null || houseId <= 0 || now == null) {
            return;
        }
        GlobalSetting setting = globalSettingMapper.selectByHouseId(houseId);
        for (Rabbit rabbit : rabbitMapper.selectByHouse(houseId, null, "2", true)) {
            if (rabbit == null || rabbit.getId() == null) {
                continue;
            }
            boolean matureByState = CommodityGrowthStage.MATURE
                == CommodityGrowthStage.fromCodeOrNull(rabbit.getGrowthStage());
            Date maturityAnchor = rabbit.getGrowthStageEnteredAt() != null
                ? rabbit.getGrowthStageEnteredAt()
                : rabbit.getArrivalDate();
            CommodityGrowthStage growthStage =
                CommodityGrowthStage.fromCodeOrNull(rabbit.getGrowthStage());
            Date maturityAt = setting != null && maturityAnchor != null && growthStage != null
                ? DateUtil.plusDays(maturityAnchor, growthStage.daysUntilMature(setting))
                : null;
            boolean matureByTime = maturityAt != null && !maturityAt.after(now);

            if (matureByState || matureByTime) {
                workTaskWriter.cancelCommodityDailyCareForRabbit(
                    houseId, rabbit.getId(), SCHEDULER_OPERATOR
                );
                workTaskWriter.scheduleForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
                    houseId,
                    TaskType.SALE_READY,
                    rabbit.getId(),
                    rabbit.getBirthBatchId(),
                    rabbit.getCageId(),
                    matureByState ? now : maturityAt,
                    "商品兔成熟后可进入出售流程",
                    SCHEDULER_OPERATOR
                ));
                continue;
            }

            workTaskWriter.cancelForRabbit(
                houseId, rabbit.getId(), TaskType.SALE_READY, SCHEDULER_OPERATOR
            );
            TaskType taskType = TaskType.forCommodityGrowthStage(rabbit.getGrowthStage());
            workTaskWriter.cancelCommodityDailyCareForRabbitExcept(
                houseId,
                rabbit.getId(),
                taskType,
                SCHEDULER_OPERATOR
            );
            if (taskType != null) {
                workTaskWriter.scheduleDailyForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
                    houseId,
                    taskType,
                    rabbit.getId(),
                    rabbit.getBirthBatchId(),
                    rabbit.getCageId(),
                    now,
                    taskType.commodityDailyCareContent(),
                    SCHEDULER_OPERATOR
                ));
            }
        }
    }
}
