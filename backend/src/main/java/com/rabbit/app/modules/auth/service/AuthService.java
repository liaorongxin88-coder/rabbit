package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.UserProfileResponse;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.house.service.HouseInvitationService;
import com.rabbit.app.security.JwtUtil;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.PermissionScope;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PhoneIdentityService phoneIdentityService;
    private final HouseInvitationService houseInvitationService;

    public AuthService(
            SysUserMapper sysUserMapper,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            PhoneIdentityService phoneIdentityService,
            HouseInvitationService houseInvitationService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.phoneIdentityService = phoneIdentityService;
        this.houseInvitationService = houseInvitationService;
    }

    @Transactional
    public AuthTokenResponse register(String userName, String password) {
        String normalizedUserName = normalizeUserName(userName);
        SysUser exist = sysUserMapper.selectByUserName(normalizedUserName);
        if (exist != null) {
            throw new BizException(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUserName(normalizedUserName);
        u.setStatus("ENABLED");
        u.setPassword(passwordEncoder.encode(password));
        u.setPasswordInitialized(Boolean.TRUE);
        sysUserMapper.insert(u);
        return tokenResponse(u);
    }

    public AuthTokenResponse login(String userName, String password) {
        SysUser u = sysUserMapper.selectByUserName(userName);
        if (u == null || !Boolean.TRUE.equals(u.getPasswordInitialized())
                || !passwordEncoder.matches(password, u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        requireEnabled(u);
        return tokenResponse(u);
    }

    public AuthTokenResponse refreshToken(Long userId) {
        return tokenResponse(requireUser(userId));
    }

    @Transactional
    public AuthTokenResponse loginOrRegisterPhone(String phone) {
        String phoneHash = phoneIdentityService.hash(phone);
        String maskedPhone = phoneIdentityService.mask(phone);
        SysUser user = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
        if (user == null) {
            SysUser created = new SysUser();
            String userName = buildPhoneUserName();
            created.setUserName(userName);
            created.setStatus("ENABLED");
            created.setPasswordInitialized(Boolean.FALSE);
            created.setPhoneCountryCode("+86");
            created.setPhoneHash(phoneHash);
            created.setPhoneMasked(maskedPhone);
            created.setPhoneBoundTime(new Date());
            created.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            try {
                sysUserMapper.insert(created);
                user = created;
            } catch (DuplicateKeyException duplicate) {
                user = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
                if (user == null) {
                    throw duplicate;
                }
            }
        }
        requireEnabled(user);
        houseInvitationService.acceptPending(phoneHash, user.getUserId());
        return tokenResponse(user);
    }

    @Transactional
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
            String userName = buildWechatUserName(t);
            x.setUserName(userName);
            x.setStatus("ENABLED");
            x.setPasswordInitialized(Boolean.FALSE);
            x.setOpenid(t);
            x.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            sysUserMapper.insert(x);
            u = x;
        }
        requireEnabled(u);
        return tokenResponse(u);
    }

    public UserProfileResponse getProfile(Long userId) {
        SysUser user = requireUser(userId);
        UserProfileResponse response = new UserProfileResponse(user);
        response.setPermissions(PermissionCode.all(PermissionScope.BUSINESS));
        return response;
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
        if (Boolean.TRUE.equals(user.getPasswordInitialized())) {
            if (oldPassword == null || oldPassword.isBlank()) {
                throw new BizException(400, "旧密码不能为空");
            }
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new BizException(400, "旧密码不正确");
            }
        }
        int updated = sysUserMapper.updatePasswordAndInitialize(userId, passwordEncoder.encode(newPassword));
        if (updated == 0) {
            throw new BizException(404, "用户不存在");
        }
    }

    private String buildWechatUserName(String openid) {
        String suffix = UUID.nameUUIDFromBytes(openid.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return "wx_" + suffix;
    }

    private String buildPhoneUserName() {
        return "mobile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private AuthTokenResponse tokenResponse(SysUser user) {
        requireEnabled(user);
        AuthTokenResponse response = new AuthTokenResponse(
                jwtUtil.generateToken(user.getUserId()),
                user.getUserId(),
                user.getUserName(),
                user.getPhoneBoundTime() != null,
                user.getPhoneMasked(),
                Boolean.TRUE.equals(user.getPasswordInitialized())
        );
        response.setPermissions(PermissionCode.all(PermissionScope.BUSINESS));
        return response;
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        requireEnabled(user);
        return user;
    }

    private void requireEnabled(SysUser user) {
        if (!"ENABLED".equals(user.getStatus())) {
            throw new BizException(403, "账号已停用");
        }
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
