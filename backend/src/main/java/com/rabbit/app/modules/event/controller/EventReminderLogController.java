package com.rabbit.app.modules.event.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.event.entity.EventReminderLog;
import com.rabbit.app.modules.event.service.EventReminderLogQueryService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import java.util.Date;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-reminder-logs")
@RequiresPermission(PermissionCode.RABBIT_AUDIT_LIST)
public class EventReminderLogController {
    private final HouseService houseService;
    private final EventReminderLogQueryService eventReminderLogQueryService;

    public EventReminderLogController(HouseService houseService, EventReminderLogQueryService eventReminderLogQueryService) {
        this.houseService = houseService;
        this.eventReminderLogQueryService = eventReminderLogQueryService;
    }

    @GetMapping("")
    public ApiResponse<List<EventReminderLog>> list(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        Date fromDt = from == null || from <= 0 ? null : new Date(from);
        Date toDt = to == null || to <= 0 ? null : new Date(to);
        return ApiResponse.ok(eventReminderLogQueryService.list(houseId, fromDt, toDt, limit));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
