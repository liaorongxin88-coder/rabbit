package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.mapper.MerchantMapper;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HouseGuardInterceptor implements HandlerInterceptor {
    private final HouseUserMapper houseUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final MerchantMapper merchantMapper;

    public HouseGuardInterceptor(HouseUserMapper houseUserMapper, RabbitHouseMapper rabbitHouseMapper, MerchantMapper merchantMapper) {
        this.houseUserMapper = houseUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.merchantMapper = merchantMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (DispatcherType.ASYNC.equals(request.getDispatcherType())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return true;
        }
        if (uri.startsWith("/api/auth/")) {
            return true;
        }
        if (uri.startsWith("/api/admin/")) {
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
        if (h.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(h.getMerchantId());
            if (merchant == null || "DISABLED".equals(merchant.getStatus())) {
                throw new BizException(403, "商户已停用");
            }
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
