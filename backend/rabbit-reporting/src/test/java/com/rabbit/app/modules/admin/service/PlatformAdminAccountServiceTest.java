package com.rabbit.app.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminAccountItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.security.PlatformAdminContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 平台账号管理。
 *
 * <p>这个类管的是「谁能进后台」，两类事故都不可逆：一是把自己降权或停用，操作者当场
 * 失去入口；二是删掉/降掉最后一个启用的超管，整个平台再没人能管账号。实现里为此埋了
 * 四道闸（不能删当前账号、不能降当前账号、删除时查超管余量、改动时查超管余量），
 * 下面逐道立用例。
 *
 * <p>{@link PlatformAdminContext} 是 ThreadLocal，用例之间必须清干净，否则前一个用例
 * 残留的身份会让后一个用例莫名其妙地有权限。
 */
class PlatformAdminAccountServiceTest {
    private PlatformAdminMapper platformAdminMapper;
    private PasswordEncoder passwordEncoder;
    private PlatformAdminAccountService service;

    @BeforeEach
    void setUp() {
        platformAdminMapper = mock(PlatformAdminMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "encoded:" + call.getArgument(0));
        service = new PlatformAdminAccountService(platformAdminMapper, passwordEncoder);
        asSuperAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        PlatformAdminContext.clear();
    }

    // ---------- 权限闸 ----------

    @Test
    void plainAdminCannotManageAccounts() {
        PlatformAdminContext.set(2L, "ADMIN");

        assertEquals(403, assertThrows(BizException.class, () -> service.list(null, 1, 20)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.get(1L)).getCode());
        assertEquals(403, assertThrows(BizException.class,
                () -> service.create("x", "password", "ADMIN", true)).getCode());
        assertEquals(403, assertThrows(BizException.class,
                () -> service.update(2L, "x", null, "ADMIN", true)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.delete(2L)).getCode());
    }

    @Test
    void anonymousCallerCannotManageAccounts() {
        PlatformAdminContext.clear();

        assertEquals(403, assertThrows(BizException.class, () -> service.list(null, 1, 20)).getCode());
        verify(platformAdminMapper, never()).countPage(anyString());
    }

    // ---------- 分页 ----------

    @Test
    void paginationFallsBackAndIsCapped() {
        when(platformAdminMapper.selectPage(isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.list(null, 0, 0);
        verify(platformAdminMapper).selectPage(isNull(), eq(0), eq(20));

        service.list(null, 2, 500);
        verify(platformAdminMapper).selectPage(isNull(), eq(100), eq(100));
    }

    @Test
    void keywordIsTrimmedBeforeItReachesTheMapper() {
        when(platformAdminMapper.selectPage(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        service.list("  admin  ", 1, 10);

        verify(platformAdminMapper).countPage("admin");
        verify(platformAdminMapper).selectPage(eq("admin"), eq(0), eq(10));
    }

    @Test
    void listCarriesTheTotalBackForThePager() {
        when(platformAdminMapper.countPage(isNull())).thenReturn(37L);
        when(platformAdminMapper.selectPage(isNull(), anyInt(), anyInt())).thenReturn(List.of(admin(5L, "ADMIN", true)));

        PageResult<AdminAccountItem> result = service.list(null, 1, 10);

        assertEquals(37L, result.getTotal());
        assertEquals(1, result.getItems().size());
        assertEquals(5L, result.getItems().get(0).getId());
    }

    // ---------- 创建 ----------

    @Test
    void createStoresAHashedPasswordAndDefaultsToEnabled() {
        when(platformAdminMapper.selectByUserName("ops")).thenReturn(null);
        when(platformAdminMapper.selectById(any())).thenReturn(admin(9L, "ADMIN", true));

        service.create("  ops  ", "s3cret", "admin", null);

        ArgumentCaptor<PlatformAdmin> captor = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(platformAdminMapper).insert(captor.capture());
        PlatformAdmin inserted = captor.getValue();
        assertEquals("ops", inserted.getUserName());
        assertEquals("encoded:s3cret", inserted.getPassword());
        assertEquals("ADMIN", inserted.getRole());
        assertEquals(Boolean.TRUE, inserted.getEnabled());
    }

    @Test
    void createRejectsADuplicateUserName() {
        when(platformAdminMapper.selectByUserName("ops")).thenReturn(admin(3L, "ADMIN", true));

        BizException error = assertThrows(BizException.class, () -> service.create("ops", "s3cret", "ADMIN", true));
        assertEquals(400, error.getCode());
        assertEquals("用户名已存在", error.getMessage());
        verify(platformAdminMapper, never()).insert(any());
    }

    @Test
    void createRejectsBlankUserName() {
        assertEquals(400, assertThrows(BizException.class,
                () -> service.create("   ", "s3cret", "ADMIN", true)).getCode());
        verify(platformAdminMapper, never()).insert(any());
    }

    @Test
    void createRejectsAnUnknownRole() {
        BizException error = assertThrows(BizException.class,
                () -> service.create("ops", "s3cret", "ROOT", true));
        assertEquals(400, error.getCode());
        assertEquals("角色不合法", error.getMessage());
    }

    // ---------- 更新 ----------

    @Test
    void updateWithoutAPasswordLeavesTheStoredOneAlone() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("ops", 2L)).thenReturn(null);
        when(platformAdminMapper.update(any())).thenReturn(1);

        service.update(2L, "ops", "   ", "ADMIN", true);

        ArgumentCaptor<PlatformAdmin> captor = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(platformAdminMapper).update(captor.capture());
        assertNull(captor.getValue().getPassword(), "空密码表示不改密，不能写成空串把原密码冲掉");
    }

    @Test
    void updateRejectsATooShortPassword() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("ops", 2L)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.update(2L, "ops", "12345", "ADMIN", true));
        assertEquals(400, error.getCode());
        assertEquals("密码长度需为6-64个字符", error.getMessage());
        verify(platformAdminMapper, never()).update(any());
    }

    @Test
    void updateRejectsATooLongPassword() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("ops", 2L)).thenReturn(null);

        assertEquals(400, assertThrows(BizException.class,
                () -> service.update(2L, "ops", "x".repeat(65), "ADMIN", true)).getCode());
    }

    /**
     * 查重要排除自己，否则「只改角色不改名」会被自己的记录判成重名。
     */
    @Test
    void updateExcludesItselfFromTheUniquenessCheck() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("ops", 2L)).thenReturn(null);
        when(platformAdminMapper.update(any())).thenReturn(1);

        service.update(2L, "ops", null, "ADMIN", true);

        verify(platformAdminMapper).selectByUserNameExceptId("ops", 2L);
        verify(platformAdminMapper, never()).selectByUserName(anyString());
    }

    @Test
    void updateOnAVanishedRowReportsNotFound() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("ops", 2L)).thenReturn(null);
        when(platformAdminMapper.update(any())).thenReturn(0);

        assertEquals(404, assertThrows(BizException.class,
                () -> service.update(2L, "ops", null, "ADMIN", true)).getCode());
    }

    // ---------- 自锁与超管余量 ----------

    @Test
    void aSuperAdminCannotDemoteThemselves() {
        asSuperAdmin(2L);
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("me", 2L)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.update(2L, "me", null, "ADMIN", true));
        assertEquals(400, error.getCode());
        assertEquals("不能停用当前账号或降低当前账号权限", error.getMessage());
        verify(platformAdminMapper, never()).update(any());
    }

    @Test
    void aSuperAdminCannotDisableThemselves() {
        asSuperAdmin(2L);
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("me", 2L)).thenReturn(null);

        assertEquals(400, assertThrows(BizException.class,
                () -> service.update(2L, "me", null, "SUPER_ADMIN", false)).getCode());
    }

    /**
     * 降级别人时也要看余量：如果被降的是最后一个启用的超管，降完就没人能管账号了。
     */
    @Test
    void demotingTheLastEnabledSuperAdminIsRefused() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("other", 2L)).thenReturn(null);
        when(platformAdminMapper.countEnabledSuperAdmins()).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> service.update(2L, "other", null, "ADMIN", true));
        assertEquals(400, error.getCode());
        assertEquals("至少保留一个启用的超级管理员", error.getMessage());
    }

    @Test
    void demotingASuperAdminIsAllowedWhenAnotherOneRemains() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", true));
        when(platformAdminMapper.selectByUserNameExceptId("other", 2L)).thenReturn(null);
        when(platformAdminMapper.countEnabledSuperAdmins()).thenReturn(2L);
        when(platformAdminMapper.update(any())).thenReturn(1);

        service.update(2L, "other", null, "ADMIN", true);

        verify(platformAdminMapper).update(any());
    }

    @Test
    void deletingYourOwnAccountIsRefused() {
        asSuperAdmin(2L);
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", true));

        BizException error = assertThrows(BizException.class, () -> service.delete(2L));
        assertEquals(400, error.getCode());
        assertEquals("不能删除当前登录账号", error.getMessage());
        verify(platformAdminMapper, never()).deleteById(anyLong());
    }

    @Test
    void deletingTheLastEnabledSuperAdminIsRefused() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", true));
        when(platformAdminMapper.countEnabledSuperAdmins()).thenReturn(1L);

        BizException error = assertThrows(BizException.class, () -> service.delete(2L));
        assertEquals(400, error.getCode());
        assertEquals("至少保留一个启用的超级管理员", error.getMessage());
        verify(platformAdminMapper, never()).deleteById(anyLong());
    }

    /**
     * 被删的是已停用的超管时不占余量，不该被余量闸拦下。
     */
    @Test
    void deletingADisabledSuperAdminIsNotBlockedByTheQuota() {
        when(platformAdminMapper.selectById(2L)).thenReturn(admin(2L, "SUPER_ADMIN", false));
        when(platformAdminMapper.deleteById(2L)).thenReturn(1);

        service.delete(2L);

        verify(platformAdminMapper).deleteById(2L);
        verify(platformAdminMapper, never()).countEnabledSuperAdmins();
    }

    @Test
    void deletingAPlainAdminIsAllowed() {
        when(platformAdminMapper.selectById(3L)).thenReturn(admin(3L, "ADMIN", true));
        when(platformAdminMapper.deleteById(3L)).thenReturn(1);

        service.delete(3L);

        verify(platformAdminMapper).deleteById(3L);
    }

    // ---------- 入参 ----------

    @Test
    void missingOrNonPositiveIdIsRejectedBeforeAnyLookup() {
        assertEquals(400, assertThrows(BizException.class, () -> service.get(null)).getCode());
        assertEquals(400, assertThrows(BizException.class, () -> service.get(0L)).getCode());
        verify(platformAdminMapper, never()).selectById(any());
    }

    @Test
    void unknownIdReportsNotFound() {
        when(platformAdminMapper.selectById(99L)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class, () -> service.get(99L)).getCode());
    }

    private void asSuperAdmin(Long id) {
        PlatformAdminContext.set(id, "SUPER_ADMIN");
    }

    private PlatformAdmin admin(Long id, String role, Boolean enabled) {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(id);
        admin.setUserName("user" + id);
        admin.setRole(role);
        admin.setEnabled(enabled);
        return admin;
    }
}
