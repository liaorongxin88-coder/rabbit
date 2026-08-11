package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.admin.dto.AdminBusinessUserItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.dto.UpdateResourceStatusRequest;
import com.rabbit.app.modules.admin.service.AdminBusinessUserService;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminBusinessUserController {
    private final AdminBusinessUserService userService;

    public AdminBusinessUserController(AdminBusinessUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequiresPermission(PermissionCode.PLATFORM_USERS_LIST)
    public ApiResponse<PageResult<AdminBusinessUserItem>> list(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ApiResponse.ok(userService.list(keyword, status, pageNum, pageSize));
    }

    @PutMapping("/{userId}/status")
    @RequiresPermission(PermissionCode.PLATFORM_USERS_EDIT)
    public ApiResponse<AdminBusinessUserItem> updateStatus(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody UpdateResourceStatusRequest request
    ) {
        return ApiResponse.ok(userService.updateStatus(userId, request.getStatus()));
    }
}
