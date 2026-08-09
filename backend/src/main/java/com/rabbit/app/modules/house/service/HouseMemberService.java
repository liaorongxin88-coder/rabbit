package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.permission.HouseRole;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseMemberService {
    private static final String STATUS_ENABLED = "ENABLED";

    private final HouseUserMapper houseUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final SysUserMapper sysUserMapper;
    private final RequestDedupService requestDedupService;

    public HouseMemberService(
            HouseUserMapper houseUserMapper,
            RabbitHouseMapper rabbitHouseMapper,
            SysUserMapper sysUserMapper,
            RequestDedupService requestDedupService
    ) {
        this.houseUserMapper = houseUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.sysUserMapper = sysUserMapper;
        this.requestDedupService = requestDedupService;
    }

    public List<HouseMemberItem> listMembers(Long houseId) {
        return houseUserMapper.selectMembersByHouse(houseId);
    }

    @Transactional
    public void addMember(
            Long houseId,
            Long operatorUserId,
            String operator,
            String userName,
            String role,
            String perms,
            Boolean isAdmin,
            String requestId
    ) {
        RabbitHouse lockedHouse = lockHouse(houseId);
        String api = "houseMember.add";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requireEnabled(lockedHouse);
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            SysUser user = sysUserMapper.selectByUserName(userName == null ? null : userName.trim());
            if (user == null || !STATUS_ENABLED.equals(user.getStatus())) {
                throw new BizException(404, "用户不存在");
            }
            if (houseUserMapper.selectByUserAndHouse(user.getUserId(), houseId) != null) {
                throw new BizException(409, "用户已是兔场成员");
            }
            HouseRole normalizedRole = normalizeRole(role, perms, isAdmin, false);
            HouseUser member = new HouseUser();
            member.setHouseId(houseId);
            member.setUserId(user.getUserId());
            member.setRole(normalizedRole.code());
            member.setStatus(STATUS_ENABLED);
            member.setPerms(normalizedRole.legacyPermission());
            member.setIsAdmin(normalizedRole.administrator());
            member.setCreateBy(operator);
            member.setUpdateBy(operator);
            try {
                houseUserMapper.insert(member);
            } catch (DuplicateKeyException duplicate) {
                throw new BizException(409, "用户已是兔场成员");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException exception) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void updateMember(
            Long houseId,
            Long targetUserId,
            Long operatorUserId,
            String operator,
            String role,
            String perms,
            Boolean isAdmin,
            String requestId
    ) {
        SysUser lockedTargetUser = sysUserMapper.selectByIdForUpdate(targetUserId);
        RabbitHouse lockedHouse = lockHouse(houseId);
        String api = "houseMember.update";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requireEnabled(lockedHouse);
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            HouseUser current = requireMember(houseId, targetUserId);
            HouseRole currentRole = roleOf(current);
            HouseRole nextRole = role == null && perms == null && isAdmin == null
                    ? currentRole
                    : normalizeRole(role, perms, isAdmin, true);
            if (currentRole != HouseRole.OWNER && nextRole == HouseRole.OWNER
                    && (lockedTargetUser == null || !STATUS_ENABLED.equals(lockedTargetUser.getStatus()))) {
                throw new BizException(409, "停用用户不能设为兔场所有者");
            }
            if (STATUS_ENABLED.equals(current.getStatus())
                    && currentRole == HouseRole.OWNER
                    && nextRole != HouseRole.OWNER) {
                assertAnotherEnabledOwner(houseId);
            }
            int updated = houseUserMapper.updateMember(
                    houseId,
                    targetUserId,
                    nextRole.code(),
                    current.getStatus(),
                    nextRole.legacyPermission(),
                    nextRole.administrator(),
                    operator
            );
            if (updated <= 0) {
                throw new BizException(404, "成员不存在");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException exception) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void removeMember(Long houseId, Long targetUserId, Long operatorUserId, String requestId) {
        sysUserMapper.selectByIdForUpdate(targetUserId);
        RabbitHouse lockedHouse = lockHouse(houseId);
        String api = "houseMember.remove";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requireEnabled(lockedHouse);
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            HouseUser current = requireMember(houseId, targetUserId);
            if (STATUS_ENABLED.equals(current.getStatus()) && roleOf(current) == HouseRole.OWNER) {
                assertAnotherEnabledOwner(houseId);
            }
            if (houseUserMapper.deleteMember(houseId, targetUserId) <= 0) {
                throw new BizException(404, "成员不存在");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException exception) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void leaveHouse(Long houseId, Long userId, String requestId) {
        sysUserMapper.selectByIdForUpdate(userId);
        RabbitHouse lockedHouse = lockHouse(houseId);
        String api = "houseMember.leave";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requireEnabled(lockedHouse);
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            HouseUser current = requireMember(houseId, userId);
            if (STATUS_ENABLED.equals(current.getStatus()) && roleOf(current) == HouseRole.OWNER) {
                assertAnotherEnabledOwner(houseId);
            }
            if (houseUserMapper.deleteMember(houseId, userId) <= 0) {
                throw new BizException(400, "退出失败");
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException exception) {
            requestDedupService.markFailed(houseId, userId, api, requestId, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public String joinByInvitation(Long houseId, Long userId, String role, String operator) {
        return join(houseId, userId, HouseRole.parseAssignable(role, false), operator, true);
    }

    @Transactional
    public String joinByAdmin(Long houseId, Long userId, String role, String operator) {
        return join(houseId, userId, HouseRole.parseAssignable(role, true), operator, false);
    }

    private String join(Long houseId, Long userId, HouseRole requested, String operator, boolean requireEnabledHouse) {
        SysUser user = sysUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!STATUS_ENABLED.equals(user.getStatus())) {
            throw new BizException(409, "停用用户不能加入兔场");
        }
        RabbitHouse lockedHouse = lockHouse(houseId);
        if (requireEnabledHouse) {
            requireEnabled(lockedHouse);
        }
        HouseUser current = houseUserMapper.selectByUserAndHouse(userId, houseId);
        if (current == null) {
            HouseUser member = new HouseUser();
            member.setHouseId(houseId);
            member.setUserId(userId);
            member.setRole(requested.code());
            member.setStatus(STATUS_ENABLED);
            member.setPerms(requested.legacyPermission());
            member.setIsAdmin(requested.administrator());
            member.setCreateBy(operator);
            member.setUpdateBy(operator);
            try {
                houseUserMapper.insert(member);
                return requested.code();
            } catch (DuplicateKeyException duplicate) {
                current = houseUserMapper.selectByUserAndHouse(userId, houseId);
                if (current == null) {
                    throw duplicate;
                }
            }
        }

        HouseRole currentRole = roleOf(current);
        HouseRole effectiveRole = STATUS_ENABLED.equals(current.getStatus())
                && currentRole.rank() >= requested.rank()
                ? currentRole
                : requested;
        if (!STATUS_ENABLED.equals(current.getStatus()) || effectiveRole != currentRole) {
            houseUserMapper.updateMember(
                    houseId,
                    userId,
                    effectiveRole.code(),
                    STATUS_ENABLED,
                    effectiveRole.legacyPermission(),
                    effectiveRole.administrator(),
                    operator
            );
        }
        return effectiveRole.code();
    }

    private HouseUser requireMember(Long houseId, Long userId) {
        HouseUser member = houseUserMapper.selectByUserAndHouse(userId, houseId);
        if (member == null) {
            throw new BizException(404, "成员不存在");
        }
        return member;
    }

    private RabbitHouse lockHouse(Long houseId) {
        RabbitHouse house = rabbitHouseMapper.selectByIdForUpdate(houseId);
        if (house == null || Boolean.TRUE.equals(house.getIsDeleted())) {
            throw new BizException(404, "兔场不存在");
        }
        return house;
    }

    private void requireEnabled(RabbitHouse house) {
        if (!STATUS_ENABLED.equals(house.getStatus())) {
            throw new BizException(403, "兔场已停用");
        }
    }

    private void assertAnotherEnabledOwner(Long houseId) {
        if (houseUserMapper.countEnabledOwners(houseId) <= 1) {
            throw new BizException(409, "兔场至少需要一名启用的所有者");
        }
    }

    private HouseRole normalizeRole(String role, String perms, Boolean isAdmin, boolean allowOwner) {
        if (role != null && !role.trim().isEmpty()) {
            return HouseRole.parseAssignable(role, allowOwner);
        }
        if (Boolean.TRUE.equals(isAdmin)) {
            return allowOwner ? HouseRole.OWNER : HouseRole.MANAGER;
        }
        if ("control".equals(perms)) {
            return HouseRole.MANAGER;
        }
        if ("view".equals(perms)) {
            return HouseRole.VIEWER;
        }
        return HouseRole.STAFF;
    }

    private HouseRole roleOf(HouseUser member) {
        return HouseRole.fromStored(member.getRole(), member.getPerms(), member.getIsAdmin());
    }
}
