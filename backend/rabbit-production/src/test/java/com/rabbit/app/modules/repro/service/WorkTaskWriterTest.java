package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.TaskSubjectType;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import java.time.Instant;
import java.time.LocalDate;
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
    void commodityCareCompletionMatchesBusinessDateAndTaskType() {
        WorkTaskMapper mapper = Mockito.mock(WorkTaskMapper.class);
        WorkTask sameDayCare = task(1L, TaskType.COMMODITY_GROWING_CARE);
        sameDayCare.setDueDate(date("2026-03-10T01:00:00Z"));
        WorkTask previousDayCare = task(2L, TaskType.COMMODITY_FATTENING_CARE);
        previousDayCare.setDueDate(date("2026-03-09T01:00:00Z"));
        WorkTask futureCare = task(3L, TaskType.COMMODITY_ADAPTATION_CARE);
        futureCare.setDueDate(date("2026-03-11T01:00:00Z"));
        WorkTask otherType = task(4L, TaskType.SALE_READY);
        otherType.setDueDate(date("2026-03-10T08:00:00Z"));
        Mockito.when(mapper.selectPendingBySubject(8L, "RABBIT", 81L))
            .thenReturn(List.of(sameDayCare, previousDayCare, futureCare, otherType));
        WorkTaskWriter writer = new WorkTaskWriter(mapper);

        writer.completeCommodityDailyCareForRabbitOnDate(
            8L, 81L, date("2026-03-10T15:30:00Z"), "7"
        );

        Mockito.verify(mapper).complete(8L, 1L, null, "7");
        Mockito.verify(mapper, Mockito.never()).complete(8L, 2L, null, "7");
        Mockito.verify(mapper, Mockito.never()).complete(8L, 3L, null, "7");
        Mockito.verify(mapper, Mockito.never()).complete(8L, 4L, null, "7");
    }

    @Test
    void commodityCareRetentionKeepsOnlyTheCurrentBusinessDate() {
        WorkTaskMapper mapper = Mockito.mock(WorkTaskMapper.class);
        WorkTask currentCare = task(5L, TaskType.COMMODITY_GROWING_CARE);
        currentCare.setDueDate(date("2026-03-10T01:00:00Z"));
        WorkTask previousCare = task(6L, TaskType.COMMODITY_GROWING_CARE);
        previousCare.setDueDate(date("2026-03-09T01:00:00Z"));
        WorkTask otherStageCare = task(7L, TaskType.COMMODITY_FATTENING_CARE);
        otherStageCare.setDueDate(date("2026-03-10T01:00:00Z"));
        WorkTask saleReady = task(8L, TaskType.SALE_READY);
        saleReady.setDueDate(date("2026-03-10T01:00:00Z"));
        Mockito.when(mapper.selectPendingBySubject(8L, "RABBIT", 81L))
            .thenReturn(List.of(currentCare, previousCare, otherStageCare, saleReady));
        WorkTaskWriter writer = new WorkTaskWriter(mapper);

        writer.cancelCommodityDailyCareForRabbitExcept(
            8L,
            81L,
            TaskType.COMMODITY_GROWING_CARE,
            LocalDate.of(2026, 3, 10),
            "care-job"
        );

        Mockito.verify(mapper, Mockito.never()).cancel(8L, 5L, "care-job");
        Mockito.verify(mapper).cancel(8L, 6L, "care-job");
        Mockito.verify(mapper).cancel(8L, 7L, "care-job");
        Mockito.verify(mapper, Mockito.never()).cancel(8L, 8L, "care-job");
    }

    @Test
    void commodityCareCompletionKeepsRabbitHouseBoundaryAndIsIdempotent() {
        WorkTaskMapper mapper = Mockito.mock(WorkTaskMapper.class);
        WorkTask care = task(5L, TaskType.COMMODITY_ADAPTATION_CARE);
        care.setDueDate(date("2026-03-10T01:00:00Z"));
        Mockito.when(mapper.selectPendingBySubject(8L, "RABBIT", 81L))
            .thenReturn(List.of(care), List.of());
        WorkTaskWriter writer = new WorkTaskWriter(mapper);

        writer.completeCommodityDailyCareForRabbitOnDate(
            8L, 81L, date("2026-03-10T02:00:00Z"), "7"
        );
        writer.completeCommodityDailyCareForRabbitOnDate(
            8L, 81L, date("2026-03-10T02:00:00Z"), "7"
        );

        Mockito.verify(mapper, Mockito.times(2))
            .selectPendingBySubject(8L, "RABBIT", 81L);
        Mockito.verify(mapper, Mockito.times(1)).complete(8L, 5L, null, "7");
        Mockito.verify(mapper, Mockito.never())
            .selectPendingBySubject(Mockito.eq(8L), Mockito.eq("RABBIT"), Mockito.eq(82L));
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

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }

    private WorkTask task(Long id, TaskType type) {
        WorkTask task = new WorkTask();
        task.setId(id);
        task.setTaskType(type.name());
        return task;
    }
}
