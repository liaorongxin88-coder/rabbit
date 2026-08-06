package com.rabbit.app.modules.merchant.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.mapper.MerchantMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.merchant.dto.MerchantMemberItem;
import com.rabbit.app.modules.merchant.dto.MerchantMembershipView;
import com.rabbit.app.modules.merchant.entity.MerchantMembership;
import com.rabbit.app.modules.merchant.mapper.MerchantMembershipMapper;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.permission.MerchantRole;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantMembershipService {
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private final MerchantMembershipMapper membershipMapper;
    private final MerchantMapper merchantMapper;
    private final SysUserMapper sysUserMapper;
    private final AccessControlService accessControlService;

    public MerchantMembershipService(
            MerchantMembershipMapper membershipMapper,
            MerchantMapper merchantMapper,
            SysUserMapper sysUserMapper,
            AccessControlService accessControlService
    ) {
        this.membershipMapper = membershipMapper;
        this.merchantMapper = merchantMapper;
        this.sysUserMapper = sysUserMapper;
        this.accessControlService = accessControlService;
    }

    public List<MerchantMembershipView> listMyMemberships(Long userId) {
        return membershipMapper.selectByUser(userId).stream().peek(item -> {
            boolean active = STATUS_ENABLED.equals(item.getMembershipStatus())
                    && STATUS_ENABLED.equals(item.getMerchantStatus());
            item.setPermissions(active ? accessControlService.merchantPermissions(item.getRole()) : List.of());
        }).toList();
    }

    public List<MerchantMemberItem> listMembers(Long operatorUserId, Long merchantId) {
        accessControlService.requireMerchantPermission(operatorUserId, merchantId, PermissionCode.MERCHANT_MEMBERS_LIST);
        return membershipMapper.selectMembers(merchantId);
    }

    @Transactional
    public void addMember(Long operatorUserId, Long merchantId, String userName, String role) {
        accessControlService.requireMerchantPermission(operatorUserId, merchantId, PermissionCode.MERCHANT_MEMBERS_ADD);
        SysUser user = sysUserMapper.selectByUserName(userName == null ? null : userName.trim());
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (membershipMapper.selectByUserAndMerchant(user.getUserId(), merchantId) != null) {
            throw new BizException(409, "用户已加入该商户");
        }
        MerchantMembership membership = new MerchantMembership();
        membership.setMerchantId(merchantId);
        membership.setUserId(user.getUserId());
        membership.setRole(normalizeAssignableRole(role));
        membership.setStatus(STATUS_ENABLED);
        membership.setCreateBy(String.valueOf(operatorUserId));
        membership.setUpdateBy(String.valueOf(operatorUserId));
        try {
            membershipMapper.insert(membership);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "用户已加入该商户");
        }
    }

    @Transactional
    public void updateMember(
            Long operatorUserId,
            Long merchantId,
            Long targetUserId,
            String role,
            String status
    ) {
        accessControlService.requireMerchantPermission(operatorUserId, merchantId, PermissionCode.MERCHANT_MEMBERS_EDIT);
        updateMembership(merchantId, targetUserId, role, status, String.valueOf(operatorUserId));
    }

    @Transactional
    public void removeMember(Long operatorUserId, Long merchantId, Long targetUserId) {
        accessControlService.requireMerchantPermission(operatorUserId, merchantId, PermissionCode.MERCHANT_MEMBERS_REMOVE);
        Merchant merchant = requireMerchant(merchantId);
        if (targetUserId != null && targetUserId.equals(merchant.getOwnerUserId())) {
            throw new BizException(400, "商户所有者不能移除，请先转让所有权");
        }
        if (membershipMapper.delete(merchantId, targetUserId) <= 0) {
            throw new BizException(404, "商户成员不存在");
        }
    }

    @Transactional
    public void createInitialOwner(Long merchantId, Long userId, String operator) {
        MerchantMembership membership = new MerchantMembership();
        membership.setMerchantId(merchantId);
        membership.setUserId(userId);
        membership.setRole(MerchantRole.OWNER.code());
        membership.setStatus(STATUS_ENABLED);
        membership.setCreateBy(operator);
        membership.setUpdateBy(operator);
        membershipMapper.insert(membership);
        merchantMapper.updateOwner(merchantId, userId, operator);
    }

    @Transactional
    public void createMember(Long merchantId, Long userId, String role, String operator) {
        MerchantMembership membership = new MerchantMembership();
        membership.setMerchantId(merchantId);
        membership.setUserId(userId);
        membership.setRole(normalizeAssignableRole(role));
        membership.setStatus(STATUS_ENABLED);
        membership.setCreateBy(operator);
        membership.setUpdateBy(operator);
        membershipMapper.insert(membership);
    }

    @Transactional
    public void updateMembership(Long merchantId, Long targetUserId, String role, String status, String operator) {
        MerchantMembership current = membershipMapper.selectByUserAndMerchant(targetUserId, merchantId);
        if (current == null) {
            throw new BizException(404, "商户成员不存在");
        }
        Merchant merchant = requireMerchant(merchantId);
        String nextRole = role == null ? current.getRole() : normalizeRole(role);
        String nextStatus = status == null ? current.getStatus() : normalizeStatus(status);
        boolean currentOwner = targetUserId.equals(merchant.getOwnerUserId());
        if (currentOwner && (!MerchantRole.OWNER.code().equals(nextRole) || !STATUS_ENABLED.equals(nextStatus))) {
            throw new BizException(400, "请先转让商户所有权");
        }
        if (MerchantRole.OWNER.code().equals(nextRole)) {
            nextStatus = STATUS_ENABLED;
            membershipMapper.demoteOtherOwners(merchantId, targetUserId, operator);
            merchantMapper.updateOwner(merchantId, targetUserId, operator);
        }
        if (membershipMapper.updateRoleAndStatus(merchantId, targetUserId, nextRole, nextStatus, operator) <= 0) {
            throw new BizException(404, "商户成员不存在");
        }
    }

    public MerchantMembership requireActiveMembership(Long userId, Long merchantId) {
        MerchantMembership membership = membershipMapper.selectByUserAndMerchant(userId, merchantId);
        if (membership == null || !STATUS_ENABLED.equals(membership.getStatus())) {
            throw new BizException(403, "无商户权限");
        }
        Merchant merchant = requireMerchant(merchantId);
        if (!STATUS_ENABLED.equals(merchant.getStatus())) {
            throw new BizException(403, "商户已停用");
        }
        return membership;
    }

    public void assertOwner(Long userId, Long merchantId) {
        accessControlService.requireMerchantPermission(userId, merchantId, PermissionCode.MERCHANT_MEMBERS_LIST);
    }

    public Long resolveMerchantId(Long userId, Long requestedMerchantId) {
        Long merchantId = requestedMerchantId;
        if (merchantId == null) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BizException(404, "用户不存在");
            }
            merchantId = user.getMerchantId();
        }
        if (merchantId == null) {
            throw new BizException(400, "请选择商户");
        }
        requireActiveMembership(userId, merchantId);
        return merchantId;
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(404, "商户不存在");
        }
        return merchant;
    }

    private String normalizeAssignableRole(String role) {
        String normalized = role == null || role.trim().isEmpty()
                ? MerchantRole.MEMBER.code()
                : normalizeRole(role);
        if (MerchantRole.OWNER.code().equals(normalized)) {
            throw new BizException(400, "请通过所有权转让设置商户所有者");
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        return MerchantRole.parse(role).code();
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (STATUS_ENABLED.equals(normalized) || STATUS_DISABLED.equals(normalized)) {
            return normalized;
        }
        throw new BizException(400, "商户成员状态不合法");
    }
}
