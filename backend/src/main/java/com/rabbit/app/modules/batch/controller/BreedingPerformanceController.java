package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BreedingPerformance;
import com.rabbit.app.modules.batch.service.BreedingPerformanceQueryService;
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
    private final BreedingPerformanceQueryService breedingPerformanceQueryService;

    public BreedingPerformanceController(HouseService houseService, BreedingPerformanceQueryService breedingPerformanceQueryService) {
        this.houseService = houseService;
        this.breedingPerformanceQueryService = breedingPerformanceQueryService;
    }

    @GetMapping("/breeding-performance")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<?> list(@RequestHeader("X-House-Id") Long houseId, @RequestParam(value = "rabbitId", required = false) Long rabbitId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        if (rabbitId != null) {
            BreedingPerformance bp = breedingPerformanceQueryService.getByRabbit(houseId, rabbitId);
            return ApiResponse.ok(bp);
        }
        List<BreedingPerformance> all = breedingPerformanceQueryService.listByHouse(houseId);
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
