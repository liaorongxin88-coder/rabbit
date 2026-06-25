package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.ParturitionRecord;
import com.rabbit.app.modules.batch.entity.PregnancyCheckRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.mapper.ParturitionRecordMapper;
import com.rabbit.app.modules.batch.mapper.PregnancyCheckRecordMapper;
import com.rabbit.app.modules.batch.mapper.WeaningRecordMapper;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.entity.RabbitDepartureRecord;
import com.rabbit.app.modules.rabbit.mapper.RabbitDepartureRecordMapper;
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
    private final PregnancyCheckRecordMapper pregnancyCheckRecordMapper;
    private final ParturitionRecordMapper parturitionRecordMapper;
    private final WeaningRecordMapper weaningRecordMapper;
    private final RabbitDepartureRecordMapper rabbitDepartureRecordMapper;

    public RecordQueryController(HouseService houseService, PregnancyCheckRecordMapper pregnancyCheckRecordMapper, ParturitionRecordMapper parturitionRecordMapper, WeaningRecordMapper weaningRecordMapper, RabbitDepartureRecordMapper rabbitDepartureRecordMapper) {
        this.houseService = houseService;
        this.pregnancyCheckRecordMapper = pregnancyCheckRecordMapper;
        this.parturitionRecordMapper = parturitionRecordMapper;
        this.weaningRecordMapper = weaningRecordMapper;
        this.rabbitDepartureRecordMapper = rabbitDepartureRecordMapper;
    }

    @GetMapping("/pregnancy-check-records")
    public ApiResponse<List<PregnancyCheckRecord>> listPregnancyChecks(@RequestHeader("X-House-Id") Long houseId,
                                                                       @RequestParam(value = "batchId", required = false) Long batchId,
                                                                       @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                                       @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        int lim = clampLimit(limit);
        if (batchId != null && batchId > 0) {
            return ApiResponse.ok(pregnancyCheckRecordMapper.selectByBatch(houseId, batchId, lim));
        }
        if (rabbitId != null && rabbitId > 0) {
            return ApiResponse.ok(pregnancyCheckRecordMapper.selectByRabbit(houseId, rabbitId, lim));
        }
        throw new BizException(400, "batchId或rabbitId至少提供一个");
    }

    @GetMapping("/parturition-records")
    public ApiResponse<List<ParturitionRecord>> listParturitions(@RequestHeader("X-House-Id") Long houseId,
                                                                 @RequestParam(value = "batchId", required = false) Long batchId,
                                                                 @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                                 @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        int lim = clampLimit(limit);
        if (batchId != null && batchId > 0) {
            return ApiResponse.ok(parturitionRecordMapper.selectByBatch(houseId, batchId, lim));
        }
        if (rabbitId != null && rabbitId > 0) {
            return ApiResponse.ok(parturitionRecordMapper.selectByRabbit(houseId, rabbitId, lim));
        }
        throw new BizException(400, "batchId或rabbitId至少提供一个");
    }

    @GetMapping("/weaning-records")
    public ApiResponse<List<WeaningRecord>> listWeanings(@RequestHeader("X-House-Id") Long houseId,
                                                        @RequestParam(value = "batchId", required = false) Long batchId,
                                                        @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                        @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        int lim = clampLimit(limit);
        if (batchId != null && batchId > 0) {
            return ApiResponse.ok(weaningRecordMapper.selectByBatch(houseId, batchId, lim));
        }
        if (rabbitId != null && rabbitId > 0) {
            return ApiResponse.ok(weaningRecordMapper.selectByRabbit(houseId, rabbitId, lim));
        }
        throw new BizException(400, "batchId或rabbitId至少提供一个");
    }

    @GetMapping("/departure-records")
    public ApiResponse<List<RabbitDepartureRecord>> listDepartureRecords(@RequestHeader("X-House-Id") Long houseId,
                                                                        @RequestParam(value = "rabbitId", required = false) Long rabbitId,
                                                                        @RequestParam(value = "fromTs", required = false) Long fromTs,
                                                                        @RequestParam(value = "toTs", required = false) Long toTs,
                                                                        @RequestParam(value = "page", required = false) Integer page,
                                                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        int p = page == null || page <= 0 ? 1 : page;
        int ps = pageSize == null || pageSize <= 0 ? 50 : pageSize;
        if (ps > 200) {
            ps = 200;
        }
        int offset = (p - 1) * ps;
        Date from = fromTs == null || fromTs <= 0 ? null : new Date(fromTs);
        Date to = toTs == null || toTs <= 0 ? null : new Date(toTs);
        return ApiResponse.ok(rabbitDepartureRecordMapper.selectPageByHouse(houseId, rabbitId, from, to, offset, ps));
    }

    private int clampLimit(Integer limit) {
        int lim = limit == null || limit <= 0 ? 50 : limit;
        if (lim > 200) {
            lim = 200;
        }
        return lim;
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}

