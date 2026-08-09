package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.service.PhoneIdentityService;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
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

    private final HouseInvitationMapper invitationMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final PhoneIdentityService phoneIdentityService;
    private final HouseMemberService houseMemberService;
    private final AccessControlService accessControlService;

    public HouseInvitationService(
            HouseInvitationMapper invitationMapper,
            RabbitHouseMapper rabbitHouseMapper,
            PhoneIdentityService phoneIdentityService,
            HouseMemberService houseMemberService,
            AccessControlService accessControlService
    ) {
        this.invitationMapper = invitationMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.phoneIdentityService = phoneIdentityService;
        this.houseMemberService = houseMemberService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public HouseInvitationResponse invite(
            Long houseId,
            Long inviterUserId,
            String phone,
            String requestedRole,
            String requestId
    ) {
        accessControlService.requireHousePermission(
                inviterUserId,
                houseId,
                PermissionCode.RABBIT_HOUSE_MEMBERS_ADD
        );
        String role = normalizeInvitationRole(requestedRole);
        String normalizedPhone = PhoneNumbers.normalizeMainlandMobile(phone);
        String phoneHash = phoneIdentityService.hash(normalizedPhone);
        String normalizedRequestId = normalizeRequestId(requestId);

        HouseInvitation invitation = new HouseInvitation();
        Date expiresTime = Date.from(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setHouseId(houseId);
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
