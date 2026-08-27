package com.rabbit.app.modules.house.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.auth.service.PhoneIdentityService;
import com.rabbit.app.modules.house.dto.HouseInvitationResponse;
import com.rabbit.app.modules.house.entity.HouseInvitation;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseInvitationMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 兔场邀请。这是「外人变成成员」的唯一入口，所以它同时是一道权限闸和一道身份闸。
 *
 * <p>两条通道语义不同，必须分开守：账号（USER_CODE）当场入伙，手机号只能挂 PENDING
 * 等对方登录。把手机号也当场入伙，就等于凭一个号码把陌生人塞进兔场；把账号也挂起来，
 * 邀请就永远不生效。
 *
 * <p>另外三处不能松：邀请人自己必须先有 {@code RABBIT_HOUSE_MEMBERS_ADD}；邀请不能
 * 直接授予 OWNER（否则任何管理员都能凭邀请把自己人升成所有者）；同一个 requestId 被
 * 换了对象或角色重放时必须 409，不能让重放悄悄改掉一条已存在邀请的含义。
 */
class HouseInvitationServiceTest {
    private static final long HOUSE_ID = 42L;
    private static final long INVITER_ID = 7L;
    private static final long TARGET_ID = 99L;
    private static final String PHONE = "13800001111";
    private static final String PHONE_HASH = "hash:13800001111";
    private static final String REQUEST_ID = "req-1";

    private HouseInvitationMapper invitationMapper;
    private RabbitHouseMapper rabbitHouseMapper;
    private PhoneIdentityService phoneIdentityService;
    private HouseMemberService houseMemberService;
    private AccessControlService accessControlService;
    private SysUserMapper sysUserMapper;
    private HouseInvitationService service;

    @BeforeEach
    void setUp() {
        invitationMapper = mock(HouseInvitationMapper.class);
        rabbitHouseMapper = mock(RabbitHouseMapper.class);
        phoneIdentityService = mock(PhoneIdentityService.class);
        houseMemberService = mock(HouseMemberService.class);
        accessControlService = mock(AccessControlService.class);
        sysUserMapper = mock(SysUserMapper.class);
        when(phoneIdentityService.hash(PHONE)).thenReturn(PHONE_HASH);
        when(phoneIdentityService.mask(PHONE)).thenReturn("138****1111");
        service = new HouseInvitationService(
                invitationMapper,
                rabbitHouseMapper,
                phoneIdentityService,
                houseMemberService,
                accessControlService,
                sysUserMapper
        );
    }

    // ---------- 权限闸 ----------

    @Test
    void invitingRequiresTheMemberAddPermission() {
        givenUserCodeTarget();
        givenPersistedUserCodeInvitation(HouseRole.STAFF.code());
        givenEnabledHouse();

        service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID);

        verify(accessControlService).requireHousePermission(
                INVITER_ID,
                HOUSE_ID,
                PermissionCode.RABBIT_HOUSE_MEMBERS_ADD
        );
    }

    @Test
    void aCallerWithoutPermissionWritesNothing() {
        when(accessControlService.requireHousePermission(anyLong(), anyLong(), any()))
                .thenThrow(new BizException(403, "仅兔场所有者可操作"));

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "STAFF", REQUEST_ID)
        ).getCode());
        verify(invitationMapper, never()).insertOrKeepExisting(any());
        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
    }

    /**
     * 邀请这条路不能产生第二个所有者。所有者能删场、能改所有人的角色，
     * 它只应该来自建场或显式转让，不能从一个「加成员」的接口里溜出来。
     */
    @Test
    void invitationCannotGrantOwnership() {
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "OWNER", REQUEST_ID)
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "  owner  ", REQUEST_ID)
        ).getCode());
        verify(invitationMapper, never()).insertOrKeepExisting(any());
    }

    @Test
    void anUnknownRoleIsRejected() {
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "SUPERVISOR", REQUEST_ID)
        ).getCode());
    }

    @Test
    void aBlankIdentifierIsRejected() {
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "   ", "STAFF", REQUEST_ID)
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, null, "STAFF", REQUEST_ID)
        ).getCode());
    }

    @Test
    void aMissingOrOverlongRequestIdIsRejected() {
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "STAFF", "  ")
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "STAFF", "x".repeat(65))
        ).getCode());
        verify(invitationMapper, never()).insertOrKeepExisting(any());
    }

    // ---------- 账号通道：当场入伙 ----------

    @Test
    void invitingAnUnknownUserCodeIsRejected() {
        when(sysUserMapper.selectByUserCode("R0123456789")).thenReturn(null);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());
        verify(invitationMapper, never()).insertOrKeepExisting(any());
    }

    @Test
    void invitingYourselfIsRejected() {
        SysUser me = new SysUser();
        me.setUserId(INVITER_ID);
        when(sysUserMapper.selectByUserCode("R0123456789")).thenReturn(me);

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());
    }

    /**
     * 账号是用户口头报出来的，o/O、i/I/l/1 会被抄错。归一化必须发生在识别通道之前，
     * 否则「r0123-456789」会被当成手机号，走到 PhoneNumbers 那里报「请输入有效手机号」，
     * 用户完全看不懂问题出在哪。
     */
    @Test
    void aSloppilyTypedUserCodeStillRoutesToTheUserCodeChannel() {
        givenUserCodeTarget();
        givenPersistedUserCodeInvitation(HouseRole.STAFF.code());
        givenEnabledHouse();

        service.invite(HOUSE_ID, INVITER_ID, " r0123-456789 ", "STAFF", REQUEST_ID);

        verify(sysUserMapper).selectByUserCode("R0123456789");
    }

    @Test
    void userCodeInvitationJoinsImmediatelyAndIsRecordedAsAccepted() {
        givenUserCodeTarget();
        givenPersistedUserCodeInvitation(HouseRole.STAFF.code());
        givenEnabledHouse();
        when(houseMemberService.joinByInvitation(HOUSE_ID, TARGET_ID, "STAFF", "user-code-invitation"))
                .thenReturn("MANAGER");

        HouseInvitationResponse response =
                service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID);

        assertEquals("JOINED", response.status());
        assertEquals("MANAGER", response.role());
        ArgumentCaptor<HouseInvitation> written = ArgumentCaptor.forClass(HouseInvitation.class);
        verify(invitationMapper).insertOrKeepExisting(written.capture());
        assertEquals("USER_CODE", written.getValue().getInviteChannel());
        assertEquals("ACCEPTED", written.getValue().getStatus());
        assertEquals(TARGET_ID, written.getValue().getInvitedUserId());
        assertEquals(TARGET_ID, written.getValue().getAcceptedUserId());
        assertNull(written.getValue().getPhoneHash());
    }

    /**
     * 同一个 requestId 换了受邀人或角色重放，必须 409。放过去的话，重放会把
     * 「邀请 A 当 STAFF」悄悄变成「邀请 B 当 MANAGER」，而调用方以为自己在做幂等重试。
     */
    @Test
    void replayingARequestIdForAnotherPersonIsRejected() {
        givenUserCodeTarget();
        HouseInvitation persisted = persistedInvitation(HouseRole.STAFF.code());
        persisted.setInvitedUserId(12345L);
        when(invitationMapper.selectByRequestIdForUpdate(HOUSE_ID, INVITER_ID, REQUEST_ID)).thenReturn(persisted);
        givenEnabledHouse();

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());
        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void replayingARequestIdForAnotherRoleIsRejected() {
        givenUserCodeTarget();
        givenPersistedUserCodeInvitation(HouseRole.MANAGER.code());
        givenEnabledHouse();

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());
        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void anInvitationThatCannotBeReadBackIsAnError() {
        givenUserCodeTarget();
        when(invitationMapper.selectByRequestIdForUpdate(HOUSE_ID, INVITER_ID, REQUEST_ID)).thenReturn(null);

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());
    }

    @Test
    void nobodyJoinsAFarmThatIsDeletedOrSuspended() {
        givenUserCodeTarget();
        givenPersistedUserCodeInvitation(HouseRole.STAFF.code());
        RabbitHouse house = givenEnabledHouse();
        house.setIsDeleted(true);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());

        house.setIsDeleted(false);
        house.setStatus("DISABLED");
        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());

        when(rabbitHouseMapper.selectByIdForUpdate(HOUSE_ID)).thenReturn(null);
        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "R0123456789", "STAFF", REQUEST_ID)
        ).getCode());

        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
    }

    // ---------- 手机号通道：只能挂起 ----------

    @Test
    void phoneInvitationIsParkedAsPendingAndNobodyJoinsYet() {
        HouseInvitation persisted = persistedInvitation(HouseRole.STAFF.code());
        persisted.setPhoneHash(PHONE_HASH);
        when(invitationMapper.selectByRequestIdForUpdate(HOUSE_ID, INVITER_ID, REQUEST_ID)).thenReturn(persisted);

        HouseInvitationResponse response = service.invite(HOUSE_ID, INVITER_ID, PHONE, "STAFF", REQUEST_ID);

        assertEquals("SUBMITTED", response.status());
        ArgumentCaptor<HouseInvitation> written = ArgumentCaptor.forClass(HouseInvitation.class);
        verify(invitationMapper).insertOrKeepExisting(written.capture());
        assertEquals("PHONE", written.getValue().getInviteChannel());
        assertEquals("PENDING", written.getValue().getStatus());
        assertEquals(PHONE_HASH, written.getValue().getPhoneHash());
        assertEquals("138****1111", written.getValue().getPhoneMasked());
        assertNull(written.getValue().getInvitedUserId());
        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void anInvalidPhoneNumberIsRejected() {
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, "12345", "STAFF", REQUEST_ID)
        ).getCode());
        verify(invitationMapper, never()).insertOrKeepExisting(any());
    }

    @Test
    void replayingARequestIdForAnotherPhoneIsRejected() {
        HouseInvitation persisted = persistedInvitation(HouseRole.STAFF.code());
        persisted.setPhoneHash("hash:somebody-else");
        when(invitationMapper.selectByRequestIdForUpdate(HOUSE_ID, INVITER_ID, REQUEST_ID)).thenReturn(persisted);

        assertEquals(409, assertThrows(
                BizException.class,
                () -> service.invite(HOUSE_ID, INVITER_ID, PHONE, "STAFF", REQUEST_ID)
        ).getCode());
    }

    // ---------- 登录时兑现挂起的邀请 ----------

    @Test
    void loggingInRedeemsAPendingInvitationAndMarksItAccepted() {
        when(invitationMapper.selectPendingByPhoneForUpdate(eq(PHONE_HASH), any()))
                .thenReturn(List.of(pendingInvitation(HOUSE_ID, "STAFF")));
        givenEnabledHouse();

        service.acceptPending(PHONE_HASH, TARGET_ID);

        verify(houseMemberService).joinByInvitation(HOUSE_ID, TARGET_ID, "STAFF", "phone-invitation");
        verify(invitationMapper).markAcceptedByHouseAndPhone(eq(HOUSE_ID), eq(PHONE_HASH), eq(TARGET_ID), any());
    }

    @Test
    void twoPendingInvitationsToTheSameFarmOnlyJoinOnce() {
        when(invitationMapper.selectPendingByPhoneForUpdate(eq(PHONE_HASH), any()))
                .thenReturn(List.of(
                        pendingInvitation(HOUSE_ID, "STAFF"),
                        pendingInvitation(HOUSE_ID, "MANAGER")
                ));
        givenEnabledHouse();

        service.acceptPending(PHONE_HASH, TARGET_ID);

        verify(houseMemberService).joinByInvitation(HOUSE_ID, TARGET_ID, "STAFF", "phone-invitation");
        verify(houseMemberService, never()).joinByInvitation(HOUSE_ID, TARGET_ID, "MANAGER", "phone-invitation");
    }

    /**
     * 兔场在邀请发出后被删掉或停用了，挂起的邀请就不该再兑现——否则一个早已废弃的
     * 邀请可以在任意时点把人塞进一个不该存在的兔场。也不能标记成已接受，
     * 兔场恢复后这条邀请还得能用。
     */
    @Test
    void aPendingInvitationToAVanishedFarmIsNeitherJoinedNorConsumed() {
        when(invitationMapper.selectPendingByPhoneForUpdate(eq(PHONE_HASH), any()))
                .thenReturn(List.of(pendingInvitation(HOUSE_ID, "STAFF")));
        RabbitHouse house = givenEnabledHouse();
        house.setIsDeleted(true);

        service.acceptPending(PHONE_HASH, TARGET_ID);

        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
        verify(invitationMapper, never()).markAcceptedByHouseAndPhone(anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void aPendingInvitationToASuspendedFarmIsNotRedeemed() {
        when(invitationMapper.selectPendingByPhoneForUpdate(eq(PHONE_HASH), any()))
                .thenReturn(List.of(pendingInvitation(HOUSE_ID, "STAFF")));
        RabbitHouse house = givenEnabledHouse();
        house.setStatus("DISABLED");

        service.acceptPending(PHONE_HASH, TARGET_ID);

        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void aPhoneWithNoPendingInvitationIsANoop() {
        when(invitationMapper.selectPendingByPhoneForUpdate(eq(PHONE_HASH), any())).thenReturn(List.of());

        service.acceptPending(PHONE_HASH, TARGET_ID);

        verify(houseMemberService, never()).joinByInvitation(anyLong(), anyLong(), anyString(), anyString());
        verify(rabbitHouseMapper, never()).selectByIdForUpdate(anyLong());
    }

    // ---------- fixtures ----------

    private void givenUserCodeTarget() {
        SysUser target = new SysUser();
        target.setUserId(TARGET_ID);
        target.setUserCode("R0123456789");
        when(sysUserMapper.selectByUserCode("R0123456789")).thenReturn(target);
    }

    private void givenPersistedUserCodeInvitation(String role) {
        HouseInvitation persisted = persistedInvitation(role);
        persisted.setInvitedUserId(TARGET_ID);
        when(invitationMapper.selectByRequestIdForUpdate(HOUSE_ID, INVITER_ID, REQUEST_ID)).thenReturn(persisted);
    }

    private HouseInvitation persistedInvitation(String role) {
        HouseInvitation persisted = new HouseInvitation();
        persisted.setId(1L);
        persisted.setHouseId(HOUSE_ID);
        persisted.setRole(role);
        persisted.setRequestId(REQUEST_ID);
        persisted.setInvitedByUserId(INVITER_ID);
        return persisted;
    }

    private HouseInvitation pendingInvitation(Long houseId, String role) {
        HouseInvitation invitation = new HouseInvitation();
        invitation.setHouseId(houseId);
        invitation.setRole(role);
        invitation.setStatus("PENDING");
        invitation.setPhoneHash(PHONE_HASH);
        invitation.setExpiresTime(new Date(System.currentTimeMillis() + 86_400_000L));
        return invitation;
    }

    private RabbitHouse givenEnabledHouse() {
        RabbitHouse house = new RabbitHouse();
        house.setId(HOUSE_ID);
        house.setStatus("ENABLED");
        house.setIsDeleted(false);
        when(rabbitHouseMapper.selectByIdForUpdate(HOUSE_ID)).thenReturn(house);
        return house;
    }
}
