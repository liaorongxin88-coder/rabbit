package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HousePermissionInfo;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.PermissionCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseService {
    private final RabbitHouseMapper rabbitHouseMapper;
    private final HouseUserMapper houseUserMapper;
    private final SysUserMapper sysUserMapper;
    private final CageMapper cageMapper;
    private final RequestDedupService requestDedupService;
    private final AccessControlService accessControlService;

    public HouseService(
            RabbitHouseMapper rabbitHouseMapper,
            HouseUserMapper houseUserMapper,
            SysUserMapper sysUserMapper,
            CageMapper cageMapper,
            RequestDedupService requestDedupService,
            AccessControlService accessControlService
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.houseUserMapper = houseUserMapper;
        this.sysUserMapper = sysUserMapper;
        this.cageMapper = cageMapper;
        this.requestDedupService = requestDedupService;
        this.accessControlService = accessControlService;
    }

    public List<RabbitHouse> listMyHouses(Long userId) {
        return rabbitHouseMapper.selectByUserId(userId);
    }

    public RabbitHouse updateHouse(Long userId, Long houseId, String name, String remark) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "houseId不能为空");
        }
        accessControlService.requireHousePermission(userId, houseId, PermissionCode.RABBIT_HOUSES_EDIT);
        int updated = rabbitHouseMapper.updateBasic(houseId, name, remark, String.valueOf(userId));
        if (updated <= 0) {
            throw new BizException(404, "兔场不存在或已删除");
        }
        return rabbitHouseMapper.selectById(houseId);
    }

    public void deleteHouse(Long userId, Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "houseId不能为空");
        }
        accessControlService.requireHousePermission(userId, houseId, PermissionCode.RABBIT_HOUSES_REMOVE);
        if (rabbitHouseMapper.markDeleted(houseId, String.valueOf(userId)) <= 0) {
            throw new BizException(404, "兔场不存在或已删除");
        }
    }

    @Transactional
    public RabbitHouse createHouse(
            Long userId,
            String name,
            int rows,
            int cols,
            int layers,
            String remark,
            String requestId
    ) {
        SysUser creator = sysUserMapper.selectByIdForUpdate(userId);
        if (creator == null || !"ENABLED".equals(creator.getStatus())) {
            throw new BizException(403, "账号已停用");
        }
        String api = "house.create";
        String createBy = String.valueOf(userId);
        RabbitHouse existing = rabbitHouseMapper.selectByCreatorAndRequestId(createBy, requestId);
        if (existing != null) {
            return existing;
        }
        if (requestDedupService.shouldSkipAsDone(0L, userId, api, requestId)) {
            RabbitHouse done = rabbitHouseMapper.selectByCreatorAndRequestId(createBy, requestId);
            if (done == null) {
                throw new BizException(500, "幂等回查失败");
            }
            return done;
        }
        requestDedupService.markProcessing(0L, userId, api, requestId);
        try {
            RabbitHouse done = rabbitHouseMapper.selectByCreatorAndRequestId(createBy, requestId);
            if (done != null) {
                requestDedupService.markDone(0L, userId, api, requestId);
                return done;
            }
            if (rows <= 0 || cols <= 0 || layers <= 0) {
                throw new BizException(400, "排数/列数/层数必须大于0");
            }

            RabbitHouse house = new RabbitHouse();
            house.setName(name);
            house.setStatus("ENABLED");
            house.setLayoutRows(rows);
            house.setLayoutCols(cols);
            house.setLayoutLayers(layers);
            house.setRemark(remark);
            house.setRequestId(requestId);
            house.setCreateBy(createBy);
            house.setUpdateBy(createBy);
            try {
                rabbitHouseMapper.insert(house);
            } catch (DuplicateKeyException duplicate) {
                RabbitHouse duplicateHouse = rabbitHouseMapper.selectByCreatorAndRequestId(createBy, requestId);
                if (duplicateHouse != null) {
                    requestDedupService.markDone(0L, userId, api, requestId);
                    return duplicateHouse;
                }
                throw duplicate;
            }

            HouseUser owner = new HouseUser();
            owner.setHouseId(house.getId());
            owner.setUserId(userId);
            owner.setRole(HouseRole.OWNER.code());
            owner.setStatus("ENABLED");
            owner.setPerms(HouseRole.OWNER.legacyPermission());
            owner.setIsAdmin(HouseRole.OWNER.administrator());
            owner.setCreateBy(createBy);
            owner.setUpdateBy(createBy);
            houseUserMapper.insert(owner);

            List<Cage> cages = new ArrayList<Cage>();
            for (int row = 1; row <= rows; row++) {
                for (int column = 1; column <= cols; column++) {
                    for (int layer = 1; layer <= layers; layer++) {
                        Cage cage = new Cage();
                        cage.setHouseId(house.getId());
                        cage.setCageNumber(row + "-" + column + "-" + layer);
                        cage.setRowCode("R" + row);
                        cage.setPositionIndex(column);
                        cage.setLayerIndex(layer);
                        cage.setStatus("0");
                        cage.setRabbitCount(0);
                        cage.setIsFed(Boolean.FALSE);
                        cage.setIsEnabled(Boolean.TRUE);
                        cage.setCreateBy(createBy);
                        cage.setUpdateBy(createBy);
                        cages.add(cage);
                    }
                }
            }
            cageMapper.insertBatch(cages);
            requestDedupService.markDone(0L, userId, api, requestId);
            return house;
        } catch (RuntimeException exception) {
            requestDedupService.markFailed(0L, userId, api, requestId, exception.getMessage());
            throw exception;
        }
    }

    public void assertHousePermission(Long userId, Long houseId, String requiredPerm) {
        accessControlService.requireHouseLevel(userId, houseId, requiredPerm);
    }

    public void assertHouseAdmin(Long userId, Long houseId) {
        accessControlService.requireHouseOwner(userId, houseId);
    }

    public HousePermissionInfo getMyHousePermission(Long userId, Long houseId) {
        AccessControlService.HouseAccess access = accessControlService.requireHousePermission(
                userId,
                houseId,
                PermissionCode.RABBIT_HOUSES_QUERY
        );
        HouseRole role = access.role();
        HousePermissionInfo info = new HousePermissionInfo();
        info.setPerms(role.legacyPermission());
        info.setRole(role.code());
        info.setIsAdmin(role.administrator());
        info.setPermissions(access.permissions());
        return info;
    }
}
