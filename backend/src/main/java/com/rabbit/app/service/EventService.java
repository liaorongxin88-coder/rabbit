package com.rabbit.app.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.mapper.EventAckMapper;
import com.rabbit.app.model.EventAck;
import com.rabbit.app.util.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EventService {
    private final EventAckMapper eventAckMapper;

    public EventService(EventAckMapper eventAckMapper) {
        this.eventAckMapper = eventAckMapper;
    }

    @Transactional
    public void ack(Long userId, Long houseId, String category, Long recordId, String action, Date snoozeUntil, String remark) {
        if (recordId == null || recordId <= 0) {
            throw new BizException(400, "recordId不合法");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new BizException(400, "category不合法");
        }
        if (!"ack".equals(action) && !"ignore".equals(action) && !"snooze".equals(action)) {
            throw new BizException(400, "action不合法");
        }
        if ("snooze".equals(action)) {
            if (snoozeUntil == null) {
                throw new BizException(400, "snoozeUntil不能为空");
            }
            if (!snoozeUntil.after(DateUtil.now())) {
                throw new BizException(400, "snoozeUntil必须大于当前时间");
            }
        } else {
            snoozeUntil = null;
        }

        EventAck ack = new EventAck();
        ack.setHouseId(houseId);
        ack.setUserId(userId);
        ack.setCategory(category);
        ack.setRecordId(recordId);
        ack.setAction(action);
        ack.setSnoozeUntil(snoozeUntil);
        ack.setRemark(remark);
        eventAckMapper.upsert(ack);
    }

    public Set<Long> getSuppressedIds(Long userId, Long houseId, String category) {
        List<Long> ids = eventAckMapper.selectSuppressedRecordIds(userId, houseId, category, DateUtil.now());
        return new HashSet<Long>(ids);
    }
}
