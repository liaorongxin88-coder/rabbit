package com.rabbit.app.modules.operation.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.operation.dto.OperationEventPage;
import com.rabbit.app.modules.operation.service.OperationEventService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import java.util.Date;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用操作事件流。
 *
 * <p>权限用 RABBIT_AUDIT_LIST 而不是 RABBIT_EVENTS_LIST：这是能翻遍整个兔场
 * 所有写操作的审计面，VIEWER 不该有。客户端据此隐藏入口，而不是让人点出 403。
 */
@RestController
@RequestMapping("/api")
public class OperationEventController {

    private final OperationEventService operationEventService;
    private final HouseService houseService;

    public OperationEventController(
        OperationEventService operationEventService,
        HouseService houseService
    ) {
        this.operationEventService = operationEventService;
        this.houseService = houseService;
    }

    @GetMapping("/operation-events")
    @RequiresPermission(PermissionCode.RABBIT_AUDIT_LIST)
    public ApiResponse<OperationEventPage> list(
        @RequestHeader("X-House-Id") Long houseId,
        @RequestParam(value = "targetType", required = false) String targetType,
        @RequestParam(value = "targetId", required = false) Long targetId,
        @RequestParam(value = "operationCode", required = false) String operationCode,
        @RequestParam(value = "cageId", required = false) Long cageId,
        @RequestParam(value = "batchId", required = false) Long batchId,
        @RequestParam(value = "occurredFrom", required = false) Long occurredFrom,
        @RequestParam(value = "occurredTo", required = false) Long occurredTo,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(operationEventService.list(
            houseId,
            targetType,
            targetId,
            operationCode,
            cageId,
            batchId,
            occurredFrom == null ? null : new Date(occurredFrom),
            occurredTo == null ? null : new Date(occurredTo),
            cursor,
            limit
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
