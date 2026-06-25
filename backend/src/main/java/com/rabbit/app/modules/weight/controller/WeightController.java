package com.rabbit.app.modules.weight.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.weight.dto.CreateWeightLogRequest;
import com.rabbit.app.modules.weight.entity.WeightLog;
import com.rabbit.app.modules.weight.service.WeightService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@HousePerm("view")
public class WeightController {
    private final HouseService houseService;
    private final WeightService weightService;

    public WeightController(HouseService houseService, WeightService weightService) {
        this.houseService = houseService;
        this.weightService = weightService;
    }

    @PostMapping("/weight-logs")
    @HousePerm("edit")
    public ApiResponse<WeightLog> create(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateWeightLogRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        WeightLog r = new WeightLog();
        r.setRabbitId(req.getRabbitId());
        r.setWeighTime(req.getWeighTime());
        r.setWeightKg(req.getWeightKg());
        r.setRemark(req.getRemark());
        return ApiResponse.ok(weightService.create(userId, houseId, r, req.getRequestId()));
    }

    @GetMapping("/weight-logs")
    public ApiResponse<List<WeightLog>> list(@RequestHeader("X-House-Id") Long houseId,
                                             @RequestParam("rabbitId") Long rabbitId,
                                             @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(weightService.listByRabbit(houseId, rabbitId, limit == null ? 50 : limit));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
