package com.rabbit.app.modules.repro.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import com.rabbit.app.modules.repro.service.ReproCommand;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReproRecoveryAdvanceJobTest {

    @Test
    void advancesEveryDueRecoveryWithADeterministicRequestId() {
        RabbitHouseMapper houseMapper = org.mockito.Mockito.mock(RabbitHouseMapper.class);
        WorkTaskMapper taskMapper = org.mockito.Mockito.mock(WorkTaskMapper.class);
        ReproStateMachineService stateMachine = org.mockito.Mockito.mock(
            ReproStateMachineService.class
        );
        WorkTask task = new WorkTask();
        task.setId(41L);
        task.setCycleId(73L);
        Date now = new Date();
        when(taskMapper.selectPendingDue(
            eq(8L),
            eq(now),
            eq(TaskType.RECOVERY.name()),
            eq(null),
            eq(null),
            eq(null),
            eq(0),
            eq(500)
        )).thenReturn(List.of(task));

        new ReproRecoveryAdvanceJob(houseMapper, taskMapper, stateMachine)
            .advanceHouse(8L, now);

        ArgumentCaptor<ReproCommand> command = ArgumentCaptor.forClass(ReproCommand.class);
        verify(stateMachine).apply(command.capture());
        assertEquals(8L, command.getValue().getHouseId());
        assertEquals(73L, command.getValue().getCycleId());
        assertEquals(ReproAction.START_CYCLE, command.getValue().getAction());
        assertEquals("recovery-auto-41", command.getValue().getRequestId());
    }
}
