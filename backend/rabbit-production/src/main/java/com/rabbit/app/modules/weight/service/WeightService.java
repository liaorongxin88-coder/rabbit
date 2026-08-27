package com.rabbit.app.modules.weight.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.weight.entity.WeightLog;
import com.rabbit.app.modules.weight.mapper.WeightLogMapper;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.util.DateUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightService {
    private final RabbitMapper rabbitMapper;
    private final WeightLogMapper weightLogMapper;

    public WeightService(RabbitMapper rabbitMapper, WeightLogMapper weightLogMapper) {
        this.rabbitMapper = rabbitMapper;
        this.weightLogMapper = weightLogMapper;
    }

    /**
     * 称重录入。
     *
     * <p>幂等记账（markProcessing / markDone / markFailed）与 create_by / update_by
     * 盖章都已由基座接管，方法体只剩业务规则。留在这里的只有一件事：
     * 命中回放时要返回<b>哪一行</b>——那是领域知识，切面无从得知。
     */
    @TrackedOperation(
            code = "weight:create",
            eventType = "WEIGHT_RECORDED",
            rabbitId = "#r.rabbitId",
            dedup = true
    )
    @Transactional
    public WeightLog create(Long userId, Long houseId, WeightLog r, String requestId) {
        OperationContext context = OperationContext.current();
        if (context != null && context.isDedupReplay()) {
            WeightLog old = weightLogMapper.selectByReq(houseId, r.getRabbitId(), requestId);
            return old == null ? r : old;
        }
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
        weightLogMapper.insert(r);
        // 这一处 operator 仍显式传：updateWeight 走的是 mapper 方法入参，
        // 不是 Stamped 实体，自动盖章够不着。T2 统一 create_by 时一并收编。
        rabbitMapper.updateWeight(houseId, r.getRabbitId(), r.getWeightKg(), String.valueOf(userId));
        return r;
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
