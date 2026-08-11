package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.RabbitEventRequest;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@RequiresPermission(PermissionCode.RABBIT_RABBITS_EDIT)
public class RabbitEventController {
    private final HouseService houseService;
    private final RabbitService rabbitService;

    public RabbitEventController(HouseService houseService, RabbitService rabbitService) {
        this.houseService = houseService;
        this.rabbitService = rabbitService;
    }

    @PostMapping("/rabbits/events")
    public ApiResponse<Void> event(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody RabbitEventRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        boolean force = req.getForceExitBatch() != null && req.getForceExitBatch();
        rabbitService.rabbitEvent(userId, houseId, req.getRabbitId(), req.getEventType(), req.getActionDate(), req.getReason(), req.getRemark(), force, req.getRequestId());
        return ApiResponse.ok(null);
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
