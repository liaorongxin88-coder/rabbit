package com.rabbit.app.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminLoginResponse;
import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.security.PlatformAdminJwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 后台登录。
 *
 * <p>四种失败原因（查无此人、账号停用、enabled 为 null、密码不对）在实现里挤在同一个
 * 条件表达式里，短路顺序决定了会不会白白多查一次库、会不会泄露账号是否存在。这里把四条
 * 分支拆开各测一次，并且断言**报错文案完全一致** —— 一旦有人好心把「用户不存在」单独
 * 拎出来提示，就等于给了枚举账号的入口。
 */
class PlatformAdminAuthServiceTest {
    private PlatformAdminMapper platformAdminMapper;
    private PasswordEncoder passwordEncoder;
    private PlatformAdminJwtUtil jwtUtil;
    private PlatformAdminAuthService service;

    @BeforeEach
    void setUp() {
        platformAdminMapper = mock(PlatformAdminMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(PlatformAdminJwtUtil.class);
        service = new PlatformAdminAuthService(platformAdminMapper, passwordEncoder, jwtUtil);
    }

    @Test
    void unknownUserIsRejected() {
        when(platformAdminMapper.selectByUserName("ghost")).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.login("ghost", "whatever"));
        assertEquals(401, error.getCode());
        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void disabledAccountIsRejected() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin("hashed", false));

        BizException error = assertThrows(BizException.class, () -> service.login("admin", "right"));
        assertEquals(401, error.getCode());
        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void nullEnabledFlagIsTreatedAsDisabled() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin("hashed", null));

        BizException error = assertThrows(BizException.class, () -> service.login("admin", "right"));
        assertEquals(401, error.getCode());
    }

    @Test
    void wrongPasswordIsRejected() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin("hashed", true));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        BizException error = assertThrows(BizException.class, () -> service.login("admin", "wrong"));
        assertEquals(401, error.getCode());
        assertEquals("用户名或密码错误", error.getMessage());
    }

    /**
     * 账号不存在和密码错误必须给出一模一样的响应，否则调用方可以据此枚举出哪些用户名有效。
     */
    @Test
    void unknownUserAndWrongPasswordAreIndistinguishable() {
        when(platformAdminMapper.selectByUserName("ghost")).thenReturn(null);
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin("hashed", true));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        BizException unknownUser = assertThrows(BizException.class, () -> service.login("ghost", "wrong"));
        BizException wrongPassword = assertThrows(BizException.class, () -> service.login("admin", "wrong"));

        assertEquals(unknownUser.getCode(), wrongPassword.getCode());
        assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
    }

    /**
     * 登录失败不能留下痕迹。刷新最后登录时间等于把「有人试过这个账号」写进库里，
     * 也会让这个字段不再能用来判断真实登录。
     */
    @Test
    void failedLoginDoesNotTouchLastLoginTimeOrMintAToken() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin("hashed", true));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(BizException.class, () -> service.login("admin", "wrong"));

        verify(platformAdminMapper, never()).updateLastLoginTime(anyLong());
        verify(jwtUtil, never()).generateToken(anyLong(), anyString());
    }

    @Test
    void successfulLoginReturnsTokenAndRefreshesLastLoginTime() {
        PlatformAdmin admin = admin("hashed", true);
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin);
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(9L, "SUPER_ADMIN")).thenReturn("signed-token");

        AdminLoginResponse response = service.login("admin", "right");

        assertEquals("signed-token", response.getToken());
        assertEquals(9L, response.getAdminId());
        assertEquals("admin", response.getUserName());
        assertEquals("SUPER_ADMIN", response.getRole());
        verify(platformAdminMapper).updateLastLoginTime(9L);
    }

    @Test
    void successfulLoginCarriesThePlatformPermissionSet() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin("hashed", true));
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(anyLong(), anyString())).thenReturn("signed-token");

        AdminLoginResponse response = service.login("admin", "right");

        assertEquals(
                true,
                response.getPermissions() != null && !response.getPermissions().isEmpty(),
                "SUPER_ADMIN 登录后必须带回平台权限集，前端据此决定菜单可见性"
        );
    }

    /**
     * 库里存了一个不认识的角色时要当场报错，而不是降级成空权限继续放行。
     */
    @Test
    void unknownStoredRoleFailsLoudly() {
        PlatformAdmin admin = admin("hashed", true);
        admin.setRole("NOT_A_REAL_ROLE");
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(admin);
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(anyLong(), anyString())).thenReturn("signed-token");

        BizException error = assertThrows(BizException.class, () -> service.login("admin", "right"));
        assertEquals(403, error.getCode());
    }

    private PlatformAdmin admin(String passwordHash, Boolean enabled) {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(9L);
        admin.setUserName("admin");
        admin.setPassword(passwordHash);
        admin.setRole("SUPER_ADMIN");
        admin.setEnabled(enabled);
        return admin;
    }
}
