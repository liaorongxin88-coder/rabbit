package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.service.HouseService;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PermissionInterceptor implements HandlerInterceptor {
    private final HouseService houseService;

    public PermissionInterceptor(HouseService houseService) {
        this.houseService = houseService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod hm = (HandlerMethod) handler;
        HousePerm perm = hm.getMethodAnnotation(HousePerm.class);
        if (perm == null) {
            perm = hm.getBeanType().getAnnotation(HousePerm.class);
        }
        if (perm == null) {
            return true;
        }

        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        HouseContext ctx = HouseContext.get();
        if (ctx == null || ctx.getHouseId() == null) {
            throw new BizException(400, "缺少X-House-Id");
        }
        houseService.assertHousePermission(userId, ctx.getHouseId(), perm.value());
        return true;
    }
}
