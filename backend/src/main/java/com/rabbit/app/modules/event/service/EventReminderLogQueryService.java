package com.rabbit.app.modules.event.service;

import com.rabbit.app.modules.event.entity.EventReminderLog;
import com.rabbit.app.modules.event.mapper.EventReminderLogMapper;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventReminderLogQueryService {
    private final EventReminderLogMapper eventReminderLogMapper;

    public EventReminderLogQueryService(EventReminderLogMapper eventReminderLogMapper) {
        this.eventReminderLogMapper = eventReminderLogMapper;
    }

    public List<EventReminderLog> list(Long houseId, Date from, Date to, Integer limit) {
        int normalizedLimit = limit == null || limit <= 0 ? 200 : Math.min(limit, 2000);
        return eventReminderLogMapper.selectByHouseAndDateRange(houseId, from, to, normalizedLimit);
    }
}
