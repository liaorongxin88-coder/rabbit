package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.UserProfileResponse;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
import com.rabbit.app.modules.auth.support.UserCodes;
import com.rabbit.app.modules.house.service.HouseInvitationService;
import com.rabbit.app.security.JwtUtil;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.PermissionScope;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
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
        u.setUserCode(nextUserCode());
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
        SysUser user = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
        if (user == null) {
            SysUser created = newPhoneUser(phone, phoneHash);
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
        return completePhoneLogin(phoneHash, user);
    }

    @Transactional
    public AuthTokenResponse loginPhone(String phone) {
        String phoneHash = phoneIdentityService.hash(phone);
        SysUser user = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
        if (user == null) {
            throw new BizException(400, "手机号未注册");
        }
        return completePhoneLogin(phoneHash, user);
    }

    @Transactional
    public AuthTokenResponse registerPhone(String phone) {
        String phoneHash = phoneIdentityService.hash(phone);
        if (sysUserMapper.selectByPhoneHashForUpdate(phoneHash) != null) {
            throw new BizException(400, "手机号已注册");
        }
        SysUser user = newPhoneUser(phone, phoneHash);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException duplicate) {
            throw new BizException(400, "手机号已注册");
        }
        return completePhoneLogin(phoneHash, user);
    }

    @Transactional
    public void resetPasswordByPhone(String phone, String newPassword) {
        String phoneHash = phoneIdentityService.hash(phone);
        SysUser user = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
        if (user == null) {
            throw new BizException(400, "手机号未注册");
        }
        requireEnabled(user);
        int updated = sysUserMapper.updatePasswordAndInitialize(
                user.getUserId(),
                passwordEncoder.encode(newPassword)
        );
        if (updated == 0) {
            throw new BizException(404, "用户不存在");
        }
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
            x.setUserCode(nextUserCode());
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

    public PhoneChangeAuthorization authorizePhoneChange(
            Long userId,
            String currentPassword,
            String currentPhone
    ) {
        SysUser user = requireUser(userId);
        if (user.getPhoneBoundTime() == null || user.getPhoneHash() == null) {
            return new PhoneChangeAuthorization(null, null, false);
        }

        String expectedPhoneHash = user.getPhoneHash();
        if (currentPassword != null && !currentPassword.isBlank()) {
            if (!Boolean.TRUE.equals(user.getPasswordInitialized())) {
                throw new BizException(400, "当前账号尚未设置密码，请验证原手机号");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new BizException(400, "当前密码不正确");
            }
            return new PhoneChangeAuthorization(expectedPhoneHash, null, false);
        }

        if (currentPhone == null || currentPhone.isBlank()) {
            throw new BizException(400, "请验证当前密码或原手机号");
        }
        String normalizedCurrentPhone = PhoneNumbers.normalizeMainlandMobile(currentPhone);
        if (!expectedPhoneHash.equals(phoneIdentityService.hash(normalizedCurrentPhone))) {
            throw new BizException(400, "原手机号与当前账号不一致");
        }
        return new PhoneChangeAuthorization(expectedPhoneHash, normalizedCurrentPhone, true);
    }

    public void ensurePhoneAvailable(Long userId, String phone) {
        String phoneHash = phoneIdentityService.hash(phone);
        SysUser existing = sysUserMapper.selectByPhoneHash(phoneHash);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new BizException(409, "该手机号已绑定其他账号");
        }
    }

    @Transactional
    public UserProfileResponse bindPhone(Long userId, String phone, String expectedCurrentPhoneHash) {
        SysUser user = sysUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        requireEnabled(user);
        if (!Objects.equals(user.getPhoneHash(), expectedCurrentPhoneHash)) {
            throw new BizException(409, "手机号绑定状态已变化，请刷新后重试");
        }

        String phoneHash = phoneIdentityService.hash(phone);
        if (phoneHash.equals(user.getPhoneHash())) {
            throw new BizException(400, "新手机号不能与当前手机号相同");
        }
        SysUser existing = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new BizException(409, "该手机号已绑定其他账号");
        }
        try {
            if (sysUserMapper.updatePhone(userId, "+86", phoneHash, phoneIdentityService.mask(phone)) == 0) {
                throw new BizException(404, "用户不存在");
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BizException(409, "该手机号已绑定其他账号");
        }
        houseInvitationService.acceptPending(phoneHash, userId);
        return getProfile(userId);
    }

    public record PhoneChangeAuthorization(
            String expectedPhoneHash,
            String normalizedCurrentPhone,
            boolean currentPhoneCodeRequired
    ) {
    }

    private String buildWechatUserName(String openid) {
        String suffix = UUID.nameUUIDFromBytes(openid.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return "wx_" + suffix;
    }

    /**
     * 取一个还没被占用的账号。唯一键最终兜底，这里先查重是为了让极小概率的
     * 碰撞在这一步就被换掉，而不是变成注册接口上一个莫名其妙的 500。
     */
    private String nextUserCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = UserCodes.random();
            if (sysUserMapper.selectByUserCode(candidate) == null) {
                return candidate;
            }
        }
        throw new BizException(500, "账号生成失败，请重试");
    }

    private String buildPhoneUserName() {
        return "mobile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private SysUser newPhoneUser(String phone, String phoneHash) {
        SysUser created = new SysUser();
        created.setUserName(buildPhoneUserName());
        created.setUserCode(nextUserCode());
        created.setStatus("ENABLED");
        created.setPasswordInitialized(Boolean.FALSE);
        created.setPhoneCountryCode("+86");
        created.setPhoneHash(phoneHash);
        created.setPhoneMasked(phoneIdentityService.mask(phone));
        created.setPhoneBoundTime(new Date());
        created.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return created;
    }

    private AuthTokenResponse completePhoneLogin(String phoneHash, SysUser user) {
        requireEnabled(user);
        houseInvitationService.acceptPending(phoneHash, user.getUserId());
        return tokenResponse(user);
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
