package com.rabbit.app.modules.auth.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.LoginRequest;
import com.rabbit.app.modules.auth.dto.RegisterRequest;
import com.rabbit.app.modules.auth.dto.WechatLoginRequest;
import com.rabbit.app.modules.auth.service.AuthService;
import com.rabbit.app.modules.auth.service.WechatService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final com.rabbit.app.modules.auth.service.WechatService wechatService;

    public AuthController(AuthService authService, com.rabbit.app.modules.auth.service.WechatService wechatService) {
        this.authService = authService;
        this.wechatService = wechatService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req.getUserName(), req.getPassword()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req.getUserName(), req.getPassword()));
    }

    @PostMapping("/wechat-login")
    public ApiResponse<AuthTokenResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest req) {
        String openid = null;
        if (req.getOpenid() != null && !req.getOpenid().trim().isEmpty()) {
            openid = req.getOpenid().trim();
        } else if (req.getCode() != null && !req.getCode().trim().isEmpty()) {
            openid = wechatService.codeToOpenid(req.getCode());
        } else {
            throw new BizException(400, "openid或code至少传一个");
        }
        return ApiResponse.ok(authService.wechatLogin(openid));
    }
}
