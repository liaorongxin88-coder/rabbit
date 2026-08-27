package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.appupdate.dto.CreateAppReleaseRequest;
import com.rabbit.app.modules.appupdate.dto.UpdateAppReleaseStatusRequest;
import com.rabbit.app.modules.appupdate.entity.AppRelease;
import com.rabbit.app.modules.appupdate.service.AppUpdateService;
import com.rabbit.app.security.PlatformAdminContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/app-updates")
public class AdminAppUpdateController {
    private final AppUpdateService appUpdateService;

    public AdminAppUpdateController(AppUpdateService appUpdateService) {
        this.appUpdateService = appUpdateService;
    }

    @PostMapping
    @RequiresPermission(PermissionCode.PLATFORM_ACCOUNTS_EDIT)
    public ApiResponse<AppRelease> publish(@Valid @RequestBody CreateAppReleaseRequest request) {
        return ApiResponse.ok(appUpdateService.publish(requireAdminId(), request));
    }

    @PutMapping("/{releaseId}/status")
    @RequiresPermission(PermissionCode.PLATFORM_ACCOUNTS_EDIT)
    public ApiResponse<AppRelease> updateStatus(
            @PathVariable("releaseId") Long releaseId,
            @Valid @RequestBody UpdateAppReleaseStatusRequest request
    ) {
        return ApiResponse.ok(appUpdateService.updateStatus(requireAdminId(), releaseId, request));
    }

    private Long requireAdminId() {
        Long adminId = PlatformAdminContext.getAdminId();
        if (adminId == null) {
            throw new IllegalStateException("平台管理员上下文缺失");
        }
        return adminId;
    }
}
