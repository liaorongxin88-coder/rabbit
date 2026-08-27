package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchRabbitItem;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.CageTransferRequest;
import com.rabbit.app.modules.rabbit.dto.CageTransferResult;
import com.rabbit.app.modules.rabbit.dto.CreateRabbitRequest;
import com.rabbit.app.modules.rabbit.dto.PromoteReplacementRequest;
import com.rabbit.app.modules.rabbit.dto.RangeRabbitEntryRequest;
import com.rabbit.app.modules.rabbit.dto.RangeRabbitEntryResult;
import com.rabbit.app.modules.rabbit.dto.ReplacementRequest;
import com.rabbit.app.modules.rabbit.dto.ReplacementConversionResponse;
import com.rabbit.app.modules.rabbit.dto.UpdateRabbitRequest;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.service.RangeRabbitEntryService;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@RequiresPermission(PermissionCode.RABBIT_RABBITS_LIST)
public class RabbitController {
    private final HouseService houseService;
    private final RabbitService rabbitService;
    private final RangeRabbitEntryService rangeRabbitEntryService;

    public RabbitController(
        HouseService houseService,
        RabbitService rabbitService,
        RangeRabbitEntryService rangeRabbitEntryService
    ) {
        this.houseService = houseService;
        this.rabbitService = rabbitService;
        this.rangeRabbitEntryService = rangeRabbitEntryService;
    }

    @PostMapping("/rabbits")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_ADD)
    public ApiResponse<Rabbit> createRabbit(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateRabbitRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");

        Rabbit r = new Rabbit();
        r.setCageId(req.getCageId());
        r.setMotherId(req.getMotherId());
        r.setType(req.getType());
        r.setGender(req.getGender());
        r.setBreed(req.getBreed());
        r.setArrivalMethod(req.getArrivalMethod());
        r.setArrivalDate(req.getArrivalDate());
        r.setWeight(req.getWeight());
        r.setGrowthStage(req.getGrowthStage());
        r.setReproductiveStage(req.getReproductiveStage());
        RabbitService.ReproEntry reproEntry = new RabbitService.ReproEntry(
            req.getReproStage(),
            req.getBatchId(),
            req.getStageEnteredAt(),
            req.getMatingDate(),
            req.getBirthDate(),
            req.getLiveKits()
        );
        return ApiResponse.ok(
            rabbitService.createRabbit(userId, houseId, r, reproEntry, req.getRequestId())
        );
    }

    @PostMapping("/rabbits/range-entry")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_ADD)
    public ApiResponse<RangeRabbitEntryResult> createRabbitsInRange(
        @RequestHeader("X-House-Id") Long houseId,
        @Valid @RequestBody RangeRabbitEntryRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(rangeRabbitEntryService.create(userId, houseId, req));
    }

    @GetMapping("/rabbits")
    public ApiResponse<List<Rabbit>> listRabbits(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam(value = "cageId", required = false) Long cageId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        if (page == null && pageSize == null) {
            return ApiResponse.ok(rabbitService.listRabbits(houseId, cageId, type, active));
        }
        return ApiResponse.ok(rabbitService.listRabbitsPage(houseId, cageId, type, active, page == null ? 1 : page, pageSize == null ? 50 : pageSize));
    }

    @GetMapping("/rabbits/{id}")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_QUERY)
    public ApiResponse<Rabbit> getRabbit(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        Rabbit r = rabbitService.getRabbit(houseId, id);
        return ApiResponse.ok(r);
    }

    @GetMapping("/rabbits/{id}/batch-memberships")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_QUERY)
    public ApiResponse<List<BatchRabbitItem>> listBatchMemberships(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("id") Long id,
            @RequestParam(value = "active", defaultValue = "true") Boolean active
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(rabbitService.listBatchMemberships(houseId, id, active));
    }

    @PostMapping("/rabbits/replacement")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_CONTROL)
    public ApiResponse<ReplacementConversionResponse> replacement(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody ReplacementRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        boolean force = req.getForceExitBatch() != null && req.getForceExitBatch();
        return ApiResponse.ok(rabbitService.convertToReplacement(
            userId, houseId, req.getRabbitIds(), force, req.getTargetCageId(), req.getRequestId()
        ));
    }

    @PostMapping("/rabbits/{id}/promote-breeder")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_CONTROL)
    public ApiResponse<Void> promoteReplacement(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("id") Long id,
            @Valid @RequestBody PromoteReplacementRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        rabbitService.promoteReplacement(userId, houseId, id, req.getRequestId());
        return ApiResponse.ok(null);
    }

    /**
     * 换笼位。客户端碰 NFC 拿到目标笼后调这里；结果的 mode 告诉它到底是入笼、合笼还是对调。
     */
    @PostMapping("/rabbits/{id}/cage-transfer")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_EDIT)
    public ApiResponse<CageTransferResult> transferCage(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("id") Long id,
            @Valid @RequestBody CageTransferRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(
                rabbitService.transferCage(userId, houseId, id, req.getTargetCageId(), req.getRequestId())
        );
    }

    @PutMapping("/rabbits/{id}")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_EDIT)
    public ApiResponse<Rabbit> updateRabbit(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id, @Valid @RequestBody UpdateRabbitRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(rabbitService.updateBaseInfo(
                userId,
                houseId,
                id,
                req.getCageId(),
                req.getMotherId(),
                req.getBreed(),
                req.getArrivalMethod(),
                req.getArrivalDate(),
                req.getWeight(),
                req.getGrowthStage(),
                req.getReproductiveStage(),
                req.getRequestId()
        ));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
