package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.admin.dto.AdminLoginRequest;
import com.rabbit.app.modules.admin.dto.AdminLoginResponse;
import com.rabbit.app.modules.admin.service.PlatformAdminAuthService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final PlatformAdminAuthService platformAdminAuthService;

    public AdminAuthController(PlatformAdminAuthService platformAdminAuthService) {
        this.platformAdminAuthService = platformAdminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        return ApiResponse.ok(platformAdminAuthService.login(req.getUserName(), req.getPassword()));
    }
}
