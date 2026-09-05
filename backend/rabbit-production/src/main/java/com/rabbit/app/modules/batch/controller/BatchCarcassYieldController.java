package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldPage;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldRequest;
import com.rabbit.app.modules.batch.dto.BatchCarcassYieldView;
import com.rabbit.app.modules.batch.service.BatchCarcassYieldService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batches/{batchId}/carcass-yields")
public class BatchCarcassYieldController {
    private final HouseService houseService;
    private final BatchCarcassYieldService carcassYieldService;

    public BatchCarcassYieldController(
        HouseService houseService,
        BatchCarcassYieldService carcassYieldService
    ) {
        this.houseService = houseService;
        this.carcassYieldService = carcassYieldService;
    }

    @PostMapping
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<BatchCarcassYieldView> append(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable Long batchId,
        @Valid @RequestBody BatchCarcassYieldRequest request
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(carcassYieldService.append(userId, houseId, batchId, request));
    }

    @GetMapping
    @RequiresPermission(PermissionCode.RABBIT_AUDIT_LIST)
    public ApiResponse<BatchCarcassYieldPage> list(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable Long batchId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(carcassYieldService.list(
            houseId,
            batchId,
            page == null ? 1 : page,
            pageSize == null ? 20 : pageSize
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
