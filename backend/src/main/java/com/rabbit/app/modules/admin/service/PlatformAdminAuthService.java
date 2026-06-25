package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminLoginResponse;
import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.security.PlatformAdminJwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminAuthService {
    private final PlatformAdminMapper platformAdminMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAdminJwtUtil platformAdminJwtUtil;

    public PlatformAdminAuthService(PlatformAdminMapper platformAdminMapper, PasswordEncoder passwordEncoder, PlatformAdminJwtUtil platformAdminJwtUtil) {
        this.platformAdminMapper = platformAdminMapper;
        this.passwordEncoder = passwordEncoder;
        this.platformAdminJwtUtil = platformAdminJwtUtil;
    }

    public AdminLoginResponse login(String userName, String password) {
        PlatformAdmin admin = platformAdminMapper.selectByUserName(userName);
        if (admin == null || admin.getEnabled() == null || !admin.getEnabled() || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        platformAdminMapper.updateLastLoginTime(admin.getId());
        String token = platformAdminJwtUtil.generateToken(admin.getId(), admin.getRole());
        return new AdminLoginResponse(token, admin.getId(), admin.getUserName(), admin.getRole());
    }
}
