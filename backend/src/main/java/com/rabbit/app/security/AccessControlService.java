package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.PermissionScope;
import com.rabbit.app.security.permission.PlatformRole;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {
    private static final String STATUS_ENABLED = "ENABLED";

    private final RabbitHouseMapper rabbitHouseMapper;
    private final HouseUserMapper houseUserMapper;
    private final SysUserMapper sysUserMapper;

    public AccessControlService(
            RabbitHouseMapper rabbitHouseMapper,
            HouseUserMapper houseUserMapper,
            SysUserMapper sysUserMapper
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.houseUserMapper = houseUserMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public Long requireBusinessPermission(PermissionCode permission) {
        requireScope(permission, PermissionScope.BUSINESS);
        return requireEnabledUser(AuthContext.getUserId()).getUserId();
    }

    public HouseAccess requireHousePermission(Long userId, Long houseId, PermissionCode permission) {
        requireScope(permission, PermissionScope.HOUSE);
        HouseAccess access = resolveHouseAccess(userId, houseId);
        assertRank(access.role().rank(), permission);
        bindHouseContext(access);
        return access;
    }

    public HouseAccess requireHouseLevel(Long userId, Long houseId, String legacyPermission) {
        int requiredRank = switch (legacyPermission == null ? "" : legacyPermission.trim().toLowerCase()) {
            case "view" -> HouseRole.VIEWER.rank();
            case "edit" -> HouseRole.STAFF.rank();
            case "control" -> HouseRole.MANAGER.rank();
            default -> throw new BizException(500, "未知兔场权限级别");
        };
        HouseAccess access = resolveHouseAccess(userId, houseId);
        if (access.role().rank() < requiredRank) {
            throw new BizException(403, "权限不足");
        }
        bindHouseContext(access);
        return access;
    }

    public HouseAccess requireHouseOwner(Long userId, Long houseId) {
        return requireHousePermission(userId, houseId, PermissionCode.RABBIT_HOUSE_MEMBERS_LIST);
    }

    public PlatformRole requirePlatformPermission(PermissionCode permission) {
        requireScope(permission, PermissionScope.PLATFORM);
        if (PlatformAdminContext.getAdminId() == null) {
            throw new BizException(401, "后台未登录");
        }
        PlatformRole role = PlatformRole.fromStored(PlatformAdminContext.getRole());
        assertRank(role.rank(), permission);
        return role;
    }

    public HouseAccess resolveHouseAccess(Long userId, Long houseId) {
        requireEnabledUser(userId);
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "X-House-Id不合法");
        }

        HouseContext current = HouseContext.get();
        if (current != null && userId.equals(current.getUserId()) && houseId.equals(current.getHouseId())
                && current.getRoleRank() > 0) {
            HouseRole role = HouseRole.fromStored(current.getRole(), current.getPerms(), current.isAdmin());
            return new HouseAccess(current.getUserId(), current.getHouseId(), role, current.getPermissions());
        }

        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null || Boolean.TRUE.equals(house.getIsDeleted())) {
            throw new BizException(410, "兔场不存在或已删除");
        }
        if (!STATUS_ENABLED.equals(house.getStatus())) {
            throw new BizException(403, "兔场已停用");
        }

        HouseUser houseUser = houseUserMapper.selectByUserAndHouse(userId, houseId);
        if (houseUser == null || !STATUS_ENABLED.equals(houseUser.getStatus())) {
            throw new BizException(403, "无兔场权限");
        }
        HouseRole role = HouseRole.fromStored(houseUser.getRole(), houseUser.getPerms(), houseUser.getIsAdmin());
        return new HouseAccess(
                userId,
                houseId,
                role,
                PermissionCode.granted(PermissionScope.HOUSE, role)
        );
    }

    public List<String> platformPermissions(String role) {
        PlatformRole platformRole = PlatformRole.fromStored(role);
        return PermissionCode.granted(PermissionScope.PLATFORM, platformRole);
    }

    private SysUser requireEnabledUser(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        if (!STATUS_ENABLED.equals(user.getStatus())) {
            throw new BizException(403, "账号已停用");
        }
        return user;
    }

    private void bindHouseContext(HouseAccess access) {
        HouseRole role = access.role();
        HouseContext.set(
                access.userId(),
                access.houseId(),
                role.legacyPermission(),
                role.code(),
                role.administrator(),
                role.rank(),
                access.permissions()
        );
    }

    private void assertRank(int actualRank, PermissionCode permission) {
        if (actualRank >= permission.minimumRank()) {
            return;
        }
        if (permission == PermissionCode.RABBIT_HOUSES_REMOVE) {
            throw new BizException(403, "仅兔场所有者可删除兔场");
        }
        if (permission.scope() == PermissionScope.HOUSE
                && permission.minimumRank() >= HouseRole.OWNER.rank()) {
            throw new BizException(403, "仅兔场所有者可操作");
        }
        throw new BizException(403, "权限不足");
    }

    private void requireScope(PermissionCode permission, PermissionScope expected) {
        if (permission == null || permission.scope() != expected) {
            throw new BizException(500, "权限作用域配置错误");
        }
    }

    public record HouseAccess(Long userId, Long houseId, HouseRole role, List<String> permissions) {
    }
}
