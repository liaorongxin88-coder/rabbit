package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.service.BatchRecordQueryService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.service.RabbitRecordQueryService;
import com.rabbit.app.security.AuthContext;
import java.util.Date;
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
public class RecordQueryController {
    private final HouseService houseService;
    private final BatchRecordQueryService batchRecordQueryService;
    private final RabbitRecordQueryService rabbitRecordQueryService;

    public RecordQueryController(
            HouseService houseService,
            BatchRecordQueryService batchRecordQueryService,
            RabbitRecordQueryService rabbitRecordQueryService
    ) {
        this.houseService = houseService;
        this.batchRecordQueryService = batchRecordQueryService;
        this.rabbitRecordQueryService = rabbitRecordQueryService;
    }

    @GetMapping("/pregnancy-check-records")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<List<PregnancyCheckRecord>> listPregnancyChecks(@RequestHeader("X-House-Id") Long houseId,
                                                                       @RequestParam(value = "batchId", required = false) Long batchId,
                                                                       @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                                       @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(batchRecordQueryService.listPregnancyChecks(houseId, batchId, rabbitId, limit));
    }

    @GetMapping("/parturition-records")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<List<ParturitionRecord>> listParturitions(@RequestHeader("X-House-Id") Long houseId,
                                                                 @RequestParam(value = "batchId", required = false) Long batchId,
                                                                 @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                                 @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(batchRecordQueryService.listParturitions(houseId, batchId, rabbitId, limit));
    }

    @GetMapping("/weaning-records")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<List<WeaningRecord>> listWeanings(@RequestHeader("X-House-Id") Long houseId,
                                                        @RequestParam(value = "batchId", required = false) Long batchId,
                                                        @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                        @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(batchRecordQueryService.listWeanings(houseId, batchId, rabbitId, limit));
    }

    @GetMapping("/departure-records")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<List<RabbitDepartureRecord>> listDepartureRecords(@RequestHeader("X-House-Id") Long houseId,
                                                                        @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                                        @RequestParam(value = "fromTs", required = false) Long fromTs,
                                                                        @RequestParam(value = "toTs", required = false) Long toTs,
                                                                        @RequestParam(value = "page", required = false) Integer page,
                                                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        Date from = fromTs == null || fromTs <= 0 ? null : new Date(fromTs);
        Date to = toTs == null || toTs <= 0 ? null : new Date(toTs);
        return ApiResponse.ok(rabbitRecordQueryService.listDepartures(
                houseId,
                rabbitId,
                from,
                to,
                page,
                pageSize
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
