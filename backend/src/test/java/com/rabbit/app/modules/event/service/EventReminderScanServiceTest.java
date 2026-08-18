package com.rabbit.app.modules.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.event.dto.EventReminderScanResult;
import com.rabbit.app.modules.event.mapper.EventReminderLogMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import java.util.Date;
import org.junit.jupiter.api.Test;

class EventReminderScanServiceTest {
    @Test
    void marksLargeDueSetsInGuardSizedChunks() {
        BatchRabbitMapper batchRabbits = mock(BatchRabbitMapper.class);
        BreedingCycleMapper cycles = mock(BreedingCycleMapper.class);
        ReplacementRecordMapper replacements = mock(
            ReplacementRecordMapper.class
        );
        EventReminderLogMapper logs = mock(EventReminderLogMapper.class);
        EventReminderScanService service = new EventReminderScanService(
            batchRabbits,
            cycles,
            replacements,
            logs
        );
        Date now = new Date();

        when(logs.insertDueBatchEventLogs(9L, now)).thenReturn(7000);
        when(logs.insertDueReplacementLogs(9L, now)).thenReturn(2020);
        when(
            batchRabbits.markDueEventsAsNotified(
                eq(9L),
                eq(now),
                eq("job"),
                eq(1000)
            )
        ).thenReturn(1000, 1000, 1000, 1000, 1000, 1000, 1000, 0);
        when(
            replacements.markDueAsNotified(
                eq(9L),
                eq(now),
                eq("job"),
                eq(1000)
            )
        ).thenReturn(1000, 1000, 20);

        EventReminderScanResult result = service.scanHouse(9L, now);

        assertEquals(7000, result.getProdLogged());
        assertEquals(7000, result.getProdMarked());
        assertEquals(2020, result.getRepLogged());
        assertEquals(2020, result.getRepMarked());
        verify(batchRabbits, times(8)).markDueEventsAsNotified(
            eq(9L),
            eq(now),
            eq("job"),
            eq(1000)
        );
        // 生产周期已退出这套扫表机制（改由 work_tasks 承载），
        // breeding_cycles 上的 next_event_date / is_event_notified 已随 V28 删除。
        verify(replacements, times(3)).markDueAsNotified(
            eq(9L),
            eq(now),
            eq("job"),
            eq(1000)
        );
    }

    @Test
    void ignoresMissingHouseIdWithoutWriting() {
        BatchRabbitMapper batchRabbits = mock(BatchRabbitMapper.class);
        BreedingCycleMapper cycles = mock(BreedingCycleMapper.class);
        ReplacementRecordMapper replacements = mock(
            ReplacementRecordMapper.class
        );
        EventReminderLogMapper logs = mock(EventReminderLogMapper.class);
        EventReminderScanService service = new EventReminderScanService(
            batchRabbits,
            cycles,
            replacements,
            logs
        );

        EventReminderScanResult result = service.scanHouse(null, new Date());

        assertEquals(0, result.getProdLogged());
        assertEquals(0, result.getProdMarked());
        verify(logs, times(0)).insertDueBatchEventLogs(any(), any());
    }
}
