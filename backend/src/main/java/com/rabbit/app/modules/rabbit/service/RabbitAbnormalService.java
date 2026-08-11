package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RabbitAbnormalService {
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private final RabbitMapper rabbitMapper;
    private final RequestDedupService requestDedupService;

    public RabbitAbnormalService(
            RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper,
            RabbitMapper rabbitMapper,
            RequestDedupService requestDedupService
    ) {
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
        this.rabbitMapper = rabbitMapper;
        this.requestDedupService = requestDedupService;
    }

    public List<RabbitAbnormalCondition> list(Long houseId, Boolean dealt) {
        return rabbitAbnormalConditionMapper.selectByHouse(houseId, dealt);
    }

    @Transactional
    public void deal(Long userId, Long houseId, Long id, boolean dealt, String requestId) {
        String api = "abnormal.deal";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            RabbitAbnormalCondition condition = rabbitAbnormalConditionMapper.selectById(houseId, id);
            if (condition == null) {
                throw new BizException(404, "异常记录不存在");
            }
            String operator = String.valueOf(userId);
            int changed = rabbitAbnormalConditionMapper.markDeal(houseId, id, dealt, operator);
            if (changed > 0 && rabbitMapper.bumpStateVersion(houseId, condition.getRabbitId(), operator) == 0) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException error) {
            requestDedupService.markFailed(houseId, userId, api, requestId, error.getMessage());
            throw error;
        }
    }
}
