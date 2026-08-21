package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.TaskSubjectType;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkTaskWriterTest {
    @Test
    void rabbitTaskUsesRabbitSubjectWithoutFakeCycle() {
        WorkTaskMapper mapper = Mockito.mock(WorkTaskMapper.class);
        WorkTaskWriter writer = new WorkTaskWriter(mapper);
        Date due = new Date();

        writer.scheduleForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
            8L, TaskType.SALE_READY, 81L, 9L, 12L, due, "成熟出售", "7"
        ));

        ArgumentCaptor<WorkTask> captor = ArgumentCaptor.forClass(WorkTask.class);
        Mockito.verify(mapper).upsert(captor.capture());
        WorkTask task = captor.getValue();
        Assertions.assertEquals(TaskSubjectType.RABBIT.name(), task.getSubjectType());
        Assertions.assertEquals(81L, task.getSubjectId());
        Assertions.assertEquals(81L, task.getRabbitId());
        Assertions.assertNull(task.getCycleId());
        Assertions.assertEquals("rabbit:81:SALE_READY", task.getDedupKey());
    }

    @Test
    void completingRabbitTaskOnlyCompletesTheRequestedType() {
        WorkTaskMapper mapper = Mockito.mock(WorkTaskMapper.class);
        WorkTask sale = task(1L, TaskType.SALE_READY);
        WorkTask mature = task(2L, TaskType.REPLACEMENT_MATURE);
        Mockito.when(mapper.selectPendingBySubject(8L, "RABBIT", 81L))
            .thenReturn(List.of(sale, mature));
        WorkTaskWriter writer = new WorkTaskWriter(mapper);

        writer.completeForRabbit(8L, 81L, TaskType.REPLACEMENT_MATURE, "7");

        Mockito.verify(mapper).complete(8L, 2L, null, "7");
        Mockito.verify(mapper, Mockito.never()).complete(8L, 1L, null, "7");
    }

    private WorkTask task(Long id, TaskType type) {
        WorkTask task = new WorkTask();
        task.setId(id);
        task.setTaskType(type.name());
        return task;
    }
}
