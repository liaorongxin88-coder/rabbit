package com.rabbit.app.modules.event.service;

import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.event.dto.EventReminderScanResult;
import com.rabbit.app.modules.event.mapper.EventReminderLogMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class EventReminderScanService {
    private static final int MARK_CHUNK_SIZE = 1000;

    private final BatchRabbitMapper batchRabbitMapper;
    private final BreedingCycleMapper breedingCycleMapper;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final EventReminderLogMapper eventReminderLogMapper;

    public EventReminderScanService(BatchRabbitMapper batchRabbitMapper, BreedingCycleMapper breedingCycleMapper, ReplacementRecordMapper replacementRecordMapper, EventReminderLogMapper eventReminderLogMapper) {
        this.batchRabbitMapper = batchRabbitMapper;
        this.breedingCycleMapper = breedingCycleMapper;
        this.replacementRecordMapper = replacementRecordMapper;
        this.eventReminderLogMapper = eventReminderLogMapper;
    }

    public EventReminderScanResult scanHouse(Long houseId, Date now) {
        EventReminderScanResult r = new EventReminderScanResult();
        if (houseId == null || houseId <= 0) {
            return r;
        }
        int prodLogged = eventReminderLogMapper.insertDueBatchEventLogs(houseId, now)
            + eventReminderLogMapper.insertDueBreedingCycleEventLogs(houseId, now);
        int prodMarked = markAllDueBatchEvents(houseId, now)
            + markAllDueBreedingCycleEvents(houseId, now);
        int repLogged = eventReminderLogMapper.insertDueReplacementLogs(houseId, now);
        int repMarked = markAllDueReplacementEvents(houseId, now);
        r.setProdLogged(prodLogged);
        r.setProdMarked(prodMarked);
        r.setRepLogged(repLogged);
        r.setRepMarked(repMarked);
        return r;
    }

    private int markAllDueBatchEvents(Long houseId, Date now) {
        int total = 0;
        int rows;
        do {
            rows = batchRabbitMapper.markDueEventsAsNotified(
                houseId,
                now,
                "job",
                MARK_CHUNK_SIZE
            );
            total += rows;
        } while (rows == MARK_CHUNK_SIZE);
        return total;
    }

    private int markAllDueBreedingCycleEvents(Long houseId, Date now) {
        int total = 0;
        int rows;
        do {
            rows = breedingCycleMapper.markDueEventsAsNotified(
                houseId,
                now,
                "job",
                MARK_CHUNK_SIZE
            );
            total += rows;
        } while (rows == MARK_CHUNK_SIZE);
        return total;
    }

    private int markAllDueReplacementEvents(Long houseId, Date now) {
        int total = 0;
        int rows;
        do {
            rows = replacementRecordMapper.markDueAsNotified(
                houseId,
                now,
                "job",
                MARK_CHUNK_SIZE
            );
            total += rows;
        } while (rows == MARK_CHUNK_SIZE);
        return total;
    }
}
