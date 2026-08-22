package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BreedingPerformance;
import com.rabbit.app.modules.batch.mapper.BreedingPerformanceMapper;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class BreedingPerformanceController {
    private final HouseService houseService;
    private final BreedingPerformanceMapper breedingPerformanceMapper;

    public BreedingPerformanceController(HouseService houseService, BreedingPerformanceMapper breedingPerformanceMapper) {
        this.houseService = houseService;
        this.breedingPerformanceMapper = breedingPerformanceMapper;
    }

    @GetMapping("/breeding-performance")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<?> list(@RequestHeader("X-House-Id") Long houseId, @RequestParam(value = "rabbitId", required = false) Long rabbitId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        if (rabbitId != null) {
            BreedingPerformance bp = breedingPerformanceMapper.selectByRabbit(houseId, rabbitId);
            return ApiResponse.ok(bp);
        }
        List<BreedingPerformance> all = breedingPerformanceMapper.selectByHouse(houseId);
        return ApiResponse.ok(all);
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
