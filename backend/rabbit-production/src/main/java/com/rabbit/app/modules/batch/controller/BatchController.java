package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.compat.LegacyEventType;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.dto.TaskView;
import com.rabbit.app.modules.repro.service.WorkTaskService;
import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.dto.CompleteBatchRequest;
import com.rabbit.app.modules.batch.dto.SeparateWeaningRecordRequest;
import com.rabbit.app.modules.batch.dto.WeaningSeparationResult;
import com.rabbit.app.modules.batch.dto.AddBatchMembersRequest;
import com.rabbit.app.modules.batch.dto.CreateBatchRequest;
import com.rabbit.app.modules.batch.dto.RenameBatchRequest;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BreedingCycle;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.service.BatchCodeFallbackResolver;
import com.rabbit.app.modules.batch.service.BatchService;
import com.rabbit.app.modules.batch.service.BatchStatisticsService;
import com.rabbit.app.modules.batch.service.BatchWeaningSeparationService;
import com.rabbit.app.modules.event.dto.EventItem;
import com.rabbit.app.modules.event.service.EventService;
import com.rabbit.app.modules.hardware.service.HardwareLinkService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.sale.dto.SaleRequest;
import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import com.rabbit.app.modules.treatment.service.TreatmentService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@RequiresPermission(PermissionCode.RABBIT_BATCHES_LIST)
public class BatchController {
    private final HouseService houseService;
    private final BatchService batchService;
    private final BatchStatisticsService batchStatisticsService;
    private final BatchCodeFallbackResolver batchCodeFallbackResolver;
    private final BatchWeaningSeparationService batchWeaningSeparationService;
    private final BatchRabbitMapper batchRabbitMapper;
    private final EventService eventService;
    private final TreatmentService treatmentService;
    private final HardwareLinkService hardwareLinkService;
    /** 生产提醒的唯一来源；首页与笼位共用它，不再各读一张镜像表。 */
    private final WorkTaskService workTaskService;

    public BatchController(
        HouseService houseService,
        BatchService batchService,
        BatchStatisticsService batchStatisticsService,
        BatchCodeFallbackResolver batchCodeFallbackResolver,
        BatchWeaningSeparationService batchWeaningSeparationService,
        BatchRabbitMapper batchRabbitMapper,
        EventService eventService,
        TreatmentService treatmentService,
        HardwareLinkService hardwareLinkService,
        WorkTaskService workTaskService
    ) {
        this.workTaskService = workTaskService;
        this.houseService = houseService;
        this.batchService = batchService;
        this.batchStatisticsService = batchStatisticsService;
        this.batchCodeFallbackResolver = batchCodeFallbackResolver;
        this.batchWeaningSeparationService = batchWeaningSeparationService;
        this.batchRabbitMapper = batchRabbitMapper;
        this.eventService = eventService;
        this.treatmentService = treatmentService;
        this.hardwareLinkService = hardwareLinkService;
    }

    @GetMapping("/batches")
    public ApiResponse<List<Batch>> listBatches(@RequestHeader("X-House-Id") Long houseId,
                                                @RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                @RequestParam(value = "q", required = false) String q) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        if (page == null && pageSize == null && (q == null || q.trim().isEmpty())) {
            return ApiResponse.ok(batchService.listBatches(houseId));
        }
        return ApiResponse.ok(batchService.listBatchesPage(houseId, q, page == null ? 1 : page, pageSize == null ? 20 : pageSize));
    }

    @PostMapping("/batches")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_ADD)
    public ApiResponse<Batch> createBatch(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateBatchRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        String houseName = req.getBatchCode() == null || req.getBatchCode().trim().isEmpty()
                ? houseService.getHouse(userId, houseId).getName()
                : null;
        String batchCode = batchCodeFallbackResolver.resolve(req.getBatchCode(), houseName);
        return ApiResponse.ok(batchService.createBatch(userId, houseId, batchCode, req.getFemaleRabbitIds(), req.getRemark(), req.getRequestId()));
    }

    /** 改批次编号。批次建完才发现名字打错时，不必重建批次搬兔只。 */
    @PostMapping("/batches/{batchId}/code")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Batch> renameBatch(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody RenameBatchRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(batchService.renameBatch(
            userId, houseId, batchId, req.getBatchCode(), req.getRequestId()));
    }

    /** 向批次追加兔只标签；同一兔只可以同时属于多个批次。 */
    @PostMapping("/batches/{batchId}/members")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> addBatchMembers(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId,
            @Valid @RequestBody AddBatchMembersRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.addMembers(
            userId, houseId, batchId, req.resolveRabbitIds(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/batches/{batchId}/members/{rabbitId}")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> removeBatchMember(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId,
            @PathVariable("rabbitId") Long rabbitId,
            @RequestParam("requestId") String requestId
    ) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new BizException(400, "requestId不能为空");
        }
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.removeMember(
            userId, houseId, batchId, rabbitId, requestId.trim());
        return ApiResponse.ok(null);
    }

    @GetMapping("/batches/{batchId}")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<Batch> getBatch(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        Batch b = batchService.getBatch(houseId, batchId);
        if (b == null || !houseId.equals(b.getHouseId())) {
            throw new BizException(400, "批次不存在");
        }
        return ApiResponse.ok(b);
    }

    @GetMapping("/batches/{batchId}/statistics")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<BatchStatistics> getBatchStatistics(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(batchStatisticsService.getStatistics(houseId, batchId));
    }

    @GetMapping("/batches/{batchId}/batch-rabbits")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<List<BatchRabbitItem>> listBatchRabbits(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "active", required = false) Boolean active
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        Batch b = batchService.getBatch(houseId, batchId);
        if (b == null || !houseId.equals(b.getHouseId())) {
            throw new BizException(400, "批次不存在");
        }
        return ApiResponse.ok(batchRabbitMapper.selectItemsByBatch(batchId, role, active));
    }

    @GetMapping("/batches/{batchId}/breeding-cycles")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<List<BreedingCycle>> listBreedingCycles(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "motherRabbitId", required = false) Long motherRabbitId,
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(batchService.listBreedingCycles(houseId, batchId, motherRabbitId, activeOnly));
    }

    @GetMapping("/batches/{batchId}/weaning-records")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<List<com.rabbit.app.modules.batch.entity.WeaningRecord>> listPendingWeaningRecords(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable("batchId") Long batchId
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(batchWeaningSeparationService.listPending(houseId, batchId));
    }

    @PostMapping("/batches/{batchId}/weaning-records/{weaningRecordId}/separation")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<WeaningSeparationResult> separateWeaningRecord(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable("batchId") Long batchId,
        @PathVariable("weaningRecordId") Long weaningRecordId,
        @Valid @RequestBody SeparateWeaningRecordRequest request
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(batchWeaningSeparationService.separate(
            userId, houseId, batchId, weaningRecordId, request
        ));
    }

    @PostMapping("/batches/{batchId}/sale")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> sale(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody SaleRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.sale(userId, houseId, batchId, req.getRabbitIds(), req.getSaleDate(), req.getRemark(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/complete")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> complete(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody CompleteBatchRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        boolean force = req.getForce() != null && req.getForce();
        batchService.completeBatch(userId, houseId, batchId, req.getEndDate(), force, req.getRemark(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @GetMapping("/events")
    @RequiresPermission(PermissionCode.RABBIT_EVENTS_LIST)
    public ApiResponse<List<EventItem>> listEvents(
        @RequestHeader("X-House-Id") Long houseId,
        @RequestParam(value = "onlyUnnotified", required = false) Boolean onlyUnnotified,
        @RequestParam(value = "dueBefore", required = false) Long dueBefore
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");

        List<EventItem> items = new ArrayList<EventItem>();
        java.util.Set<Long> suppressedProd = eventService.getSuppressedIds(userId, houseId, "生产");
        java.util.Set<Long> suppressedCycles = eventService.getSuppressedIds(userId, houseId, "生产周期");
        java.util.Set<Long> suppressedRep = eventService.getSuppressedIds(userId, houseId, "后备成熟");
        java.util.Set<Long> suppressedReview = eventService.getSuppressedIds(userId, houseId, "治疗复查");

        // 生产提醒一律来自待办中心（work_tasks）。
        //
        // 旧实现分两路读 breeding_cycles.next_event_* 与 batch_rabbits.next_event_*，
        // 两张表各自维护、各自漂移，首页与笼位因此给出不一致的提醒（飞书 recvsrmZKv1cqp）。
        // 现在首页、笼位 NFC、兔卡、批次详情共用 work_tasks 这一个来源，不可能再分歧。
        // 商品出售与后备成熟同样来自 work_tasks；治疗复查暂时仍走治疗记录。
        for (TaskView task : workTaskService.pendingDue(
                houseId,
                dueBefore == null ? null : new Date(dueBefore),
                null,
                null,
                null,
                null,
                1,
                500
            ).items()) {
            TaskType taskType = TaskType.parse(task.taskType());
            if (taskType == TaskType.SALE_READY) {
                if (!suppressedProd.contains(task.id())) {
                    items.add(new EventItem(
                        task.id(), "生产", "出售", task.dueDate(), task.batchId(), task.rabbitId(),
                        task.overdue() ? "overdue" : null
                    ));
                }
                continue;
            }
            if (taskType == TaskType.REPLACEMENT_MATURE) {
                if (!suppressedRep.contains(task.id())) {
                    items.add(new EventItem(
                        task.id(), "后备成熟", "后备兔转种", task.dueDate(), null, task.rabbitId(),
                        task.overdue() ? "overdue" : null
                    ));
                }
                continue;
            }
            if (taskType.isCommodityDailyCare()) {
                if (!suppressedProd.contains(task.id())) {
                    items.add(new EventItem(
                        task.id(),
                        "生产",
                        task.taskLabel(),
                        task.dueDate(),
                        task.batchId(),
                        task.rabbitId(),
                        task.overdue() ? "overdue" : null,
                        task.remark()
                    ));
                }
                continue;
            }
            if (task.cycleId() == null || suppressedCycles.contains(task.cycleId())) {
                continue;
            }
            items.add(new EventItem(
                task.cycleId(),
                "生产周期",
                // 旧客户端按中文事件名分流，而 TaskType.label() 是新 UI 词汇（待分笼 ≠ 断奶），
                // 所以这里走 compat 映射而不是直接用 label。
                LegacyEventType.of(TaskType.parse(task.taskType())),
                task.dueDate(),
                task.batchId(),
                task.rabbitId(),
                task.overdue() ? "overdue" : null
            ));
        }
        List<TreatmentRecord> dueReview = treatmentService.listDueReviews(houseId);
        for (TreatmentRecord tr : dueReview) {
            if (suppressedReview.contains(tr.getId())) {
                continue;
            }
            items.add(new EventItem(tr.getId(), "治疗复查", "治疗复查", tr.getNextReviewDate(), null, tr.getRabbitId(), "治疗中"));
        }
        attachBatchCodes(houseId, items);
        return ApiResponse.ok(items);
    }

    /**
     * 给提醒补上批次编号。
     *
     * <p>{@code work_tasks.batch_id} 是内部主键，界面上渲染成「批次 #12」；批次列表和批次详情
     * 显示的却是批次编号。两个称呼对不上号，操作者只能猜，常把它读成周期号。这里按兔舍一次性
     * 取批次做映射，让提醒页用和批次页同一个名字称呼同一个批次。
     *
     * <p>取不到编号时留空而不是回落到 id：批次不在本兔舍或已不存在时，显示一个查无此物的号码
     * 比不显示更糟。{@code batchId} 保持原样，生产动作提交仍要用它。
     */
    private void attachBatchCodes(Long houseId, List<EventItem> items) {
        boolean anyBatch = false;
        for (EventItem item : items) {
            if (item.getBatchId() != null && item.getBatchId() > 0) {
                anyBatch = true;
                break;
            }
        }
        if (!anyBatch) {
            return;
        }
        Map<Long, String> codes = new HashMap<Long, String>();
        for (Batch batch : batchService.listBatches(houseId)) {
            codes.put(batch.getId(), batch.getBatchCode());
        }
        for (EventItem item : items) {
            Long batchId = item.getBatchId();
            if (batchId == null || batchId <= 0) {
                continue;
            }
            String code = codes.get(batchId);
            if (code != null && !code.trim().isEmpty()) {
                item.setBatchCode(code);
            }
        }
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
