package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.service.OpenCycleCommand;
import com.rabbit.app.modules.repro.service.OperatorNameResolver;
import com.rabbit.app.modules.repro.service.ReproActionService;
import com.rabbit.app.modules.repro.service.ReproRequestIds;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.mapper.BreedingCycleMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.CageTransferResult;
import com.rabbit.app.modules.rabbit.dto.ReplacementConversionItem;
import com.rabbit.app.modules.rabbit.dto.ReplacementConversionResponse;
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
import java.util.ArrayList;
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
    private static final Set<String> GROWTH_STAGES = Set.of(
            "JUVENILE", "GROWING", "FATTENING", "MATURE"
    );
    private static final Set<String> REPRODUCTIVE_STAGES = Set.of(
            "RESERVE", "EMPTY", "MATED", "PREGNANT", "LACTATING", "RESTING", "READY"
    );
    private final RabbitMapper rabbitMapper;
    private final CageMapper cageMapper;
    private final SettingService settingService;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final BatchMapper batchMapper;
    private final BreedingCycleMapper breedingCycleMapper;
    /** 兔子离场时结清生产周期与待办。 */
    private final ReproActionService reproActionService;
    private final ReproStateMachineService reproStateMachineService;
    private final OperatorNameResolver operatorNameResolver;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;
    private final RequestDedupService requestDedupService;
    private final WorkTaskWriter workTaskWriter;
    private final HouseService houseService;
    private final int commodityCageCapacity;

    public RabbitService(
            RabbitMapper rabbitMapper,
            CageMapper cageMapper,
            SettingService settingService,
            ReplacementRecordMapper replacementRecordMapper,
            BatchRabbitMapper batchRabbitMapper,
            BatchMapper batchMapper,
            BreedingCycleMapper breedingCycleMapper,
            ReproActionService reproActionService,
            ReproStateMachineService reproStateMachineService,
            OperatorNameResolver operatorNameResolver,
            RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
            RabbitDepartureRecordMapper rabbitDepartureRecordMapper,
            RequestDedupService requestDedupService,
            WorkTaskWriter workTaskWriter,
            HouseService houseService,
            @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.rabbitMapper = rabbitMapper;
        this.cageMapper = cageMapper;
        this.settingService = settingService;
        this.replacementRecordMapper = replacementRecordMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.batchMapper = batchMapper;
        this.breedingCycleMapper = breedingCycleMapper;
        this.reproActionService = reproActionService;
        this.reproStateMachineService = reproStateMachineService;
        this.operatorNameResolver = operatorNameResolver;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
        this.requestDedupService = requestDedupService;
        this.workTaskWriter = workTaskWriter;
        this.houseService = houseService;
        this.commodityCageCapacity =
            commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
    }

    /**
     * 录入种母兔时直接指定的生产阶段及其历史事实。
     *
     * <p>存栏母兔很少处于“什么都没发生”的起点；若只能从头起跑，用户就会去手写旧的
     * reproductive_stage 字段，反而造出两套并存的阶段。
     */
    public record ReproEntry(
        String stage,
        Date stageEnteredAt,
        Date matingDate,
        Date birthDate,
        Integer liveKits
    ) {}

    @Transactional
    public Rabbit createRabbit(Long userId, Long houseId, Rabbit rabbit, String requestId) {
        return createRabbit(userId, houseId, rabbit, null, requestId);
    }

    @Transactional
    public Rabbit createRabbit(
        Long userId,
        Long houseId,
        Rabbit rabbit,
        ReproEntry reproEntry,
        String requestId
    ) {
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

            normalizeAndValidateStages(rabbit.getType(), rabbit.getGender(), rabbit);

            // A locking read is current under MySQL REPEATABLE READ. Every manual entry
            // serializes on its destination cage before it observes capacity/count state.
            Cage cage = cageMapper.selectByIdForUpdate(houseId, rabbit.getCageId());
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
            if ("2".equals(rabbit.getType()) && rabbit.getGrowthStage() != null) {
                rabbit.setGrowthStageEnteredAt(
                    rabbit.getArrivalDate() != null ? rabbit.getArrivalDate() : DateUtil.now()
                );
            }
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
                throw new BizException(409, "该繁殖笼已有在栏种兔，请刷新后重试");
            }

            int newCount = cageRabbitCount(cage) + 1;
            String newStatus = "0".equals(cage.getStatus()) ? targetCageStatus : cage.getStatus();
            if (cageMapper.updateRabbitCountAndStatus(houseId, cage.getId(), newCount, newStatus, String.valueOf(userId)) != 1) {
                throw new BizException(409, "笼位状态已变化，请刷新后重试");
            }

            RabbitStatusHistory h = new RabbitStatusHistory();
            h.setHouseId(houseId);
            h.setRabbitId(rabbit.getId());
            h.setFromStatus(null);
            h.setToStatus("入栏");
            h.setChangeTime(DateUtil.now());
            h.setReason("录入兔子" + stageAuditSuffix(rabbit.getGrowthStage(), rabbit.getReproductiveStage()));
            h.setCreateBy(String.valueOf(userId));
            h.setUpdateBy(String.valueOf(userId));
            rabbitStatusHistoryMapper.insert(h);

            scheduleEntryLifecycleTask(userId, houseId, rabbit);

            // 入轨与录入同事务：要么兔子和它的生产周期一起存在，要么都不存在。
            // 分两步会留下“已入栏但永远进不了生产流程”的兔，那正是建批次曾经的缺陷。
            openReproEntryIfRequested(userId, houseId, rabbit, reproEntry, requestId);

            requestDedupService.markDone(houseId, userId, api, requestId);
            return rabbit;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void scheduleEntryLifecycleTask(Long userId, Long houseId, Rabbit rabbit) {
        String operator = String.valueOf(userId);
        GlobalSetting setting = settingService.getEffectiveSetting(userId, houseId);
        if ("2".equals(rabbit.getType())) {
            Date startedAt = rabbit.getArrivalDate() != null
                ? rabbit.getArrivalDate()
                : DateUtil.now();
            workTaskWriter.scheduleForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
                houseId,
                TaskType.SALE_READY,
                rabbit.getId(),
                null,
                rabbit.getCageId(),
                DateUtil.plusDays(startedAt, setting.commodityMaturityDays()),
                "商品兔成熟后可进入出售流程",
                operator
            ));
            return;
        }
        if (!"1".equals(rabbit.getType())) {
            return;
        }
        Date startedAt = DateUtil.now();
        ReplacementRecord replacement = new ReplacementRecord();
        replacement.setHouseId(houseId);
        replacement.setRabbitId(rabbit.getId());
        replacement.setOriginalType("1");
        replacement.setReplacementDate(startedAt);
        replacement.setExpectedMatureDate(DateUtil.plusDays(startedAt, setting.getReplacementDays()));
        replacement.setIsMatureNotified(Boolean.FALSE);
        replacement.setStatus("PENDING");
        replacement.setCreateBy(operator);
        replacement.setUpdateBy(operator);
        replacementRecordMapper.insert(replacement);
        workTaskWriter.scheduleForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
            houseId,
            TaskType.REPLACEMENT_MATURE,
            rabbit.getId(),
            null,
            rabbit.getCageId(),
            replacement.getExpectedMatureDate(),
            "后备兔成熟后可转为种兔",
            operator
        ));
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

    public List<BatchRabbitItem> listBatchMemberships(Long houseId, Long rabbitId, Boolean active) {
        getRabbit(houseId, rabbitId);
        return batchRabbitMapper.selectItemsByRabbit(houseId, rabbitId, active);
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
    public Rabbit updateBaseInfo(
            Long userId,
            Long houseId,
            Long rabbitId,
            Long cageId,
            Long motherId,
            String breed,
            String arrivalMethod,
            Date arrivalDate,
            Double weight,
            String growthStage,
            String reproductiveStage,
            String requestId
    ) {
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
            Rabbit r = rabbitMapper.selectByIdsForUpdate(houseId, List.of(rabbitId)).stream()
                    .findFirst()
                    .orElse(null);
            if (r == null) {
                throw new BizException(400, "兔子不存在");
            }
            if (r.getIsActive() == null || !r.getIsActive()) {
                throw new BizException(400, "兔子已离场");
            }
            Long newCageId = cageId == null || cageId <= 0 ? r.getCageId() : cageId;
            Set<Long> cageIds = new LinkedHashSet<>();
            cageIds.add(r.getCageId());
            cageIds.add(newCageId);
            Map<Long, Cage> lockedCages = new HashMap<>();
            for (Cage cage : cageMapper.selectByIdsForUpdate(houseId, cageIds.stream().sorted().toList())) {
                lockedCages.put(cage.getId(), cage);
            }
            Cage oldCage = lockedCages.get(r.getCageId());
            Cage newCage = lockedCages.get(newCageId);
            if (oldCage == null || !houseId.equals(oldCage.getHouseId())) {
                throw new BizException(409, "原笼位不存在，请刷新后重试");
            }
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

            if (oldCage != null && !oldCage.getId().equals(newCageId)) {
                assertCageHasCapacityForNewRabbit(newCage, r.getType());
                int newCount = cageRabbitCount(oldCage) - 1;
                if (newCount < 0) {
                    throw new BizException(409, "原笼位在栏数量已变化，请刷新后重试");
                }
                String status = newCount == 0 ? "0" : oldCage.getStatus();
                if (cageMapper.updateRabbitCountAndStatus(houseId, oldCage.getId(), newCount, status, String.valueOf(userId)) != 1) {
                    throw new BizException(409, "原笼位状态已变化，请刷新后重试");
                }

                int addCount = cageRabbitCount(newCage) + 1;
                String newStatus = "0".equals(newCage.getStatus()) ? targetCageStatus : newCage.getStatus();
                if (cageMapper.updateRabbitCountAndStatus(houseId, newCageId, addCount, newStatus, String.valueOf(userId)) != 1) {
                    throw new BizException(409, "目标笼位状态已变化，请刷新后重试");
                }
            }

            String nextGrowthStage = normalizeStage(growthStage);
            String nextReproductiveStage = normalizeStage(reproductiveStage);
            if (nextGrowthStage == null) {
                nextGrowthStage = r.getGrowthStage();
            }
            if (nextReproductiveStage == null) {
                nextReproductiveStage = r.getReproductiveStage();
            }
            Rabbit stages = new Rabbit();
            stages.setGrowthStage(nextGrowthStage);
            stages.setReproductiveStage(nextReproductiveStage);
            normalizeAndValidateStages(r.getType(), r.getGender(), stages);

            try {
                if (rabbitMapper.updateBaseInfo(
                        houseId,
                        rabbitId,
                        newCageId,
                        motherId,
                        breed,
                        arrivalMethod,
                        arrivalDate,
                        weight,
                        stages.getGrowthStage(),
                        stages.getReproductiveStage(),
                        String.valueOf(userId)
                ) != 1) {
                    throw new BizException(409, "兔只状态已变化，请刷新后重试");
                }
            } catch (DuplicateKeyException e) {
                throw new BizException(409, "该繁殖笼已有在栏种兔，请刷新后重试");
            }
            if (!sameStage(r.getGrowthStage(), stages.getGrowthStage())
                    || !sameStage(r.getReproductiveStage(), stages.getReproductiveStage())) {
                insertStageHistory(
                        houseId,
                        rabbitId,
                        r.getGrowthStage(),
                        r.getReproductiveStage(),
                        stages.getGrowthStage(),
                        stages.getReproductiveStage(),
                        String.valueOf(userId)
                );
            }
            Rabbit done = rabbitMapper.selectById(houseId, rabbitId);
            requestDedupService.markDone(houseId, userId, api, requestId);
            return done;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    /**
     * 换笼位。
     *
     * <p>与编辑走的 {@link #updateBaseInfo} 不同：编辑只能把兔子搬进空笼或同用途的
     * 商品兔笼，目标笼已有种兔时只会撞到唯一键。本方法按目标笼内的实际占用情况分三种结局：
     *
     * <ol>
     *   <li>目标笼无在栏兔：直接入笼，笼位改为对应用途并置为已占用（MOVE）。</li>
     *   <li>目标笼有兔且被移位的是商品兔：仅当笼内全是商品兔且未满时合笼（APPEND）。</li>
     *   <li>目标笼有兔且被移位的是种兔或后备兔：仅当笼内不是商品兔时两笼对调（SWAP）。</li>
     * </ol>
     *
     * <p>目标笼的用途一律从在栏兔实行推导，而不是读 {@code cages.status}：后者是反范式
     * 冷数据，一旦漂移就会把合笼、对调判断得完全相反。
     */
    @Transactional
    public CageTransferResult transferCage(
            Long userId,
            Long houseId,
            Long rabbitId,
            Long targetCageId,
            String requestId
    ) {
        String api = "rabbit.transferCage";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            Rabbit done = rabbitMapper.selectById(houseId, rabbitId);
            if (done == null) {
                throw new BizException(500, "幂等回查失败");
            }
            // 重放时已无法区分当时走的是哪条分支，只能如实告知当前落位，
            // 不能随便拿一个 mode 充数——客户端会拿它去提示“已与某兔对调”。
            return new CageTransferResult(
                    CageTransferResult.MODE_REPLAY,
                    rabbitId,
                    done.getCageId(),
                    done.getCageId(),
                    null
            );
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (targetCageId == null || targetCageId <= 0) {
                throw new BizException(400, "targetCageId不能为空");
            }
            Rabbit observed = rabbitMapper.selectById(houseId, rabbitId);
            if (observed == null) {
                throw new BizException(400, "兔子不存在");
            }
            if (!Boolean.TRUE.equals(observed.getIsActive())) {
                throw new BizException(400, "兔子已离场");
            }
            if (targetCageId.equals(observed.getCageId())) {
                throw new BizException(400, "兔子已在该笼位");
            }

            // 先用未加锁的观测值凑齐要锁的行，再在拿到锁后复查集合没变，
            // 与离场路径的 lockRabbitExitState 保持同一套加锁顺序：先兔只、后笼位，均按 id 升序。
            Set<Long> lockIds = new LinkedHashSet<>();
            lockIds.add(rabbitId);
            for (Rabbit occupant : rabbitMapper.selectByHouse(houseId, targetCageId, null, Boolean.TRUE)) {
                lockIds.add(occupant.getId());
            }
            Map<Long, Rabbit> lockedRabbits = new LinkedHashMap<>();
            for (Rabbit locked : rabbitMapper.selectByIdsForUpdate(houseId, lockIds.stream().sorted().toList())) {
                lockedRabbits.put(locked.getId(), locked);
            }
            Rabbit moving = lockedRabbits.get(rabbitId);
            if (moving == null) {
                throw new BizException(400, "兔子不存在");
            }
            if (!Boolean.TRUE.equals(moving.getIsActive())) {
                throw new BizException(409, "兔子已离场");
            }
            Long sourceCageId = moving.getCageId();
            if (targetCageId.equals(sourceCageId)) {
                throw new BizException(400, "兔子已在该笼位");
            }

            Map<Long, Cage> lockedCages = new HashMap<>();
            for (Cage cage : cageMapper.selectByIdsForUpdate(
                    houseId,
                    Set.of(sourceCageId, targetCageId).stream().sorted().toList()
            )) {
                lockedCages.put(cage.getId(), cage);
            }
            Cage sourceCage = lockedCages.get(sourceCageId);
            Cage targetCage = lockedCages.get(targetCageId);
            if (sourceCage == null) {
                throw new BizException(409, "原笼位不存在，请刷新后重试");
            }
            if (targetCage == null) {
                throw new BizException(400, "笼位不存在");
            }
            if (Boolean.FALSE.equals(targetCage.getIsEnabled())) {
                throw new BizException(400, "笼位已停用");
            }

            List<Rabbit> occupants = rabbitMapper.selectActiveByCageForUpdate(houseId, targetCageId);
            for (Rabbit occupant : occupants) {
                if (!lockedRabbits.containsKey(occupant.getId())) {
                    throw new BizException(409, "目标笼位已变化，请刷新后重试");
                }
            }

            String op = String.valueOf(userId);
            String movingPurpose = typeToCageStatus(moving.getType());
            if (occupants.isEmpty()) {
                if (!"0".equals(targetCage.getStatus()) && !movingPurpose.equals(targetCage.getStatus())) {
                    throw new BizException(400, "笼位用途不匹配");
                }
                return moveIntoCage(
                        houseId,
                        op,
                        moving,
                        sourceCage,
                        targetCage,
                        movingPurpose,
                        CageTransferResult.MODE_MOVE,
                        userId,
                        api,
                        requestId
                );
            }

            if ("2".equals(moving.getType())) {
                boolean allCommodity = occupants.stream().allMatch(o -> "2".equals(o.getType()));
                if (!allCommodity) {
                    throw new BizException(400, "目标笼位不是商品兔笼，不能合笼");
                }
                if (occupants.size() >= commodityCageCapacity) {
                    throw new BizException(409, "商品兔笼已满，请刷新后重试");
                }
                return moveIntoCage(
                        houseId,
                        op,
                        moving,
                        sourceCage,
                        targetCage,
                        movingPurpose,
                        CageTransferResult.MODE_APPEND,
                        userId,
                        api,
                        requestId
                );
            }

            // 种兔 / 后备兔进入有兔的笼位：只有对调一条路。
            if (occupants.size() != 1) {
                throw new BizException(400, "目标笼位是商品兔笼，不能与种兔、后备兔对调");
            }
            Rabbit occupant = occupants.get(0);
            if ("2".equals(occupant.getType())) {
                throw new BizException(400, "目标笼位是商品兔笼，不能与种兔、后备兔对调");
            }
            int sourceActive = rabbitMapper.countActiveByCage(houseId, sourceCageId);
            if (sourceActive != 1) {
                // 原笼还有别的在栏兔时换过来的那只会挤进一个已被占的笼，宁可拒绝。
                throw new BizException(409, "原笼位在栏数量异常，无法对调");
            }

            // 三步对调：先把被移位的那只临时挂起，让唯一键放开原笼位，
            // 再把占用者挪过来，最后带着目标笼位复位。详见 RabbitMapper.xml 里的说明。
            if (rabbitMapper.parkForCageSwap(houseId, rabbitId, op) != 1) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
            if (rabbitMapper.updateCageIfActive(houseId, occupant.getId(), sourceCageId, op) != 1) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
            if (rabbitMapper.restoreFromCageSwap(houseId, rabbitId, targetCageId, op) != 1) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
            // 两边各一只，数量不变，变的只是笼位用途跟着兔子类型走。
            if (cageMapper.updateRabbitCountAndStatus(
                    houseId, sourceCageId, 1, typeToCageStatus(occupant.getType()), op) != 1) {
                throw new BizException(409, "原笼位状态已变化，请刷新后重试");
            }
            if (cageMapper.updateRabbitCountAndStatus(
                    houseId, targetCageId, 1, movingPurpose, op) != 1) {
                throw new BizException(409, "目标笼位状态已变化，请刷新后重试");
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
            return new CageTransferResult(
                    CageTransferResult.MODE_SWAP,
                    rabbitId,
                    sourceCageId,
                    targetCageId,
                    occupant.getId()
            );
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private CageTransferResult moveIntoCage(
            Long houseId,
            String op,
            Rabbit moving,
            Cage sourceCage,
            Cage targetCage,
            String movingPurpose,
            String mode,
            Long userId,
            String api,
            String requestId
    ) {
        int sourceCount = cageRabbitCount(sourceCage) - 1;
        if (sourceCount < 0) {
            throw new BizException(409, "原笼位在栏数量已变化，请刷新后重试");
        }
        String sourceStatus = sourceCount == 0 ? "0" : sourceCage.getStatus();
        if (cageMapper.updateRabbitCountAndStatus(
                houseId, sourceCage.getId(), sourceCount, sourceStatus, op) != 1) {
            throw new BizException(409, "原笼位状态已变化，请刷新后重试");
        }
        int targetCount = cageRabbitCount(targetCage) + 1;
        String targetStatus = "0".equals(targetCage.getStatus()) ? movingPurpose : targetCage.getStatus();
        if (cageMapper.updateRabbitCountAndStatus(
                houseId, targetCage.getId(), targetCount, targetStatus, op) != 1) {
            throw new BizException(409, "目标笼位状态已变化，请刷新后重试");
        }
        try {
            if (rabbitMapper.updateCageIfActive(houseId, moving.getId(), targetCage.getId(), op) != 1) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该繁殖笼已有在栏种兔，请刷新后重试");
        }
        requestDedupService.markDone(houseId, userId, api, requestId);
        return new CageTransferResult(mode, moving.getId(), sourceCage.getId(), targetCage.getId(), null);
    }

    @Transactional
    public ReplacementConversionResponse convertToReplacement(Long userId, Long houseId, List<Long> rabbitIds, boolean forceExitBatch, Long targetCageId, String requestId) {
        houseService.assertHousePermission(userId, houseId, "control");
        String api = "rabbit.toReplacement";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return replacementConversionResponse(houseId, requestId);
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
            List<ReplacementConversionItem> converted = new ArrayList<>();
            for (Long rabbitId : sortedRabbitIds) {
                List<BatchRabbit> activeBatchLinks = batchRabbitMapper.selectActiveByRabbit(houseId, rabbitId);
                if (!activeBatchLinks.isEmpty()) {
                    if (!forceExitBatch) {
                        throw new BizException(400, "兔子仍在活跃批次中");
                    }
                    for (BatchRabbit br : activeBatchLinks) {
                        // 新口径：成员退完不再自动结束批次，改由批次查询派生“可结束”提示。
                        batchRabbitMapper.deactivate(houseId, br.getId(), now, "转为后备兔", operator);
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
                workTaskWriter.completeForRabbit(
                    houseId, rabbitId, TaskType.SALE_READY, operator
                );

                ReplacementRecord rr = new ReplacementRecord();
                rr.setHouseId(houseId);
                rr.setRabbitId(rabbitId);
                rr.setRequestId(requestId);
                rr.setOriginalType("2");
                rr.setReplacementDate(now);
                rr.setExpectedMatureDate(DateUtil.plusDays(now, gs.getReplacementDays()));
                rr.setIsMatureNotified(Boolean.FALSE);
                rr.setStatus("PENDING");
                rr.setCreateBy(operator);
                rr.setUpdateBy(operator);
                replacementRecordMapper.insert(rr);
                converted.add(new ReplacementConversionItem(
                    rabbitId, rr.getId(), targetByRabbit.get(rabbitId)
                ));

                workTaskWriter.scheduleForRabbit(new WorkTaskWriter.RabbitTaskScheduleRequest(
                    houseId,
                    TaskType.REPLACEMENT_MATURE,
                    rabbitId,
                    null,
                    targetByRabbit.get(rabbitId),
                    rr.getExpectedMatureDate(),
                    "后备兔成熟后可转为种兔",
                    operator
                ));

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
            return new ReplacementConversionResponse(List.copyOf(converted));
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private ReplacementConversionResponse replacementConversionResponse(Long houseId, String requestId) {
        List<ReplacementRecord> records = replacementRecordMapper.selectByRequestId(houseId, requestId);
        if (records.isEmpty()) {
            throw new BizException(500, "留后备兔幂等回查失败");
        }
        List<ReplacementConversionItem> items = new ArrayList<>();
        for (ReplacementRecord record : records) {
            Rabbit rabbit = rabbitMapper.selectById(houseId, record.getRabbitId());
            items.add(new ReplacementConversionItem(
                record.getRabbitId(),
                record.getId(),
                rabbit == null ? null : rabbit.getCageId()
            ));
        }
        return new ReplacementConversionResponse(List.copyOf(items));
    }

    /** 后备兔成熟后原笼转种兔笼；母兔同时进入无批次的待催情周期。 */
    @Transactional
    public void promoteReplacement(
        Long userId,
        Long houseId,
        Long rabbitId,
        String requestId
    ) {
        houseService.assertHousePermission(userId, houseId, "control");
        String api = "rabbit.promoteReplacement";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            List<Rabbit> locked = rabbitMapper.selectByIdsForUpdate(houseId, List.of(rabbitId));
            if (locked.size() != 1) {
                throw new BizException(404, "后备兔不存在");
            }
            Rabbit rabbit = locked.get(0);
            if (!Boolean.TRUE.equals(rabbit.getIsActive()) || !"1".equals(rabbit.getType())) {
                throw new BizException(409, "仅在栏后备兔可转为种兔");
            }
            ReplacementRecord replacement =
                replacementRecordMapper.selectPendingByRabbitForUpdate(houseId, rabbitId);
            if (replacement == null) {
                throw new BizException(409, "该后备兔没有待处理的成熟记录");
            }
            Date now = DateUtil.now();
            if (replacement.getExpectedMatureDate() == null
                || replacement.getExpectedMatureDate().after(now)) {
                throw new BizException(409, "该后备兔尚未达到成熟日期");
            }

            Cage cage = cageMapper.selectByIdForUpdate(houseId, rabbit.getCageId());
            if (cage == null || !Boolean.TRUE.equals(cage.getIsEnabled())) {
                throw new BizException(409, "当前后备兔笼不存在或已停用");
            }
            List<Rabbit> occupants = rabbitMapper.selectActiveByCageForUpdate(houseId, cage.getId());
            if (occupants.size() != 1 || !rabbitId.equals(occupants.get(0).getId())) {
                throw new BizException(409, "后备兔笼占用状态异常，无法转种");
            }

            String operator = String.valueOf(userId);
            String reproductiveStage = "1".equals(rabbit.getGender()) ? "READY" : null;
            if (rabbitMapper.promoteReplacement(
                houseId, rabbitId, reproductiveStage, operator
            ) != 1) {
                throw new BizException(409, "后备兔状态已变化，请刷新后重试");
            }
            if (cageMapper.updateRabbitCountAndStatus(
                houseId, cage.getId(), 1, "1", operator
            ) != 1) {
                throw new BizException(409, "笼位状态已变化，请刷新后重试");
            }
            if (replacementRecordMapper.markPromoted(
                houseId, replacement.getId(), now, operator
            ) != 1) {
                throw new BizException(409, "成熟记录已被处理，请刷新后重试");
            }
            workTaskWriter.completeForRabbit(
                houseId, rabbitId, TaskType.REPLACEMENT_MATURE, operator
            );

            RabbitStatusHistory history = new RabbitStatusHistory();
            history.setHouseId(houseId);
            history.setRabbitId(rabbitId);
            history.setFromStatus("后备兔");
            history.setToStatus("1".equals(rabbit.getGender()) ? "种公兔" : "种母兔");
            history.setChangeTime(now);
            history.setReason("后备成熟转种");
            history.setRelatedRecordId(replacement.getId());
            history.setRelatedRecordTable("replacement_records");
            history.setCreateBy(operator);
            history.setUpdateBy(operator);
            rabbitStatusHistoryMapper.insert(history);

            if (!"1".equals(rabbit.getGender())) {
                reproStateMachineService.openCycleAt(new OpenCycleCommand(
                    houseId,
                    userId,
                    operatorNameResolver.resolve(userId),
                    rabbitId,
                    null,
                    ReproStage.AWAIT_ESTRUS,
                    now,
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "后备成熟转种",
                    ReproRequestIds.derive(requestId, "repro")
                ));
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
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
                throw new BizException(409, "该笼位已有兔子，请刷新后重试");
            }
            return;
        }
        if ("2".equals(rabbitType)
                && ("3".equals(cage.getStatus()) || "0".equals(cage.getStatus()))
                && cageRabbitCount(cage) >= commodityCageCapacity) {
            throw new BizException(409, "商品兔笼已满，请刷新后重试");
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

    /**
     * 若录入时指定了生产阶段，就在同一事务里把母兔入轨。
     *
     * <p>只对种母兔生效；其他类型传了就直接报错，而不是静默忽略——静默忽略会让
     * 用户以为已经入轨，实际上这只兔永远不会出现在待办里。
     */
    private void openReproEntryIfRequested(
        Long userId,
        Long houseId,
        Rabbit rabbit,
        ReproEntry entry,
        String requestId
    ) {
        if (entry == null || entry.stage() == null || entry.stage().isBlank()) {
            return;
        }
        if (!"0".equals(rabbit.getType()) || !"0".equals(rabbit.getGender())) {
            throw new BizException(400, "只有种母兔可以指定生产阶段入轨");
        }
        ReproStage target = ReproStage.parse(entry.stage());
        Date enteredAt = entry.stageEnteredAt() == null ? DateUtil.now() : entry.stageEnteredAt();
        OpenCycleCommand command = new OpenCycleCommand(
            houseId,
            userId,
            operatorNameResolver.resolve(userId),
            rabbit.getId(),
            null,
            target,
            enteredAt,
            enteredAt,
            entry.matingDate(),
            null,
            entry.birthDate(),
            entry.liveKits(),
            entry.liveKits(),
            null,
            null,
            null,
            null,
            ReproRequestIds.derive(requestId, "entry-" + rabbit.getId())
        );
        reproStateMachineService.openCycleAt(command);
    }

    private void normalizeAndValidateStages(String type, String gender, Rabbit rabbit) {
        String growthStage = normalizeStage(rabbit.getGrowthStage());
        String reproductiveStage = normalizeStage(rabbit.getReproductiveStage());
        if ("2".equals(type) && growthStage == null) {
            growthStage = "JUVENILE";
        }
        if (growthStage != null && !GROWTH_STAGES.contains(growthStage)) {
            throw new BizException(400, "growthStage不支持");
        }
        if (reproductiveStage != null && !REPRODUCTIVE_STAGES.contains(reproductiveStage)) {
            throw new BizException(400, "reproductiveStage不支持");
        }
        if (reproductiveStage != null) {
            if ("2".equals(type)) {
                throw new BizException(400, "商品兔不能录入繁殖阶段");
            }
            if ("1".equals(type) && !"RESERVE".equals(reproductiveStage)) {
                throw new BizException(400, "后备兔繁殖阶段仅支持RESERVE");
            }
            if ("0".equals(type) && "0".equals(gender)) {
                // doe-breeding-v2：种母兔的阶段改由生产流程状态机维护（rabbits.current_stage）。
                // 旧的 reproductive_stage 是同一事实的第二套词汇，两者并存就会重现
                // recvsrp9E2dqvB「阶段与批次不对应」那类缺陷：人手写一个、状态机写另一个，
                // 谁都不知道该信哪个。因此直接拒收，并指向正确入口。
                throw new BizException(400, "种母兔的繁育阶段由生产流程维护，请在录入时选择生产阶段（reproStage）或在生产流程中推进");
            }
            if ("0".equals(type) && "1".equals(gender)
                    && !"READY".equals(reproductiveStage) && !"RESTING".equals(reproductiveStage)) {
                throw new BizException(400, "种公兔繁殖阶段仅支持READY或RESTING");
            }
            if (!"0".equals(type) && !"1".equals(type)) {
                throw new BizException(400, "兔子类型不支持繁殖阶段");
            }
        }
        rabbit.setGrowthStage(growthStage);
        rabbit.setReproductiveStage(reproductiveStage);
    }

    private String normalizeStage(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean sameStage(String left, String right) {
        return java.util.Objects.equals(left, right);
    }

    private String stageAuditSuffix(String growthStage, String reproductiveStage) {
        String detail = stageSummary(growthStage, reproductiveStage);
        return "未录入阶段".equals(detail) ? "" : "；" + detail;
    }

    private void insertStageHistory(
            Long houseId,
            Long rabbitId,
            String oldGrowthStage,
            String oldReproductiveStage,
            String newGrowthStage,
            String newReproductiveStage,
            String operator
    ) {
        RabbitStatusHistory history = new RabbitStatusHistory();
        history.setHouseId(houseId);
        history.setRabbitId(rabbitId);
        history.setFromStatus(stageSummary(oldGrowthStage, oldReproductiveStage));
        history.setToStatus(stageSummary(newGrowthStage, newReproductiveStage));
        history.setChangeTime(DateUtil.now());
        history.setReason("更新生长/繁殖阶段");
        history.setRelatedRecordTable("rabbits");
        history.setCreateBy(operator);
        history.setUpdateBy(operator);
        rabbitStatusHistoryMapper.insert(history);
    }

    private String stageSummary(String growthStage, String reproductiveStage) {
        if (growthStage == null && reproductiveStage == null) {
            return "未录入阶段";
        }
        if (growthStage == null) {
            return "繁殖阶段:" + reproductiveStage;
        }
        if (reproductiveStage == null) {
            return "生长阶段:" + growthStage;
        }
        return "生长阶段:" + growthStage + "；繁殖阶段:" + reproductiveStage;
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

            LockedRabbitExit lockedExit = lockRabbitExitState(houseId, rabbitId);
            r = lockedExit.rabbit();
            if (r == null) {
                throw new BizException(400, "兔子不存在");
            }
            if (!Boolean.TRUE.equals(r.getIsActive())) {
                throw new BizException(409, "兔子已离场");
            }
            // 先结清生产周期。
            //
            // 这一步不能交给下面的 closeOpenByMother：那条遗留 UPDATE 只写
            // status/closed_at/close_reason，不认识 lifecycle/result/stage，也不会取消 work_tasks
            // 或刷新 rabbits 上的阶段投影。先走 RETIRE 才能让新旧两个视角一致——
            // 否则兔子已离场而周期仍 OPEN，她会永久占着 uk_bc_pipeline，
            // 待办也永远停在 PENDING，今日清单里一直有一只不存在的兔子。
            //
            // 散养母兔（不属任何批次）同样需要结清，所以放在批次判断之外。
            reproActionService.retireMother(
                houseId,
                userId,
                op,
                rabbitId,
                now,
                "兔离场:" + t,
                requestId
            );
            // 商品兔、后备兔和无开放周期的母兔不会进入 RETIRE 状态机，
            // 仍要在同一离场事务中取消兔只级待办。
            workTaskWriter.cancelAllForRabbit(houseId, rabbitId, op);

            List<BatchRabbit> activeBatchLinks = lockedExit.batchLinks();
            if (!activeBatchLinks.isEmpty()) {
                if (!forceExitBatch) {
                    throw new BizException(400, "兔子仍在活跃批次中");
                }
                for (BatchRabbit br : activeBatchLinks) {
                    // 周期已由上面的 retireMother 结清（同时维护 lifecycle、待办与母兔投影），
                    // 这里只需解除批次成员关系。旧的 closeOpenByMother 只写 status/closed_at，
                    // 再调一次只会把刚写好的结果覆盖成遗留词汇。
                    batchRabbitMapper.deactivateIfActive(houseId, br.getId(), now, "兔离场:" + t, op);
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

    private LockedRabbitExit lockRabbitExitState(Long houseId, Long rabbitId) {
        List<BatchRabbit> observedLinks = batchRabbitMapper.selectActiveByRabbit(houseId, rabbitId);
        List<Long> observedBatchIds = observedLinks.stream()
            .map(BatchRabbit::getBatchId)
            .distinct()
            .sorted()
            .toList();
        for (Long batchId : observedBatchIds) {
            if (batchMapper.selectByIdForUpdate(houseId, batchId) == null) {
                throw new BizException(409, "批次状态已变化，请刷新后重试");
            }
        }

        Rabbit rabbit = rabbitMapper.selectByIdsForUpdate(houseId, List.of(rabbitId))
            .stream()
            .findFirst()
            .orElse(null);
        List<BatchRabbit> lockedLinks = batchRabbitMapper.selectActiveByRabbitForUpdate(
            houseId,
            rabbitId
        );
        Set<Long> observed = new LinkedHashSet<Long>(observedBatchIds);
        boolean unprotectedBatchAppeared = lockedLinks.stream()
            .map(BatchRabbit::getBatchId)
            .anyMatch(batchId -> !observed.contains(batchId));
        if (unprotectedBatchAppeared) {
            throw new BizException(409, "批次状态已变化，请刷新后重试");
        }
        return new LockedRabbitExit(rabbit, lockedLinks);
    }

    private record LockedRabbitExit(Rabbit rabbit, List<BatchRabbit> batchLinks) {}
}
