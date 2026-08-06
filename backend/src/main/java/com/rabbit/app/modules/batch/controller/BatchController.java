package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.AphrodisiacRequest;
import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.batch.dto.CompleteBatchRequest;
import com.rabbit.app.modules.batch.dto.CreateBatchRequest;
import com.rabbit.app.modules.batch.dto.MatingRequest;
import com.rabbit.app.modules.batch.dto.ParturitionRequest;
import com.rabbit.app.modules.batch.dto.PregnancyCheckRequest;
import com.rabbit.app.modules.batch.dto.PrepartumRequest;
import com.rabbit.app.modules.batch.dto.WeaningRequest;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
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

    public BatchController(HouseService houseService, BatchService batchService, BatchRabbitMapper batchRabbitMapper, EventService eventService, TreatmentService treatmentService, HardwareLinkService hardwareLinkService) {
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

    @PostMapping("/batches/{batchId}/mating")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> mating(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody MatingRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.mating(userId, houseId, batchId, req.getFemaleRabbitId(), req.getMaleRabbitId(), req.getMatingDate(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/aphrodisiac/start")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> aphrodisiacStart(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody AphrodisiacRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        if (req.getTriggerHardware() != null && req.getTriggerHardware()) {
            houseService.assertHousePermission(userId, houseId, "control");
            hardwareLinkService.aphrodisiacStart(houseId, batchId, req.getRabbitIds());
        }
        batchService.aphrodisiacStart(userId, houseId, batchId, req.getRabbitIds(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/aphrodisiac/finish")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> aphrodisiacFinish(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody AphrodisiacRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        if (req.getTriggerHardware() != null && req.getTriggerHardware()) {
            houseService.assertHousePermission(userId, houseId, "control");
            hardwareLinkService.aphrodisiacFinish(houseId, batchId, req.getRabbitIds());
        }
        batchService.aphrodisiacFinish(userId, houseId, batchId, req.getRabbitIds(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/pregnancy-check")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> pregnancyCheck(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody PregnancyCheckRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.pregnancyCheck(userId, houseId, batchId, req.getRabbitId(), req.getCheckDate(), req.getResult(), req.getRemark(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/prepartum/finish")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> prepartumFinish(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody PrepartumRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.prepartumFinish(userId, houseId, batchId, req.getRabbitId(), req.getActionDate(), req.getRemark(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/parturition")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> parturition(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody ParturitionRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        boolean failed = req.getFailed() != null && req.getFailed();
        batchService.parturition(userId, houseId, batchId, req.getRabbitId(), req.getBirthDate(), req.getTotalKits(), req.getLiveKits(), failed, req.getRemark(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/batches/{batchId}/weaning")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<Void> weaning(@RequestHeader("X-House-Id") Long houseId, @PathVariable("batchId") Long batchId, @Valid @RequestBody WeaningRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        batchService.weaning(userId, houseId, batchId, req.getRabbitId(), req.getWeaningDate(), req.getWeaningCount(), req.getMaleCount(), req.getFemaleCount(), req.getTargetCageId(), req.getAvgWeight(), req.getRemark(), req.getRequestId());
        return ApiResponse.ok(null);
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
    public ApiResponse<List<EventItem>> listEvents(@RequestHeader("X-House-Id") Long houseId, @RequestParam(value = "onlyUnnotified", required = false) Boolean onlyUnnotified) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");

        List<EventItem> items = new ArrayList<EventItem>();
        java.util.Set<Long> suppressedProd = eventService.getSuppressedIds(userId, houseId, "生产");
        java.util.Set<Long> suppressedRep = eventService.getSuppressedIds(userId, houseId, "后备成熟");
        java.util.Set<Long> suppressedReview = eventService.getSuppressedIds(userId, houseId, "治疗复查");

        boolean x = onlyUnnotified != null && onlyUnnotified;
        List<BatchRabbit> due = batchService.listDueBatchEvents(houseId, x);
        for (BatchRabbit br : due) {
            if (suppressedProd.contains(br.getId())) {
                continue;
            }
            items.add(new EventItem(br.getId(), "生产", br.getNextEventType(), br.getNextEventDate(), br.getBatchId(), br.getRabbitId(), br.getCurrentStatus()));
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
