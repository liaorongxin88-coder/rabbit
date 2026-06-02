package com.rabbit.app.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.mapper.RabbitMapper;
import com.rabbit.app.mapper.WeightLogMapper;
import com.rabbit.app.model.Rabbit;
import com.rabbit.app.model.WeightLog;
import com.rabbit.app.util.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WeightService {
    private final RabbitMapper rabbitMapper;
    private final WeightLogMapper weightLogMapper;
    private final RequestDedupService requestDedupService;

    public WeightService(RabbitMapper rabbitMapper, WeightLogMapper weightLogMapper, RequestDedupService requestDedupService) {
        this.rabbitMapper = rabbitMapper;
        this.weightLogMapper = weightLogMapper;
        this.requestDedupService = requestDedupService;
    }

    @Transactional
    public WeightLog create(Long userId, Long houseId, WeightLog r, String requestId) {
        String api = "weight:create";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            WeightLog old = weightLogMapper.selectByReq(houseId, r.getRabbitId(), requestId);
            if (old != null) {
                return old;
            }
            return r;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (r == null) {
                throw new BizException(400, "称重记录不能为空");
            }
            if (r.getRabbitId() == null || r.getRabbitId() <= 0) {
                throw new BizException(400, "rabbitId不合法");
            }
            if (r.getWeightKg() == null || r.getWeightKg() <= 0) {
                throw new BizException(400, "weightKg不合法");
            }
            Rabbit rabbit = rabbitMapper.selectById(houseId, r.getRabbitId());
            if (rabbit == null || !houseId.equals(rabbit.getHouseId())) {
                throw new BizException(400, "兔子不存在");
            }
            if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
                throw new BizException(400, "兔子不在场");
            }
            if (r.getWeighTime() == null) {
                r.setWeighTime(DateUtil.now());
            }
            r.setHouseId(houseId);
            r.setRequestId(requestId);
            r.setCreateBy(String.valueOf(userId));
            r.setUpdateBy(String.valueOf(userId));
            weightLogMapper.insert(r);
            rabbitMapper.updateWeight(houseId, r.getRabbitId(), r.getWeightKg(), String.valueOf(userId));
            requestDedupService.markDone(houseId, userId, api, requestId);
            return r;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    public List<WeightLog> listByRabbit(Long houseId, Long rabbitId, int limit) {
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 200) {
            limit = 200;
        }
        return weightLogMapper.selectByRabbit(houseId, rabbitId, limit);
    }
}
