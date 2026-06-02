package com.rabbit.app.service;

import com.rabbit.app.dto.EventReminderScanResult;
import com.rabbit.app.mapper.BatchRabbitMapper;
import com.rabbit.app.mapper.EventReminderLogMapper;
import com.rabbit.app.mapper.ReplacementRecordMapper;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class EventReminderScanService {
    private final BatchRabbitMapper batchRabbitMapper;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final EventReminderLogMapper eventReminderLogMapper;

    public EventReminderScanService(BatchRabbitMapper batchRabbitMapper, ReplacementRecordMapper replacementRecordMapper, EventReminderLogMapper eventReminderLogMapper) {
        this.batchRabbitMapper = batchRabbitMapper;
        this.replacementRecordMapper = replacementRecordMapper;
        this.eventReminderLogMapper = eventReminderLogMapper;
    }

    public EventReminderScanResult scanHouse(Long houseId, Date now) {
        EventReminderScanResult r = new EventReminderScanResult();
        if (houseId == null || houseId <= 0) {
            return r;
        }
        int prodLogged = eventReminderLogMapper.insertDueBatchEventLogs(houseId, now);
        int prodMarked = batchRabbitMapper.markDueEventsAsNotified(houseId, now, "job");
        int repLogged = eventReminderLogMapper.insertDueReplacementLogs(houseId, now);
        int repMarked = replacementRecordMapper.markDueAsNotified(houseId, now, "job");
        r.setProdLogged(prodLogged);
        r.setProdMarked(prodMarked);
        r.setRepLogged(repLogged);
        r.setRepMarked(repMarked);
        return r;
    }
}

