package com.rabbit.app.modules.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.security.PlatformAdminContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 后台接口的准入闸门。
 *
 * <p>这里守的是「谁能进 /api/admin/」，判错一边就是越权，判错另一边就是把管理员挡在门外，
 * 所以每条放行分支和每条拒绝分支都单独立一个用例，而不是只测 happy path。
 *
 * <p>注意 {@link PlatformAdminContext} 是 ThreadLocal，用例之间必须清干净，否则前一个用例
 * 残留的身份会让后一个用例「凭空登录成功」，而且失败现象取决于执行顺序，极难归因。
 */
class PlatformAdminGuardInterceptorTest {
    private PlatformAdminMapper platformAdminMapper;
    private PlatformAdminGuardInterceptor interceptor;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        platformAdminMapper = mock(PlatformAdminMapper.class);
        interceptor = new PlatformAdminGuardInterceptor(platformAdminMapper);
        response = mock(HttpServletResponse.class);
        PlatformAdminContext.clear();
    }

    @AfterEach
    void tearDown() {
        PlatformAdminContext.clear();
    }

    @Test
    void asyncDispatchSkipsTheGuardSoItIsNotEnforcedTwicePerRequest() {
        MockHttpServletRequest request = adminRequest("GET", "/api/admin/farms");
        request.setDispatcherType(DispatcherType.ASYNC);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(platformAdminMapper, never()).selectById(anyLong());
    }

    @Test
    void corsPreflightPassesWithoutCredentials() {
        MockHttpServletRequest request = adminRequest("OPTIONS", "/api/admin/farms");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(platformAdminMapper, never()).selectById(anyLong());
    }

    @Test
    void nonAdminPathsAreNotThisGuardsBusiness() {
        MockHttpServletRequest request = adminRequest("GET", "/api/houses");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(platformAdminMapper, never()).selectById(anyLong());
    }

    @Test
    void loginEndpointStaysOpenOtherwiseNobodyCouldEverLogIn() {
        MockHttpServletRequest request = adminRequest("POST", "/api/admin/auth/login");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(platformAdminMapper, never()).selectById(anyLong());
    }

    /**
     * 前缀匹配不能退化成「包含」：`/api/admin/auth/login/extra` 不是登录接口，必须继续鉴权。
     */
    @Test
    void pathsBelowTheLoginEndpointAreStillGuarded() {
        MockHttpServletRequest request = adminRequest("POST", "/api/admin/auth/login/extra");

        BizException error = assertThrows(
                BizException.class,
                () -> interceptor.preHandle(request, response, new Object())
        );
        assertEquals(401, error.getCode());
    }

    @Test
    void missingAdminIdIsRejectedAsNotLoggedIn() {
        MockHttpServletRequest request = adminRequest("GET", "/api/admin/farms");

        BizException error = assertThrows(
                BizException.class,
                () -> interceptor.preHandle(request, response, new Object())
        );
        assertEquals(401, error.getCode());
        assertEquals("后台未登录", error.getMessage());
    }

    @Test
    void tokenForADeletedAdminIsRejected() {
        PlatformAdminContext.set(7L, "ADMIN");
        when(platformAdminMapper.selectById(7L)).thenReturn(null);

        BizException error = assertThrows(
                BizException.class,
                () -> interceptor.preHandle(adminRequest("GET", "/api/admin/farms"), response, new Object())
        );
        assertEquals(401, error.getCode());
        assertEquals("后台账号不可用", error.getMessage());
    }

    @Test
    void disabledAdminIsRejectedEvenWithAValidToken() {
        PlatformAdminContext.set(7L, "ADMIN");
        when(platformAdminMapper.selectById(7L)).thenReturn(admin(7L, "SUPER_ADMIN", false));

        BizException error = assertThrows(
                BizException.class,
                () -> interceptor.preHandle(adminRequest("GET", "/api/admin/farms"), response, new Object())
        );
        assertEquals(401, error.getCode());
        assertEquals("后台账号不可用", error.getMessage());
    }

    /**
     * `enabled` 为 null 时按停用处理。历史行数据可能没有这个字段，默认放行等于给了一批
     * 来路不明的账号后台权限。
     */
    @Test
    void nullEnabledFlagIsTreatedAsDisabled() {
        PlatformAdminContext.set(7L, "ADMIN");
        when(platformAdminMapper.selectById(7L)).thenReturn(admin(7L, "SUPER_ADMIN", null));

        BizException error = assertThrows(
                BizException.class,
                () -> interceptor.preHandle(adminRequest("GET", "/api/admin/farms"), response, new Object())
        );
        assertEquals(401, error.getCode());
        assertEquals("后台账号不可用", error.getMessage());
    }

    /**
     * 放行时角色要以库里的为准重写一遍上下文。token 里的角色可能是降权前签发的，
     * 直接沿用等于让改过权限的账号继续用旧角色。
     */
    @Test
    void enabledAdminPassesAndTheRoleIsRefreshedFromTheDatabase() {
        PlatformAdminContext.set(7L, "STALE_ROLE_FROM_TOKEN");
        when(platformAdminMapper.selectById(7L)).thenReturn(admin(7L, "SUPER_ADMIN", true));

        assertTrue(interceptor.preHandle(adminRequest("GET", "/api/admin/farms"), response, new Object()));
        assertEquals(7L, PlatformAdminContext.getAdminId());
        assertEquals("SUPER_ADMIN", PlatformAdminContext.getRole());
    }

    @Test
    void requestWithoutAUriIsLeftAlone() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", null);
        request.setRequestURI(null);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNull(PlatformAdminContext.getAdminId());
        verify(platformAdminMapper, never()).selectById(anyLong());
    }

    private MockHttpServletRequest adminRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private PlatformAdmin admin(Long id, String role, Boolean enabled) {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(id);
        admin.setRole(role);
        admin.setEnabled(enabled);
        return admin;
    }
}
