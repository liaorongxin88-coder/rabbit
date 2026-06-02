package com.rabbit.app.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.mapper.RabbitStatusHistoryMapper;
import com.rabbit.app.model.RabbitStatusHistory;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.service.HouseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class RabbitHistoryController {
    private final HouseService houseService;
    private final RabbitStatusHistoryMapper rabbitStatusHistoryMapper;

    public RabbitHistoryController(HouseService houseService, RabbitStatusHistoryMapper rabbitStatusHistoryMapper) {
        this.houseService = houseService;
        this.rabbitStatusHistoryMapper = rabbitStatusHistoryMapper;
    }

    @GetMapping("/rabbit-status-history")
    public ApiResponse<List<RabbitStatusHistory>> list(@RequestHeader("X-House-Id") Long houseId, @RequestParam("rabbitId") Long rabbitId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(rabbitStatusHistoryMapper.selectByRabbit(houseId, rabbitId));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}

