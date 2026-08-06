package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.modules.house.dto.UserSearchItem;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.merchant.entity.MerchantHousePolicy;
import com.rabbit.app.modules.merchant.entity.MerchantMembership;
import com.rabbit.app.modules.merchant.mapper.MerchantHousePolicyMapper;
import com.rabbit.app.modules.merchant.service.MerchantMembershipService;
import com.rabbit.app.security.permission.HouseRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseMemberService {
    private final HouseUserMapper houseUserMapper;
    private final SysUserMapper sysUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final RequestDedupService requestDedupService;
    private final MerchantMembershipService membershipService;
    private final MerchantHousePolicyMapper policyMapper;

    public HouseMemberService(
            HouseUserMapper houseUserMapper,
            SysUserMapper sysUserMapper,
            RabbitHouseMapper rabbitHouseMapper,
            RequestDedupService requestDedupService,
            MerchantMembershipService membershipService,
            MerchantHousePolicyMapper policyMapper
    ) {
        this.houseUserMapper = houseUserMapper;
        this.sysUserMapper = sysUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.requestDedupService = requestDedupService;
        this.membershipService = membershipService;
        this.policyMapper = policyMapper;
    }

    public List<HouseMemberItem> listMembers(Long houseId) {
        return houseUserMapper.selectMembersByHouse(houseId);
    }

    public List<UserSearchItem> searchCandidates(Long houseId, String keyword, int limit) {
        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(404, "兔舍不存在");
        }
        if (house.getMerchantId() == null) {
            throw new BizException(500, "兔舍未归属商户");
        }
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > 20) {
            limit = 20;
        }
        List<Long> exclude = houseUserMapper.selectMemberUserIds(houseId);
        List<SysUser> users = sysUserMapper.searchByMerchant(house.getMerchantId(), q, exclude, limit);
        List<UserSearchItem> items = new ArrayList<UserSearchItem>();
        for (SysUser user : users) {
            UserSearchItem item = new UserSearchItem();
            item.setUserId(user.getUserId());
            item.setUserName(user.getUserName());
            items.add(item);
        }
        return items;
    }

    @Transactional
    public void addMember(Long houseId, Long operatorUserId, String operator, String userName, String role, String perms, Boolean isAdmin, String requestId) {
        String api = "houseMember.add";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            SysUser user = sysUserMapper.selectByUserName(userName);
            if (user == null) {
                throw new BizException(404, "用户不存在");
            }
            assertSameMerchant(houseId, user);
            if (houseUserMapper.selectByUserAndHouse(user.getUserId(), houseId) != null) {
                throw new BizException(409, "用户已是兔舍成员");
            }
            assertMemberLimit(houseId);
            HouseRole normalizedRole = normalizeRole(role, perms, isAdmin, false);
            HouseUser hu = new HouseUser();
            hu.setHouseId(houseId);
            hu.setUserId(user.getUserId());
            hu.setRole(normalizedRole.code());
            hu.setPerms(normalizedRole.legacyPermission());
            hu.setIsAdmin(normalizedRole.administrator());
            hu.setCreateBy(operator);
            hu.setUpdateBy(operator);
            try {
                houseUserMapper.insert(hu);
            } catch (DuplicateKeyException e) {
                throw new BizException(409, "用户已是兔舍成员");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void updateMember(Long houseId, Long targetUserId, Long operatorUserId, String operator, String role, String perms, Boolean isAdmin, String requestId) {
        String api = "houseMember.update";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            HouseUser current = houseUserMapper.selectByUserAndHouse(targetUserId, houseId);
            if (current == null) {
                throw new BizException(404, "成员不存在");
            }
            assertMemberManagementEnabled(houseId);
            HouseRole currentRole = roleOf(current);
            HouseRole newRole = normalizeRole(role, perms, isAdmin, true);
            if (role == null && perms == null && isAdmin == null) {
                newRole = currentRole;
            }
            if (currentRole == HouseRole.OWNER && newRole != HouseRole.OWNER) {
                throw new BizException(400, "请先将其他成员设为所有者完成转让");
            }
            if (newRole == HouseRole.OWNER) {
                houseUserMapper.demoteOtherOwners(houseId, targetUserId, operator);
                rabbitHouseMapper.updateOwner(houseId, targetUserId, operator);
            }
            int n = houseUserMapper.updateMember(
                    houseId,
                    targetUserId,
                    newRole.code(),
                    newRole.legacyPermission(),
                    newRole.administrator(),
                    operator
            );
            if (n <= 0) {
                throw new BizException(400, "更新失败");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void removeMember(Long houseId, Long targetUserId, Long operatorUserId, String requestId) {
        String api = "houseMember.remove";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            HouseUser current = houseUserMapper.selectByUserAndHouse(targetUserId, houseId);
            if (current == null) {
                throw new BizException(404, "成员不存在");
            }
            assertMemberManagementEnabled(houseId);
            if (roleOf(current) == HouseRole.OWNER) {
                throw new BizException(400, "兔场所有者不能移除，请先转让所有权");
            }
            int n = houseUserMapper.deleteMember(houseId, targetUserId);
            if (n <= 0) {
                throw new BizException(400, "移除失败");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void leaveHouse(Long houseId, Long userId, String requestId) {
        String api = "houseMember.leave";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            HouseUser current = houseUserMapper.selectByUserAndHouse(userId, houseId);
            if (current == null) {
                throw new BizException(404, "您不是该兔舍成员");
            }
            if (roleOf(current) == HouseRole.OWNER) {
                throw new BizException(400, "兔场所有者不能直接退出，请先转让所有权");
            }
            int n = houseUserMapper.deleteMember(houseId, userId);
            if (n <= 0) {
                throw new BizException(400, "退出失败");
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void assertSameMerchant(Long houseId, SysUser user) {
        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(404, "兔舍不存在");
        }
        if (house.getMerchantId() == null) {
            throw new BizException(500, "兔舍未归属商户");
        }
        MerchantMembership membership = membershipService.requireActiveMembership(user.getUserId(), house.getMerchantId());
        if (membership == null) {
            throw new BizException(400, "只能添加同商户下的账号");
        }
    }

    private void assertMemberLimit(Long houseId) {
        MerchantHousePolicy policy = assertMemberManagementEnabled(houseId);
        if (houseUserMapper.countMembers(houseId) >= policy.getMaxMembersPerHouse()) {
            throw new BizException(409, "已达到单兔场成员数量上限");
        }
    }

    private MerchantHousePolicy assertMemberManagementEnabled(Long houseId) {
        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null || house.getMerchantId() == null) {
            throw new BizException(404, "兔场不存在");
        }
        MerchantHousePolicy policy = policyMapper.selectByMerchantId(house.getMerchantId());
        if (policy == null || !Boolean.TRUE.equals(policy.getHouseMemberManagementEnabled())) {
            throw new BizException(403, "商户未开通兔场成员管理权限");
        }
        return policy;
    }

    private HouseRole normalizeRole(String role, String perms, Boolean isAdmin, boolean allowOwner) {
        HouseRole normalized;
        if (role != null && !role.trim().isEmpty()) {
            normalized = HouseRole.parseAssignable(role, allowOwner);
        } else if (Boolean.TRUE.equals(isAdmin)) {
            normalized = allowOwner ? HouseRole.OWNER : HouseRole.MANAGER;
        } else if ("control".equals(perms)) {
            normalized = HouseRole.MANAGER;
        } else if ("view".equals(perms)) {
            normalized = HouseRole.VIEWER;
        } else {
            normalized = HouseRole.STAFF;
        }
        return normalized;
    }

    private HouseRole roleOf(HouseUser member) {
        return HouseRole.fromStored(member.getRole(), member.getPerms(), member.getIsAdmin());
    }
}
