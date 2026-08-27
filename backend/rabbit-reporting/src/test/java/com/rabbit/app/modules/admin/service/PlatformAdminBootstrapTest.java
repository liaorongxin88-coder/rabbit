package com.rabbit.app.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 初始管理员的自举。
 *
 * <p>它实现了 {@code ApplicationRunner}，每次启动都会跑一遍，所以幂等是硬要求：
 * 重复建号会让同一个用户名出现多条记录，登录查出来哪条全看数据库顺序。
 *
 * <p>另一条线是「配置不完整时宁可不建」。用空密码建出一个 SUPER_ADMIN，比不建危险得多。
 */
class PlatformAdminBootstrapTest {
    private PlatformAdminMapper platformAdminMapper;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        platformAdminMapper = mock(PlatformAdminMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "encoded:" + call.getArgument(0));
    }

    @Test
    void bootstrapCreatesASuperAdminOnAnEmptyDatabase() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(null);

        bootstrap(true, "admin", "admin123456").ensureBootstrapAdmin();

        PlatformAdmin created = captureInserted();
        assertEquals("admin", created.getUserName());
        assertEquals("SUPER_ADMIN", created.getRole());
        assertEquals(Boolean.TRUE, created.getEnabled());
    }

    /**
     * 密码必须过编码器。明文落库意味着任何一次库泄露都直接等于后台失守。
     */
    @Test
    void theBootstrapPasswordIsHashedNotStoredInClear() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(null);

        bootstrap(true, "admin", "admin123456").ensureBootstrapAdmin();

        PlatformAdmin created = captureInserted();
        assertEquals("encoded:admin123456", created.getPassword());
        assertNotEquals("admin123456", created.getPassword());
        verify(passwordEncoder).encode("admin123456");
    }

    @Test
    void runningTwiceDoesNotCreateASecondAdmin() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(existingAdmin());

        bootstrap(true, "admin", "admin123456").ensureBootstrapAdmin();

        verify(platformAdminMapper, never()).insert(any());
    }

    @Test
    void disabledBootstrapDoesNothing() {
        bootstrap(false, "admin", "admin123456").ensureBootstrapAdmin();

        verify(platformAdminMapper, never()).selectByUserName(anyString());
        verify(platformAdminMapper, never()).insert(any());
    }

    @Test
    void blankPasswordIsRefused() {
        bootstrap(true, "admin", "   ").ensureBootstrapAdmin();

        verify(platformAdminMapper, never()).insert(any());
    }

    @Test
    void blankUserNameIsRefused() {
        bootstrap(true, "  ", "admin123456").ensureBootstrapAdmin();

        verify(platformAdminMapper, never()).insert(any());
    }

    @Test
    void nullCredentialsAreRefused() {
        bootstrap(true, null, null).ensureBootstrapAdmin();

        verify(platformAdminMapper, never()).insert(any());
    }

    /**
     * 用户名两侧空格要在查重和写入时都去掉。只在一边去掉，就会「查不到、于是每次都新建」。
     */
    @Test
    void userNameIsTrimmedForBothTheLookupAndTheInsert() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(null);

        bootstrap(true, "  admin  ", "admin123456").ensureBootstrapAdmin();

        verify(platformAdminMapper).selectByUserName("admin");
        assertEquals("admin", captureInserted().getUserName());
    }

    @Test
    void runDelegatesToTheSameIdempotentPath() {
        when(platformAdminMapper.selectByUserName("admin")).thenReturn(existingAdmin());

        bootstrap(true, "admin", "admin123456").run(null);

        verify(platformAdminMapper).selectByUserName("admin");
        verify(platformAdminMapper, never()).insert(any());
        assertTrue(true);
    }

    private PlatformAdminBootstrap bootstrap(boolean enabled, String userName, String password) {
        return new PlatformAdminBootstrap(platformAdminMapper, passwordEncoder, enabled, userName, password);
    }

    private PlatformAdmin existingAdmin() {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(1L);
        admin.setUserName("admin");
        return admin;
    }

    private PlatformAdmin captureInserted() {
        ArgumentCaptor<PlatformAdmin> captor = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(platformAdminMapper).insert(captor.capture());
        return captor.getValue();
    }
}
