package com.rabbit.app.modules.house.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HousePermissionInfo;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.house.spi.HouseInitializationContext;
import com.rabbit.app.modules.house.spi.HouseInitializer;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.HouseContext;
import com.rabbit.app.security.permission.HouseRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/**
 * 兔场服务，同时也是全系统的租户闸门。
 *
 * <p>这里刻意接进真的 {@link AccessControlService}，只把 mapper 挡在外面。原因是
 * 「谁能改这个兔场」的判断并不住在 {@code HouseService} 里，而是住在角色等级和
 * {@code PermissionCode.minimumRank()} 的比较上；把 AccessControlService 也 mock 掉，
 * 用例就只能证明「调用了权限方法」，证明不了「判断结果是对的」。越权是不会有人来报的
 * 缺陷——用户不会主动说「我看到了不该看的数据」，所以这道闸只能靠用例守。
 *
 * <p>覆盖四类必须拒绝的情形：角色等级不够、压根不是成员（或成员已停用）、兔场已删除或
 * 停用、账号本身已停用。另外守住 {@link HouseContext} 这个 ThreadLocal 缓存——它按
 * (userId, houseId) 命中，一旦命中条件放宽，A 用户的角色就会被 B 用户复用，那是跨租户
 * 越权。ThreadLocal 也必须逐用例清理，否则上一条用例的身份会让下一条莫名其妙有权限。
 */
class HouseServiceTest {
    private static final long USER_ID = 7L;
    private static final long HOUSE_ID = 42L;

    private RabbitHouseMapper rabbitHouseMapper;
    private HouseUserMapper houseUserMapper;
    private SysUserMapper sysUserMapper;
    private RequestDedupService requestDedupService;
    private List<HouseInitializer> initializers;
    private HouseService service;

    @BeforeEach
    void setUp() {
        rabbitHouseMapper = mock(RabbitHouseMapper.class);
        houseUserMapper = mock(HouseUserMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        requestDedupService = mock(RequestDedupService.class);
        initializers = new ArrayList<>();
        AccessControlService accessControlService = new AccessControlService(
                rabbitHouseMapper,
                houseUserMapper,
                sysUserMapper
        );
        service = new HouseService(
                rabbitHouseMapper,
                houseUserMapper,
                sysUserMapper,
                requestDedupService,
                accessControlService,
                initializers
        );
        HouseContext.clear();
    }

    @AfterEach
    void tearDown() {
        HouseContext.clear();
    }

    // ---------- 角色等级不够就必须拒绝 ----------

    @Test
    void viewerCannotRenameTheFarm() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.VIEWER);

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateHouse(USER_ID, HOUSE_ID, "新名字", null)
        );

        assertEquals(403, error.getCode());
        verify(rabbitHouseMapper, never()).updateBasic(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void staffCannotRenameTheFarm() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.STAFF);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.updateHouse(USER_ID, HOUSE_ID, "新名字", null)
        ).getCode());
        verify(rabbitHouseMapper, never()).updateBasic(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void managerCanRenameTheFarm() {
        givenEnabledUser();
        RabbitHouse house = givenEnabledHouse();
        givenMembership(HouseRole.MANAGER);
        when(rabbitHouseMapper.updateBasic(HOUSE_ID, "新名字", "备注", "7")).thenReturn(1);
        when(rabbitHouseMapper.selectById(HOUSE_ID)).thenReturn(house);

        assertSame(house, service.updateHouse(USER_ID, HOUSE_ID, "新名字", "备注"));
    }

    @Test
    void onlyTheOwnerCanDeleteTheFarm() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.MANAGER);

        BizException error = assertThrows(BizException.class, () -> service.deleteHouse(USER_ID, HOUSE_ID));

        assertEquals(403, error.getCode());
        assertEquals("仅兔场所有者可删除兔场", error.getMessage());
        verify(rabbitHouseMapper, never()).markDeleted(anyLong(), anyString());
    }

    @Test
    void ownerCanDeleteTheFarm() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.OWNER);
        when(rabbitHouseMapper.markDeleted(HOUSE_ID, "7")).thenReturn(1);

        service.deleteHouse(USER_ID, HOUSE_ID);

        verify(rabbitHouseMapper).markDeleted(HOUSE_ID, "7");
    }

    @Test
    void assertHouseAdminRejectsEveryoneBelowOwner() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.MANAGER);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHouseAdmin(USER_ID, HOUSE_ID)
        ).getCode());
    }

    @Test
    void assertHouseAdminAcceptsTheOwner() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.OWNER);

        service.assertHouseAdmin(USER_ID, HOUSE_ID);

        assertEquals(HouseRole.OWNER.code(), HouseContext.get().getRole());
    }

    // ---------- 旧的 view/edit/control 三级映射 ----------

    @Test
    void viewerPassesViewButFailsEditAndControl() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.VIEWER);

        service.assertHousePermission(USER_ID, HOUSE_ID, "view");

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "edit")
        ).getCode());
        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "control")
        ).getCode());
    }

    @Test
    void staffPassesEditButFailsControl() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.STAFF);

        service.assertHousePermission(USER_ID, HOUSE_ID, "edit");

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "control")
        ).getCode());
    }

    @Test
    void managerPassesControl() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.MANAGER);

        service.assertHousePermission(USER_ID, HOUSE_ID, "control");
    }

    /**
     * 拼错的权限级别必须炸成 500 而不是悄悄放行。写错常量名是很容易发生的事，
     * 若默认分支返回「通过」，一个 typo 就等于把这道闸整个拆掉。
     */
    @Test
    void unknownPermissionLevelIsRejectedInsteadOfSilentlyPassing() {
        givenEnabledUser();

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "admin")
        ).getCode());
        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, null)
        ).getCode());
    }

    // ---------- 不是成员 / 兔场不可用 / 账号停用 ----------

    @Test
    void nonMemberIsRejected() {
        givenEnabledUser();
        givenEnabledHouse();
        when(houseUserMapper.selectByUserAndHouse(USER_ID, HOUSE_ID)).thenReturn(null);

        BizException error = assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        );

        assertEquals(403, error.getCode());
        assertEquals("无兔场权限", error.getMessage());
    }

    @Test
    void suspendedMembershipIsRejected() {
        givenEnabledUser();
        givenEnabledHouse();
        HouseUser member = membership(HouseRole.OWNER);
        member.setStatus("DISABLED");
        when(houseUserMapper.selectByUserAndHouse(USER_ID, HOUSE_ID)).thenReturn(member);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        ).getCode());
    }

    @Test
    void deletedFarmIsRejected() {
        givenEnabledUser();
        RabbitHouse house = givenEnabledHouse();
        house.setIsDeleted(true);

        BizException error = assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        );

        assertEquals(410, error.getCode());
        verify(houseUserMapper, never()).selectByUserAndHouse(anyLong(), anyLong());
    }

    @Test
    void missingFarmIsRejected() {
        givenEnabledUser();
        when(rabbitHouseMapper.selectById(HOUSE_ID)).thenReturn(null);

        assertEquals(410, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        ).getCode());
    }

    @Test
    void suspendedFarmIsRejectedEvenForItsOwner() {
        givenEnabledUser();
        RabbitHouse house = givenEnabledHouse();
        house.setStatus("DISABLED");
        givenMembership(HouseRole.OWNER);

        BizException error = assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        );

        assertEquals(403, error.getCode());
        assertEquals("兔场已停用", error.getMessage());
    }

    /**
     * 账号停用要在查兔场之前就拦下。顺序有意义：停用账号连「这个兔场存在吗」都不该问出来。
     */
    @Test
    void suspendedAccountIsRejectedBeforeTheFarmIsEvenLookedUp() {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        user.setStatus("DISABLED");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(user);

        BizException error = assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        );

        assertEquals(403, error.getCode());
        verify(rabbitHouseMapper, never()).selectById(anyLong());
    }

    @Test
    void unknownAccountIsRejected() {
        when(sysUserMapper.selectById(USER_ID)).thenReturn(null);

        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "view")
        ).getCode());
    }

    @Test
    void anonymousCallerIsRejected() {
        assertEquals(401, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(null, HOUSE_ID, "view")
        ).getCode());
    }

    @Test
    void nonPositiveHouseIdIsRejected() {
        givenEnabledUser();

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, 0L, "view")
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, null, "view")
        ).getCode());
    }

    @Test
    void updateAndDeleteRejectMissingHouseIdBeforeTouchingAnyMapper() {
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.updateHouse(USER_ID, null, "n", null)
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.deleteHouse(USER_ID, -1L)
        ).getCode());
        verify(sysUserMapper, never()).selectById(anyLong());
    }

    // ---------- HouseContext 缓存不能跨用户/跨兔场复用 ----------

    /**
     * 缓存命中要求 userId 和 houseId 都对得上。若只比对 houseId，同一个请求线程上
     * 另一个用户就会直接继承前一个用户的角色——这是最典型的跨租户越权。
     */
    @Test
    void cachedHouseContextIsNotReusedByAnotherUser() {
        HouseContext.set(999L, HOUSE_ID, "control", HouseRole.OWNER.code(), true, HouseRole.OWNER.rank(), List.of());
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.VIEWER);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "control")
        ).getCode());
        verify(houseUserMapper).selectByUserAndHouse(USER_ID, HOUSE_ID);
    }

    @Test
    void cachedHouseContextIsNotReusedForAnotherFarm() {
        HouseContext.set(USER_ID, 4242L, "control", HouseRole.OWNER.code(), true, HouseRole.OWNER.rank(), List.of());
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.VIEWER);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.assertHousePermission(USER_ID, HOUSE_ID, "control")
        ).getCode());
        verify(houseUserMapper).selectByUserAndHouse(USER_ID, HOUSE_ID);
    }

    // ---------- 权限信息回显 ----------

    @Test
    void viewerPermissionInfoReportsViewOnlyCapabilities() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.VIEWER);

        HousePermissionInfo info = service.getMyHousePermission(USER_ID, HOUSE_ID);

        assertEquals(HouseRole.VIEWER.code(), info.getRole());
        assertEquals("view", info.getPerms());
        assertFalse(info.getIsAdmin());
        assertTrue(info.getPermissions().contains("rabbit:houses:query"));
        assertFalse(info.getPermissions().contains("rabbit:houses:edit"));
        assertFalse(info.getPermissions().contains("rabbit:house-members:add"));
    }

    @Test
    void ownerPermissionInfoReportsAdministratorCapabilities() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.OWNER);

        HousePermissionInfo info = service.getMyHousePermission(USER_ID, HOUSE_ID);

        assertEquals(HouseRole.OWNER.code(), info.getRole());
        assertTrue(info.getIsAdmin());
        assertTrue(info.getPermissions().contains("rabbit:houses:remove"));
        assertTrue(info.getPermissions().contains("rabbit:house-members:add"));
    }

    // ---------- 更新/删除命中 0 行 ----------

    @Test
    void renamingAVanishedFarmReportsNotFound() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.OWNER);
        when(rabbitHouseMapper.updateBasic(anyLong(), anyString(), any(), anyString())).thenReturn(0);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.updateHouse(USER_ID, HOUSE_ID, "新名字", null)
        ).getCode());
    }

    @Test
    void deletingAVanishedFarmReportsNotFound() {
        givenEnabledUser();
        givenEnabledHouse();
        givenMembership(HouseRole.OWNER);
        when(rabbitHouseMapper.markDeleted(anyLong(), anyString())).thenReturn(0);

        assertEquals(404, assertThrows(
                BizException.class,
                () -> service.deleteHouse(USER_ID, HOUSE_ID)
        ).getCode());
    }

    // ---------- 建场 ----------

    @Test
    void suspendedAccountCannotCreateAFarm() {
        SysUser creator = new SysUser();
        creator.setUserId(USER_ID);
        creator.setStatus("DISABLED");
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(creator);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.createHouse(USER_ID, "场子", 1, 1, 1, null, "req-1")
        ).getCode());
        verify(rabbitHouseMapper, never()).insert(any());
    }

    @Test
    void unknownAccountCannotCreateAFarm() {
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(null);

        assertEquals(403, assertThrows(
                BizException.class,
                () -> service.createHouse(USER_ID, "场子", 1, 1, 1, null, "req-1")
        ).getCode());
        verify(rabbitHouseMapper, never()).insert(any());
    }

    @Test
    void replayedRequestIdReturnsTheExistingFarmWithoutInserting() {
        givenEnabledCreator();
        RabbitHouse existing = new RabbitHouse();
        existing.setId(HOUSE_ID);
        when(rabbitHouseMapper.selectByCreatorAndRequestId("7", "req-1")).thenReturn(existing);

        assertSame(existing, service.createHouse(USER_ID, "场子", 1, 1, 1, null, "req-1"));

        verify(rabbitHouseMapper, never()).insert(any());
        verify(requestDedupService, never()).markProcessing(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void dedupSaysDoneButTheRowIsGoneIsAnError() {
        givenEnabledCreator();
        when(rabbitHouseMapper.selectByCreatorAndRequestId("7", "req-1")).thenReturn(null);
        when(requestDedupService.shouldSkipAsDone(0L, USER_ID, "house.create", "req-1")).thenReturn(true);

        assertEquals(500, assertThrows(
                BizException.class,
                () -> service.createHouse(USER_ID, "场子", 1, 1, 1, null, "req-1")
        ).getCode());
    }

    @Test
    void nonPositiveLayoutIsRejectedAndTheAttemptIsMarkedFailed() {
        givenEnabledCreator();

        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.createHouse(USER_ID, "场子", 0, 3, 3, null, "req-1")
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.createHouse(USER_ID, "场子", 3, 0, 3, null, "req-2")
        ).getCode());
        assertEquals(400, assertThrows(
                BizException.class,
                () -> service.createHouse(USER_ID, "场子", 3, 3, 0, null, "req-3")
        ).getCode());
        verify(rabbitHouseMapper, never()).insert(any());
        verify(requestDedupService).markFailed(
                eq(0L), eq(USER_ID), eq("house.create"), eq("req-1"), anyString()
        );
    }

    @Test
    void createdFarmGrantsOwnerMembershipAndRunsInitializers() {
        givenEnabledCreator();
        List<HouseInitializationContext> seen = new ArrayList<>();
        initializers.add(seen::add);
        when(rabbitHouseMapper.insert(any())).thenAnswer(call -> {
            call.<RabbitHouse>getArgument(0).setId(HOUSE_ID);
            return 1;
        });

        RabbitHouse created = service.createHouse(USER_ID, "场子", 2, 3, 4, "备注", "req-1");

        assertEquals(HOUSE_ID, created.getId());
        assertEquals("ENABLED", created.getStatus());
        ArgumentCaptor<HouseUser> owner = ArgumentCaptor.forClass(HouseUser.class);
        verify(houseUserMapper).insert(owner.capture());
        assertEquals(HouseRole.OWNER.code(), owner.getValue().getRole());
        assertEquals("ENABLED", owner.getValue().getStatus());
        assertTrue(owner.getValue().getIsAdmin());
        assertEquals(USER_ID, owner.getValue().getUserId());
        assertEquals(HOUSE_ID, owner.getValue().getHouseId());
        assertEquals(1, seen.size());
        assertEquals(HOUSE_ID, seen.get(0).houseId());
        verify(requestDedupService).markDone(0L, USER_ID, "house.create", "req-1");
    }

    @Test
    void duplicateInsertFallsBackToTheRowTheRaceWinnerWrote() {
        givenEnabledCreator();
        RabbitHouse winner = new RabbitHouse();
        winner.setId(HOUSE_ID);
        when(rabbitHouseMapper.selectByCreatorAndRequestId("7", "req-1"))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(winner);
        when(rabbitHouseMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertSame(winner, service.createHouse(USER_ID, "场子", 1, 1, 1, null, "req-1"));

        verify(houseUserMapper, never()).insert(any());
        verify(requestDedupService).markDone(0L, USER_ID, "house.create", "req-1");
    }

    @Test
    void duplicateInsertWithNoVisibleRowPropagatesAndMarksFailed() {
        givenEnabledCreator();
        when(rabbitHouseMapper.selectByCreatorAndRequestId("7", "req-1")).thenReturn(null);
        when(rabbitHouseMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(
                DuplicateKeyException.class,
                () -> service.createHouse(USER_ID, "场子", 1, 1, 1, null, "req-1")
        );
        verify(requestDedupService).markFailed(
                eq(0L), eq(USER_ID), eq("house.create"), eq("req-1"), any()
        );
    }

    @Test
    void getHouseReturnsTheRequestedHouse() {
        givenEnabledUser();
        RabbitHouse house = givenEnabledHouse();
        givenMembership(HouseRole.VIEWER);
        house.setName("东一舍");

        assertSame(house, service.getHouse(USER_ID, HOUSE_ID));
    }

    @Test
    void getHouseRejectsAMissingHouse() {
        givenEnabledUser();
        when(rabbitHouseMapper.selectById(HOUSE_ID)).thenReturn(null);

        BizException error = assertThrows(
                BizException.class,
                () -> service.getHouse(USER_ID, HOUSE_ID)
        );

        assertEquals(410, error.getCode());
        assertEquals("兔场不存在或已删除", error.getMessage());
    }

    @Test
    void listMyHousesOnlyReturnsRowsScopedToTheCaller() {
        RabbitHouse mine = new RabbitHouse();
        when(rabbitHouseMapper.selectByUserId(USER_ID)).thenReturn(List.of(mine));

        assertEquals(List.of(mine), service.listMyHouses(USER_ID));
        verify(rabbitHouseMapper).selectByUserId(USER_ID);
    }

    // ---------- fixtures ----------

    private void givenEnabledUser() {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        user.setStatus("ENABLED");
        when(sysUserMapper.selectById(USER_ID)).thenReturn(user);
    }

    private void givenEnabledCreator() {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        user.setStatus("ENABLED");
        when(sysUserMapper.selectByIdForUpdate(USER_ID)).thenReturn(user);
    }

    private RabbitHouse givenEnabledHouse() {
        RabbitHouse house = new RabbitHouse();
        house.setId(HOUSE_ID);
        house.setStatus("ENABLED");
        house.setIsDeleted(false);
        when(rabbitHouseMapper.selectById(HOUSE_ID)).thenReturn(house);
        return house;
    }

    private void givenMembership(HouseRole role) {
        when(houseUserMapper.selectByUserAndHouse(USER_ID, HOUSE_ID)).thenReturn(membership(role));
    }

    private HouseUser membership(HouseRole role) {
        HouseUser member = new HouseUser();
        member.setHouseId(HOUSE_ID);
        member.setUserId(USER_ID);
        member.setRole(role.code());
        member.setStatus("ENABLED");
        member.setPerms(role.legacyPermission());
        member.setIsAdmin(role.administrator());
        return member;
    }
}
