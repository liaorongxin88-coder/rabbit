package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminFarmItem;
import com.rabbit.app.modules.admin.dto.AdminFarmOverview;
import com.rabbit.app.modules.admin.dto.CreateAdminFarmRequest;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.mapper.AdminFarmMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.auth.service.PhoneIdentityService;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.house.service.HouseMemberService;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.PlatformAdminContext;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminFarmService {
    private static final Set<String> FARM_STATUSES = Set.of("ENABLED", "SUSPENDED", "ORPHANED");
    private static final int MAX_INITIAL_CAGES = 2_000;
    private static final int MAX_LAYOUT_DIMENSION = 100;

    private final AdminFarmMapper adminFarmMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final HouseUserMapper houseUserMapper;
    private final SysUserMapper sysUserMapper;
    private final CageMapper cageMapper;
    private final HouseMemberService houseMemberService;
    private final PhoneIdentityService phoneIdentityService;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;

    public AdminFarmService(
            AdminFarmMapper adminFarmMapper,
            RabbitHouseMapper rabbitHouseMapper,
            HouseUserMapper houseUserMapper,
            SysUserMapper sysUserMapper,
            CageMapper cageMapper,
            HouseMemberService houseMemberService,
            PhoneIdentityService phoneIdentityService,
            PasswordEncoder passwordEncoder,
            AccessControlService accessControlService
    ) {
        this.adminFarmMapper = adminFarmMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.houseUserMapper = houseUserMapper;
        this.sysUserMapper = sysUserMapper;
        this.cageMapper = cageMapper;
        this.houseMemberService = houseMemberService;
        this.phoneIdentityService = phoneIdentityService;
        this.passwordEncoder = passwordEncoder;
        this.accessControlService = accessControlService;
    }

    public PageResult<AdminFarmItem> list(
            String keyword,
            String status,
            Integer pageNum,
            Integer pageSize
    ) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_LIST);
        int page = normalizePage(pageNum);
        int size = normalizePageSize(pageSize);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = status == null || status.isBlank() ? null : normalizeFarmStatus(status);
        long total = adminFarmMapper.count(normalizedKeyword, normalizedStatus);
        List<AdminFarmItem> items = adminFarmMapper.selectPage(
                normalizedKeyword,
                normalizedStatus,
                (page - 1) * size,
                size
        );
        return new PageResult<AdminFarmItem>(items, total, page, size);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminFarmItem create(CreateAdminFarmRequest request) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_ADD);
        String name = normalizeName(request.getName());
        int rows = requirePositiveDimension(request.getLayoutRows(), "排数");
        int cols = requirePositiveDimension(request.getLayoutCols(), "列数");
        int layers = requirePositiveDimension(request.getLayoutLayers(), "层数");
        long cageCount = (long) rows * cols * layers;
        if (cageCount > MAX_INITIAL_CAGES) {
            throw new BizException(400, "初始笼位数量不能超过" + MAX_INITIAL_CAGES);
        }
        String remark = trimToNull(request.getRemark());
        String requestId = normalizeRequestId(request.getRequestId());
        OwnerSelector ownerSelector = normalizeOwnerSelector(request.getOwnerUserId(), request.getOwnerPhone());
        String operator = platformOperator();

        RabbitHouse existing = rabbitHouseMapper.selectByCreatorAndRequestId(operator, requestId);
        if (existing != null) {
            return verifyIdempotentCreate(existing, name, rows, cols, layers, remark, ownerSelector);
        }

        SysUser owner = resolveInitialOwner(ownerSelector);
        existing = rabbitHouseMapper.selectByCreatorAndRequestId(operator, requestId);
        if (existing != null) {
            return verifyIdempotentCreate(existing, name, rows, cols, layers, remark, ownerSelector);
        }

        RabbitHouse farm = new RabbitHouse();
        farm.setName(name);
        farm.setStatus("ENABLED");
        farm.setLayoutRows(rows);
        farm.setLayoutCols(cols);
        farm.setLayoutLayers(layers);
        farm.setRemark(remark);
        farm.setRequestId(requestId);
        farm.setCreateBy(operator);
        farm.setUpdateBy(operator);
        try {
            rabbitHouseMapper.insert(farm);
        } catch (DuplicateKeyException duplicate) {
            RabbitHouse duplicateFarm = rabbitHouseMapper.selectByCreatorAndRequestId(operator, requestId);
            if (duplicateFarm != null) {
                return verifyIdempotentCreate(
                        duplicateFarm,
                        name,
                        rows,
                        cols,
                        layers,
                        remark,
                        ownerSelector
                );
            }
            throw duplicate;
        }

        houseMemberService.joinByAdmin(farm.getId(), owner.getUserId(), "OWNER", operator);
        cageMapper.insertBatch(initialCages(farm.getId(), rows, cols, layers, operator));
        if (houseUserMapper.countEnabledOwners(farm.getId()) <= 0) {
            throw new BizException(500, "兔场初始所有者创建失败");
        }
        return requireFarm(farm.getId());
    }

    public AdminFarmOverview overview(Long houseId) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_QUERY);
        AdminFarmItem farm = requireFarm(houseId);
        AdminFarmOverview overview = new AdminFarmOverview();
        overview.setFarm(farm);
        overview.setMemberCount(farm.getMemberCount());
        overview.setCageCount(farm.getCageCount());
        overview.setRabbitCount(farm.getRabbitCount());
        overview.setBatchCount(adminFarmMapper.countBatches(houseId));
        overview.setMembers(houseUserMapper.selectMembersByHouse(houseId));
        overview.setRecentAuditLogs(adminFarmMapper.selectRecentAuditLogs(houseId, 10));
        return overview;
    }

    public List<HouseMemberItem> members(Long houseId) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_QUERY);
        requireFarm(houseId);
        return houseUserMapper.selectMembersByHouse(houseId);
    }

    @Transactional
    public AdminFarmItem update(Long houseId, String name, String remark) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_EDIT);
        lockFarm(houseId);
        if (rabbitHouseMapper.updateBasic(
                houseId,
                normalizeName(name),
                trimToNull(remark),
                platformOperator()
        ) <= 0) {
            throw new BizException(404, "兔场不存在");
        }
        return requireFarm(houseId);
    }

    @Transactional
    public List<HouseMemberItem> addMember(Long houseId, Long userId, String role) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_EDIT);
        SysUser user = sysUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!"ENABLED".equals(user.getStatus())) {
            throw new BizException(409, "用户已停用");
        }
        lockFarm(houseId);
        houseMemberService.joinByAdmin(houseId, userId, role, platformOperator());
        return houseUserMapper.selectMembersByHouse(houseId);
    }

    @Transactional
    public AdminFarmItem updateStatus(Long houseId, String status) {
        accessControlService.requirePlatformPermission(PermissionCode.PLATFORM_FARMS_EDIT);
        lockFarm(houseId);
        String normalizedStatus = normalizeFarmStatus(status);
        if ("ENABLED".equals(normalizedStatus) && houseUserMapper.countEnabledOwners(houseId) <= 0) {
            throw new BizException(409, "兔场没有可用所有者");
        }
        if (rabbitHouseMapper.updateStatus(houseId, normalizedStatus, platformOperator()) <= 0) {
            throw new BizException(404, "兔场不存在");
        }
        return requireFarm(houseId);
    }

    private AdminFarmItem requireFarm(Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "farmId不合法");
        }
        AdminFarmItem farm = adminFarmMapper.selectById(houseId);
        if (farm == null) {
            throw new BizException(404, "兔场不存在");
        }
        return farm;
    }

    private void lockFarm(Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "farmId不合法");
        }
        if (rabbitHouseMapper.selectByIdForUpdate(houseId) == null) {
            throw new BizException(404, "兔场不存在");
        }
        requireFarm(houseId);
    }

    private OwnerSelector normalizeOwnerSelector(Long ownerUserId, String ownerPhone) {
        String phone = trimToNull(ownerPhone);
        if ((ownerUserId == null) == (phone == null)) {
            throw new BizException(400, "必须且只能指定一位初始所有者");
        }
        if (ownerUserId != null) {
            if (ownerUserId <= 0) {
                throw new BizException(400, "ownerUserId不合法");
            }
            return new OwnerSelector(ownerUserId, null, null);
        }
        String normalizedPhone = PhoneNumbers.normalizeMainlandMobile(phone);
        return new OwnerSelector(null, normalizedPhone, phoneIdentityService.hash(normalizedPhone));
    }

    private SysUser resolveInitialOwner(OwnerSelector selector) {
        SysUser owner;
        if (selector.userId() != null) {
            owner = sysUserMapper.selectByIdForUpdate(selector.userId());
            if (owner == null) {
                throw new BizException(404, "初始所有者不存在");
            }
        } else {
            owner = sysUserMapper.selectByPhoneHashForUpdate(selector.phoneHash());
            if (owner == null) {
                owner = provisionPhoneIdentity(selector.phone(), selector.phoneHash());
            }
        }
        if (!"ENABLED".equals(owner.getStatus())) {
            throw new BizException(409, "初始所有者账号已停用");
        }
        return owner;
    }

    private SysUser provisionPhoneIdentity(String phone, String phoneHash) {
        SysUser created = new SysUser();
        created.setUserName("mobile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        created.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        created.setPasswordInitialized(Boolean.FALSE);
        created.setStatus("ENABLED");
        created.setPhoneCountryCode("+86");
        created.setPhoneHash(phoneHash);
        created.setPhoneMasked(phoneIdentityService.mask(phone));
        created.setPhoneBoundTime(new Date());
        try {
            sysUserMapper.insert(created);
            return created;
        } catch (DuplicateKeyException duplicate) {
            SysUser existing = sysUserMapper.selectByPhoneHashForUpdate(phoneHash);
            if (existing != null) {
                return existing;
            }
            throw duplicate;
        }
    }

    private AdminFarmItem verifyIdempotentCreate(
            RabbitHouse existing,
            String name,
            int rows,
            int cols,
            int layers,
            String remark,
            OwnerSelector ownerSelector
    ) {
        boolean sameFarm = !Boolean.TRUE.equals(existing.getIsDeleted())
                && Objects.equals(existing.getName(), name)
                && Objects.equals(existing.getLayoutRows(), rows)
                && Objects.equals(existing.getLayoutCols(), cols)
                && Objects.equals(existing.getLayoutLayers(), layers)
                && Objects.equals(trimToNull(existing.getRemark()), remark);
        boolean sameOwner = ownerSelector.userId() != null
                ? adminFarmMapper.countOwnerMembershipByUserId(existing.getId(), ownerSelector.userId()) > 0
                : adminFarmMapper.countOwnerMembershipByPhoneHash(existing.getId(), ownerSelector.phoneHash()) > 0;
        if (!sameFarm || !sameOwner) {
            throw new BizException(409, "requestId已用于其他兔场创建请求");
        }
        return requireFarm(existing.getId());
    }

    private List<Cage> initialCages(Long houseId, int rows, int cols, int layers, String operator) {
        List<Cage> cages = new ArrayList<Cage>(rows * cols * layers);
        for (int row = 1; row <= rows; row++) {
            for (int column = 1; column <= cols; column++) {
                for (int layer = 1; layer <= layers; layer++) {
                    Cage cage = new Cage();
                    cage.setHouseId(houseId);
                    cage.setCageNumber(row + "-" + column + "-" + layer);
                    cage.setRowCode("R" + row);
                    cage.setPositionIndex(column);
                    cage.setLayerIndex(layer);
                    cage.setStatus("0");
                    cage.setRabbitCount(0);
                    cage.setIsFed(Boolean.FALSE);
                    cage.setIsEnabled(Boolean.TRUE);
                    cage.setCreateBy(operator);
                    cage.setUpdateBy(operator);
                    cages.add(cage);
                }
            }
        }
        return cages;
    }

    private String normalizeName(String name) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            throw new BizException(400, "兔场名称不能为空");
        }
        if (normalized.length() > 100) {
            throw new BizException(400, "兔场名称不能超过100个字符");
        }
        return normalized;
    }

    private int requirePositiveDimension(Integer value, String label) {
        if (value == null || value <= 0) {
            throw new BizException(400, label + "必须大于0");
        }
        if (value > MAX_LAYOUT_DIMENSION) {
            throw new BizException(400, label + "不能超过" + MAX_LAYOUT_DIMENSION);
        }
        return value;
    }

    private String normalizeRequestId(String requestId) {
        String normalized = trimToNull(requestId);
        if (normalized == null || normalized.length() > 64
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BizException(400, "requestId不合法");
        }
        return normalized;
    }

    private String normalizeFarmStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!FARM_STATUSES.contains(normalized)) {
            throw new BizException(400, "兔场状态不合法");
        }
        return normalized;
    }

    private String platformOperator() {
        Long adminId = PlatformAdminContext.getAdminId();
        return adminId == null ? "platform" : "platform:" + adminId;
    }

    private int normalizePage(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    private int normalizePageSize(Integer size) {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record OwnerSelector(Long userId, String phone, String phoneHash) {
    }
}
