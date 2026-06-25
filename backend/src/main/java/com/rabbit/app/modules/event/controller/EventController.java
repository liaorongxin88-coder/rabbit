package com.rabbit.app.modules.event.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.event.dto.AckEventRequest;
import com.rabbit.app.modules.event.service.EventService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
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
public class EventController {
    private final HouseService houseService;
    private final EventService eventService;

    public EventController(HouseService houseService, EventService eventService) {
        this.houseService = houseService;
        this.eventService = eventService;
    }

    @PostMapping("/events/ack")
    @HousePerm("view")
    public ApiResponse<Void> ack(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody AckEventRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        eventService.ack(userId, houseId, req.getCategory(), req.getRecordId(), req.getAction(), req.getSnoozeUntil(), req.getRemark());
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
