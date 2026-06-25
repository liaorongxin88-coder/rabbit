package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.MarkNotifiedRequest;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.rabbit.mapper.ReplacementRecordMapper;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import com.rabbit.app.util.DateUtil;
import jakarta.validation.Valid;
import java.util.Date;
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
public class ReplacementController {
    private final HouseService houseService;
    private final ReplacementRecordMapper replacementRecordMapper;
    private final RequestDedupService requestDedupService;

    public ReplacementController(HouseService houseService, ReplacementRecordMapper replacementRecordMapper, RequestDedupService requestDedupService) {
        this.houseService = houseService;
        this.replacementRecordMapper = replacementRecordMapper;
        this.requestDedupService = requestDedupService;
    }

    @GetMapping("/replacement-records")
    public ApiResponse<List<ReplacementRecord>> list(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam(value = "matureNotified", required = false) Boolean matureNotified,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        int p = page == null || page <= 0 ? 1 : page;
        int ps = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        int offset = (p - 1) * ps;
        Date fromDt = from == null || from <= 0 ? null : new Date(from);
        Date toDt = to == null || to <= 0 ? null : new Date(to);
        return ApiResponse.ok(replacementRecordMapper.selectByHouse(houseId, matureNotified, fromDt, toDt, offset, ps));
    }

    @PostMapping("/replacement-records/mark-notified")
    @HousePerm("edit")
    public ApiResponse<Void> markNotified(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody MarkNotifiedRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        String api = "replacement.markNotified";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(houseId, userId, api, req.getRequestId());
        try {
            for (Long id : req.getRecordIds()) {
                replacementRecordMapper.markNotified(houseId, id, DateUtil.now(), String.valueOf(userId));
            }
            requestDedupService.markDone(houseId, userId, api, req.getRequestId());
            return ApiResponse.ok(null);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, req.getRequestId(), e.getMessage());
            throw e;
        }
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
