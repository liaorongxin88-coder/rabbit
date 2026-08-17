package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.AphrodisiacRequest;
import com.rabbit.app.modules.repro.compat.LegacyEventType;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.repro.dto.TaskView;
import com.rabbit.app.modules.repro.service.WorkTaskService;
import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.batch.dto.BulkMatingRequest;
import com.rabbit.app.modules.batch.dto.BulkMatingResult;
import com.rabbit.app.modules.batch.dto.CompleteBatchRequest;
import com.rabbit.app.modules.batch.dto.CreateBatchRequest;
import com.rabbit.app.modules.batch.dto.MatingRequest;
import com.rabbit.app.modules.batch.dto.ParturitionRequest;
import com.rabbit.app.modules.batch.dto.PregnancyCheckRequest;
import com.rabbit.app.modules.batch.dto.PrepartumRequest;
import com.rabbit.app.modules.batch.dto.WeaningRequest;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.entity.BreedingCycle;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.service.BatchService;
import com.rabbit.app.modules.event.dto.EventItem;
import com.rabbit.app.modules.event.service.EventService;
import com.rabbit.app.modules.hardware.service.HardwareLinkService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.sale.dto.SaleRequest;
import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import com.rabbit.app.modules.treatment.service.TreatmentService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.validation.annotation.Validated;
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
    private final BatchRabbitMapper batchRabbitMapper;
    private final EventService eventService;
    private final TreatmentService treatmentService;
    private final HardwareLinkService hardwareLinkService;
    /** 生产提醒的唯一来源；首页与笼位共用它，不再各读一张镜像表。 */
    private final WorkTaskService workTaskService;

    public BatchController(HouseService houseService, BatchService batchService, BatchRabbitMapper batchRabbitMapper, EventService eventService, TreatmentService treatmentService, HardwareLinkService hardwareLinkService, WorkTaskService workTaskService) {
        this.workTaskService = workTaskService;
        this.houseService = houseService;
        this.batchService = batchService;
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
        return ApiResponse.ok(batchService.createBatch(userId, houseId, req.getBatchCode(), req.getFemaleRabbitIds(), req.getRemark(), req.getRequestId()));
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

    // 旧的繁殖写端点（配种/批量配种/催情开始/催情完成/孕检/备产/接产/分笼）已于
    // doe-breeding-v2 P4 删除，统一走 POST /api/repro/cycles/{cycleId}/actions。
    // 硬件催情不随之消失：它本就有独立的 HardwareController 端点，客户端分别调用。


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
    public ApiResponse<List<EventItem>> listEvents(@RequestHeader("X-House-Id") Long houseId, @RequestParam(value = "onlyUnnotified", required = false) Boolean onlyUnnotified) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");

        List<EventItem> items = new ArrayList<EventItem>();
        java.util.Set<Long> suppressedProd = eventService.getSuppressedIds(userId, houseId, "生产");
        java.util.Set<Long> suppressedCycles = eventService.getSuppressedIds(userId, houseId, "生产周期");
        java.util.Set<Long> suppressedRep = eventService.getSuppressedIds(userId, houseId, "后备成熟");
        java.util.Set<Long> suppressedReview = eventService.getSuppressedIds(userId, houseId, "治疗复查");

        boolean x = onlyUnnotified != null && onlyUnnotified;

        // 生产提醒一律来自待办中心（work_tasks）。
        //
        // 旧实现分两路读 breeding_cycles.next_event_* 与 batch_rabbits.next_event_*，
        // 两张表各自维护、各自漂移，首页与笼位因此给出不一致的提醒（飞书 recvsrmZKv1cqp）。
        // 现在首页、笼位 NFC、兔卡、批次详情共用 work_tasks 这一个来源，不可能再分歧。
        // 后备成熟与治疗复查暂未进入待办中心，仍走各自的数据源。
        for (TaskView task : workTaskService.pendingDue(
                houseId, null, null, null, null, null, 1, 500).items()) {
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
        List<ReplacementRecord> dueRep = batchService.listDueReplacement(houseId, x);
        for (ReplacementRecord rr : dueRep) {
            if (suppressedRep.contains(rr.getId())) {
                continue;
            }
            items.add(new EventItem(rr.getId(), "后备成熟", "后备兔成熟", rr.getExpectedMatureDate(), null, rr.getRabbitId(), null));
        }
        List<TreatmentRecord> dueReview = treatmentService.listDueReviews(houseId);
        for (TreatmentRecord tr : dueReview) {
            if (suppressedReview.contains(tr.getId())) {
                continue;
            }
            items.add(new EventItem(tr.getId(), "治疗复查", "治疗复查", tr.getNextReviewDate(), null, tr.getRabbitId(), "治疗中"));
        }
        return ApiResponse.ok(items);
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
