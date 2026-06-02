package com.rabbit.app.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.mapper.BatchRabbitMapper;
import com.rabbit.app.mapper.BatchMapper;
import com.rabbit.app.mapper.CageMapper;
import com.rabbit.app.mapper.GlobalSettingMapper;
import com.rabbit.app.mapper.RabbitMapper;
import com.rabbit.app.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.mapper.ReplacementRecordMapper;
import com.rabbit.app.model.BatchRabbit;
import com.rabbit.app.model.Batch;
import com.rabbit.app.model.Cage;
import com.rabbit.app.model.GlobalSetting;
import com.rabbit.app.model.Rabbit;
import com.rabbit.app.model.RabbitDepartureRecord;
import com.rabbit.app.model.RabbitStatusHistory;
import com.rabbit.app.model.ReplacementRecord;
import com.rabbit.app.util.DateUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class RabbitService {
    private final RabbitMapper rabbitMapper;
    private final CageMapper cageMapper;
    private final GlobalSettingMapper globalSettingMapper;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final BatchMapper batchMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;
    private final RequestDedupService requestDedupService;

    public RabbitService(
            RabbitMapper rabbitMapper,
            CageMapper cageMapper,
            GlobalSettingMapper globalSettingMapper,
            ReplacementRecordMapper replacementRecordMapper,
            BatchRabbitMapper batchRabbitMapper,
            BatchMapper batchMapper,
            RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
            RabbitDepartureRecordMapper rabbitDepartureRecordMapper,
            RequestDedupService requestDedupService
    ) {
        this.rabbitMapper = rabbitMapper;
        this.cageMapper = cageMapper;
        this.globalSettingMapper = globalSettingMapper;
        this.replacementRecordMapper = replacementRecordMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.batchMapper = batchMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
        this.requestDedupService = requestDedupService;
    }

    @Transactional
    public Rabbit createRabbit(Long userId, Long houseId, Rabbit rabbit, String requestId) {
        String api = "rabbit.create";
        Rabbit existing = rabbitMapper.selectByHouseAndRequestId(houseId, requestId);
        if (existing != null) {
            return existing;
        }
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            Rabbit done = rabbitMapper.selectByHouseAndRequestId(houseId, requestId);
            if (done == null) {
                throw new BizException(500, "幂等回查失败");
            }
            return done;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Rabbit done = rabbitMapper.selectByHouseAndRequestId(houseId, requestId);
            if (done != null) {
                requestDedupService.markDone(houseId, userId, api, requestId);
                return done;
            }

            Cage cage = cageMapper.selectById(houseId, rabbit.getCageId());
            if (cage == null || !houseId.equals(cage.getHouseId())) {
                throw new BizException(400, "笼位不存在");
            }
            if (Boolean.FALSE.equals(cage.getIsEnabled())) {
                throw new BizException(400, "笼位已停用");
            }

            String targetCageStatus = typeToCageStatus(rabbit.getType());
            if (!"0".equals(cage.getStatus()) && !targetCageStatus.equals(cage.getStatus())) {
                throw new BizException(400, "笼位用途不匹配");
            }

            rabbit.setHouseId(houseId);
            rabbit.setIsActive(Boolean.TRUE);
            rabbit.setRequestId(requestId);
            if (rabbit.getIsQuarantined() == null) {
                rabbit.setIsQuarantined(Boolean.FALSE);
            }
            rabbit.setCreateBy(String.valueOf(userId));
            rabbit.setUpdateBy(String.valueOf(userId));
            try {
                rabbitMapper.insert(rabbit);
            } catch (DuplicateKeyException e) {
                Rabbit dup = rabbitMapper.selectByHouseAndRequestId(houseId, requestId);
                if (dup != null) {
                    requestDedupService.markDone(houseId, userId, api, requestId);
                    return dup;
                }
                throw e;
            }

            int newCount = cage.getRabbitCount() == null ? 1 : cage.getRabbitCount() + 1;
            String newStatus = "0".equals(cage.getStatus()) ? targetCageStatus : cage.getStatus();
            cageMapper.updateRabbitCountAndStatus(houseId, cage.getId(), newCount, newStatus, String.valueOf(userId));

            RabbitStatusHistory h = new RabbitStatusHistory();
            h.setRabbitId(rabbit.getId());
            h.setFromStatus(null);
            h.setToStatus("入栏");
            h.setChangeTime(DateUtil.now());
            h.setReason("录入兔子");
            h.setCreateBy(String.valueOf(userId));
            h.setUpdateBy(String.valueOf(userId));
            rabbitStatusHistoryMapper.insert(h);

            requestDedupService.markDone(houseId, userId, api, requestId);
            return rabbit;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    public List<Rabbit> listRabbits(Long houseId, Long cageId, String type, Boolean active) {
        return rabbitMapper.selectByHouse(houseId, cageId, type, active);
    }

    public Rabbit getRabbit(Long houseId, Long rabbitId) {
        Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
        if (r == null) {
            throw new BizException(404, "兔子不存在");
        }
        return r;
    }

    public List<Rabbit> listRabbitsPage(Long houseId, Long cageId, String type, Boolean active, int page, int pageSize) {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 50;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
        int offset = (page - 1) * pageSize;
        return rabbitMapper.selectPageByHouse(houseId, cageId, type, active, offset, pageSize);
    }

    @Transactional
    public Rabbit updateBaseInfo(Long userId, Long houseId, Long rabbitId, Long cageId, Long motherId, String breed, String arrivalMethod, Date arrivalDate, Double weight, String requestId) {
        String api = "rabbit.updateBaseInfo";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            Rabbit done = rabbitMapper.selectById(houseId, rabbitId);
            if (done == null) {
                throw new BizException(500, "幂等回查失败");
            }
            return done;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
            if (r == null) {
                throw new BizException(400, "兔子不存在");
            }
            if (r.getIsActive() == null || !r.getIsActive()) {
                throw new BizException(400, "兔子已离场");
            }
            Long newCageId = cageId == null || cageId <= 0 ? r.getCageId() : cageId;
            Cage newCage = cageMapper.selectById(houseId, newCageId);
            if (newCage == null || !houseId.equals(newCage.getHouseId())) {
                throw new BizException(400, "笼位不存在");
            }
            if (Boolean.FALSE.equals(newCage.getIsEnabled())) {
                throw new BizException(400, "笼位已停用");
            }
            String targetCageStatus = typeToCageStatus(r.getType());
            if (!"0".equals(newCage.getStatus()) && !targetCageStatus.equals(newCage.getStatus())) {
                throw new BizException(400, "笼位用途不匹配");
            }

            Cage oldCage = cageMapper.selectById(houseId, r.getCageId());
            if (oldCage != null && !oldCage.getId().equals(newCageId)) {
                int newCount = (oldCage.getRabbitCount() == null ? 0 : oldCage.getRabbitCount()) - 1;
                if (newCount < 0) {
                    newCount = 0;
                }
                String status = newCount == 0 ? "0" : oldCage.getStatus();
                cageMapper.updateRabbitCountAndStatus(houseId, oldCage.getId(), newCount, status, String.valueOf(userId));

                int addCount = (newCage.getRabbitCount() == null ? 0 : newCage.getRabbitCount()) + 1;
                String newStatus = "0".equals(newCage.getStatus()) ? targetCageStatus : newCage.getStatus();
                cageMapper.updateRabbitCountAndStatus(houseId, newCageId, addCount, newStatus, String.valueOf(userId));
            }

            rabbitMapper.updateBaseInfo(houseId, rabbitId, newCageId, motherId, breed, arrivalMethod, arrivalDate, weight, String.valueOf(userId));
            Rabbit done = rabbitMapper.selectById(houseId, rabbitId);
            requestDedupService.markDone(houseId, userId, api, requestId);
            return done;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void convertToReplacement(Long userId, Long houseId, List<Long> rabbitIds, boolean forceExitBatch, Long targetCageId, String requestId) {
        String api = "rabbit.toReplacement";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
        GlobalSetting gs = globalSettingMapper.selectByHouseId(houseId);
        if (gs == null) {
            throw new BizException(400, "兔舍未初始化周期配置");
        }
        Date now = DateUtil.now();

        Cage targetCage = null;
        if (targetCageId != null) {
            targetCage = cageMapper.selectById(houseId, targetCageId);
            if (targetCage == null || !houseId.equals(targetCage.getHouseId())) {
                throw new BizException(400, "目标笼位不存在");
            }
            if (Boolean.FALSE.equals(targetCage.getIsEnabled())) {
                throw new BizException(400, "目标笼位已停用");
            }
            if (!"0".equals(targetCage.getStatus()) && !"2".equals(targetCage.getStatus())) {
                throw new BizException(400, "目标笼位不是后备兔笼");
            }
        }

        for (Long rabbitId : rabbitIds) {
            Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
            if (r == null) {
                throw new BizException(400, "兔子不存在");
            }
            if (r.getIsActive() == null || !r.getIsActive()) {
                throw new BizException(400, "兔子不在场");
            }
            if (!"2".equals(r.getType())) {
                throw new BizException(400, "仅商品兔可转后备兔");
            }

            List<BatchRabbit> activeBatchLinks = batchRabbitMapper.selectActiveByRabbit(houseId, rabbitId);
            if (!activeBatchLinks.isEmpty()) {
                if (!forceExitBatch) {
                    throw new BizException(400, "兔子仍在活跃批次中");
                }
                for (BatchRabbit br : activeBatchLinks) {
                    batchRabbitMapper.deactivate(houseId, br.getId(), now, "转为后备兔", String.valueOf(userId));
                    checkAndCompleteBatch(houseId, br.getBatchId(), userId, now);
                }
            }

            Cage oldCage = cageMapper.selectById(houseId, r.getCageId());
            if (oldCage != null && houseId.equals(oldCage.getHouseId())) {
                int newCount = (oldCage.getRabbitCount() == null ? 0 : oldCage.getRabbitCount()) - 1;
                if (newCount < 0) {
                    newCount = 0;
                }
                String status = newCount == 0 ? "0" : oldCage.getStatus();
                cageMapper.updateRabbitCountAndStatus(houseId, oldCage.getId(), newCount, status, String.valueOf(userId));
            }

            Cage finalTargetCage = targetCage != null ? targetCage : pickReplacementCage(houseId);
            int newTargetCount = (finalTargetCage.getRabbitCount() == null ? 0 : finalTargetCage.getRabbitCount()) + 1;
            String newTargetStatus = "0".equals(finalTargetCage.getStatus()) ? "2" : finalTargetCage.getStatus();
            cageMapper.updateRabbitCountAndStatus(houseId, finalTargetCage.getId(), newTargetCount, newTargetStatus, String.valueOf(userId));
            rabbitMapper.updateTypeAndCage(houseId, rabbitId, "1", finalTargetCage.getId(), String.valueOf(userId));

            ReplacementRecord rr = new ReplacementRecord();
            rr.setHouseId(houseId);
            rr.setRabbitId(rabbitId);
            rr.setOriginalType("2");
            rr.setReplacementDate(now);
            rr.setExpectedMatureDate(DateUtil.plusDays(now, gs.getReplacementDays()));
            rr.setIsMatureNotified(Boolean.FALSE);
            rr.setCreateBy(String.valueOf(userId));
            rr.setUpdateBy(String.valueOf(userId));
            replacementRecordMapper.insert(rr);

            RabbitStatusHistory h = new RabbitStatusHistory();
            h.setRabbitId(rabbitId);
            h.setFromStatus("商品兔");
            h.setToStatus("后备兔");
            h.setChangeTime(now);
            h.setReason("转后备兔");
            h.setRelatedRecordId(rr.getId());
            h.setRelatedRecordTable("replacement_records");
            h.setCreateBy(String.valueOf(userId));
            h.setUpdateBy(String.valueOf(userId));
            rabbitStatusHistoryMapper.insert(h);
        }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void checkAndCompleteBatch(Long houseId, Long batchId, Long userId, Date endDate) {
        if (batchId == null) {
            return;
        }
        int active = batchRabbitMapper.countActiveByBatch(batchId);
        if (active != 0) {
            return;
        }
        Batch b = batchMapper.selectById(houseId, batchId);
        if (b == null) {
            return;
        }
        batchMapper.updateStatusAndDates(houseId, batchId, "已完成", b.getStartDate(), endDate, String.valueOf(userId));
    }

    private Cage pickReplacementCage(Long houseId) {
        List<Cage> cages = cageMapper.selectByHouseId(houseId);
        for (Cage c : cages) {
            if ("2".equals(c.getStatus())) {
                return c;
            }
        }
        for (Cage c : cages) {
            if ("0".equals(c.getStatus())) {
                return c;
            }
        }
        throw new BizException(400, "没有可用后备兔笼位");
    }

    private String typeToCageStatus(String type) {
        if ("0".equals(type)) {
            return "1";
        }
        if ("1".equals(type)) {
            return "2";
        }
        return "3";
    }

    @Transactional
    public void rabbitEvent(Long userId, Long houseId, Long rabbitId, String eventType, Date actionDate, String reason, String remark, boolean forceExitBatch, String requestId) {
        String api = "rabbit:event:" + (eventType == null ? "" : eventType);
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
            if (r == null) {
                throw new BizException(400, "兔子不存在");
            }
            Date now = actionDate == null ? DateUtil.now() : actionDate;
            String op = String.valueOf(userId);
            String t = eventType == null ? "" : eventType.trim().toLowerCase();
            if (t.isEmpty()) {
                throw new BizException(400, "eventType不能为空");
            }

            if ("quarantine".equals(t)) {
                if (r.getIsActive() == null || !r.getIsActive()) {
                    throw new BizException(400, "兔子不在场");
                }
                rabbitMapper.updateQuarantine(houseId, rabbitId, Boolean.TRUE, now, reason, op);
                RabbitStatusHistory h = new RabbitStatusHistory();
                h.setRabbitId(rabbitId);
                h.setFromStatus("在栏");
                h.setToStatus("隔离");
                h.setChangeTime(now);
                h.setReason(reason == null ? "隔离" : reason);
                h.setCreateBy(op);
                h.setUpdateBy(op);
                rabbitStatusHistoryMapper.insert(h);
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }

            if ("recover".equals(t)) {
                if (r.getIsActive() == null || !r.getIsActive()) {
                    throw new BizException(400, "兔子不在场");
                }
                rabbitMapper.updateQuarantine(houseId, rabbitId, Boolean.FALSE, null, null, op);
                RabbitStatusHistory h = new RabbitStatusHistory();
                h.setRabbitId(rabbitId);
                h.setFromStatus("隔离");
                h.setToStatus("解除隔离");
                h.setChangeTime(now);
                h.setReason(reason == null ? "解除隔离" : reason);
                h.setCreateBy(op);
                h.setUpdateBy(op);
                rabbitStatusHistoryMapper.insert(h);
                requestDedupService.markDone(houseId, userId, api, requestId);
                return;
            }

            if (!"death".equals(t) && !"cull".equals(t) && !"sale".equals(t)) {
                throw new BizException(400, "eventType不支持");
            }

            if (r.getIsActive() == null || !r.getIsActive()) {
                throw new BizException(409, "兔子已离场");
            }

            List<BatchRabbit> activeBatchLinks = batchRabbitMapper.selectActiveByRabbit(houseId, rabbitId);
            if (!activeBatchLinks.isEmpty()) {
                if (!forceExitBatch) {
                    throw new BizException(400, "兔子仍在活跃批次中");
                }
                for (BatchRabbit br : activeBatchLinks) {
                    batchRabbitMapper.deactivateIfActive(houseId, br.getId(), now, "兔离场:" + t, op);
                    checkAndCompleteBatch(houseId, br.getBatchId(), userId, now);
                }
            }

            Cage oldCage = cageMapper.selectById(houseId, r.getCageId());
            if (oldCage != null && houseId.equals(oldCage.getHouseId())) {
                int newCount = (oldCage.getRabbitCount() == null ? 0 : oldCage.getRabbitCount()) - 1;
                if (newCount < 0) {
                    newCount = 0;
                }
                String status = newCount == 0 ? "0" : oldCage.getStatus();
                cageMapper.updateRabbitCountAndStatus(houseId, oldCage.getId(), newCount, status, String.valueOf(userId));
            }

            rabbitMapper.updateDeparture(houseId, rabbitId, now, t, op);

            RabbitDepartureRecord dr = new RabbitDepartureRecord();
            dr.setHouseId(houseId);
            dr.setRabbitId(rabbitId);
            dr.setDepartureType(t);
            dr.setDepartureDate(now);
            dr.setReason(reason);
            dr.setRemark(remark);
            dr.setRequestId(requestId);
            dr.setCreateBy(op);
            dr.setUpdateBy(op);
            rabbitDepartureRecordMapper.insert(dr);

            RabbitStatusHistory h = new RabbitStatusHistory();
            h.setRabbitId(rabbitId);
            h.setFromStatus("在栏");
            h.setToStatus("death".equals(t) ? "死亡" : ("cull".equals(t) ? "淘汰" : "出售出栏"));
            h.setChangeTime(now);
            h.setReason(reason == null ? h.getToStatus() : reason);
            h.setRelatedRecordId(dr.getId());
            h.setRelatedRecordTable("rabbit_departure_records");
            h.setCreateBy(op);
            h.setUpdateBy(op);
            rabbitStatusHistoryMapper.insert(h);

            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }
}
