package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class PlatformAdminGuardInterceptor implements HandlerInterceptor {
    private final PlatformAdminMapper platformAdminMapper;

    public PlatformAdminGuardInterceptor(PlatformAdminMapper platformAdminMapper) {
        this.platformAdminMapper = platformAdminMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (DispatcherType.ASYNC.equals(request.getDispatcherType())) {
            return true;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/admin/")) {
            return true;
        }
        if ("/api/admin/auth/login".equals(uri)) {
            return true;
        }
        Long adminId = PlatformAdminContext.getAdminId();
        if (adminId == null) {
            throw new BizException(401, "后台未登录");
        }
        PlatformAdmin admin = platformAdminMapper.selectById(adminId);
        if (admin == null || admin.getEnabled() == null || !admin.getEnabled()) {
            throw new BizException(401, "后台账号不可用");
        }
        PlatformAdminContext.set(admin.getId(), admin.getRole());
        return true;
    }
}
