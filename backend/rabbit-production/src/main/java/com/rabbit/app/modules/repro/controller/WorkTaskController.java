package com.rabbit.app.modules.repro.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.repro.config.ReproFeatureFlags;
import com.rabbit.app.modules.repro.dto.BulkActionRequest;
import com.rabbit.app.modules.repro.dto.BulkActionResult;
import com.rabbit.app.modules.repro.dto.TaskPage;
import com.rabbit.app.modules.repro.service.OperatorNameResolver;
import com.rabbit.app.modules.repro.service.WorkTaskService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.Date;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办中心（设计 §5.4）。
 *
 * <p>一个端点同时服务首页今日待办、NFC 碰笼和兔卡片：三者只是过滤条件不同，
 * 数据来源同一张 work_tasks，因此不会再出现旧实现里首页和兔笼页提醒对不上的情况
 * （recvsrmZKv1cqp）。
 */
@Validated
@RestController
@RequestMapping("/api")
public class WorkTaskController {

    private final HouseService houseService;
    private final WorkTaskService workTaskService;
    private final ReproFeatureFlags featureFlags;
    private final OperatorNameResolver operatorNames;

    public WorkTaskController(
        HouseService houseService,
        WorkTaskService workTaskService,
        ReproFeatureFlags featureFlags,
        OperatorNameResolver operatorNames
    ) {
        this.houseService = houseService;
        this.workTaskService = workTaskService;
        this.featureFlags = featureFlags;
        this.operatorNames = operatorNames;
    }

    /**
     * 待办列表。
     *
     * @param dueBefore    时间戳（毫秒，沿用 ReportController 的日期参数约定）；
     *                     含当日，缺省为今天，即「今日及逾期」
     * @param includeFuture 是否忽略到期日上限并返回全部未来待办；缺省为 false
     * @param type         任务类型过滤（ESTRUS/MATING/...）
     * @param cageId    NFC 碰笼直查该笼待办
     */
    @GetMapping("/tasks")
    @RequiresPermission(PermissionCode.RABBIT_EVENTS_LIST)
    public ApiResponse<TaskPage> listTasks(
        @RequestHeader("X-House-Id") Long houseId,
        @RequestParam(value = "dueBefore", required = false) Long dueBefore,
        @RequestParam(value = "includeFuture", defaultValue = "false") boolean includeFuture,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "batchId", required = false) Long batchId,
        @RequestParam(value = "cageId", required = false) Long cageId,
        @RequestParam(value = "rabbitId", required = false) Long rabbitId,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        featureFlags.assertV2Enabled();
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(workTaskService.pendingDue(
            houseId,
            dueBefore == null ? null : new Date(dueBefore),
            type,
            batchId,
            cageId,
            rabbitId,
            page,
            size,
            includeFuture
        ));
    }

    /**
     * 批量推进待办。
     *
     * <p>部分成功语义：返回体逐项标注成败，HTTP 状态始终是 200。用 200 而不是
     * 207/400 是因为「99 成 1 败」既不是成功也不是失败，客户端要的是明细而不是
     * 一个笼统的状态码。
     */
    @PostMapping("/repro/tasks/bulk-actions")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<BulkActionResult> bulkActions(
        @RequestHeader("X-House-Id") Long houseId,
        @Valid @RequestBody BulkActionRequest request
    ) {
        featureFlags.assertV2Enabled();
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        // 操作者名每请求只解析一次再传入：一次最多 500 只，下沉到逐只解析
        // 就是 500 次重复查询。
        return ApiResponse.ok(
            workTaskService.bulkApply(houseId, userId, operatorNames.resolve(userId), request)
        );
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
