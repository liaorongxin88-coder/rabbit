package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.service.PhoneIdentityService;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
import com.rabbit.app.modules.auth.support.UserCodes;
import com.rabbit.app.modules.house.dto.HouseInvitationResponse;
import com.rabbit.app.modules.house.entity.HouseInvitation;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseInvitationMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.PermissionCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseInvitationService {
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String CHANNEL_PHONE = "PHONE";
    private static final String CHANNEL_USER_CODE = "USER_CODE";

    private final HouseInvitationMapper invitationMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final PhoneIdentityService phoneIdentityService;
    private final HouseMemberService houseMemberService;
    private final AccessControlService accessControlService;
    private final SysUserMapper sysUserMapper;

    public HouseInvitationService(
            HouseInvitationMapper invitationMapper,
            RabbitHouseMapper rabbitHouseMapper,
            PhoneIdentityService phoneIdentityService,
            HouseMemberService houseMemberService,
            AccessControlService accessControlService,
            SysUserMapper sysUserMapper
    ) {
        this.invitationMapper = invitationMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.phoneIdentityService = phoneIdentityService;
        this.houseMemberService = houseMemberService;
        this.accessControlService = accessControlService;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 邀人进兔舍。identifier 可以是手机号，也可以是对方在「我的 → 账号设置」
     * 里看得到的兔号。两条通道的语义天生不同，不能揉成一条：
     *
     * <ul>
     *   <li>手机号：对方可能还没注册，只能先挂着，等他用这个号码登录时才生效；</li>
     *   <li>兔号：兔号只属于已存在的账号，没什么好等的，当场入伙。</li>
     * </ul>
     */
    @Transactional
    public HouseInvitationResponse invite(
            Long houseId,
            Long inviterUserId,
            String identifier,
            String requestedRole,
            String requestId
    ) {
        accessControlService.requireHousePermission(
                inviterUserId,
                houseId,
                PermissionCode.RABBIT_HOUSE_MEMBERS_ADD
        );
        String role = normalizeInvitationRole(requestedRole);
        String trimmed = identifier == null ? "" : identifier.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(400, "请填写手机号或兔号");
        }
        String candidateCode = UserCodes.normalize(trimmed);
        if (UserCodes.looksLikeUserCode(candidateCode)) {
            return inviteByUserCode(houseId, inviterUserId, candidateCode, role, requestId);
        }
        return inviteByPhone(houseId, inviterUserId, trimmed, role, requestId);
    }

    private HouseInvitationResponse inviteByUserCode(
            Long houseId,
            Long inviterUserId,
            String userCode,
            String role,
            String requestId
    ) {
        SysUser target = sysUserMapper.selectByUserCode(userCode);
        if (target == null) {
            throw new BizException(404, "没找到兔号 " + userCode + "，请让对方在「我的 → 账号设置」里核对");
        }
        if (target.getUserId().equals(inviterUserId)) {
            throw new BizException(400, "不用邀请自己");
        }
        String normalizedRequestId = normalizeRequestId(requestId);
        Date now = new Date();

        HouseInvitation invitation = new HouseInvitation();
        invitation.setHouseId(houseId);
        invitation.setInviteChannel(CHANNEL_USER_CODE);
        invitation.setInvitedUserId(target.getUserId());
        invitation.setRole(role);
        // 对方已经在平台上，没有「等他注册」这回事，直接计作已接受。
        invitation.setStatus(STATUS_ACCEPTED);
        invitation.setAcceptedUserId(target.getUserId());
        invitation.setAcceptedTime(now);
        invitation.setRequestId(normalizedRequestId);
        invitation.setInvitedByUserId(inviterUserId);
        invitation.setExpiresTime(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        invitationMapper.insertOrKeepExisting(invitation);

        HouseInvitation persisted = invitationMapper.selectByRequestIdForUpdate(
                houseId,
                inviterUserId,
                normalizedRequestId
        );
        if (persisted == null) {
            throw new BizException(500, "邀请幂等回查失败");
        }
        if (!target.getUserId().equals(persisted.getInvitedUserId()) || !role.equals(persisted.getRole())) {
            throw new BizException(409, "requestId已用于其他邀请");
        }

        RabbitHouse house = rabbitHouseMapper.selectByIdForUpdate(houseId);
        if (house == null || Boolean.TRUE.equals(house.getIsDeleted()) || !STATUS_ENABLED.equals(house.getStatus())) {
            throw new BizException(409, "兔舍不可用，无法拉人入伙");
        }
        // 重放同一个 requestId 时这里也会再跑一遍，join 本身幂等，
        // 而且不会把已有的高权限降下来。
        String effectiveRole = houseMemberService.joinByInvitation(
                houseId,
                target.getUserId(),
                role,
                "user-code-invitation"
        );
        return new HouseInvitationResponse("JOINED", effectiveRole);
    }

    private HouseInvitationResponse inviteByPhone(
            Long houseId,
            Long inviterUserId,
            String phone,
            String role,
            String requestId
    ) {
        String normalizedPhone = PhoneNumbers.normalizeMainlandMobile(phone);
        String phoneHash = phoneIdentityService.hash(normalizedPhone);
        String normalizedRequestId = normalizeRequestId(requestId);

        HouseInvitation invitation = new HouseInvitation();
        Date expiresTime = Date.from(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setHouseId(houseId);
        invitation.setInviteChannel(CHANNEL_PHONE);
        invitation.setPhoneHash(phoneHash);
        invitation.setPhoneMasked(phoneIdentityService.mask(normalizedPhone));
        invitation.setRole(role);
        invitation.setStatus(STATUS_PENDING);
        invitation.setRequestId(normalizedRequestId);
        invitation.setInvitedByUserId(inviterUserId);
        invitation.setExpiresTime(expiresTime);
        invitationMapper.insertOrKeepExisting(invitation);
        HouseInvitation persisted = invitationMapper.selectByRequestIdForUpdate(
                houseId,
                inviterUserId,
                normalizedRequestId
        );
        if (persisted == null) {
            throw new BizException(500, "邀请幂等回查失败");
        }
        if (!phoneHash.equals(persisted.getPhoneHash()) || !role.equals(persisted.getRole())) {
            throw new BizException(409, "requestId已用于其他邀请");
        }
        return responseOf(persisted);
    }

    @Transactional
    public void acceptPending(String phoneHash, Long userId) {
        List<HouseInvitation> pending = invitationMapper.selectPendingByPhoneForUpdate(phoneHash, new Date());
        Long handledHouseId = null;
        for (HouseInvitation invitation : pending) {
            if (invitation.getHouseId().equals(handledHouseId)) {
                continue;
            }
            handledHouseId = invitation.getHouseId();
            RabbitHouse house = rabbitHouseMapper.selectByIdForUpdate(invitation.getHouseId());
            if (house == null || Boolean.TRUE.equals(house.getIsDeleted()) || !STATUS_ENABLED.equals(house.getStatus())) {
                continue;
            }
            houseMemberService.joinByInvitation(
                    invitation.getHouseId(),
                    userId,
                    invitation.getRole(),
                    "phone-invitation"
            );
            invitationMapper.markAcceptedByHouseAndPhone(
                    invitation.getHouseId(),
                    phoneHash,
                    userId,
                    new Date()
            );
        }
    }

    private HouseInvitationResponse responseOf(HouseInvitation invitation) {
        return new HouseInvitationResponse("SUBMITTED", invitation.getRole());
    }

    private String normalizeInvitationRole(String value) {
        if (HouseRole.OWNER.code().equalsIgnoreCase(value == null ? "" : value.trim())) {
            throw new BizException(400, "邀请不能直接授予兔场所有者");
        }
        HouseRole role = HouseRole.parseAssignable(value, false);
        return role.code();
    }

    private String normalizeRequestId(String requestId) {
        String value = requestId == null ? "" : requestId.trim();
        if (value.isEmpty() || value.length() > 64) {
            throw new BizException(400, "requestId不合法");
        }
        return value;
    }
}
