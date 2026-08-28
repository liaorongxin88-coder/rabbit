package com.rabbit.app.modules.treatment.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import com.rabbit.app.modules.treatment.mapper.TreatmentRecordMapper;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentService {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_DONE = "DONE";

    private final RabbitMapper rabbitMapper;
    private final TreatmentRecordMapper treatmentRecordMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;

    public TreatmentService(RabbitMapper rabbitMapper, TreatmentRecordMapper treatmentRecordMapper, RabbitStatusHistoryMapper rabbitStatusHistoryMapper) {
        this.rabbitMapper = rabbitMapper;
        this.treatmentRecordMapper = treatmentRecordMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
    }

    /**
     * 开始治疗。
     *
     * <p>本方法是双域试点里更值钱的那个：它写两张表（treatment_records 与
     * rabbit_status_history），改造前两处实体各要手写一对 setCreateBy/setUpdateBy，
     * 而 {@code create_by} 存的还是展示名口径的变体。现在两处都由盖章拦截器
     * 按同一口径（数字用户 ID）填，跨表归因不再依赖两段互不相干的手写代码保持一致。
     */
    @TrackedOperation(
            code = "treatment:create",
            eventType = "TREATMENT_STARTED",
            rabbitId = "#r.rabbitId",
            dedup = true
    )
    @Transactional
    public TreatmentRecord create(Long userId, Long houseId, TreatmentRecord r, String requestId) {
        OperationContext context = OperationContext.current();
        if (context != null && context.isDedupReplay()) {
            TreatmentRecord old = treatmentRecordMapper.selectByReq(houseId, r.getRabbitId(), requestId);
            return old == null ? r : old;
        }
        if (r == null) {
            throw new BizException(400, "治疗记录不能为空");
        }
        if (r.getRabbitId() == null || r.getRabbitId() <= 0) {
            throw new BizException(400, "rabbitId不合法");
        }
        Rabbit rabbit = rabbitMapper.selectById(houseId, r.getRabbitId());
        if (rabbit == null || !houseId.equals(rabbit.getHouseId())) {
            throw new BizException(400, "兔子不存在");
        }
        if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
            throw new BizException(400, "兔子不在场");
        }

        Date now = DateUtil.now();
        String operator = String.valueOf(userId);
        if (rabbitMapper.bumpStateVersionIfActive(houseId, r.getRabbitId(), operator) == 0) {
            throw new BizException(409, "兔只状态已变化，请刷新后重试");
        }
        if (r.getStartDate() == null) {
            r.setStartDate(now);
        }
        r.setHouseId(houseId);
        r.setCageId(rabbit.getCageId());
        r.setStatus(STATUS_OPEN);
        r.setRequestId(requestId);
        treatmentRecordMapper.insert(r);

        RabbitStatusHistory h = new RabbitStatusHistory();
        h.setHouseId(houseId);
        h.setRabbitId(r.getRabbitId());
        h.setCageId(rabbit.getCageId());
        h.setFromStatus("在栏");
        h.setToStatus("治疗");
        h.setChangeTime(r.getStartDate());
        h.setReason("治疗：" + safe(r.getDrug()));
        h.setRelatedRecordId(r.getId());
        h.setRelatedRecordTable("treatment_records");
        rabbitStatusHistoryMapper.insert(h);

        return r;
    }

    @TrackedOperation(
            code = "treatment:complete",
            eventType = "TREATMENT_COMPLETED",
            dedup = true
    )
    @Transactional
    public void complete(Long userId, Long houseId, Long id, Date completeTime, String remark, String requestId) {
        OperationContext context = OperationContext.current();
        if (context != null && context.isDedupReplay()) {
            return;
        }
        TreatmentRecord tr = treatmentRecordMapper.selectById(houseId, id);
        if (tr == null) {
            throw new BizException(400, "记录不存在");
        }
        if (!STATUS_OPEN.equals(tr.getStatus())) {
            throw new BizException(409, "记录已完成");
        }
        if (context != null) {
            // 目标兔只在方法内部才查得到，补进上下文让事件带上正确坐标。
            context.setRabbitId(tr.getRabbitId());
        }
        String operator = String.valueOf(userId);
        if (rabbitMapper.bumpStateVersion(houseId, tr.getRabbitId(), operator) == 0) {
            throw new BizException(409, "兔只状态已变化，请刷新后重试");
        }
        treatmentRecordMapper.updateStatus(houseId, id, STATUS_DONE, operator);

        Date t = completeTime == null ? DateUtil.now() : completeTime;
        RabbitStatusHistory h = new RabbitStatusHistory();
        h.setHouseId(houseId);
        h.setRabbitId(tr.getRabbitId());
        h.setCageId(tr.getCageId());
        h.setFromStatus("治疗");
        h.setToStatus("复查完成");
        h.setChangeTime(t);
        h.setReason("治疗复查完成");
        h.setRelatedRecordId(tr.getId());
        h.setRelatedRecordTable("treatment_records");
        rabbitStatusHistoryMapper.insert(h);
    }

    public List<TreatmentRecord> listByRabbit(Long houseId, Long rabbitId, int limit) {
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 200) {
            limit = 200;
        }
        return treatmentRecordMapper.selectByRabbit(houseId, rabbitId, limit);
    }

    public List<TreatmentRecord> listDueReviews(Long houseId) {
        return treatmentRecordMapper.selectDueReviewsByHouse(houseId, DateUtil.now());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
