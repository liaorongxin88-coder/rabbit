package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RabbitService {
    private final RabbitMapper rabbitMapper;
    private final CageMapper cageMapper;
    private final SettingService settingService;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final BatchMapper batchMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;
    private final RequestDedupService requestDedupService;
    private final HouseService houseService;
    private final int commodityCageCapacity;

    public RabbitService(
            RabbitMapper rabbitMapper,
            CageMapper cageMapper,
            SettingService settingService,
            ReplacementRecordMapper replacementRecordMapper,
            BatchRabbitMapper batchRabbitMapper,
            BatchMapper batchMapper,
            RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
            RabbitDepartureRecordMapper rabbitDepartureRecordMapper,
            RequestDedupService requestDedupService,
            HouseService houseService,
            @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.rabbitMapper = rabbitMapper;
        this.cageMapper = cageMapper;
        this.settingService = settingService;
        this.replacementRecordMapper = replacementRecordMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.batchMapper = batchMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
        this.requestDedupService = requestDedupService;
        this.houseService = houseService;
        this.commodityCageCapacity =
            commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
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

            assertCageHasCapacityForNewRabbit(cage, rabbit.getType());

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
            h.setHouseId(houseId);
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
                assertCageHasCapacityForNewRabbit(newCage, r.getType());
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
        houseService.assertHousePermission(userId, houseId, "control");
        String api = "rabbit.toReplacement";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (rabbitIds == null || rabbitIds.isEmpty()) {
                throw new BizException(400, "rabbitIds不能为空");
            }
            Set<Long> normalizedRabbitIds = new LinkedHashSet<>();
            for (Long rabbitId : rabbitIds) {
                if (rabbitId == null || rabbitId <= 0 || !normalizedRabbitIds.add(rabbitId)) {
                    throw new BizException(400, "rabbitIds包含无效或重复值");
                }
            }
            List<Long> sortedRabbitIds = normalizedRabbitIds.stream().sorted().toList();

            List<Rabbit> lockedRabbits = rabbitMapper.selectByIdsForUpdate(houseId, sortedRabbitIds);
            if (lockedRabbits.size() != sortedRabbitIds.size()) {
                throw new BizException(400, "兔子不存在");
            }
            Set<Long> sourceCageIds = new LinkedHashSet<>();
            for (Rabbit rabbit : lockedRabbits) {
                if (rabbit.getCageId() == null) {
                    throw new BizException(409, "兔子未分配原笼位: " + rabbit.getId());
                }
                sourceCageIds.add(rabbit.getCageId());
                if (!Boolean.TRUE.equals(rabbit.getIsActive())) {
                    throw new BizException(400, "兔子不在场");
                }
                if (!"2".equals(rabbit.getType())) {
                    throw new BizException(400, "仅商品兔可转后备兔");
                }
            }

            List<Cage> lockedCages;
            if (targetCageId == null) {
                lockedCages = cageMapper.selectByHouseIdForUpdate(houseId);
            } else {
                Set<Long> cageIds = new LinkedHashSet<>(sourceCageIds);
                cageIds.add(targetCageId);
                lockedCages = cageMapper.selectByIdsForUpdate(houseId, cageIds.stream().sorted().toList());
            }
            Map<Long, Cage> cageById = new LinkedHashMap<>();
            Map<Long, Integer> projectedCounts = new HashMap<>();
            for (Cage cage : lockedCages) {
                cageById.put(cage.getId(), cage);
                projectedCounts.put(cage.getId(), cageRabbitCount(cage));
            }
            if (!cageById.keySet().containsAll(sourceCageIds)) {
                throw new BizException(409, "原笼位不存在");
            }

            Map<Long, Integer> sourceDeltas = new HashMap<>();
            for (Rabbit rabbit : lockedRabbits) {
                sourceDeltas.merge(rabbit.getCageId(), 1, Integer::sum);
            }
            for (Map.Entry<Long, Integer> source : sourceDeltas.entrySet()) {
                if (projectedCounts.getOrDefault(source.getKey(), 0) < source.getValue()) {
                    throw new BizException(409, "原笼位在栏数量不足: " + source.getKey());
                }
            }

            Map<Long, Long> targetByRabbit = new LinkedHashMap<>();
            Map<Long, Integer> targetDeltas = new HashMap<>();
            if (targetCageId != null) {
                if (sourceCageIds.contains(targetCageId)) {
                    throw new BizException(400, "目标笼位不能与原笼位相同");
                }
                Cage target = requireReplacementTarget(cageById.get(targetCageId));
                if (cageRabbitCount(target) + lockedRabbits.size() > 1) {
                    throw new BizException(400, "目标后备兔笼容量不足");
                }
                for (Long rabbitId : sortedRabbitIds) {
                    targetByRabbit.put(rabbitId, targetCageId);
                }
                targetDeltas.put(targetCageId, lockedRabbits.size());
            } else {
                for (Long rabbitId : sortedRabbitIds) {
                    Cage target = pickReplacementCage(lockedCages, projectedCounts, sourceCageIds);
                    targetByRabbit.put(rabbitId, target.getId());
                    targetDeltas.merge(target.getId(), 1, Integer::sum);
                    projectedCounts.merge(target.getId(), 1, Integer::sum);
                }
            }

            GlobalSetting gs = settingService.getEffectiveSetting(userId, houseId);
            Date now = DateUtil.now();
            String operator = String.valueOf(userId);
            for (Long rabbitId : sortedRabbitIds) {
                List<BatchRabbit> activeBatchLinks = batchRabbitMapper.selectActiveByRabbit(houseId, rabbitId);
                if (!activeBatchLinks.isEmpty()) {
                    if (!forceExitBatch) {
                        throw new BizException(400, "兔子仍在活跃批次中");
                    }
                    for (BatchRabbit br : activeBatchLinks) {
                        batchRabbitMapper.deactivate(houseId, br.getId(), now, "转为后备兔", operator);
                        checkAndCompleteBatch(houseId, br.getBatchId(), userId, now);
                    }
                }
            }

            Set<Long> touchedCages = new LinkedHashSet<>();
            touchedCages.addAll(sourceDeltas.keySet());
            touchedCages.addAll(targetDeltas.keySet());
            for (Long cageId : touchedCages.stream().sorted().toList()) {
                Cage cage = cageById.get(cageId);
                int finalCount = cageRabbitCount(cage)
                        - sourceDeltas.getOrDefault(cageId, 0)
                        + targetDeltas.getOrDefault(cageId, 0);
                String finalStatus = finalCount == 0
                        ? "0"
                        : targetDeltas.containsKey(cageId) ? "2" : cage.getStatus();
                if (cageMapper.updateRabbitCountAndStatus(houseId, cageId, finalCount, finalStatus, operator) != 1) {
                    throw new BizException(409, "笼位状态已变化: " + cageId);
                }
            }

            for (Long rabbitId : sortedRabbitIds) {
                if (rabbitMapper.updateTypeAndCage(houseId, rabbitId, "1", targetByRabbit.get(rabbitId), operator) != 1) {
                    throw new BizException(409, "兔子状态已变化: " + rabbitId);
                }

                ReplacementRecord rr = new ReplacementRecord();
                rr.setHouseId(houseId);
                rr.setRabbitId(rabbitId);
                rr.setOriginalType("2");
                rr.setReplacementDate(now);
                rr.setExpectedMatureDate(DateUtil.plusDays(now, gs.getReplacementDays()));
                rr.setIsMatureNotified(Boolean.FALSE);
                rr.setCreateBy(operator);
                rr.setUpdateBy(operator);
                replacementRecordMapper.insert(rr);

                RabbitStatusHistory h = new RabbitStatusHistory();
                h.setHouseId(houseId);
                h.setRabbitId(rabbitId);
                h.setFromStatus("商品兔");
                h.setToStatus("后备兔");
                h.setChangeTime(now);
                h.setReason("转后备兔");
                h.setRelatedRecordId(rr.getId());
                h.setRelatedRecordTable("replacement_records");
                h.setCreateBy(operator);
                h.setUpdateBy(operator);
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

    private Cage pickReplacementCage(List<Cage> cages, Map<Long, Integer> projectedCounts,
                                     Set<Long> sourceCageIds) {
        for (Cage c : cages) {
            if (!sourceCageIds.contains(c.getId()) && Boolean.TRUE.equals(c.getIsEnabled())
                    && "2".equals(c.getStatus()) && projectedCounts.getOrDefault(c.getId(), 0) < 1) {
                return c;
            }
        }
        for (Cage c : cages) {
            if (!sourceCageIds.contains(c.getId()) && Boolean.TRUE.equals(c.getIsEnabled())
                    && "0".equals(c.getStatus()) && projectedCounts.getOrDefault(c.getId(), 0) < 1) {
                return c;
            }
        }
        throw new BizException(400, "没有可用后备兔笼位");
    }

    private Cage requireReplacementTarget(Cage cage) {
        if (cage == null) {
            throw new BizException(400, "目标笼位不存在");
        }
        if (Boolean.FALSE.equals(cage.getIsEnabled())) {
            throw new BizException(400, "目标笼位已停用");
        }
        if (!"0".equals(cage.getStatus()) && !"2".equals(cage.getStatus())) {
            throw new BizException(400, "目标笼位不是后备兔笼");
        }
        return cage;
    }

    private void assertCageHasCapacityForNewRabbit(Cage cage, String rabbitType) {
        if ("1".equals(cage.getStatus()) || "2".equals(cage.getStatus())) {
            if (cageRabbitCount(cage) >= 1) {
                throw new BizException(400, "该笼位已有兔子，不能再存放");
            }
            return;
        }
        if ("2".equals(rabbitType)
                && ("3".equals(cage.getStatus()) || "0".equals(cage.getStatus()))
                && cageRabbitCount(cage) >= commodityCageCapacity) {
            throw new BizException(400, "商品兔笼已满");
        }
    }

    private int cageRabbitCount(Cage cage) {
        return cage.getRabbitCount() == null ? 0 : cage.getRabbitCount();
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
                h.setHouseId(houseId);
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
                h.setHouseId(houseId);
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
            h.setHouseId(houseId);
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
