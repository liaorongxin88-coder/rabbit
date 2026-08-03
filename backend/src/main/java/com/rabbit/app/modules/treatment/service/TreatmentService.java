package com.rabbit.app.modules.treatment.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import com.rabbit.app.modules.treatment.mapper.TreatmentRecordMapper;
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
    private final RequestDedupService requestDedupService;

    public TreatmentService(RabbitMapper rabbitMapper, TreatmentRecordMapper treatmentRecordMapper, RabbitStatusHistoryMapper rabbitStatusHistoryMapper, RequestDedupService requestDedupService) {
        this.rabbitMapper = rabbitMapper;
        this.treatmentRecordMapper = treatmentRecordMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.requestDedupService = requestDedupService;
    }

    @Transactional
    public TreatmentRecord create(Long userId, Long houseId, TreatmentRecord r, String requestId) {
        String api = "treatment:create";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            TreatmentRecord old = treatmentRecordMapper.selectByReq(houseId, r.getRabbitId(), requestId);
            if (old != null) {
                return old;
            }
            return r;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
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
            r.setStatus(STATUS_OPEN);
            r.setRequestId(requestId);
            r.setCreateBy(operator);
            r.setUpdateBy(operator);
            treatmentRecordMapper.insert(r);

            RabbitStatusHistory h = new RabbitStatusHistory();
            h.setHouseId(houseId);
            h.setRabbitId(r.getRabbitId());
            h.setFromStatus("在栏");
            h.setToStatus("治疗");
            h.setChangeTime(r.getStartDate());
            h.setReason("治疗：" + safe(r.getDrug()));
            h.setRelatedRecordId(r.getId());
            h.setRelatedRecordTable("treatment_records");
            h.setCreateBy(operator);
            h.setUpdateBy(operator);
            rabbitStatusHistoryMapper.insert(h);

            requestDedupService.markDone(houseId, userId, api, requestId);
            return r;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void complete(Long userId, Long houseId, Long id, Date completeTime, String remark, String requestId) {
        String api = "treatment:complete";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            TreatmentRecord tr = treatmentRecordMapper.selectById(houseId, id);
            if (tr == null) {
                throw new BizException(400, "记录不存在");
            }
            if (!STATUS_OPEN.equals(tr.getStatus())) {
                throw new BizException(409, "记录已完成");
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
            h.setFromStatus("治疗");
            h.setToStatus("复查完成");
            h.setChangeTime(t);
            h.setReason("治疗复查完成");
            h.setRelatedRecordId(tr.getId());
            h.setRelatedRecordTable("treatment_records");
            h.setCreateBy(operator);
            h.setUpdateBy(operator);
            rabbitStatusHistoryMapper.insert(h);

            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
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
