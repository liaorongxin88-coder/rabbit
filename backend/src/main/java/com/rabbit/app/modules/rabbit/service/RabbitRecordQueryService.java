package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RabbitRecordQueryService {
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;

    public RabbitRecordQueryService(
            RabbitDepartureRecordMapper rabbitDepartureRecordMapper,
            RabbitStatusHistoryMapper rabbitStatusHistoryMapper
    ) {
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
    }

    public List<RabbitStatusHistory> listStatusHistory(Long houseId, Long rabbitId) {
        return rabbitStatusHistoryMapper.selectByRabbit(houseId, rabbitId);
    }

    public List<RabbitDepartureRecord> listDepartures(
            Long houseId,
            Long rabbitId,
            Date from,
            Date to,
            Integer page,
            Integer pageSize
    ) {
        int normalizedPage = page == null || page <= 0 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        return rabbitDepartureRecordMapper.selectPageByHouse(
                houseId,
                rabbitId,
                from,
                to,
                offset,
                normalizedPageSize
        );
    }
}
