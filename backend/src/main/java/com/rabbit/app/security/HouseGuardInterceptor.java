package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.mapper.HouseUserMapper;
import com.rabbit.app.mapper.RabbitHouseMapper;
import com.rabbit.app.model.HouseUser;
import com.rabbit.app.model.RabbitHouse;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HouseGuardInterceptor implements HandlerInterceptor {
    private final HouseUserMapper houseUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;

    public HouseGuardInterceptor(HouseUserMapper houseUserMapper, RabbitHouseMapper rabbitHouseMapper) {
        this.houseUserMapper = houseUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return true;
        }
        if (uri.startsWith("/api/auth/")) {
            return true;
        }
        if ("/api/houses".equals(uri)) {
            return true;
        }

        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }

        String houseHeader = request.getHeader("X-House-Id");
        if (houseHeader == null || houseHeader.trim().isEmpty()) {
            throw new BizException(400, "缺少X-House-Id");
        }

        Long houseId;
        try {
            houseId = Long.parseLong(houseHeader.trim());
        } catch (Exception e) {
            throw new BizException(400, "X-House-Id不合法");
        }

        RabbitHouse h = rabbitHouseMapper.selectById(houseId);
        if (h == null || Boolean.TRUE.equals(h.getIsDeleted())) {
            throw new BizException(410, "兔舍不存在或已删除");
        }

        HouseUser hu = houseUserMapper.selectByUserAndHouse(userId, houseId);
        if (hu == null) {
            throw new BizException(403, "无兔舍权限");
        }
        boolean admin = hu.getIsAdmin() != null && hu.getIsAdmin();
        HouseContext.set(userId, houseId, hu.getPerms(), admin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        HouseContext.clear();
    }
}
