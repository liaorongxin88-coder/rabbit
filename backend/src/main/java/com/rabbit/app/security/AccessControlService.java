package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.mapper.MerchantMapper;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.merchant.entity.MerchantMembership;
import com.rabbit.app.modules.merchant.mapper.MerchantMembershipMapper;
import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.MerchantRole;
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
    private final MerchantMapper merchantMapper;
    private final MerchantMembershipMapper membershipMapper;

    public AccessControlService(
            RabbitHouseMapper rabbitHouseMapper,
            HouseUserMapper houseUserMapper,
            MerchantMapper merchantMapper,
            MerchantMembershipMapper membershipMapper
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.houseUserMapper = houseUserMapper;
        this.merchantMapper = merchantMapper;
        this.membershipMapper = membershipMapper;
    }

    public Long requireBusinessPermission(PermissionCode permission) {
        requireScope(permission, PermissionScope.BUSINESS);
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    public MerchantAccess requireMerchantPermission(Long userId, Long merchantId, PermissionCode permission) {
        requireScope(permission, PermissionScope.MERCHANT);
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        Merchant merchant = requireEnabledMerchant(merchantId);
        MerchantMembership membership = requireEnabledMembership(userId, merchantId);
        MerchantRole role = MerchantRole.fromStored(membership.getRole());
        assertRank(role.rank(), permission);
        return new MerchantAccess(userId, merchant.getId(), role, PermissionCode.granted(PermissionScope.MERCHANT, role));
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
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "X-House-Id不合法");
        }

        HouseContext current = HouseContext.get();
        if (current != null && userId.equals(current.getUserId()) && houseId.equals(current.getHouseId())
                && current.getRoleRank() > 0) {
            HouseRole role = HouseRole.MERCHANT_OWNER.code().equals(current.getRole())
                    ? HouseRole.MERCHANT_OWNER
                    : HouseRole.fromStored(current.getRole(), current.getPerms(), current.isAdmin());
            return new HouseAccess(
                    current.getUserId(),
                    current.getHouseId(),
                    current.getMerchantId(),
                    role,
                    current.getPermissions()
            );
        }

        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null || Boolean.TRUE.equals(house.getIsDeleted())) {
            throw new BizException(410, "兔舍不存在或已删除");
        }

        MerchantRole merchantRole = null;
        if (house.getMerchantId() != null) {
            requireEnabledMerchant(house.getMerchantId());
            MerchantMembership membership = requireEnabledMembership(userId, house.getMerchantId());
            merchantRole = MerchantRole.fromStored(membership.getRole());
        }

        HouseRole role;
        if (merchantRole == MerchantRole.OWNER) {
            role = HouseRole.MERCHANT_OWNER;
        } else {
            HouseUser houseUser = houseUserMapper.selectByUserAndHouse(userId, houseId);
            if (houseUser == null) {
                throw new BizException(403, "无兔舍权限");
            }
            role = HouseRole.fromStored(houseUser.getRole(), houseUser.getPerms(), houseUser.getIsAdmin());
        }

        return new HouseAccess(
                userId,
                houseId,
                house.getMerchantId(),
                role,
                PermissionCode.granted(PermissionScope.HOUSE, role)
        );
    }

    public List<String> platformPermissions(String role) {
        PlatformRole platformRole = PlatformRole.fromStored(role);
        return PermissionCode.granted(PermissionScope.PLATFORM, platformRole);
    }

    public List<String> merchantPermissions(String role) {
        MerchantRole merchantRole = MerchantRole.fromStored(role);
        return PermissionCode.granted(PermissionScope.MERCHANT, merchantRole);
    }

    private void bindHouseContext(HouseAccess access) {
        HouseRole role = access.role();
        HouseContext.set(
                access.userId(),
                access.houseId(),
                access.merchantId(),
                role.legacyPermission(),
                role.code(),
                role.administrator(),
                role.rank(),
                access.permissions()
        );
    }

    private Merchant requireEnabledMerchant(Long merchantId) {
        if (merchantId == null || merchantId <= 0) {
            throw new BizException(400, "merchantId不能为空");
        }
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(404, "商户不存在");
        }
        if (!STATUS_ENABLED.equals(merchant.getStatus())) {
            throw new BizException(403, "商户已停用");
        }
        return merchant;
    }

    private MerchantMembership requireEnabledMembership(Long userId, Long merchantId) {
        MerchantMembership membership = membershipMapper.selectByUserAndMerchant(userId, merchantId);
        if (membership == null || !STATUS_ENABLED.equals(membership.getStatus())) {
            throw new BizException(403, "无商户权限");
        }
        return membership;
    }

    private void assertRank(int actualRank, PermissionCode permission) {
        if (actualRank >= permission.minimumRank()) {
            return;
        }
        if (permission == PermissionCode.MERCHANT_HOUSES_ADD) {
            throw new BizException(403, "当前商户角色不能创建兔场");
        }
        if (permission == PermissionCode.RABBIT_HOUSES_REMOVE) {
            throw new BizException(403, "仅兔场或商户所有者可删除兔场");
        }
        if (permission.scope() == PermissionScope.MERCHANT
                && permission.minimumRank() >= MerchantRole.OWNER.rank()) {
            throw new BizException(403, "仅商户所有者可操作");
        }
        if (permission.scope() == PermissionScope.HOUSE
                && permission.minimumRank() >= HouseRole.OWNER.rank()) {
            throw new BizException(403, "仅管理员可操作");
        }
        throw new BizException(403, "权限不足");
    }

    private void requireScope(PermissionCode permission, PermissionScope expected) {
        if (permission == null || permission.scope() != expected) {
            throw new BizException(500, "权限作用域配置错误");
        }
    }

    public record MerchantAccess(Long userId, Long merchantId, MerchantRole role, List<String> permissions) {
    }

    public record HouseAccess(Long userId, Long houseId, Long merchantId, HouseRole role, List<String> permissions) {
    }
}
