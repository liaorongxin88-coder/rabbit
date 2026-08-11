package com.rabbit.app.modules.auth.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.LoginRequest;
import com.rabbit.app.modules.auth.dto.PhoneLoginRequest;
import com.rabbit.app.modules.auth.dto.PhoneOneTapLoginRequest;
import com.rabbit.app.modules.auth.dto.RegisterRequest;
import com.rabbit.app.modules.auth.dto.SendSmsCodeRequest;
import com.rabbit.app.modules.auth.dto.SmsCodeSendResponse;
import com.rabbit.app.modules.auth.dto.UpdatePasswordRequest;
import com.rabbit.app.modules.auth.dto.UpdateUserProfileRequest;
import com.rabbit.app.modules.auth.dto.UserProfileResponse;
import com.rabbit.app.modules.auth.dto.WechatLoginRequest;
import com.rabbit.app.modules.auth.service.AuthService;
import com.rabbit.app.modules.auth.service.PhoneAuthService;
import com.rabbit.app.modules.auth.service.PhoneOneTapLoginService;
import com.rabbit.app.modules.auth.service.SmsVerificationService;
import com.rabbit.app.modules.auth.service.WechatService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final WechatService wechatService;
    private final SmsVerificationService smsVerificationService;
    private final PhoneAuthService phoneAuthService;
    private final PhoneOneTapLoginService phoneOneTapLoginService;

    public AuthController(
            AuthService authService,
            WechatService wechatService,
            SmsVerificationService smsVerificationService,
            PhoneAuthService phoneAuthService,
            PhoneOneTapLoginService phoneOneTapLoginService
    ) {
        this.authService = authService;
        this.wechatService = wechatService;
        this.smsVerificationService = smsVerificationService;
        this.phoneAuthService = phoneAuthService;
        this.phoneOneTapLoginService = phoneOneTapLoginService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req.getUserName(), req.getPassword()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req.getUserName(), req.getPassword()));
    }

    @PostMapping("/sms/code")
    public ApiResponse<SmsCodeSendResponse> sendSmsCode(
            @Valid @RequestBody SendSmsCodeRequest req,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(smsVerificationService.sendCode(req.getPhone(), request.getRemoteAddr()));
    }

    @PostMapping("/sms/login")
    public ApiResponse<AuthTokenResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest req) {
        return ApiResponse.ok(phoneAuthService.loginOrRegister(req.getPhone(), req.getCode()));
    }

    @PostMapping("/phone-one-tap-login")
    public ApiResponse<AuthTokenResponse> phoneOneTapLogin(
            @Valid @RequestBody PhoneOneTapLoginRequest req,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(phoneOneTapLoginService.login(
                req.getProvider(),
                req.getAccessToken(),
                req.getRequestId(),
                request.getRemoteAddr()
        ));
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

    @GetMapping("/me")
    @RequiresPermission(PermissionCode.ACCOUNT_PROFILE_QUERY)
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.ok(authService.getProfile(requireLogin()));
    }

    @PutMapping("/me")
    @RequiresPermission(PermissionCode.ACCOUNT_PROFILE_EDIT)
    public ApiResponse<UserProfileResponse> updateMe(@Valid @RequestBody UpdateUserProfileRequest req) {
        return ApiResponse.ok(authService.updateUserName(requireLogin(), req.getUserName()));
    }

    @PutMapping("/password")
    @RequiresPermission(PermissionCode.ACCOUNT_PASSWORD_EDIT)
    public ApiResponse<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest req) {
        authService.updatePassword(requireLogin(), req.getOldPassword(), req.getNewPassword());
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
