package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommodityGrowthService {
    private final RabbitMapper rabbitMapper;

    public CommodityGrowthService(RabbitMapper rabbitMapper) {
        this.rabbitMapper = rabbitMapper;
    }

    @Transactional
    public int advanceHouse(Long houseId, Date now) {
        if (houseId == null || houseId <= 0 || now == null) {
            return 0;
        }
        return rabbitMapper.advanceCommodityGrowthStages(houseId, now, "growth-job");
    }
}
