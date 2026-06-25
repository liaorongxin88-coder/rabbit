package com.rabbit.app.modules.event.service;

import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.event.dto.EventReminderScanResult;
import com.rabbit.app.modules.event.mapper.EventReminderLogMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import java.util.Date;
import org.springframework.stereotype.Service;

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

