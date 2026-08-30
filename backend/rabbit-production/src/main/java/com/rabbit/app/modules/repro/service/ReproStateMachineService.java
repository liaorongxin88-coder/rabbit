package com.rabbit.app.modules.repro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.file.service.BusinessFileService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.entity.RabbitStatusHistory;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.modules.repro.domain.CycleLifecycle;
import com.rabbit.app.modules.repro.domain.CycleResult;
import com.rabbit.app.modules.repro.domain.DeliveryOutcome;
import com.rabbit.app.modules.repro.domain.DueContext;
import com.rabbit.app.modules.repro.domain.DueDateCalculator;
import com.rabbit.app.modules.repro.domain.EntryPoint;
import com.rabbit.app.modules.repro.domain.LitterStatus;
import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproEventType;
import com.rabbit.app.modules.repro.domain.ReproSettings;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.domain.TaskSubjectType;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.domain.Transition;
import com.rabbit.app.modules.repro.domain.TransitionTable;
import com.rabbit.app.modules.repro.entity.BizAttachment;
import com.rabbit.app.modules.repro.entity.Litter;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.BizAttachmentMapper;
import com.rabbit.app.modules.repro.mapper.LitterMapper;
import com.rabbit.app.modules.repro.mapper.RabbitStageProjectionMapper;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 母兔生产流程的唯一写路径（设计 §3.3）。
 *
 * <p>取代 BatchService 里 mating / pregnancyCheck / prepartumFinish / parturition /
 * weaning 五个各自约 200-300 行、规则互相复制的方法。所有推进都收敛到 {@link #apply}
 * 的固定七步，阶段合法性交给数据驱动的转换表，因此新增一个阶段只需加一行数据，
 * 而不是再复制一个方法。
 *
 * <p>并发与幂等的分工刻意分离：
 * <ul>
 *   <li>幂等靠 repro_events 的 uk_re_request——重复提交返回首次结果，不产生第二次状态变更；</li>
 *   <li>并发靠周期行的 FOR UPDATE 加 state_version 比对——后写者影响 0 行并得到 409，
 *       而不是静默覆盖先写者。</li>
 * </ul>
 */
@Service
public class ReproStateMachineService {
    private final ReproCycleMapper reproCycleMapper;
    private final ReproEventMapper reproEventMapper;
    private final LitterMapper litterMapper;
    private final BizAttachmentMapper bizAttachmentMapper;
    private final RabbitStageProjectionMapper rabbitStageProjectionMapper;
    private final RabbitMapper rabbitMapper;
    private final BatchMapper batchMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;
    private final WorkTaskWriter workTaskWriter;
    private final ReproSettingResolver settingResolver;
    private final BreedingEligibilityValidator eligibilityValidator;
    private final ObjectMapper objectMapper;
    private final BusinessFileService businessFileService;

    public ReproStateMachineService(
        ReproCycleMapper reproCycleMapper,
        ReproEventMapper reproEventMapper,
        LitterMapper litterMapper,
        BizAttachmentMapper bizAttachmentMapper,
        RabbitStageProjectionMapper rabbitStageProjectionMapper,
        RabbitMapper rabbitMapper,
        BatchMapper batchMapper,
        BatchRabbitMapper batchRabbitMapper,
        RabbitStatusHistoryMapper rabbitStatusHistoryMapper,
        WorkTaskWriter workTaskWriter,
        ReproSettingResolver settingResolver,
        BreedingEligibilityValidator eligibilityValidator,
        ObjectMapper objectMapper,
        BusinessFileService businessFileService
    ) {
        this.eligibilityValidator = eligibilityValidator;
        this.reproCycleMapper = reproCycleMapper;
        this.reproEventMapper = reproEventMapper;
        this.litterMapper = litterMapper;
        this.bizAttachmentMapper = bizAttachmentMapper;
        this.rabbitStageProjectionMapper = rabbitStageProjectionMapper;
        this.rabbitMapper = rabbitMapper;
        this.batchMapper = batchMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
        this.workTaskWriter = workTaskWriter;
        this.settingResolver = settingResolver;
        this.objectMapper = objectMapper;
        this.businessFileService = businessFileService;
    }

    /**
     * 推进一个已存在的周期。
     *
     * @throws BizException 404 周期不存在；409 阶段不允许该动作 / 并发冲突；400 缺必要事实
     */
    @Transactional
    public ReproResult apply(ReproCommand command) {
        requireNotNull(command.getHouseId(), "兔舍");
        requireNotNull(command.getCycleId(), "生产周期");
        requireNotNull(command.getAction(), "操作类型");

        // 步骤 0：幂等回放。命中即原样返回首次结果，不做任何状态变更。
        ReproEvent replay = findReplay(command.getHouseId(), command.getRequestId());
        if (replay != null) {
            return replayResult(replay);
        }

        Date occurredAt = command.getOccurredAt() != null ? command.getOccurredAt() : DateUtil.now();
        Date today = DateUtil.now();
        String operator = operatorOf(command.getOperatorName(), command.getUserId());

        // 步骤 1：先锁批次和成员，再锁周期。移除成员、兔只离场和周期动作都按
        // 同一顺序取锁，避免动作与解除成员关系交错后留下无成员的 OPEN 周期。
        ReproCycle cycle = lockOpenCycleWithInvariant(
            command.getHouseId(), command.getCycleId()
        );
        Long expectedVersion = cycle.getStateVersion();
        ReproStage fromStage = ReproStage.parse(cycle.getStage());

        // 步骤 2：查转换表。非法组合在这里被挡下，且只有这一处判定。
        Transition transition = TransitionTable.require(fromStage, command.getAction(), discriminatorOf(command));
        validateFacts(command, transition, cycle);
        bindBatchForMatingIfNeeded(cycle, command, occurredAt, operator);
        boolean hasNextTask = willHaveNextTask(command, cycle, transition);
        validateNextReminderOutcome(command, hasNextTask);

        ReproSettings settings = settingResolver.resolve(command.getUserId(), command.getHouseId());

        // 步骤 3：把本次操作携带的事实落到周期上，随后据此算到期日。
        applyFacts(cycle, command, transition, occurredAt);

        Date calculatedDueTime = DueDateCalculator.compute(
            transition.dueAnchor(),
            DueContext.builder(occurredAt, today)
                .stageEnteredAt(cycle.getStageEnteredAt())
                .matingDate(cycle.getMatingDate())
                .expectedBirthDate(cycle.getExpectedBirthDate())
                .birthDate(cycle.getBirthDate())
                .userSpecified(command.getNextRemindAt())
                .build(),
            settings
        );
        Date dueTime = command.getNextRemindAt() != null
            ? command.getNextRemindAt()
            : calculatedDueTime;

        // 步骤 4：写事件。uk_re_request 是幂等的最终防线。
        ReproEvent event = writeEvent(
            command, cycle, transition, occurredAt, operator, hasNextTask, dueTime
        );

        // 步骤 5：维护窝，并把窝的计数回写到周期的计数列。
        Long litterId = maintainLitter(command, cycle, transition, occurredAt, operator);

        // 步骤 6：关闭 / 推进周期，带乐观锁。
        closeOrAdvance(cycle, transition, occurredAt, command, operator);
        if (reproCycleMapper.applyTransition(cycle, expectedVersion) == 0) {
            throw new BizException(409, "状态已变化，请刷新后重试");
        }
        if (transition.closesCycle()) {
            releaseClosedCycleBatchMembership(cycle, occurredAt, operator);
        }

        // 步骤 7：任务流转 —— 完成当前待办，建立下一条。
        TaskOutcome taskOutcome = rotateTasks(
            command, cycle, transition, litterId, dueTime, operator, event.getId(), occurredAt
        );
        if (taskOutcome.hasNextTask() != hasNextTask) {
            throw new IllegalStateException("下一待办结果与事务内预判不一致");
        }

        // 步骤 8：投影到 rabbits，供列表页免 join 过滤。
        projectMother(cycle, transition, occurredAt, operator);
        if (command.getAction() == ReproAction.MATING && command.getMaleRabbitId() != null) {
            rabbitStageProjectionMapper.touchLastMatingDate(
                command.getHouseId(), command.getMaleRabbitId(), occurredAt, operator
            );
        }

        saveAttachments(command, event.getId(), operator);

        ResultProjection projection = currentProjection(
            command.getHouseId(), cycle.getMotherRabbitId()
        );
        return new ReproResult(
            cycle.getId(),
            projection.currentCycleId(),
            event.getId(),
            litterId,
            taskOutcome.taskId(),
            projection.stage(),
            projection.lifecycle(),
            taskOutcome.dueTime(),
            taskOutcome.followUpCycle() != null ? taskOutcome.followUpCycle().getId() : null,
            false
        );
    }

    /**
     * 从任意阶段开启周期（T1 / 存量录入 / 回填共用）。
     *
     * <p>缺失必录事实一律拒绝而不是给默认值：默认值会造出线上不可能出现的状态组合，
     * 等到切换时才暴露。
     */
    @Transactional
    public ReproResult openCycleAt(OpenCycleCommand command) {
        requireNotNull(command.houseId(), "兔舍");
        requireNotNull(command.motherRabbitId(), "母兔");
        requireNotNull(command.targetStage(), "入轨阶段");

        ReproEvent replay = findReplay(command.houseId(), command.requestId());
        if (replay != null) {
            return replayResult(replay);
        }

        EntryPoint entry = EntryPoint.forStage(command.targetStage());
        validateEntryFacts(entry, command);

        Date occurredAt = command.occurredAt() != null ? command.occurredAt() : DateUtil.now();
        Date today = DateUtil.now();
        String operator = operatorOf(command.operatorName(), command.userId());
        ReproSettings settings = settingResolver.resolve(command.userId(), command.houseId());

        Long selectedBatchId = command.batchId();
        Long entryBatchId = entry.requiresBatch() ? selectedBatchId : null;
        if (entry.requiresBatch() && entryBatchId == null) {
            throw new BizException(400, "从【" + entry.stage().label() + "】入轨必须选择生产批次");
        }
        if (selectedBatchId != null) {
            requireActiveBatchForUpdate(command.houseId(), selectedBatchId);
        }
        Rabbit mother = requireEligibleMotherForUpdate(
            command.houseId(), command.motherRabbitId()
        );
        if (entry.stage() == ReproStage.AWAIT_PALPATION) {
            eligibilityValidator.validateMating(
                command.houseId(),
                command.motherRabbitId(),
                command.maleRabbitId(),
                command.stageEnteredAt() != null ? command.stageEnteredAt() : occurredAt
            );
        }
        assertPipelineFree(command.houseId(), command.motherRabbitId(), entry);
        if (entryBatchId != null) {
            assertBatchCycleFree(command.houseId(), entryBatchId, command.motherRabbitId());
        }
        if (selectedBatchId != null) {
            ensureActiveBreedingMember(
                command.houseId(), selectedBatchId, mother, entry.stage(), occurredAt, operator
            );
        }

        ReproCycle cycle = newCycle(command, entry, entryBatchId, occurredAt, operator);

        // 首任务到期日必须在 insert 之前算：它既是 work_tasks 的 due，也是
        // next_event_date 兼容列的值，而没有 OTA 的老 APK 只认后者。
        // 原先算在 insert 之后，新开的周期在旧提醒列表里会完全不出现。
        Date dueTime = DueDateCalculator.compute(
            entry.dueAnchor(),
            DueContext.builder(occurredAt, today)
                .stageEnteredAt(cycle.getStageEnteredAt())
                .matingDate(cycle.getMatingDate())
                .expectedBirthDate(cycle.getExpectedBirthDate())
                .birthDate(cycle.getBirthDate())
                .userSpecified(command.firstDueAt())
                .build(),
            settings
        );

        // 待分笼入轨必须同事务建窝：没有窝就没有分笼任务的主体，
        // 母兔会卡在一个永远不会被提醒的阶段。
        // 窝先建对象、后落库：仔数要同步进周期的兼容列，而 openCycleAt 全程只
        // insert 一次周期，insert 之后再改就只改到了内存里。
        Litter litter = entry.stage() == ReproStage.AWAIT_WEANING
            ? newLitterForEntry(command, cycle, operator)
            : null;
        if (litter != null) {
            syncLitterCounters(cycle, litter);
        }

        try {
            reproCycleMapper.insert(cycle);
        } catch (DuplicateKeyException e) {
            // uk_bc_batch_member 的兜底：前置检查与 insert 之间并发插入了同一 (批次, 母兔)。
            // 让它以业务语义 409 出去，而不是一个看不懂的 500。
            throw new BizException(409, "该母兔在本批次已有进行中的生产周期，请改选其他批次");
        }

        ReproEvent event = new ReproEvent();
        event.setHouseId(command.houseId());
        event.setCycleId(cycle.getId());
        event.setMotherRabbitId(command.motherRabbitId());
        event.setBatchId(cycle.getBatchId());
        event.setEventType(ReproEventType.CYCLE_START.name());
        event.setFromStage(null);
        event.setToStage(entry.stage().name());
        event.setOccurredAt(occurredAt);
        Map<String, Object> entryPayload = new LinkedHashMap<>();
        entryPayload.put("entryStage", entry.stage().name());
        putIfPresent(entryPayload, "plannedBatchId", cycle.getPlannedBatchId());
        event.setPayload(toJson(entryPayload));
        event.setOperatorId(command.userId());
        event.setOperatorName(operator);
        event.setRequestId(requireRequestId(command.requestId()));
        insertEvent(event);
        writeEntryOperationEvent(command, cycle, entry, occurredAt, operator);

        Long litterId = null;
        if (litter != null) {
            litter.setCycleId(cycle.getId());
            litterMapper.insert(litter);
            litterId = litter.getId();
        }

        WorkTask task = scheduleFor(cycle, litterId, TaskType.forStage(entry.stage()), dueTime, operator);
        projectCurrentMotherState(cycle, occurredAt, operator);

        ResultProjection projection = currentProjection(command.houseId(), command.motherRabbitId());
        return new ReproResult(
            cycle.getId(), projection.currentCycleId(), event.getId(), litterId, task.getId(),
            projection.stage(), projection.lifecycle(), dueTime, null, false
        );
    }

    // ---------------------------------------------------------------- 内部实现

    private ReproCycle lockOpenCycleWithInvariant(Long houseId, Long cycleId) {
        ReproCycle observed = reproCycleMapper.selectById(houseId, cycleId);
        if (observed == null) {
            throw new BizException(404, "生产周期不存在");
        }
        if (!CycleLifecycle.OPEN.name().equals(observed.getLifecycle())) {
            throw new BizException(409, "该生产周期已结束，无法继续操作");
        }

        ReproStage observedStage = ReproStage.parse(observed.getStage());
        if (observed.getBatchId() == null) {
            if (observedStage != ReproStage.READY
                && observedStage != ReproStage.AWAIT_ESTRUS
                && observedStage != ReproStage.AWAIT_MATING) {
                throw new BizException(409, "该生产阶段缺少生产批次，请联系管理员修复");
            }
            requireEligibleMotherForUpdate(houseId, observed.getMotherRabbitId());
        } else {
            requireActiveBatchForUpdate(houseId, observed.getBatchId());
            requireEligibleMotherForUpdate(houseId, observed.getMotherRabbitId());
            BatchRabbit member = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
                houseId, observed.getBatchId(), observed.getMotherRabbitId()
            );
            if (member == null || !"breeding".equals(member.getBatchRole())) {
                throw new BizException(409, "生产周期对应的繁殖批次成员关系不存在");
            }
        }

        ReproCycle locked = reproCycleMapper.selectByIdForUpdate(houseId, cycleId);
        if (locked == null) {
            throw new BizException(404, "生产周期不存在");
        }
        if (!CycleLifecycle.OPEN.name().equals(locked.getLifecycle())) {
            throw new BizException(409, "该生产周期已结束，无法继续操作");
        }
        if (!java.util.Objects.equals(observed.getBatchId(), locked.getBatchId())
            || !observed.getMotherRabbitId().equals(locked.getMotherRabbitId())) {
            throw new BizException(409, "生产周期归属已变化，请刷新后重试");
        }
        return locked;
    }

    private Batch requireActiveBatchForUpdate(Long houseId, Long batchId) {
        Batch batch = batchMapper.selectByIdForUpdate(houseId, batchId);
        if (batch == null) {
            throw new BizException(400, "生产批次不存在");
        }
        if (!"进行中".equals(batch.getStatus())) {
            throw new BizException(409, "生产批次不在进行中");
        }
        return batch;
    }

    private Rabbit requireEligibleMotherForUpdate(Long houseId, Long motherRabbitId) {
        Rabbit mother = rabbitMapper.selectByIdsForUpdate(houseId, List.of(motherRabbitId))
            .stream()
            .findFirst()
            .orElse(null);
        if (mother == null) {
            throw new BizException(400, "母兔不存在");
        }
        if (!Boolean.TRUE.equals(mother.getIsActive())) {
            throw new BizException(409, "母兔不在场");
        }
        if (!"0".equals(mother.getGender())) {
            throw new BizException(400, "母兔性别不正确");
        }
        if (!"0".equals(mother.getType()) && !"1".equals(mother.getType())) {
            throw new BizException(400, "母兔类型不正确");
        }
        return mother;
    }

    private void ensureActiveBreedingMember(
        Long houseId,
        Long batchId,
        Rabbit mother,
        ReproStage stage,
        Date occurredAt,
        String operator
    ) {
        BatchRabbit member = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
            houseId, batchId, mother.getId()
        );
        if (member != null) {
            if (!"breeding".equals(member.getBatchRole())) {
                throw new BizException(409, "母兔在所选批次中的成员角色不是繁殖兔");
            }
            return;
        }

        BatchRabbit link = new BatchRabbit();
        link.setBatchId(batchId);
        link.setRabbitId(mother.getId());
        link.setJoinReason("生产入轨");
        link.setBatchRole("breeding");
        link.setCurrentStatus(stage.label());
        link.setIsActive(Boolean.TRUE);
        link.setJoinDate(occurredAt);
        batchRabbitMapper.insertBatch(List.of(link));

        RabbitStatusHistory history = new RabbitStatusHistory();
        history.setHouseId(houseId);
        history.setRabbitId(mother.getId());
        history.setBatchId(batchId);
        history.setFromStatus(null);
        history.setToStatus(stage.label());
        history.setChangeTime(occurredAt);
        history.setReason("生产入轨加入批次");
        rabbitStatusHistoryMapper.insert(history);
    }

    private void bindBatchForMatingIfNeeded(
        ReproCycle cycle,
        ReproCommand command,
        Date occurredAt,
        String operator
    ) {
        if (command.getAction() != ReproAction.MATING) {
            return;
        }
        Long selectedBatchId = command.getBatchId() != null
            ? command.getBatchId()
            : cycle.getBatchId() != null
                ? cycle.getBatchId()
                : cycle.getPlannedBatchId();
        if (selectedBatchId == null) {
            throw new BizException(400, "配种时请选择生产批次");
        }
        if (cycle.getBatchId() != null) {
            if (!cycle.getBatchId().equals(selectedBatchId)) {
                throw new BizException(409, "生产周期已绑定其他批次，请刷新后重试");
            }
            return;
        }

        requireActiveBatchForUpdate(cycle.getHouseId(), selectedBatchId);
        assertBatchCycleFree(cycle.getHouseId(), selectedBatchId, cycle.getMotherRabbitId());
        if (cycle.getPlannedBatchId() != null
            && !cycle.getPlannedBatchId().equals(selectedBatchId)) {
            releasePlannedBatchMembership(cycle, occurredAt, operator);
        }
        Rabbit mother = requireEligibleMotherForUpdate(
            cycle.getHouseId(), cycle.getMotherRabbitId()
        );
        ensureActiveBreedingMember(
            cycle.getHouseId(),
            selectedBatchId,
            mother,
            ReproStage.AWAIT_PALPATION,
            occurredAt,
            operator
        );
        int batchCycleNo = nextCycleNo(
            cycle.getHouseId(), selectedBatchId, cycle.getMotherRabbitId()
        );
        if (reproCycleMapper.assignBatchIfUnboundWithCycleNo(
            cycle.getHouseId(), cycle.getId(), selectedBatchId, batchCycleNo, operator
        ) != 1) {
            throw new BizException(409, "生产周期批次已变化，请刷新后重试");
        }
        workTaskWriter.assignPendingCycleTasksToBatch(
            cycle.getHouseId(), cycle.getId(), selectedBatchId, operator
        );
        cycle.setBatchId(selectedBatchId);
        cycle.setPlannedBatchId(null);
        cycle.setCycleNo(batchCycleNo);
    }

    private void releasePlannedBatchMembership(
        ReproCycle cycle,
        Date occurredAt,
        String operator
    ) {
        BatchRabbit planned = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
            cycle.getHouseId(), cycle.getPlannedBatchId(), cycle.getMotherRabbitId()
        );
        if (planned != null) {
            batchRabbitMapper.deactivateIfActive(
                cycle.getHouseId(),
                planned.getId(),
                occurredAt,
                "配种时改选生产批次",
                operator
            );
        }
    }

    private void releaseClosedCycleBatchMembership(
        ReproCycle cycle,
        Date occurredAt,
        String operator
    ) {
        Long membershipBatchId = cycle.getBatchId() != null
            ? cycle.getBatchId()
            : cycle.getPlannedBatchId();
        if (membershipBatchId == null) {
            return;
        }
        BatchRabbit member = batchRabbitMapper.selectActiveByBatchAndRabbitForUpdate(
            cycle.getHouseId(), membershipBatchId, cycle.getMotherRabbitId()
        );
        if (member == null) {
            return;
        }
        int changed = batchRabbitMapper.deactivateIfActive(
            cycle.getHouseId(),
            member.getId(),
            occurredAt,
            "生产周期结束",
            operator
        );
        // 多表 UPDATE 同时修改 batch_rabbits 与 rabbits，MySQL 会返回 2。
        if (changed <= 0) {
            throw new BizException(409, "批次成员关系已变化，请刷新后重试");
        }

        RabbitStatusHistory history = new RabbitStatusHistory();
        history.setHouseId(cycle.getHouseId());
        history.setRabbitId(cycle.getMotherRabbitId());
        history.setBatchId(membershipBatchId);
        history.setFromStatus(ReproStage.parse(cycle.getStage()).label());
        history.setToStatus(CycleResult.REMOVED.name().equals(cycle.getResult())
            ? ReproStage.RETIRED.label()
            : ReproStage.READY.label());
        history.setChangeTime(occurredAt);
        history.setReason("生产周期结束，退出原批次");
        rabbitStatusHistoryMapper.insert(history);
    }

    private void writeEntryOperationEvent(
        OpenCycleCommand command,
        ReproCycle cycle,
        EntryPoint entry,
        Date occurredAt,
        String operator
    ) {
        ReproEventType eventType;
        ReproStage fromStage;
        Map<String, Object> payload = new LinkedHashMap<>();
        if (entry.stage() == ReproStage.AWAIT_PALPATION) {
            eventType = ReproEventType.MATING_DONE;
            fromStage = ReproStage.AWAIT_MATING;
            putIfPresent(payload, "maleRabbitId", command.maleRabbitId());
            putIfPresent(payload, "matingMethod", command.matingMethod());
        } else if (entry.stage() == ReproStage.AWAIT_WEANING) {
            eventType = ReproEventType.DELIVERY_DONE;
            fromStage = ReproStage.AWAIT_DELIVERY;
            putIfPresent(payload, "totalKits", command.totalKits());
            putIfPresent(payload, "liveKits", command.liveKits());
            putIfPresent(payload, "keptKits", command.keptKits());
        } else {
            return;
        }

        ReproEvent operation = new ReproEvent();
        operation.setHouseId(command.houseId());
        operation.setCycleId(cycle.getId());
        operation.setMotherRabbitId(cycle.getMotherRabbitId());
        operation.setBatchId(cycle.getBatchId());
        operation.setEventType(eventType.name());
        operation.setFromStage(fromStage.name());
        operation.setToStage(entry.stage().name());
        operation.setOccurredAt(
            command.stageEnteredAt() != null ? command.stageEnteredAt() : occurredAt
        );
        operation.setPayload(toJson(payload));
        operation.setOperatorId(command.userId());
        operation.setOperatorName(operator);
        operation.setRequestId(ReproRequestIds.derive(command.requestId(), eventType.name()));
        insertEvent(operation);
    }

    private ReproEvent findReplay(Long houseId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return reproEventMapper.selectByRequestId(houseId, requestId);
    }

    private ReproResult replayResult(ReproEvent event) {
        ReproCycle cycle = event.getCycleId() == null
            ? null
            : reproCycleMapper.selectById(event.getHouseId(), event.getCycleId());
        ReplayTaskMetadata taskMetadata = replayTaskMetadata(event);
        Date nextDueTime = null;
        if (!taskMetadata.known() || taskMetadata.hasNextTask()) {
            nextDueTime = cycle != null ? nextDueTimeOf(cycle) : null;
        }
        if (nextDueTime == null && taskMetadata.hasNextTask() && cycle != null
            && CycleLifecycle.CLOSED.name().equals(cycle.getLifecycle())) {
            for (ReproCycle candidate : reproCycleMapper.selectOpenByMother(
                cycle.getHouseId(), cycle.getMotherRabbitId()
            )) {
                nextDueTime = nextDueTimeOf(candidate);
                if (nextDueTime != null) {
                    break;
                }
            }
        }
        if (nextDueTime == null && taskMetadata.hasNextTask()) {
            nextDueTime = taskMetadata.dueTime();
        }
        Long motherRabbitId = event.getMotherRabbitId() != null
            ? event.getMotherRabbitId()
            : cycle != null ? cycle.getMotherRabbitId() : null;
        ResultProjection projection = currentProjection(event.getHouseId(), motherRabbitId);
        return new ReproResult(
            event.getCycleId(),
            projection.currentCycleId(),
            event.getId(),
            event.getLitterId(),
            null,
            projection.stage(),
            projection.lifecycle(),
            nextDueTime,
            null,
            true
        );
    }

    private ResultProjection currentProjection(Long houseId, Long motherRabbitId) {
        Rabbit mother = rabbitMapper.selectById(houseId, motherRabbitId);
        if (mother == null) {
            throw new IllegalStateException("母兔权威投影不存在");
        }
        Long currentCycleId = mother.getCurrentCycleId();
        String lifecycle = currentCycleId != null
            ? CycleLifecycle.OPEN.name()
            : CycleLifecycle.CLOSED.name();
        return new ResultProjection(
            ReproStage.parse(mother.getCurrentStage()), currentCycleId, lifecycle
        );
    }

    /** 该周期当前未完成待办的到期时间；没有则为 null。 */
    private Date nextDueTimeOf(ReproCycle cycle) {
        List<WorkTask> pending = workTaskWriter.pendingBySubject(
            cycle.getHouseId(), TaskSubjectType.CYCLE, cycle.getId());
        if (!pending.isEmpty()) {
            return pending.get(0).getDueTime();
        }
        Litter litter = litterMapper.selectByCycleId(cycle.getHouseId(), cycle.getId());
        if (litter == null) {
            return null;
        }
        pending = workTaskWriter.pendingBySubject(
            cycle.getHouseId(), TaskSubjectType.LITTER, litter.getId());
        return pending.isEmpty() ? null : pending.get(0).getDueTime();
    }

    private ReplayTaskMetadata replayTaskMetadata(ReproEvent event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            return ReplayTaskMetadata.unknown();
        }
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            JsonNode hasNextTask = payload.get("resultHasNextTask");
            if (hasNextTask == null || hasNextTask.isNull()) {
                return ReplayTaskMetadata.unknown();
            }
            if (!hasNextTask.asBoolean()) {
                return new ReplayTaskMetadata(true, false, null);
            }
            JsonNode dueTime = payload.get("resultNextDueTime");
            Date value = dueTime == null || dueTime.isNull()
                ? null
                : objectMapper.treeToValue(dueTime, Date.class);
            return new ReplayTaskMetadata(true, true, value);
        } catch (JsonProcessingException e) {
            return ReplayTaskMetadata.unknown();
        }
    }

    private ReproEvent writeEvent(
        ReproCommand command,
        ReproCycle cycle,
        Transition transition,
        Date occurredAt,
        String operator,
        boolean hasNextTask,
        Date nextDueTime
    ) {
        ReproEvent event = new ReproEvent();
        event.setHouseId(command.getHouseId());
        event.setCycleId(cycle.getId());
        event.setMotherRabbitId(cycle.getMotherRabbitId());
        event.setBatchId(cycle.getBatchId());
        event.setEventType(transition.eventType().name());
        event.setFromStage(transition.fromStage().name());
        event.setToStage(transition.toStage() != null ? transition.toStage().name() : null);
        event.setOccurredAt(occurredAt);
        event.setPayload(toJson(payloadOf(command, hasNextTask, nextDueTime)));
        event.setOperatorId(command.getUserId());
        event.setOperatorName(operator);
        event.setRequestId(requireRequestId(command.getRequestId()));
        insertEvent(event);
        return event;
    }

    private void insertEvent(ReproEvent event) {
        try {
            reproEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：前置回查没命中但唯一键命中。这里不做「读回首次结果」——
            // 当前事务的 REPEATABLE READ 快照看不到并发事务刚提交的行，读回只会拿到 null。
            // 交给客户端重试，届时步骤 0 会正常命中回放。
            throw new BizException(409, "该操作正在处理中，请勿重复提交");
        }
    }

    /** 把命令携带的事实落到周期上。到期日随后基于这些事实计算，故必须先于计算执行。 */
    private void applyFacts(
        ReproCycle cycle,
        ReproCommand command,
        Transition transition,
        Date occurredAt
    ) {
        switch (command.getAction()) {
            case MATING -> {
                cycle.setMatingDate(occurredAt);
                cycle.setMaleRabbitId(command.getMaleRabbitId());
                MatingMethod matingMethod = command.getMatingMethod();
                // 老 APK 未发送 matingMethod；只要带有公兔即可按体配兼容回填。
                if (matingMethod == null && command.getMaleRabbitId() != null) {
                    matingMethod = MatingMethod.NATURAL;
                }
                cycle.setMatingMethod(matingMethod != null ? matingMethod.name() : null);
                // 预产期参考值在配种时一次算定，不参与后续提醒推进。
                cycle.setExpectedBirthDate(DueDateCalculator.expectedBirthDate(occurredAt));
            }
            case PALPATION -> {
                cycle.setPregnancyCheckDate(occurredAt);
                cycle.setPregnancyResult(command.getPalpationResult() != null
                    ? command.getPalpationResult().label()
                    : null);
            }
            case DELIVERY -> {
                if (DeliveryOutcome.BORN.name().equals(command.getOutcome())) {
                    cycle.setBirthDate(occurredAt);
                }
            }
            case WEANING -> cycle.setWeaningDate(occurredAt);
            default -> {
                // ESTRUS / PREPARTUM / ABORTION / POSTPONE / RETIRE 不携带额外事实。
            }
        }
        if (!transition.closesCycle() && transition.toStage() != null
            && transition.toStage() != transition.fromStage()) {
            cycle.setStageEnteredAt(occurredAt);
        }
    }

    private void closeOrAdvance(
        ReproCycle cycle,
        Transition transition,
        Date occurredAt,
        ReproCommand command,
        String operator
    ) {
        if (transition.closesCycle()) {
            cycle.setLifecycle(CycleLifecycle.CLOSED.name());
            cycle.setResult(transition.result().name());
            cycle.setClosedAt(occurredAt);
            cycle.setCloseReason(command.getReason() != null
                ? command.getReason()
                : transition.result().label());
            // stage 刻意保持不变：流产统计要按「在哪个阶段流的」分组，
            // 关闭时把它抹平就再也查不出来了。并发守卫只看 lifecycle，不受影响。
        } else if (transition.toStage() != null) {
            cycle.setStage(transition.toStage().name());
        }
    }

    private Long maintainLitter(
        ReproCommand command,
        ReproCycle cycle,
        Transition transition,
        Date occurredAt,
        String operator
    ) {
        if (transition.createsLitter()) {
            Litter litter = new Litter();
            litter.setHouseId(cycle.getHouseId());
            litter.setCycleId(cycle.getId());
            litter.setMotherRabbitId(cycle.getMotherRabbitId());
            litter.setSireRabbitId(cycle.getMaleRabbitId());
            litter.setBatchId(cycle.getBatchId());
            litter.setBirthDate(occurredAt);
            litter.setTotalKits(command.getTotalKits());
            litter.setLiveKits(command.getLiveKits());
            litter.setKeptKits(command.getKeptKits() != null ? command.getKeptKits() : command.getLiveKits());
            litter.setFosterIn(0);
            litter.setFosterOut(0);
            litter.setLossCount(Math.max(0, orZero(command.getTotalKits()) - orZero(command.getLiveKits())));
            litter.setCurrentNursing(litter.getKeptKits() != null ? litter.getKeptKits() : 0);
            litter.setStatus(LitterStatus.NURSING.name());
            litter.setNursingCageId(command.getNursingCageId());
            litter.setRequestId(command.getRequestId());
            litter.setRemark(command.getRemark());
            litterMapper.insert(litter);
            syncLitterCounters(cycle, litter);
            return litter.getId();
        }

        if (command.getAction() == ReproAction.WEANING) {
            Litter litter = litterMapper.selectByCycleIdForUpdate(cycle.getHouseId(), cycle.getId());
            if (litter == null) {
                throw new BizException(409, "该周期没有可分笼的窝");
            }
            int currentNursing = orZero(litter.getCurrentNursing());
            if (command.getWeanedCount() > currentNursing) {
                throw new BizException(
                    409,
                    "当前哺乳数已变化，最多可断奶 " + currentNursing + " 只，请刷新后重试"
                );
            }
            litter.setStatus(LitterStatus.WEANED.name());
            litter.setWeaningDate(occurredAt);
            litter.setWeanedCount(command.getWeanedCount());
            litter.setAvgWeaningWeight(command.getAvgWeaningWeight());
            litter.setCurrentNursing(0);
            litter.setNursingCageId(command.getNursingCageId() != null
                ? command.getNursingCageId()
                : litter.getNursingCageId());
            litterMapper.update(litter);
            syncLitterCounters(cycle, litter);
            return litter.getId();
        }

        Litter existing = litterMapper.selectByCycleId(cycle.getHouseId(), cycle.getId());
        return existing != null ? existing.getId() : null;
    }

    /** 把窝的权威计数复制到周期的兼容列，仅为让未升级的老 APK 继续正确显示。 */
    private void syncLitterCounters(ReproCycle cycle, Litter litter) {
        cycle.setTotalKits(litter.getTotalKits());
        cycle.setLiveKits(litter.getLiveKits());
        cycle.setCurrentNursingKits(litter.getCurrentNursing());
        cycle.setWeanedKits(litter.getWeanedCount());
        cycle.setAvgWeaningWeight(litter.getAvgWeaningWeight());
    }

    private TaskOutcome rotateTasks(
        ReproCommand command,
        ReproCycle cycle,
        Transition transition,
        Long litterId,
        Date dueTime,
        String operator,
        Long eventId,
        Date occurredAt
    ) {
        if (command.getAction() == ReproAction.POSTPONE) {
            // 推迟不换阶段也不换任务，只改期；任务保持 PENDING，不会从待办里消失。
            TaskSubjectType subjectType = ReproStage.parse(cycle.getStage()) == ReproStage.AWAIT_WEANING
                ? TaskSubjectType.LITTER
                : TaskSubjectType.CYCLE;
            Long subjectId = subjectType == TaskSubjectType.LITTER ? litterId : cycle.getId();
            Long taskId = null;
            Date actualDueTime = null;
            for (WorkTask task : pendingOf(cycle.getHouseId(), subjectType, subjectId)) {
                workTaskWriter.postpone(cycle.getHouseId(), task.getId(), dueTime, operator);
                taskId = task.getId();
                actualDueTime = dueTime;
            }
            return new TaskOutcome(taskId, actualDueTime, null);
        }

        if (transition.cancelsAllTasks()) {
            workTaskWriter.cancelAllForRabbit(cycle.getHouseId(), cycle.getMotherRabbitId(), operator);
            return new TaskOutcome(null, null, null);
        }

        // 完成当前待办：周期主体与窝主体都要扫，血配时两条任务并存。
        workTaskWriter.completeBySubject(
            cycle.getHouseId(), TaskSubjectType.CYCLE, cycle.getId(), eventId, operator
        );
        if (litterId != null && command.getAction() == ReproAction.WEANING) {
            workTaskWriter.completeBySubject(
                cycle.getHouseId(), TaskSubjectType.LITTER, litterId, eventId, operator
            );
        }

        if (!transition.closesCycle()) {
            WorkTask next = scheduleFor(
                cycle, litterId, TaskType.forStage(transition.toStage()), dueTime, operator
            );
            return new TaskOutcome(next.getId(), next.getDueTime(), null);
        }

        workTaskWriter.cancelBySubject(cycle.getHouseId(), TaskSubjectType.CYCLE, cycle.getId(), operator);
        if (transition.followUpStage() == null) {
            return new TaskOutcome(null, null, null);
        }

        // 血配：母兔在哺乳期已被重新配种，管线上已有周期在跑，这里不能再开一个，
        // 否则同一母兔出现两条管线周期，V27 的 uk_bc_pipeline 会直接拒绝写入。
        ReproCycle running = reproCycleMapper.selectOpenPipelineForUpdate(
            cycle.getHouseId(), cycle.getMotherRabbitId()
        );
        if (running != null) {
            return new TaskOutcome(null, null, null);
        }

        ReproCycle followUp = openFollowUpCycle(cycle, transition, occurredAt, operator);
        WorkTask next = scheduleFor(
            followUp, null, TaskType.forStage(transition.followUpStage()), dueTime, operator
        );
        return new TaskOutcome(next.getId(), next.getDueTime(), followUp);
    }

    private ReproCycle openFollowUpCycle(
        ReproCycle closed,
        Transition transition,
        Date occurredAt,
        String operator
    ) {
        ReproCycle followUp = new ReproCycle();
        followUp.setHouseId(closed.getHouseId());
        followUp.setTenantId(closed.getTenantId());
        followUp.setBatchId(null);
        followUp.setMotherRabbitId(closed.getMotherRabbitId());
        followUp.setCycleNo(nextCycleNo(
            closed.getHouseId(), null, closed.getMotherRabbitId()
        ));
        followUp.setStage(transition.followUpStage().name());
        followUp.setStageEnteredAt(occurredAt);
        followUp.setLifecycle(CycleLifecycle.OPEN.name());
        reproCycleMapper.insert(followUp);
        return followUp;
    }

    private ReproCycle newCycle(
        OpenCycleCommand command,
        EntryPoint entry,
        Long entryBatchId,
        Date occurredAt,
        String operator
    ) {
        ReproCycle cycle = new ReproCycle();
        cycle.setHouseId(command.houseId());
        cycle.setBatchId(entryBatchId);
        cycle.setPlannedBatchId(entry.requiresBatch() ? null : command.batchId());
        cycle.setMotherRabbitId(command.motherRabbitId());
        cycle.setMaleRabbitId(command.maleRabbitId());
        cycle.setCycleNo(nextCycleNo(command.houseId(), entryBatchId, command.motherRabbitId()));
        cycle.setStage(entry.stage().name());
        cycle.setStageEnteredAt(command.stageEnteredAt() != null ? command.stageEnteredAt() : occurredAt);
        cycle.setLifecycle(CycleLifecycle.OPEN.name());
        cycle.setMatingMethod(command.matingMethod() != null ? command.matingMethod().name() : null);
        Date enteredAt = command.stageEnteredAt() != null ? command.stageEnteredAt() : occurredAt;
        cycle.setMatingDate(entry.stage() == ReproStage.AWAIT_PALPATION
            ? enteredAt
            : command.matingDate());
        cycle.setBirthDate(entry.stage() == ReproStage.AWAIT_WEANING
            ? enteredAt
            : command.birthDate());
        cycle.setExpectedBirthDate(command.expectedBirthDate() != null
            ? command.expectedBirthDate()
            : DueDateCalculator.expectedBirthDate(cycle.getMatingDate()));
        cycle.setRequestId(command.requestId());
        cycle.setRemark(command.remark());
        return cycle;
    }

    private Litter newLitterForEntry(OpenCycleCommand command, ReproCycle cycle, String operator) {
        Litter litter = new Litter();
        litter.setHouseId(command.houseId());
        litter.setCycleId(cycle.getId());
        litter.setMotherRabbitId(command.motherRabbitId());
        litter.setSireRabbitId(command.maleRabbitId());
        litter.setBatchId(cycle.getBatchId());
        litter.setBirthDate(cycle.getBirthDate());
        litter.setTotalKits(command.totalKits());
        litter.setLiveKits(command.liveKits());
        litter.setKeptKits(command.keptKits());
        litter.setFosterIn(0);
        litter.setFosterOut(0);
        litter.setLossCount(Math.max(0, orZero(litter.getTotalKits()) - orZero(command.liveKits())));
        litter.setCurrentNursing(orZero(command.keptKits()));
        litter.setStatus(LitterStatus.NURSING.name());
        litter.setRequestId(command.requestId());
        return litter;
    }

    /**
     * 把母兔的当前阶段投影到 rabbits。
     *
     * <p>刻意<b>从真实状态反查</b>，而不是照抄本次转换的 {@code projectedMotherStage()}：
     * 血配场景下关闭旧的哺乳周期时，母兔其实正跑在另一条管线周期上，照抄转换结果
     * 会把她的阶段写成旧周期的后继（待催情），投影当场失真。
     * 「投影 = 事实的函数」这条规则一旦破例，就退回旧模型多写点各自漂移的老路了。
     */
    private void projectMother(ReproCycle cycle, Transition transition, Date occurredAt, String operator) {
        Long houseId = cycle.getHouseId();
        Long motherId = cycle.getMotherRabbitId();

        // 离场是终态，优先级高于任何在跑的周期。
        if (transition.result() == CycleResult.REMOVED) {
            rabbitStageProjectionMapper.projectStage(
                houseId, motherId, ReproStage.RETIRED.name(), null, occurredAt, operator
            );
            return;
        }

        projectCurrentMotherState(cycle, occurredAt, operator);
    }

    /**
     * 母兔投影只表达下一轮繁育管线；窝的哺乳与分笼状态留在旧周期和窝待办中。
     * 因此分娩后即回到准备态，可在尚未分笼时开启下一轮待催情。
     */
    private void projectCurrentMotherState(
        ReproCycle changedCycle,
        Date occurredAt,
        String operator
    ) {
        Long houseId = changedCycle.getHouseId();
        Long motherId = changedCycle.getMotherRabbitId();
        ReproCycle pipeline = reproCycleMapper.selectOpenPipelineForUpdate(houseId, motherId);
        if (pipeline != null) {
            rabbitStageProjectionMapper.projectStage(
                houseId, motherId, pipeline.getStage(), pipeline.getId(), occurredAt, operator
            );
            return;
        }

        ReproStage changedStage = ReproStage.parse(changedCycle.getStage());
        if (CycleLifecycle.OPEN.name().equals(changedCycle.getLifecycle())
            && changedStage != ReproStage.AWAIT_WEANING) {
            rabbitStageProjectionMapper.projectStage(
                houseId,
                motherId,
                changedStage.name(),
                changedCycle.getId(),
                occurredAt,
                operator
            );
            return;
        }
        rabbitStageProjectionMapper.projectStage(
            houseId, motherId, ReproStage.READY.name(), null, occurredAt, operator
        );
    }

    private WorkTask scheduleFor(ReproCycle cycle, Long litterId, TaskType taskType, Date dueTime, String operator) {
        return workTaskWriter.schedule(new WorkTaskWriter.TaskScheduleRequest(
            cycle.getHouseId(),
            taskType,
            cycle.getId(),
            litterId,
            cycle.getMotherRabbitId(),
            cycle.getBatchId() != null ? cycle.getBatchId() : cycle.getPlannedBatchId(),
            cageOf(cycle.getHouseId(), cycle.getMotherRabbitId()),
            dueTime,
            operator
        ));
    }

    private List<WorkTask> pendingOf(Long houseId, TaskSubjectType subjectType, Long subjectId) {
        if (subjectId == null) {
            return List.of();
        }
        return workTaskWriter.pendingBySubject(houseId, subjectType, subjectId);
    }

    private Long cageOf(Long houseId, Long rabbitId) {
        Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
        return rabbit != null ? rabbit.getCageId() : null;
    }

    private int nextCycleNo(Long houseId, Long batchId, Long motherRabbitId) {
        Integer max = reproCycleMapper.selectMaxCycleNo(houseId, batchId, motherRabbitId);
        return max == null ? 1 : max + 1;
    }

    private void saveAttachments(ReproCommand command, Long eventId, String operator) {
        List<String> fileIds = command.getAttachmentFileIds();
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        int sort = 0;
        for (String fileId : fileIds) {
            BizAttachment attachment = new BizAttachment();
            attachment.setHouseId(command.getHouseId());
            attachment.setBizType("REPRO_EVENT");
            attachment.setBizId(eventId);
            attachment.setFileId(fileId);
            attachment.setSortNo(sort++);
            attachment.setRequestId(command.getRequestId());
            bizAttachmentMapper.insertIgnore(attachment);
        }
    }

    /**
     * 转换表的第三维判别值。
     *
     * <p>摸胎的分支由「怀孕/空怀/不确定」决定，而它语义上就是 {@code palpationResult}；
     * 接产的分支由「产仔/失败」决定，在 {@code outcome}。此前这里只读 outcome，
     * 于是 HTTP 客户端必须把摸胎结论重复填两个字段，否则只能得到一句
     * 「当前阶段【待摸胎】不允许执行【摸胎】」——看不出真正的原因。
     * 既有的服务层测试两个字段都填，恰好把这个缺陷绕了过去。
     */
    private static String discriminatorOf(ReproCommand command) {
        if (command.getAction() == ReproAction.PALPATION && command.getPalpationResult() != null) {
            return command.getPalpationResult().name();
        }
        return command.getOutcome();
    }

    // ------------------------------------------------------------------ 前置校验

    private boolean willHaveNextTask(
        ReproCommand command,
        ReproCycle cycle,
        Transition transition
    ) {
        if (command.getAction() == ReproAction.POSTPONE || !transition.closesCycle()) {
            return true;
        }
        if (transition.cancelsAllTasks() || transition.followUpStage() == null) {
            return false;
        }
        if (transition.fromStage() != ReproStage.AWAIT_WEANING) {
            return true;
        }
        // 分笼是否接续新周期取决于当前是否已有血配管线，不能只看转换表的 followUpStage。
        return reproCycleMapper.selectOpenPipelineForUpdate(
            cycle.getHouseId(), cycle.getMotherRabbitId()
        ) == null;
    }

    private static void validateNextReminderOutcome(ReproCommand command, boolean hasNextTask) {
        if (command.getNextRemindAt() != null && !hasNextTask) {
            throw new BizException(400, "本次操作不会生成后续待办，不能设置下次提醒日期");
        }
    }

    private void validateFacts(ReproCommand command, Transition transition, ReproCycle cycle) {
        requireNotNull(command.getOccurredAt(), "执行时间");
        if (command.getOccurredAt().after(new Date(DateUtil.now().getTime() + 5L * 60L * 1000L))) {
            throw new BizException(400, "执行时间不能晚于当前时间");
        }
        command.setAttachmentFileIds(
            businessFileService.requireImages(
                command.getHouseId(), command.getAttachmentFileIds(), false
            )
        );
        if (command.getNextRemindAt() != null && !isAllowedReminderDate(command.getNextRemindAt())) {
            throw new BizException(400, "下次提醒日期不能早于今天");
        }
        switch (command.getAction()) {
            case MATING -> {
                if (cycle.getBatchId() == null
                    && cycle.getPlannedBatchId() == null
                    && command.getBatchId() == null) {
                    throw new BizException(400, "配种时请选择生产批次");
                }
                if (command.getMatingMethod() == null) {
                    throw new BizException(400, "请选择配种方式");
                }
                if (command.getMatingMethod() != MatingMethod.AI
                    && command.getMaleRabbitId() == null) {
                    throw new BizException(400, "请选择配种公兔");
                }
                // 放在这里而不是编排层：批量待办直连状态机，
                // 校验若在外层，批量配种会整片漏检。
                // 母兔取自已加锁的周期，不信任入参：
                // 在既有周期上执行动作时，客户端只传 cycleId。
                eligibilityValidator.validateMating(
                    command.getHouseId(),
                    cycle.getMotherRabbitId(),
                    command.getMaleRabbitId(),
                    command.getOccurredAt()
                );
            }
            case PALPATION -> {
                if (command.getPalpationResult() == null) {
                    throw new BizException(400, "请选择摸胎结论");
                }
                if (command.getPalpationResult() == PalpationResult.UNSURE
                    && !isAllowedReminderDate(command.getNextRemindAt())) {
                    // 不确定必须给今天或之后的复查日：不给的话这只兔子会停在待摸胎且没有下一次提醒，
                    // 正是旧实现里「兔子消失在流程中」的典型成因。
                    throw new BizException(400, "摸胎结论为不确定时，请选择今天或未来的复查日期");
                }
            }
            case DELIVERY -> {
                if (!DeliveryOutcome.BORN.name().equals(command.getOutcome())
                    && !DeliveryOutcome.FAILED.name().equals(command.getOutcome())) {
                    throw new BizException(400, "接产结果必须是产仔或失败产");
                }
                requireNotNull(command.getTotalKits(), "总产仔数");
                requireNotNull(command.getLiveKits(), "活仔数");
                requireNotNull(command.getKeptKits(), "留仔数");
                if (command.getTotalKits() < 0 || command.getLiveKits() < 0
                    || command.getKeptKits() < 0) {
                    throw new BizException(400, "产仔数量不能为负数");
                }
                if (DeliveryOutcome.FAILED.name().equals(command.getOutcome())) {
                    if (command.getTotalKits() != 0 || command.getLiveKits() != 0
                        || command.getKeptKits() != 0) {
                        throw new BizException(400, "失败产的总产仔数、活仔数和留仔数必须为 0");
                    }
                    requireText(command.getRemark(), "难产详情");
                    command.setAttachmentFileIds(
                        businessFileService.requireImages(
                            command.getHouseId(), command.getAttachmentFileIds(), true
                        )
                    );
                } else {
                    if (command.getLiveKits() > command.getTotalKits()) {
                        throw new BizException(400, "活仔数不能大于总产仔数");
                    }
                    if (command.getKeptKits() > command.getLiveKits()) {
                        throw new BizException(400, "留仔数不能大于活仔数");
                    }
                }
            }
            case WEANING -> {
                requireNotNull(command.getWeanedCount(), "断奶只数");
                if (command.getWeanedCount() < 0) {
                    throw new BizException(400, "断奶只数不能为负数");
                }
            }
            case ABORTION -> {
                requireText(command.getRemark(), "流产详情");
                command.setAttachmentFileIds(
                    businessFileService.requireImages(
                        command.getHouseId(), command.getAttachmentFileIds(), true
                    )
                );
                requireNotNull(command.getStillbirthCount(), "流产死胎数");
                if (command.getStillbirthCount() < 0) {
                    throw new BizException(400, "流产死胎数不能为负数");
                }
            }
            case POSTPONE -> {
                if (!isAllowedReminderDate(command.getNextRemindAt())) {
                    throw new BizException(400, "请选择今天或未来的下次提醒日期");
                }
            }
            default -> {
                // 其余动作无附加必填项。
            }
        }
    }

    private static boolean isAllowedReminderDate(Date value) {
        return value != null && DateUtil.isTodayOrFuture(value);
    }

    private void validateEntryFacts(EntryPoint entry, OpenCycleCommand command) {
        for (EntryPoint.RequiredFact fact : entry.requiredFacts()) {
            boolean present = switch (fact) {
                case STAGE_ENTERED_AT -> command.stageEnteredAt() != null || command.occurredAt() != null;
                case MATING_DATE -> command.matingDate() != null;
                case BIRTH_DATE -> command.birthDate() != null;
                case MALE_RABBIT -> command.maleRabbitId() != null;
                case MATING_METHOD -> command.matingMethod() != null;
                case TOTAL_KITS -> command.totalKits() != null;
                case LIVE_KITS -> command.liveKits() != null;
                case KEPT_KITS -> command.keptKits() != null;
            };
            if (!present) {
                throw new BizException(400, "从【" + entry.stage().label() + "】入轨需要补录" + fact.label());
            }
        }
        if (entry.stage() == ReproStage.AWAIT_WEANING) {
            if (command.totalKits() < 0 || command.liveKits() < 0 || command.keptKits() < 0) {
                throw new BizException(400, "产仔数量不能为负数");
            }
            if (command.liveKits() > command.totalKits()) {
                throw new BizException(400, "活仔数不能大于产仔数");
            }
            if (command.keptKits() > command.liveKits()) {
                throw new BizException(400, "留仔数不能大于活仔数");
            }
        }
    }

    /** 管线互斥：同一母兔同时只能有一条管线周期，哺乳段不占位。 */
    private void assertPipelineFree(Long houseId, Long motherRabbitId, EntryPoint entry) {
        if (!entry.occupiesPipeline()) {
            return;
        }
        ReproCycle running = reproCycleMapper.selectOpenPipelineForUpdate(houseId, motherRabbitId);
        if (running != null) {
            throw new BizException(409, "该母兔已有进行中的生产周期（阶段："
                + ReproStage.parse(running.getStage()).label() + "）");
        }
    }

    /**
     * 批次互斥：一只母兔在同一批次内至多一条未结束周期（V44 uk_bc_batch_member）。
     *
     * <p>这里拦的恰好是 {@link #assertPipelineFree} 放行的那一格：哺乳周期不占管线，
     * 所以母兔带崽期间可以重新配种（血配）。新定义下血配<b>仍然允许</b>，只是第二条
     * 并行周期必须落在另一个批次上，于是「母兔同时处于两个周期」自然等价于
     * 「母兔同时属于两个批次」（飞书 recvqh3EJXzmO1）。
     *
     * <p>目标批次<b>要求调用方显式传入</b>，服务端不自动建批：批次是需求里明写的
     * 「用户创建、用户点击结束」的容器，自动造出来的批次带着用户没取过的编号出现在
     * 列表里，还得用户亲手去结束它；而且自动建批直接剥夺了「把它放进现有批次」
     * 这个更常见的选择。
     */
    private void assertBatchCycleFree(Long houseId, Long batchId, Long motherRabbitId) {
        ReproCycle inBatch = reproCycleMapper.selectOpenByBatchAndMotherForUpdate(
            houseId, batchId, motherRabbitId
        );
        if (inBatch != null) {
            throw new BizException(409, "该母兔在本批次已有进行中的生产周期（阶段："
                + ReproStage.parse(inBatch.getStage()).label()
                + "），并行的下一轮请改选其他批次");
        }
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static void requireNotNull(Object value, String what) {
        if (value == null) {
            throw new BizException(400, "请填写" + what);
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, "请填写" + what);
        }
    }

    private static String requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BizException(400, "requestId不能为空");
        }
        return requestId;
    }

    private static String operatorOf(String operatorName, Long userId) {
        if (operatorName != null && !operatorName.isBlank()) {
            return operatorName;
        }
        return userId != null ? String.valueOf(userId) : "system";
    }

    private Map<String, Object> payloadOf(
        ReproCommand command,
        boolean hasNextTask,
        Date nextDueTime
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultHasNextTask", hasNextTask);
        putIfPresent(payload, "resultNextDueTime", hasNextTask ? nextDueTime : null);
        putIfPresent(payload, "maleRabbitId", command.getMaleRabbitId());
        MatingMethod matingMethod = command.getMatingMethod();
        if (matingMethod == null && command.getMaleRabbitId() != null) {
            matingMethod = MatingMethod.NATURAL;
        }
        putIfPresent(payload, "matingMethod", matingMethod);
        putIfPresent(payload, "palpationResult", command.getPalpationResult());
        putIfPresent(payload, "totalKits", command.getTotalKits());
        putIfPresent(payload, "liveKits", command.getLiveKits());
        putIfPresent(payload, "keptKits", command.getKeptKits());
        putIfPresent(payload, "stillbirthCount", command.getStillbirthCount());
        putIfPresent(payload, "weanedCount", command.getWeanedCount());
        putIfPresent(payload, "avgWeaningWeight", command.getAvgWeaningWeight());
        putIfPresent(payload, "nursingCageId", command.getNursingCageId());
        putIfPresent(payload, "nextRemindAt", command.getNextRemindAt());
        putIfPresent(payload, "reason", command.getReason());
        putIfPresent(payload, "remark", command.getRemark());
        putIfPresent(payload, "attachments", command.getAttachmentFileIds());
        return payload;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value instanceof Enum<?> e ? e.name() : value);
        }
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "事件内容序列化失败");
        }
    }

    private record TaskOutcome(Long taskId, Date dueTime, ReproCycle followUpCycle) {
        private boolean hasNextTask() {
            return dueTime != null;
        }
    }

    private record ReplayTaskMetadata(boolean known, boolean hasNextTask, Date dueTime) {
        private static ReplayTaskMetadata unknown() {
            return new ReplayTaskMetadata(false, false, null);
        }
    }

    private record ResultProjection(ReproStage stage, Long currentCycleId, String lifecycle) {
    }
}
