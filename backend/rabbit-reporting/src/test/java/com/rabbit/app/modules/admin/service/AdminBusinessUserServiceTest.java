package com.rabbit.app.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminBusinessUserItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.mapper.AdminBusinessUserMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 业务用户的后台管理。
 *
 * <p>真正要守的是停用路径：一个用户如果是某个兔场唯一的有效所有者，停用他等于让那个
 * 兔场没人能管。实现里为此先按 id 锁住他名下的兔场，再统计「唯一所有者」的数量 ——
 * 顺序反了就是典型的检查后失效（TOCTOU），并发下能把最后一个所有者停掉。
 * 下面用 {@link InOrder} 把这个顺序钉住。
 */
class AdminBusinessUserServiceTest {
    private AdminBusinessUserMapper userMapper;
    private SysUserMapper sysUserMapper;
    private RabbitHouseMapper rabbitHouseMapper;
    private AccessControlService accessControlService;
    private AdminBusinessUserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(AdminBusinessUserMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        rabbitHouseMapper = mock(RabbitHouseMapper.class);
        accessControlService = mock(AccessControlService.class);
        service = new AdminBusinessUserService(userMapper, sysUserMapper, rabbitHouseMapper, accessControlService);
    }

    // ---------- 权限 ----------

    @Test
    void listRequiresTheListPermission() {
        doThrow(new BizException(403, "无权访问"))
                .when(accessControlService).requirePlatformPermission(PermissionCode.PLATFORM_USERS_LIST);

        assertEquals(403, assertThrows(BizException.class, () -> service.list(null, null, 1, 20)).getCode());
        verify(userMapper, never()).count(anyString(), anyString());
    }

    @Test
    void updateStatusRequiresTheEditPermission() {
        doThrow(new BizException(403, "无权访问"))
                .when(accessControlService).requirePlatformPermission(PermissionCode.PLATFORM_USERS_EDIT);

        assertEquals(403, assertThrows(BizException.class, () -> service.updateStatus(5L, "DISABLED")).getCode());
        verify(sysUserMapper, never()).selectByIdForUpdate(anyLong());
    }

    // ---------- 列表 ----------

    @Test
    void paginationFallsBackAndIsCapped() {
        when(userMapper.selectPage(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.list(null, null, 0, 0);
        verify(userMapper).selectPage(isNull(), isNull(), eq(0), eq(20));

        service.list(null, null, 3, 500);
        verify(userMapper).selectPage(isNull(), isNull(), eq(200), eq(100));
    }

    @Test
    void blankKeywordAndStatusBecomeNullFiltersRatherThanEmptyStrings() {
        when(userMapper.selectPage(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.list("   ", "  ", 1, 20);

        verify(userMapper).count(isNull(), isNull());
    }

    @Test
    void statusFilterIsUpperCased() {
        when(userMapper.selectPage(isNull(), eq("ENABLED"), anyInt(), anyInt())).thenReturn(List.of());

        service.list(null, " enabled ", 1, 20);

        verify(userMapper).count(isNull(), eq("ENABLED"));
    }

    @Test
    void unknownStatusFilterIsRejected() {
        BizException error = assertThrows(BizException.class, () -> service.list(null, "FROZEN", 1, 20));
        assertEquals(400, error.getCode());
        assertEquals("用户状态不合法", error.getMessage());
    }

    @Test
    void listCarriesTheTotalBack() {
        when(userMapper.count(isNull(), isNull())).thenReturn(12L);
        when(userMapper.selectPage(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of(item(5L)));

        PageResult<AdminBusinessUserItem> result = service.list(null, null, 1, 20);

        assertEquals(12L, result.getTotal());
        assertEquals(1, result.getItems().size());
    }

    // ---------- 改状态 ----------

    @Test
    void unknownTargetStatusIsRejectedBeforeAnyLocking() {
        BizException error = assertThrows(BizException.class, () -> service.updateStatus(5L, "FROZEN"));
        assertEquals(400, error.getCode());
        verify(sysUserMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void missingUserIsReportedAsNotFound() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class, () -> service.updateStatus(5L, "DISABLED")).getCode());
        verify(sysUserMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void disablingASoleOwnerIsRefused() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("ENABLED"));
        when(userMapper.selectOwnedHouseIdsForUpdate(5L)).thenReturn(List.of(11L));
        when(userMapper.countNonDeletedHousesWhereSoleOwner(5L)).thenReturn(1L);

        BizException error = assertThrows(BizException.class, () -> service.updateStatus(5L, "DISABLED"));
        assertEquals(409, error.getCode());
        assertEquals("该用户是兔场唯一的有效所有者，请先指定另一名所有者", error.getMessage());
        verify(sysUserMapper, never()).updateStatus(anyLong(), anyString());
    }

    /**
     * 先锁兔场行、再统计唯一所有者。反过来就是检查后失效：统计完到写入之间，
     * 别的事务可以把另一个所有者移走，于是这次停用把最后一个所有者也停掉了。
     */
    @Test
    void ownedHousesAreLockedBeforeTheSoleOwnerCountIsTaken() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("ENABLED"));
        when(userMapper.selectOwnedHouseIdsForUpdate(5L)).thenReturn(List.of(11L, 12L));
        when(userMapper.countNonDeletedHousesWhereSoleOwner(5L)).thenReturn(0L);
        when(sysUserMapper.updateStatus(5L, "DISABLED")).thenReturn(1);
        when(userMapper.selectById(5L)).thenReturn(item(5L));

        service.updateStatus(5L, "DISABLED");

        InOrder order = inOrder(sysUserMapper, userMapper, rabbitHouseMapper);
        order.verify(sysUserMapper).selectByIdForUpdate(5L);
        order.verify(userMapper).selectOwnedHouseIdsForUpdate(5L);
        order.verify(rabbitHouseMapper).selectByIdForUpdate(11L);
        order.verify(rabbitHouseMapper).selectByIdForUpdate(12L);
        order.verify(userMapper).countNonDeletedHousesWhereSoleOwner(5L);
        order.verify(sysUserMapper).updateStatus(5L, "DISABLED");
    }

    /**
     * 启用是安全方向，不该为它付出锁全部兔场的代价。
     */
    @Test
    void enablingSkipsTheSoleOwnerGuardEntirely() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("DISABLED"));
        when(sysUserMapper.updateStatus(5L, "ENABLED")).thenReturn(1);
        when(userMapper.selectById(5L)).thenReturn(item(5L));

        service.updateStatus(5L, "ENABLED");

        verify(userMapper, never()).selectOwnedHouseIdsForUpdate(anyLong());
        verify(userMapper, never()).countNonDeletedHousesWhereSoleOwner(anyLong());
    }

    /**
     * 已经停用的用户再停用一次是空操作，不必再走一遍所有者校验。
     */
    @Test
    void disablingAnAlreadyDisabledUserSkipsTheGuard() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("DISABLED"));
        when(sysUserMapper.updateStatus(5L, "DISABLED")).thenReturn(1);
        when(userMapper.selectById(5L)).thenReturn(item(5L));

        service.updateStatus(5L, "DISABLED");

        verify(userMapper, never()).countNonDeletedHousesWhereSoleOwner(anyLong());
    }

    @Test
    void statusArgumentIsCaseInsensitive() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("DISABLED"));
        when(sysUserMapper.updateStatus(5L, "ENABLED")).thenReturn(1);
        when(userMapper.selectById(5L)).thenReturn(item(5L));

        service.updateStatus(5L, " enabled ");

        verify(sysUserMapper).updateStatus(5L, "ENABLED");
    }

    @Test
    void aRowThatVanishedBetweenLockAndWriteIsReportedAsNotFound() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("DISABLED"));
        when(sysUserMapper.updateStatus(5L, "ENABLED")).thenReturn(0);

        assertEquals(404, assertThrows(BizException.class, () -> service.updateStatus(5L, "ENABLED")).getCode());
    }

    @Test
    void theUpdatedRowIsReadBackForTheResponse() {
        when(sysUserMapper.selectByIdForUpdate(5L)).thenReturn(sysUser("DISABLED"));
        when(sysUserMapper.updateStatus(5L, "ENABLED")).thenReturn(1);
        when(userMapper.selectById(5L)).thenReturn(item(5L));

        assertEquals(5L, service.updateStatus(5L, "ENABLED").getUserId());
    }

    private SysUser sysUser(String status) {
        SysUser user = new SysUser();
        user.setStatus(status);
        return user;
    }

    private AdminBusinessUserItem item(Long userId) {
        AdminBusinessUserItem item = new AdminBusinessUserItem();
        item.setUserId(userId);
        return item;
    }
}
