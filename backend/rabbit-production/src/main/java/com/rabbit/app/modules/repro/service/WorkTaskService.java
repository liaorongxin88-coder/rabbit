package com.rabbit.app.modules.repro.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.dto.BulkActionRequest;
import com.rabbit.app.modules.repro.dto.BulkActionResult;
import com.rabbit.app.modules.repro.dto.TaskPage;
import com.rabbit.app.modules.repro.dto.TaskView;
import com.rabbit.app.modules.repro.entity.WorkTask;
import com.rabbit.app.modules.repro.mapper.WorkTaskMapper;
import com.rabbit.app.util.DateUtil;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 待办中心：查询与批量推进。
 *
 * <p>取代原来的夜间扫表 + 三处提醒字段。查询走 {@code idx_wt_due} 单索引直查，
 * 批量则把每一只母兔的推进委托给状态机的单只事务。
 *
 * <p><b>本类刻意不加 {@code @Transactional}。</b>批量必须是部分成功语义：
 * 一百只里有一只被他人并发推进过，不该让另外九十九只一起回滚。事务边界因此
 * 落在 {@code ReproStateMachineService.apply()} 的单只调用上（设计 §5.1
 * 「分块短事务」），本类只负责解析目标、定序、收集逐项结果。
 */
@Service
public class WorkTaskService {

    /**
     * 单次批量的目标上限。
     *
     * <p>逐项独立事务本身没有长事务风险，设上限是为了不让一个请求跑到 HTTP 超时——
     * 超时的批量会留下「提交了一半且客户端不知道推进到哪」的状态。宁可让客户端
     * 收到明确的 400 去缩小范围，也不要一个结果不可知的请求。
     */
    private static final int MAX_BULK_TARGETS = 500;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 不允许批量的操作。
     *
     * <p>接产与分笼的每只数据都不同（仔数、活仔数、断奶数、目标笼）。共享一份
     * payload 会把同一个仔数写给整批母兔，产出的是一批看起来成功、实则全错的记录。
     * 这类操作只能逐只提交。
     */
    private static final Set<ReproAction> BULK_FORBIDDEN =
        EnumSet.of(ReproAction.START_CYCLE, ReproAction.DELIVERY, ReproAction.WEANING);

    /** 与具体任务类型无关、对任何待办都成立的操作。 */
    private static final Set<ReproAction> TYPE_AGNOSTIC =
        EnumSet.of(ReproAction.POSTPONE, ReproAction.RETIRE, ReproAction.ABORTION);

    private final WorkTaskMapper workTaskMapper;
    private final ReproActionService reproActionService;

    public WorkTaskService(WorkTaskMapper workTaskMapper, ReproActionService reproActionService) {
        this.workTaskMapper = workTaskMapper;
        this.reproActionService = reproActionService;
    }

    /**
     * 待办列表（首页今日待办 / NFC 碰笼 / 兔卡片共用）。
     *
     * @param dueBefore 含当日；为空时取今天，即「今日及逾期」
     */
    public TaskPage pendingDue(
        Long houseId,
        Date dueBefore,
        String taskType,
        Long batchId,
        Long cageId,
        Long rabbitId,
        int page,
        int size
    ) {
        return pendingDue(houseId, dueBefore, taskType, batchId, cageId, rabbitId, page, size, false);
    }

    /**
     * 待办列表，可选择忽略到期日上限以查询全部未来待办。
     *
     * @param dueBefore    含当日；为空且不含未来时取今天，即「今日及逾期」
     * @param includeFuture 为 true 时忽略 dueBefore，不应用到期日上限
     */
    public TaskPage pendingDue(
        Long houseId,
        Date dueBefore,
        String taskType,
        Long batchId,
        Long cageId,
        Long rabbitId,
        int page,
        int size,
        boolean includeFuture
    ) {
        Date today = startOfToday();
        Date bound = includeFuture ? null : (dueBefore == null ? today : dueBefore);
        String normalizedType = taskType == null || taskType.isBlank()
            ? null
            : TaskType.parse(taskType).name();
        int safeSize = Math.clamp(size, 1, 200);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;

        long total = workTaskMapper.countPendingDue(houseId, bound, normalizedType, batchId, cageId, rabbitId);
        List<WorkTask> rows = workTaskMapper.selectPendingDue(
            houseId, bound, normalizedType, batchId, cageId, rabbitId, offset, safeSize
        );
        List<TaskView> items = new ArrayList<>(rows.size());
        for (WorkTask row : rows) {
            items.add(TaskView.of(row, today));
        }
        return new TaskPage(total, safePage, safeSize, items);
    }

    /**
     * 批量推进待办。
     *
     * <p>逐项派生幂等键（{@code requestId-taskId}），因此整批重试时已成功的项
     * 会命中回放而不会二次推进。
     */
    public BulkActionResult bulkApply(
        Long houseId,
        Long userId,
        String operatorName,
        BulkActionRequest request
    ) {
        ReproAction action = ReproAction.parse(request.getAction());
        if (action == ReproAction.MATING) {
            throw new BizException(400, "批量配种功能已下线，请逐只提交配种记录");
        }
        if (BULK_FORBIDDEN.contains(action)) {
            throw new BizException(400, "【" + action.label() + "】每只数据不同，不支持批量，请逐只提交");
        }
        if (action == ReproAction.POSTPONE && request.getNextRemindAt() == null) {
            throw new BizException(400, "推迟必须指定下次提醒时间");
        }

        List<WorkTask> targets = resolveTargets(houseId, request);
        if (targets.isEmpty()) {
            return new BulkActionResult(0, 0, 0, List.of());
        }

        Date occurredAt = request.getOccurredAt() == null ? DateUtil.now() : request.getOccurredAt();
        List<BulkActionResult.Item> items = new ArrayList<>(targets.size());
        int succeeded = 0;

        for (WorkTask task : targets) {
            BulkActionResult.Item item = applyOne(houseId, userId, operatorName, request, action, occurredAt, task);
            if (item.ok()) {
                succeeded++;
            }
            items.add(item);
        }
        return new BulkActionResult(targets.size(), succeeded, targets.size() - succeeded, items);
    }

    /** 单项推进：任何业务异常都收敛成一条失败明细，绝不中断整批。 */
    private BulkActionResult.Item applyOne(
        Long houseId,
        Long userId,
        String operatorName,
        BulkActionRequest request,
        ReproAction action,
        Date occurredAt,
        WorkTask task
    ) {
        TaskType type;
        try {
            type = TaskType.parse(task.getTaskType());
        } catch (BizException e) {
            return failure(task, e.getCode(), e.getMessage());
        }

        if (!TYPE_AGNOSTIC.contains(action) && type.action() != action) {
            return failure(
                task, 400, "待办【" + type.label() + "】不支持操作【" + action.label() + "】"
            );
        }
        if (task.getCycleId() == null) {
            return failure(task, 400, "该待办不属于生产周期，无法执行【" + action.label() + "】");
        }

        ReproCommand command = ReproCommand.builder()
            .houseId(houseId)
            .userId(userId)
            .operatorName(operatorName)
            .cycleId(task.getCycleId())
            .motherRabbitId(task.getRabbitId())
            .batchId(task.getBatchId())
            .action(action)
            .outcome(request.getOutcome())
            .occurredAt(occurredAt)
            .requestId(ReproRequestIds.derive(request.getRequestId(), String.valueOf(task.getId())))
            .remark(request.getRemark())
            .reason(request.getReason())
            .palpationResult(PalpationResult.parse(request.getPalpationResult()))
            .nextRemindAt(request.getNextRemindAt())
            .build();

        try {
            // 走编排层而不是直连状态机：现在批量已拒绝 DELIVERY/WEANING，
            // 但那是一条策略，不该变成正确性的前提。从这里走，
            // 日后若放开某个带副作用的动作，副作用不会被静默跳过。
            // 每项仍是独立短事务（编排层 @Transactional），批量本身不包事务。
            ReproResult result = reproActionService.apply(command, null);
            return new BulkActionResult.Item(
                task.getId(), result.cycleId(), task.getRabbitId(), true, null, null, result.replayed()
            );
        } catch (BizException e) {
            return failure(task, e.getCode(), e.getMessage());
        }
    }

    /**
     * 解析批量目标。
     *
     * <p>两种形式都按 rabbit_id 定序后返回：批量取锁顺序固定是现有的防死锁约定，
     * 两个并发批量若以不同顺序锁同一批母兔就会互等。
     */
    private List<WorkTask> resolveTargets(Long houseId, BulkActionRequest request) {
        boolean hasIds = request.getTaskIds() != null && !request.getTaskIds().isEmpty();
        BulkActionRequest.Filter filter = request.getFilter();
        boolean hasFilter = filter != null
            && (filter.getBatchId() != null || filter.getTaskType() != null || filter.getCageId() != null);

        if (hasIds == hasFilter) {
            throw new BizException(400, "批量目标请二选一：taskIds 或 filter");
        }

        // 上限先于加载检查：loadByIds 是逐个 selectById，客户端丢一万个 id 过来，
        // 若先加载后判上限，就已经白白打了一万次库。
        if (hasIds && request.getTaskIds().size() > MAX_BULK_TARGETS) {
            throw new BizException(400, "单次批量最多 " + MAX_BULK_TARGETS + " 项，请缩小范围后重试");
        }

        List<WorkTask> targets = hasIds
            ? loadByIds(houseId, request.getTaskIds())
            : workTaskMapper.selectPendingByFilter(
                houseId,
                filter.getTaskType() == null || filter.getTaskType().isBlank()
                    ? null
                    : TaskType.parse(filter.getTaskType()).name(),
                filter.getBatchId(),
                filter.getCageId(),
                MAX_BULK_TARGETS + 1
            );

        if (targets.size() > MAX_BULK_TARGETS) {
            throw new BizException(400, "单次批量最多 " + MAX_BULK_TARGETS + " 项，请缩小范围后重试");
        }
        targets.sort(
            Comparator.comparing(WorkTask::getRabbitId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WorkTask::getId)
        );
        return targets;
    }

    /**
     * 按 id 载入并去重。
     *
     * <p>去重是必要的：客户端多选控件重复提交同一 id 时，第二次会命中同一幂等键，
     * 表现为一条「回放成功」的假成功项，让操作者以为处理了两只兔。
     */
    private List<WorkTask> loadByIds(Long houseId, List<Long> taskIds) {
        Map<Long, WorkTask> unique = new LinkedHashMap<>();
        for (Long id : taskIds) {
            if (id == null || unique.containsKey(id)) {
                continue;
            }
            WorkTask task = workTaskMapper.selectById(houseId, id);
            if (task == null) {
                throw new BizException(404, "待办不存在: " + id);
            }
            unique.put(id, task);
        }
        return new ArrayList<>(unique.values());
    }

    private static BulkActionResult.Item failure(WorkTask task, Integer code, String message) {
        return new BulkActionResult.Item(
            task.getId(), task.getCycleId(), task.getRabbitId(), false, code, message, false
        );
    }

    private static Date startOfToday() {
        return Date.from(Instant.now().atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant());
    }
}
