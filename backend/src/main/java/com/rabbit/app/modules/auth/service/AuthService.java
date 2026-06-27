package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.UserProfileResponse;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.security.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthTokenResponse register(String userName, String password) {
        SysUser exist = sysUserMapper.selectByUserName(userName);
        if (exist != null) {
            throw new BizException(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUserName(userName);
        u.setPassword(passwordEncoder.encode(password));
        sysUserMapper.insert(u);
        String token = jwtUtil.generateToken(u.getUserId());
        return new AuthTokenResponse(token, u.getUserId(), u.getUserName());
    }

    public AuthTokenResponse login(String userName, String password) {
        SysUser u = sysUserMapper.selectByUserName(userName);
        if (u == null || !passwordEncoder.matches(password, u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(u.getUserId());
        return new AuthTokenResponse(token, u.getUserId(), u.getUserName());
    }

    public AuthTokenResponse wechatLogin(String openid) {
        if (openid == null) {
            throw new BizException(400, "openid不能为空");
        }
        String t = openid.trim();
        if (t.isEmpty() || t.length() > 128) {
            throw new BizException(400, "openid不合法");
        }
        SysUser u = sysUserMapper.selectByOpenid(t);
        if (u == null) {
            SysUser legacy = sysUserMapper.selectByUserName("wx_" + t);
            if (legacy != null) {
                sysUserMapper.updateOpenid(legacy.getUserId(), t);
                legacy.setOpenid(t);
                u = legacy;
            }
        }
        if (u == null) {
            SysUser x = new SysUser();
            x.setUserName(buildWechatUserName(t));
            x.setOpenid(t);
            x.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            sysUserMapper.insert(x);
            u = x;
        }
        String token = jwtUtil.generateToken(u.getUserId());
        return new AuthTokenResponse(token, u.getUserId(), u.getUserName());
    }

    public UserProfileResponse getProfile(Long userId) {
        SysUser user = requireUser(userId);
        return new UserProfileResponse(user);
    }

    public UserProfileResponse updateUserName(Long userId, String userName) {
        SysUser user = requireUser(userId);
        String nextName = normalizeUserName(userName);
        SysUser existing = sysUserMapper.selectByUserName(nextName);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new BizException(400, "用户名已存在");
        }
        if (!nextName.equals(user.getUserName())) {
            int updated = sysUserMapper.updateUserName(userId, nextName);
            if (updated == 0) {
                throw new BizException(404, "用户不存在");
            }
        }
        return getProfile(userId);
    }

    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = requireUser(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(400, "旧密码不正确");
        }
        int updated = sysUserMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        if (updated == 0) {
            throw new BizException(404, "用户不存在");
        }
    }

    private String buildWechatUserName(String openid) {
        String suffix = UUID.nameUUIDFromBytes(openid.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return "wx_" + suffix;
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private String normalizeUserName(String userName) {
        String trimmed = userName == null ? "" : userName.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(400, "用户名不能为空");
        }
        if (trimmed.length() > 64) {
            throw new BizException(400, "用户名过长");
        }
        return trimmed;
    }
}
