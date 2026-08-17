package com.rabbit.app.modules.repro.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.repro.config.ReproFeatureFlags;
import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.dto.CycleActionRequest;
import com.rabbit.app.modules.repro.dto.CycleView;
import com.rabbit.app.modules.repro.dto.OpenCycleRequest;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.service.OpenCycleCommand;
import com.rabbit.app.modules.repro.service.OperatorNameResolver;
import com.rabbit.app.modules.repro.service.ReproActionService;
import com.rabbit.app.modules.repro.service.ReproCommand;
import com.rabbit.app.modules.repro.service.ReproResult;
import com.rabbit.app.modules.repro.service.ReproStateMachineService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import com.rabbit.app.util.DateUtil;
import jakarta.validation.Valid;
import java.util.Date;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产周期的唯一写入端点（设计 §5.1）。
 *
 * <p>取代 BatchService 的六个专用写方法。所有动作走同一个 {@code actions}
 * 端点：合法性由转换表判定，而不是由「客户端调对了哪个 URL」隐式决定。
 *
 * <p>P2 阶段 Feature Flag 默认关闭，本控制器全部返回 404；旧端点继续服务线上。
 */
@Validated
@RestController
@RequestMapping("/api/repro")
public class ReproCycleController {

    private final HouseService houseService;
    private final ReproStateMachineService stateMachine;
    private final ReproCycleMapper reproCycleMapper;
    private final ReproFeatureFlags featureFlags;
    private final OperatorNameResolver operatorNames;
    private final ReproActionService reproActionService;

    public ReproCycleController(
        HouseService houseService,
        ReproStateMachineService stateMachine,
        ReproCycleMapper reproCycleMapper,
        ReproFeatureFlags featureFlags,
        OperatorNameResolver operatorNames,
        ReproActionService reproActionService
    ) {
        this.reproActionService = reproActionService;
        this.houseService = houseService;
        this.stateMachine = stateMachine;
        this.reproCycleMapper = reproCycleMapper;
        this.featureFlags = featureFlags;
        this.operatorNames = operatorNames;
    }

    /**
     * 开启周期：可从任意阶段入轨。
     *
     * <p>存量母兔录入、开场初始化、后备兔转种共用这一个入口，与 V27 回填脚本
     * 走同一套校验，避免回填数据带上线上不可能出现的状态组合。
     */
    @PostMapping("/cycles")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<ReproResult> openCycle(
        @RequestHeader("X-House-Id") Long houseId,
        @Valid @RequestBody OpenCycleRequest request
    ) {
        featureFlags.assertV2Enabled();
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");

        ReproStage stage = request.getStage() == null || request.getStage().isBlank()
            ? ReproStage.AWAIT_ESTRUS
            : ReproStage.parse(request.getStage());
        Date occurredAt = request.getOccurredAt() == null ? DateUtil.now() : request.getOccurredAt();

        return ApiResponse.ok(stateMachine.openCycleAt(new OpenCycleCommand(
            houseId,
            userId,
            operatorNames.resolve(userId),
            request.getMotherRabbitId(),
            request.getBatchId(),
            stage,
            occurredAt,
            request.getStageEnteredAt(),
            request.getMatingDate(),
            request.getExpectedBirthDate(),
            request.getBirthDate(),
            request.getTotalKits(),
            request.getLiveKits(),
            request.getMaleRabbitId(),
            MatingMethod.parse(request.getMatingMethod()),
            request.getFirstDueAt(),
            request.getRemark(),
            request.getRequestId()
        )));
    }

    /** 单只母兔的一次状态推进。 */
    @PostMapping("/cycles/{cycleId}/actions")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<ReproResult> applyAction(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable Long cycleId,
        @Valid @RequestBody CycleActionRequest request
    ) {
        featureFlags.assertV2Enabled();
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");

        ReproCommand command = ReproCommand.builder()
            .houseId(houseId)
            .userId(userId)
            .operatorName(operatorNames.resolve(userId))
            .cycleId(cycleId)
            .action(ReproAction.parse(request.getAction()))
            .outcome(request.getOutcome())
            .occurredAt(request.getOccurredAt() == null ? DateUtil.now() : request.getOccurredAt())
            .requestId(request.getRequestId())
            .remark(request.getRemark())
            .reason(request.getReason())
            .maleRabbitId(request.getMaleRabbitId())
            .matingMethod(MatingMethod.parse(request.getMatingMethod()))
            .palpationResult(PalpationResult.parse(request.getPalpationResult()))
            .nextRemindAt(request.getNextRemindAt())
            .totalKits(request.getTotalKits())
            .liveKits(request.getLiveKits())
            .keptKits(request.getKeptKits())
            .stillbirthCount(request.getStillbirthCount())
            .weanedCount(request.getWeanedCount())
            .avgWeaningWeight(request.getAvgWeaningWeight())
            .nursingCageId(request.getNursingCageId())
            .attachmentFileIds(request.getAttachmentFileIds())
            .build();

        // 走编排层而不是直调状态机：分笼除了关周期，还要给仔兔占笼、建档，
        // 两者必须同一事务，否则会出现「窝已断奶、仔兔不存在」的悬空态。
        return ApiResponse.ok(reproActionService.apply(
            command,
            new ReproActionService.PlacementInput(
                request.getTargetCageId(), request.getMaleCount(), request.getFemaleCount()
            )
        ));
    }

    @GetMapping("/cycles/{cycleId}")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<CycleView> getCycle(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable Long cycleId
    ) {
        featureFlags.assertV2Enabled();
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");

        ReproCycle cycle = reproCycleMapper.selectById(houseId, cycleId);
        if (cycle == null) {
            throw new BizException(404, "生产周期不存在");
        }
        return ApiResponse.ok(CycleView.of(cycle));
    }

    /**
     * 阶段→可执行动作字典，供客户端决定入口显隐。
     *
     * <p>内容是业务常量，与房舍无关，客户端拉一次缓起来即可；
     * 仍然要求 X-House-Id 只是因为本项目的权限校验本身按房舍进行。
     */
    @GetMapping("/stage-actions")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ApiResponse<java.util.List<com.rabbit.app.modules.repro.dto.StageActionsView>> stageActions(
        @RequestHeader("X-House-Id") Long houseId
    ) {
        featureFlags.assertV2Enabled();
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(com.rabbit.app.modules.repro.dto.StageActionsView.all());
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
