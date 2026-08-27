package com.rabbit.app.modules.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminFarmItem;
import com.rabbit.app.modules.admin.dto.CreateAdminFarmRequest;
import com.rabbit.app.modules.admin.mapper.AdminFarmMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.auth.service.PhoneIdentityService;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.house.service.HouseMemberService;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.PlatformAdminContext;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 平台侧的兔场管理。
 *
 * <p>重点在建场：它是幂等接口，靠 {@code (创建者, requestId)} 去重，而且一次要写三样东西
 * （兔场、所有者成员关系、初始笼位）。三处约束必须钉住：
 *
 * <ul>
 *   <li>同一个 requestId 重放要拿回同一个兔场，而不是建出第二个；
 *   <li>同一个 requestId 换了参数必须报冲突，否则调用方以为改成功了，实际拿回的是旧场；
 *   <li>建完必须确认所有者真的落库，没有所有者的兔场谁也进不去。
 * </ul>
 *
 * <p>{@link PlatformAdminContext} 是 ThreadLocal，用例之间要清干净。
 */
class AdminFarmServiceTest {
    private AdminFarmMapper adminFarmMapper;
    private RabbitHouseMapper rabbitHouseMapper;
    private HouseUserMapper houseUserMapper;
    private SysUserMapper sysUserMapper;
    private CageMapper cageMapper;
    private HouseMemberService houseMemberService;
    private PhoneIdentityService phoneIdentityService;
    private PasswordEncoder passwordEncoder;
    private AccessControlService accessControlService;
    private AdminFarmService service;

    @BeforeEach
    void setUp() {
        adminFarmMapper = mock(AdminFarmMapper.class);
        rabbitHouseMapper = mock(RabbitHouseMapper.class);
        houseUserMapper = mock(HouseUserMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        cageMapper = mock(CageMapper.class);
        houseMemberService = mock(HouseMemberService.class);
        phoneIdentityService = mock(PhoneIdentityService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        accessControlService = mock(AccessControlService.class);
        service = new AdminFarmService(
                adminFarmMapper,
                rabbitHouseMapper,
                houseUserMapper,
                sysUserMapper,
                cageMapper,
                houseMemberService,
                phoneIdentityService,
                passwordEncoder,
                accessControlService
        );
        PlatformAdminContext.set(3L, "SUPER_ADMIN");
    }

    @AfterEach
    void tearDown() {
        PlatformAdminContext.clear();
    }

    // ---------- 权限 ----------

    @Test
    void everyEntryPointIsPermissionChecked() {
        doThrow(new BizException(403, "无权访问"))
                .when(accessControlService).requirePlatformPermission(any(PermissionCode.class));

        assertEquals(403, assertThrows(BizException.class, () -> service.list(null, null, 1, 20)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.overview(1L)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.members(1L)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.update(1L, "n", null)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.addMember(1L, 2L, "MEMBER")).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.updateStatus(1L, "ENABLED")).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.create(request())).getCode());
    }

    // ---------- 入参 ----------

    @Test
    void blankNameIsRejected() {
        CreateAdminFarmRequest request = request();
        request.setName("   ");

        assertEquals(400, assertThrows(BizException.class, () -> service.create(request)).getCode());
    }

    @Test
    void overlongNameIsRejected() {
        CreateAdminFarmRequest request = request();
        request.setName("x".repeat(101));

        assertEquals(400, assertThrows(BizException.class, () -> service.create(request)).getCode());
    }

    @Test
    void nonPositiveDimensionsAreRejected() {
        CreateAdminFarmRequest request = request();
        request.setLayoutRows(0);

        BizException error = assertThrows(BizException.class, () -> service.create(request));
        assertEquals(400, error.getCode());
        assertEquals("排数必须大于0", error.getMessage());
    }

    @Test
    void oversizedDimensionsAreRejected() {
        CreateAdminFarmRequest request = request();
        request.setLayoutCols(101);

        BizException error = assertThrows(BizException.class, () -> service.create(request));
        assertEquals(400, error.getCode());
        assertEquals("列数不能超过100", error.getMessage());
    }

    /**
     * 每一维都在上限内，乘起来仍可能过大。笼位是一次性批量插入的，
     * 少了这道乘积闸，一个请求就能塞进一百万行。
     */
    @Test
    void theCageProductIsCappedEvenWhenEveryDimensionIsLegal() {
        CreateAdminFarmRequest request = request();
        request.setLayoutRows(100);
        request.setLayoutCols(100);
        request.setLayoutLayers(1);

        BizException error = assertThrows(BizException.class, () -> service.create(request));
        assertEquals(400, error.getCode());
        assertEquals("初始笼位数量不能超过2000", error.getMessage());
        verify(cageMapper, never()).insertBatch(any());
    }

    @Test
    void malformedRequestIdIsRejected() {
        CreateAdminFarmRequest request = request();
        request.setRequestId("bad id!");

        assertEquals(400, assertThrows(BizException.class, () -> service.create(request)).getCode());
    }

    @Test
    void missingRequestIdIsRejected() {
        CreateAdminFarmRequest request = request();
        request.setRequestId(null);

        assertEquals(400, assertThrows(BizException.class, () -> service.create(request)).getCode());
    }

    /**
     * 所有者必须二选一。两个都给或都不给，都说明调用方没想清楚，
     * 沉默地挑一个会让「建出来的场归谁」变成实现细节。
     */
    @Test
    void exactlyOneOwnerSelectorIsRequired() {
        CreateAdminFarmRequest neither = request();
        neither.setOwnerUserId(null);
        neither.setOwnerPhone(null);
        BizException noOwner = assertThrows(BizException.class, () -> service.create(neither));
        assertEquals(400, noOwner.getCode());
        assertEquals("必须且只能指定一位初始所有者", noOwner.getMessage());

        CreateAdminFarmRequest both = request();
        both.setOwnerUserId(7L);
        both.setOwnerPhone("13800000000");
        assertEquals(400, assertThrows(BizException.class, () -> service.create(both)).getCode());
    }

    @Test
    void nonPositiveOwnerUserIdIsRejected() {
        CreateAdminFarmRequest request = request();
        request.setOwnerUserId(0L);

        assertEquals(400, assertThrows(BizException.class, () -> service.create(request)).getCode());
    }

    // ---------- 建场 ----------

    @Test
    void creatingAFarmWiresUpTheOwnerAndTheInitialCages() {
        stubFreshCreate();

        service.create(request());

        verify(houseMemberService).joinByAdmin(50L, 7L, "OWNER", "platform:3");
        ArgumentCaptor<List<Cage>> cages = ArgumentCaptor.forClass(List.class);
        verify(cageMapper).insertBatch(cages.capture());
        assertEquals(4, cages.getValue().size(), "2 排 x 2 列 x 1 层 = 4 个笼位");
    }

    @Test
    void theFarmIsCreatedEnabledAndStampedWithTheOperator() {
        stubFreshCreate();

        service.create(request());

        ArgumentCaptor<RabbitHouse> farm = ArgumentCaptor.forClass(RabbitHouse.class);
        verify(rabbitHouseMapper).insert(farm.capture());
        assertEquals("ENABLED", farm.getValue().getStatus());
        assertEquals("platform:3", farm.getValue().getCreateBy());
        assertEquals("req-1", farm.getValue().getRequestId());
    }

    @Test
    void aDisabledOwnerCannotBeAssigned() {
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(null);
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(sysUser("DISABLED"));

        BizException error = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals(409, error.getCode());
        assertEquals("初始所有者账号已停用", error.getMessage());
        verify(rabbitHouseMapper, never()).insert(any());
    }

    @Test
    void anUnknownOwnerIsRejected() {
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(null);
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class, () -> service.create(request())).getCode());
    }

    /**
     * 兔场、成员关系、笼位是分三步写的。若成员关系那步悄悄没生效，就会留下一个
     * 谁也进不去的兔场，所以收尾要复核一次所有者数量。
     */
    @Test
    void aFarmThatEndedUpWithoutAnOwnerFailsLoudly() {
        stubFreshCreate();
        when(houseUserMapper.countEnabledOwners(50L)).thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals(500, error.getCode());
        assertEquals("兔场初始所有者创建失败", error.getMessage());
    }

    // ---------- 幂等 ----------

    @Test
    void replayingTheSameRequestReturnsTheSameFarmWithoutCreatingASecond() {
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(existingFarm());
        when(adminFarmMapper.countOwnerMembershipByUserId(50L, 7L)).thenReturn(1L);
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());

        assertEquals(50L, service.create(request()).getId());

        verify(rabbitHouseMapper, never()).insert(any());
        verify(cageMapper, never()).insertBatch(any());
        verify(houseMemberService, never()).joinByAdmin(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void reusingARequestIdWithDifferentParametersIsAConflict() {
        RabbitHouse existing = existingFarm();
        existing.setName("另一个名字");
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(existing);
        when(adminFarmMapper.countOwnerMembershipByUserId(50L, 7L)).thenReturn(1L);

        BizException error = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals(409, error.getCode());
        assertEquals("requestId已用于其他兔场创建请求", error.getMessage());
    }

    @Test
    void reusingARequestIdForADifferentOwnerIsAConflict() {
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(existingFarm());
        when(adminFarmMapper.countOwnerMembershipByUserId(50L, 7L)).thenReturn(0L);

        assertEquals(409, assertThrows(BizException.class, () -> service.create(request())).getCode());
    }

    @Test
    void aSoftDeletedFarmDoesNotCountAsAnIdempotentHit() {
        RabbitHouse existing = existingFarm();
        existing.setIsDeleted(Boolean.TRUE);
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(existing);
        when(adminFarmMapper.countOwnerMembershipByUserId(50L, 7L)).thenReturn(1L);

        assertEquals(409, assertThrows(BizException.class, () -> service.create(request())).getCode());
    }

    /**
     * 两个并发请求带同一个 requestId 时，落后的那个会撞唯一键。这不是错误，
     * 应当退化成幂等命中，把先建好的那个兔场返回去。
     */
    @Test
    void losingTheInsertRaceDegradesIntoAnIdempotentHit() {
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1"))
                .thenReturn(null, null, existingFarm());
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(sysUser("ENABLED"));
        doThrow(new DuplicateKeyException("uk_house_request")).when(rabbitHouseMapper).insert(any());
        when(adminFarmMapper.countOwnerMembershipByUserId(50L, 7L)).thenReturn(1L);
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());

        assertEquals(50L, service.create(request()).getId());

        verify(cageMapper, never()).insertBatch(any());
    }

    // ---------- 状态与成员 ----------

    @Test
    void unknownFarmStatusIsRejected() {
        when(rabbitHouseMapper.selectByIdForUpdate(50L)).thenReturn(existingFarm());
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());

        BizException error = assertThrows(BizException.class, () -> service.updateStatus(50L, "PAUSED"));
        assertEquals(400, error.getCode());
        assertEquals("兔场状态不合法", error.getMessage());
    }

    /**
     * 启用一个没有可用所有者的兔场等于放出一个无人能管的场，必须先补所有者。
     */
    @Test
    void aFarmWithoutAnOwnerCannotBeEnabled() {
        when(rabbitHouseMapper.selectByIdForUpdate(50L)).thenReturn(existingFarm());
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());
        when(houseUserMapper.countEnabledOwners(50L)).thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.updateStatus(50L, "ENABLED"));
        assertEquals(409, error.getCode());
        assertEquals("兔场没有可用所有者", error.getMessage());
        verify(rabbitHouseMapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    /**
     * 停用方向不需要所有者，否则一个已经没有所有者的问题兔场反而停不掉。
     */
    @Test
    void suspendingDoesNotRequireAnOwner() {
        when(rabbitHouseMapper.selectByIdForUpdate(50L)).thenReturn(existingFarm());
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());
        when(rabbitHouseMapper.updateStatus(50L, "SUSPENDED", "platform:3")).thenReturn(1);

        service.updateStatus(50L, "suspended");

        verify(houseUserMapper, never()).countEnabledOwners(anyLong());
    }

    @Test
    void addingADisabledUserAsAMemberIsRefused() {
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(sysUser("DISABLED"));

        BizException error = assertThrows(BizException.class, () -> service.addMember(50L, 7L, "MEMBER"));
        assertEquals(409, error.getCode());
        assertEquals("用户已停用", error.getMessage());
        verify(houseMemberService, never()).joinByAdmin(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void addingAnUnknownUserAsAMemberIsRefused() {
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class, () -> service.addMember(50L, 7L, "MEMBER")).getCode());
    }

    @Test
    void anUnknownFarmIsReportedAsNotFound() {
        when(rabbitHouseMapper.selectByIdForUpdate(50L)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class, () -> service.update(50L, "n", null)).getCode());
    }

    @Test
    void nonPositiveFarmIdIsRejected() {
        assertEquals(400, assertThrows(BizException.class, () -> service.overview(0L)).getCode());
        assertEquals(400, assertThrows(BizException.class, () -> service.overview(null)).getCode());
    }

    // ---------- 列表 ----------

    @Test
    void paginationFallsBackAndIsCapped() {
        when(adminFarmMapper.selectPage(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.list(null, null, 0, 0);
        verify(adminFarmMapper).selectPage(isNull(), isNull(), eq(0), eq(20));

        service.list(null, null, 2, 500);
        verify(adminFarmMapper).selectPage(isNull(), isNull(), eq(100), eq(100));
    }

    @Test
    void blankStatusFilterIsDroppedRatherThanValidated() {
        when(adminFarmMapper.selectPage(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());

        service.list(null, "   ", 1, 20);

        verify(adminFarmMapper).count(isNull(), isNull());
    }

    @Test
    void unknownStatusFilterIsRejected() {
        assertEquals(400, assertThrows(BizException.class, () -> service.list(null, "PAUSED", 1, 20)).getCode());
    }

    // ---------- 操作者标识 ----------

    /**
     * 没有登录上下文时退化成 "platform"，用于系统内部调用；有上下文时必须带上 id，
     * 否则审计里看不出是哪个管理员建的场。
     */
    @Test
    void theOperatorFallsBackToPlatformWithoutAnAdminContext() {
        PlatformAdminContext.clear();
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform", "req-1")).thenReturn(null);
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(sysUser("ENABLED"));
        when(rabbitHouseMapper.insert(any())).thenAnswer(call -> {
            call.getArgument(0, RabbitHouse.class).setId(50L);
            return 1;
        });
        when(houseUserMapper.countEnabledOwners(50L)).thenReturn(1);
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());

        service.create(request());

        ArgumentCaptor<RabbitHouse> farm = ArgumentCaptor.forClass(RabbitHouse.class);
        verify(rabbitHouseMapper).insert(farm.capture());
        assertEquals("platform", farm.getValue().getCreateBy());
    }

    // ---------- 夹具 ----------

    private void stubFreshCreate() {
        when(rabbitHouseMapper.selectByCreatorAndRequestId("platform:3", "req-1")).thenReturn(null);
        when(sysUserMapper.selectByIdForUpdate(7L)).thenReturn(sysUser("ENABLED"));
        when(rabbitHouseMapper.insert(any())).thenAnswer(call -> {
            call.getArgument(0, RabbitHouse.class).setId(50L);
            return 1;
        });
        when(houseUserMapper.countEnabledOwners(50L)).thenReturn(1);
        when(adminFarmMapper.selectById(50L)).thenReturn(farmItem());
    }

    private CreateAdminFarmRequest request() {
        CreateAdminFarmRequest request = new CreateAdminFarmRequest();
        request.setName("示范兔场");
        request.setLayoutRows(2);
        request.setLayoutCols(2);
        request.setLayoutLayers(1);
        request.setOwnerUserId(7L);
        request.setRequestId("req-1");
        return request;
    }

    private RabbitHouse existingFarm() {
        RabbitHouse farm = new RabbitHouse();
        farm.setId(50L);
        farm.setName("示范兔场");
        farm.setLayoutRows(2);
        farm.setLayoutCols(2);
        farm.setLayoutLayers(1);
        return farm;
    }

    private AdminFarmItem farmItem() {
        AdminFarmItem item = new AdminFarmItem();
        item.setId(50L);
        return item;
    }

    private SysUser sysUser(String status) {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setStatus(status);
        return user;
    }
}
