package com.rabbit.app.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.dto.CageRabbitBrief;
import com.rabbit.app.dto.CageSummary;
import com.rabbit.app.mapper.CageMapper;
import com.rabbit.app.mapper.FeedLogMapper;
import com.rabbit.app.mapper.RabbitMapper;
import com.rabbit.app.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.model.Cage;
import com.rabbit.app.model.FeedLog;
import com.rabbit.app.model.Rabbit;
import com.rabbit.app.model.RabbitAbnormalCondition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CageSummaryService {
    private final HouseService houseService;
    private final CageMapper cageMapper;
    private final FeedLogMapper feedLogMapper;
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private final RabbitMapper rabbitMapper;

    public CageSummaryService(HouseService houseService, CageMapper cageMapper, FeedLogMapper feedLogMapper, RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper, RabbitMapper rabbitMapper) {
        this.houseService = houseService;
        this.cageMapper = cageMapper;
        this.feedLogMapper = feedLogMapper;
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
        this.rabbitMapper = rabbitMapper;
    }

    public CageSummary getSummary(Long userId, Long houseId, Long cageId) {
        houseService.assertHousePermission(userId, houseId, "view");
        if (cageId == null || cageId <= 0) {
            throw new BizException(400, "cageId不能为空");
        }
        Cage cage = cageMapper.selectById(houseId, cageId);
        if (cage == null || !houseId.equals(cage.getHouseId())) {
            throw new BizException(404, "cage不存在");
        }

        CageSummary res = new CageSummary();
        res.setCageId(cage.getId());
        res.setCageNumber(cage.getCageNumber());
        res.setRabbitCount(cage.getRabbitCount());
        res.setIsFed(cage.getIsFed());

        FeedLog lastFeed = feedLogMapper.selectLatestByCage(houseId, cageId);
        if (lastFeed != null) {
            res.setLastFeedTime(lastFeed.getFeedTime());
            res.setLastFeedType(lastFeed.getFeedType());
            res.setLastFeedAmount(lastFeed.getAmount());
            res.setLastFeedUnit(lastFeed.getUnit());
        }

        int undeal = rabbitAbnormalConditionMapper.countUndealByCage(houseId, cageId);
        res.setAbnormalUndealCount(undeal);
        RabbitAbnormalCondition lastAb = rabbitAbnormalConditionMapper.selectLatestByCage(houseId, cageId);
        if (lastAb != null) {
            res.setLastAbnormalTime(lastAb.getWarningTime());
            res.setLastAbnormalStatus(lastAb.getWarningStatus());
        }

        List<Rabbit> rabbits = rabbitMapper.selectPageByHouse(houseId, cageId, null, true, 0, 5);
        List<CageRabbitBrief> brief = new ArrayList<CageRabbitBrief>();
        if (rabbits != null) {
            for (Rabbit r : rabbits) {
                CageRabbitBrief b = new CageRabbitBrief();
                b.setRabbitId(r.getId());
                b.setWeight(r.getWeight());
                b.setType(r.getType());
                b.setGender(r.getGender());
                b.setStatus(calcStatus(r));
                brief.add(b);
            }
        }
        res.setRabbits(brief);
        return res;
    }

    private String calcStatus(Rabbit r) {
        if (r == null) {
            return "";
        }
        if (Boolean.TRUE.equals(r.getIsQuarantined())) {
            return "隔离";
        }
        if (!Boolean.TRUE.equals(r.getIsActive())) {
            return "离场";
        }
        return "在栏";
    }
}
