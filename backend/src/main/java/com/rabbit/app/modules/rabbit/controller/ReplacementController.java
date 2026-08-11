package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.MarkNotifiedRequest;
import com.rabbit.app.modules.rabbit.entity.ReplacementRecord;
import com.rabbit.app.modules.rabbit.service.ReplacementService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
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
@RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
public class ReplacementController {
    private final HouseService houseService;
    private final ReplacementService replacementService;

    public ReplacementController(HouseService houseService, ReplacementService replacementService) {
        this.houseService = houseService;
        this.replacementService = replacementService;
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
        Date fromDt = from == null || from <= 0 ? null : new Date(from);
        Date toDt = to == null || to <= 0 ? null : new Date(to);
        return ApiResponse.ok(replacementService.list(houseId, matureNotified, fromDt, toDt, page, pageSize));
    }

    @PostMapping("/replacement-records/mark-notified")
    @RequiresPermission(PermissionCode.RABBIT_RABBITS_EDIT)
    public ApiResponse<Void> markNotified(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody MarkNotifiedRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        replacementService.markNotified(userId, houseId, req.getRecordIds(), req.getRequestId());
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
