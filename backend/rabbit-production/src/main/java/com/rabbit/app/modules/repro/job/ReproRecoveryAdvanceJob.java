package com.rabbit.app.modules.repro.job;

import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import com.rabbit.app.modules.repro.service.ReproCommand;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 将到期的休养周期自动推进到待催情。 */
@Component
public class ReproRecoveryAdvanceJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReproRecoveryAdvanceJob.class);
    private static final int PAGE_SIZE = 500;

    private final RabbitHouseMapper rabbitHouseMapper;
    private final WorkTaskMapper workTaskMapper;
    private final ReproStateMachineService stateMachine;

    public ReproRecoveryAdvanceJob(
        RabbitHouseMapper rabbitHouseMapper,
        WorkTaskMapper workTaskMapper,
        ReproStateMachineService stateMachine
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.workTaskMapper = workTaskMapper;
        this.stateMachine = stateMachine;
    }

    @Scheduled(cron = "0 */15 * * * ?", zone = "Asia/Shanghai")
    public void advanceDueRecoveries() {
        Date now = DateUtil.now();
        for (RabbitHouse house : rabbitHouseMapper.selectAllActive()) {
            Long houseId = house.getId();
            if (houseId == null || houseId <= 0) {
                continue;
            }
            advanceHouse(houseId, now);
        }
    }

    void advanceHouse(Long houseId, Date now) {
        List<WorkTask> tasks = workTaskMapper.selectPendingDue(
            houseId,
            now,
            TaskType.RECOVERY.name(),
            null,
            null,
            null,
            0,
            PAGE_SIZE
        );
        for (WorkTask task : tasks) {
            try {
                stateMachine.apply(ReproCommand.builder()
                    .houseId(houseId)
                    .userId(0L)
                    .operatorName("system")
                    .cycleId(task.getCycleId())
                    .action(ReproAction.START_CYCLE)
                    .occurredAt(now)
                    .requestId("recovery-auto-" + task.getId())
                    .build());
            } catch (RuntimeException error) {
                LOGGER.warn(
                    "自动结束休养期失败: houseId={}, taskId={}, cycleId={}",
                    houseId,
                    task.getId(),
                    task.getCycleId(),
                    error
                );
            }
        }
    }
}
