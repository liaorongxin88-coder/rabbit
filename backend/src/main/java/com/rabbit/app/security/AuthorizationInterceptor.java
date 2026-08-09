package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthorizationInterceptor implements HandlerInterceptor {
    private final AccessControlService accessControlService;

    public AuthorizationInterceptor(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HouseContext.clear();
        if (DispatcherType.ASYNC.equals(request.getDispatcherType())
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresPermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresPermission.class);
        }
        if (annotation == null) {
            throw new BizException(500, "接口未配置权限");
        }

        PermissionCode permission = annotation.value();
        switch (permission.scope()) {
            case BUSINESS -> accessControlService.requireBusinessPermission(permission);
            case PLATFORM -> accessControlService.requirePlatformPermission(permission);
            case HOUSE -> accessControlService.requireHousePermission(
                    AuthContext.getUserId(),
                    houseId(request),
                    permission
            );
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        HouseContext.clear();
    }

    private Long houseId(HttpServletRequest request) {
        String value = request.getHeader("X-House-Id");
        if (value == null || value.isBlank()) {
            throw new BizException(400, "缺少X-House-Id");
        }
        return parsePositiveId(value, "X-House-Id不合法");
    }

    private Long parsePositiveId(String value, String message) {
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BizException(400, message);
        }
    }
}
