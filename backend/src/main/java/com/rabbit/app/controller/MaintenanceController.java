package com.rabbit.app.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.dto.BreedingPerformanceRecalcResult;
import com.rabbit.app.dto.EventReminderScanResult;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import com.rabbit.app.service.BreedingPerformanceRecalcService;
import com.rabbit.app.service.EventReminderScanService;
import com.rabbit.app.service.FeedLogRabbitBackfillService;
import com.rabbit.app.util.DateUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@HousePerm("control")
public class MaintenanceController {
    private final FeedLogRabbitBackfillService backfillService;
    private final EventReminderScanService eventReminderScanService;
    private final BreedingPerformanceRecalcService breedingPerformanceRecalcService;

    public MaintenanceController(FeedLogRabbitBackfillService backfillService,
                                 EventReminderScanService eventReminderScanService,
                                 BreedingPerformanceRecalcService breedingPerformanceRecalcService) {
        this.backfillService = backfillService;
        this.eventReminderScanService = eventReminderScanService;
        this.breedingPerformanceRecalcService = breedingPerformanceRecalcService;
    }

    @PostMapping("/maintenance/feed-log-rabbits/backfill")
    public ApiResponse<Integer> backfillFeedLogRabbits(@RequestHeader("X-House-Id") Long houseId,
                                                       @RequestParam(value = "batchSize", required = false) Integer batchSize) {
        Long userId = requireLogin();
        int n = backfillService.backfillOnce(userId, houseId, batchSize == null ? 200 : batchSize);
        return ApiResponse.ok(n);
    }

    @PostMapping("/maintenance/events/scan")
    public ApiResponse<EventReminderScanResult> scanEvents(@RequestHeader("X-House-Id") Long houseId) {
        requireLogin();
        return ApiResponse.ok(eventReminderScanService.scanHouse(houseId, DateUtil.now()));
    }

    @PostMapping("/maintenance/breeding-performance/recalc")
    public ApiResponse<BreedingPerformanceRecalcResult> recalcBreedingPerformance(@RequestHeader("X-House-Id") Long houseId) {
        requireLogin();
        return ApiResponse.ok(breedingPerformanceRecalcService.recalcHouse(houseId));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
