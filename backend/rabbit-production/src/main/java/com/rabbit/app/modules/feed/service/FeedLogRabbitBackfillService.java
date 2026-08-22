package com.rabbit.app.modules.feed.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.feed.entity.FeedLog;
import com.rabbit.app.modules.feed.entity.FeedLogRabbit;
import com.rabbit.app.modules.feed.mapper.FeedLogMapper;
import com.rabbit.app.modules.feed.mapper.FeedLogRabbitMapper;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.RabbitCageRow;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedLogRabbitBackfillService {
    private final HouseService houseService;
    private final FeedLogMapper feedLogMapper;
    private final RabbitMapper rabbitMapper;
    private final FeedLogRabbitMapper feedLogRabbitMapper;

    public FeedLogRabbitBackfillService(HouseService houseService, FeedLogMapper feedLogMapper, RabbitMapper rabbitMapper, FeedLogRabbitMapper feedLogRabbitMapper) {
        this.houseService = houseService;
        this.feedLogMapper = feedLogMapper;
        this.rabbitMapper = rabbitMapper;
        this.feedLogRabbitMapper = feedLogRabbitMapper;
    }

    @Transactional
    public int backfillOnce(Long userId, Long houseId, int batchSize) {
        houseService.assertHousePermission(userId, houseId, "control");
        int lim = batchSize <= 0 ? 200 : batchSize;
        if (lim > 2000) {
            lim = 2000;
        }
        List<FeedLog> logs = feedLogMapper.selectWithoutRabbits(houseId, lim);
        if (logs == null || logs.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (FeedLog fl : logs) {
            inserted += backfillOne(houseId, fl);
        }
        return inserted;
    }

    private int backfillOne(Long houseId, FeedLog fl) {
        if (fl == null || fl.getId() == null || fl.getId() <= 0) {
            return 0;
        }
        String s = fl.getFeedingRabbits();
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        String[] parts = s.split(",");
        LinkedHashSet<Long> ids = new LinkedHashSet<Long>();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String x = p.trim();
            if (x.isEmpty()) {
                continue;
            }
            try {
                long rid = Long.parseLong(x);
                if (rid > 0) {
                    ids.add(rid);
                }
            } catch (Exception ignored) {
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        List<Long> idList = new ArrayList<Long>(ids);
        List<RabbitCageRow> cageRows = rabbitMapper.selectCageIdsByIds(houseId, idList);
        Map<Long, Long> cageMap = new HashMap<Long, Long>();
        if (cageRows != null) {
            for (RabbitCageRow r : cageRows) {
                if (r != null && r.getId() != null) {
                    cageMap.put(r.getId(), r.getCageId());
                }
            }
        }
        List<FeedLogRabbit> rels = new ArrayList<FeedLogRabbit>();
        for (Long rid : idList) {
            if (rid == null || rid <= 0) {
                continue;
            }
            if (!cageMap.containsKey(rid)) {
                continue;
            }
            FeedLogRabbit rel = new FeedLogRabbit();
            rel.setHouseId(houseId);
            rel.setFeedLogId(fl.getId());
            rel.setRabbitId(rid);
            rel.setCageId(cageMap.get(rid));
            rels.add(rel);
        }
        if (rels.isEmpty()) {
            return 0;
        }
        try {
            feedLogRabbitMapper.insertBatch(rels);
            return rels.size();
        } catch (Exception e) {
            throw new BizException(500, "回填失败: feedLogId=" + fl.getId());
        }
    }
}

