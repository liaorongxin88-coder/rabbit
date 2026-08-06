package com.rabbit.app.modules.outbound.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.service.OutboundSubmitCoordinator;
import com.rabbit.app.modules.outbound.service.OutboundTaskService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/outbound")
@RequiresPermission(PermissionCode.RABBIT_OUTBOUND_LIST)
public class OutboundController {
    private final HouseService houseService;
    private final OutboundTaskService taskService;
    private final OutboundSubmitCoordinator submitService;

    public OutboundController(HouseService houseService, OutboundTaskService taskService,
                              OutboundSubmitCoordinator submitService) {
        this.houseService = houseService;
        this.taskService = taskService;
        this.submitService = submitService;
    }

    @PostMapping("/tasks")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_EDIT)
    public ApiResponse<OutboundDtos.TaskView> create(@RequestHeader("X-House-Id") Long houseId,
                                                      @Valid @RequestBody OutboundDtos.CreateTaskRequest request) {
        Long userId = requireEdit(houseId);
        return ApiResponse.ok(taskService.create(userId, houseId, request));
    }

    @GetMapping("/tasks/{taskId}")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_QUERY)
    public ApiResponse<OutboundDtos.TaskView> get(@RequestHeader("X-House-Id") Long houseId,
                                                  @PathVariable String taskId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(taskService.get(userId, houseId, taskId));
    }

    @PostMapping("/tasks/{taskId}/precheck")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_EDIT)
    public ApiResponse<OutboundDtos.TaskView> precheck(@RequestHeader("X-House-Id") Long houseId,
                                                       @PathVariable String taskId) {
        Long userId = requireEdit(houseId);
        return ApiResponse.ok(taskService.precheck(userId, houseId, taskId));
    }

    @PutMapping("/tasks/{taskId}")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_EDIT)
    public ApiResponse<OutboundDtos.TaskView> save(@RequestHeader("X-House-Id") Long houseId,
                                                   @PathVariable String taskId,
                                                   @Valid @RequestBody OutboundDtos.SaveDraftRequest request) {
        Long userId = requireEdit(houseId);
        return ApiResponse.ok(taskService.save(userId, houseId, taskId, request));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_EDIT)
    public ApiResponse<Void> cancel(@RequestHeader("X-House-Id") Long houseId, @PathVariable String taskId) {
        Long userId = requireEdit(houseId);
        taskService.cancel(userId, houseId, taskId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/{taskId}/submit")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_EDIT)
    public ApiResponse<OutboundDtos.SubmitResult> submit(@RequestHeader("X-House-Id") Long houseId,
                                                         @PathVariable String taskId,
                                                         @Valid @RequestBody OutboundDtos.SubmitRequest request) {
        Long userId = requireEdit(houseId);
        return ApiResponse.ok(submitService.submit(userId, houseId, taskId, request));
    }

    @GetMapping("/requests/{requestId}")
    @RequiresPermission(PermissionCode.RABBIT_OUTBOUND_QUERY)
    public ApiResponse<OutboundDtos.SubmitResult> status(@RequestHeader("X-House-Id") Long houseId,
                                                         @PathVariable String requestId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(submitService.status(userId, houseId, requestId));
    }

    private Long requireEdit(Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return userId;
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) throw new BizException(401, "未登录");
        return userId;
    }
}
