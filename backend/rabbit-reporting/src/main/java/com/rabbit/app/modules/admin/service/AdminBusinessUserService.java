package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminBusinessUserItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.mapper.AdminBusinessUserMapper;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBusinessUserService {
    private static final Set<String> USER_STATUSES = Set.of("ENABLED", "DISABLED");

    private final AdminBusinessUserMapper userMapper;
    private final SysUserMapper sysUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final AccessControlService accessControlService;

    public AdminBusinessUserService(
            AdminBusinessUserMapper userMapper,
            SysUserMapper sysUserMapper,
            RabbitHouseMapper rabbitHouseMapper,
            AccessControlService accessControlService
    ) {
        this.userMapper = userMapper;
        this.sysUserMapper = sysUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.accessControlService = accessControlService;
    }

    public PageResult<AdminBusinessUserItem> list(
            String keyword,
            String status,
            Integer pageNum,
            Integer pageSize
    ) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_USERS_LIST);
        int page = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        String normalizedStatus = normalizeOptionalStatus(status);
        long total = userMapper.count(normalizedKeyword, normalizedStatus);
        List<AdminBusinessUserItem> items = userMapper.selectPage(
                normalizedKeyword,
                normalizedStatus,
                (page - 1) * size,
                size
        );
        return new PageResult<AdminBusinessUserItem>(items, total, page, size);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminBusinessUserItem updateStatus(Long userId, String status) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_USERS_EDIT);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!USER_STATUSES.contains(normalized)) {
            throw new BizException(400, "用户状态不合法");
        }
        SysUser lockedUser = sysUserMapper.selectByIdForUpdate(userId);
        if (lockedUser == null) {
            throw new BizException(404, "用户不存在");
        }
        if ("DISABLED".equals(normalized)
                && "ENABLED".equals(lockedUser.getStatus())) {
            for (Long houseId : userMapper.selectOwnedHouseIdsForUpdate(userId)) {
                rabbitHouseMapper.selectByIdForUpdate(houseId);
            }
            if (userMapper.countNonDeletedHousesWhereSoleOwner(userId) > 0) {
                throw new BizException(409, "该用户是兔场唯一的有效所有者，请先指定另一名所有者");
            }
        }
        if (sysUserMapper.updateStatus(userId, normalized) <= 0) {
            throw new BizException(404, "用户不存在");
        }
        return requireUser(userId);
    }

    private AdminBusinessUserItem requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(400, "userId不合法");
        }
        AdminBusinessUserItem user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!USER_STATUSES.contains(normalized)) {
            throw new BizException(400, "用户状态不合法");
        }
        return normalized;
    }
}
