package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HousePermissionInfo;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.modules.merchant.entity.MerchantHousePolicy;
import com.rabbit.app.modules.merchant.mapper.MerchantHousePolicyMapper;
import com.rabbit.app.modules.merchant.service.MerchantMembershipService;
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
    private final CageMapper cageMapper;
    private final RequestDedupService requestDedupService;
    private final MerchantMembershipService membershipService;
    private final MerchantHousePolicyMapper policyMapper;
    private final AccessControlService accessControlService;

    public HouseService(
            RabbitHouseMapper rabbitHouseMapper,
            HouseUserMapper houseUserMapper,
            CageMapper cageMapper,
            RequestDedupService requestDedupService,
            MerchantMembershipService membershipService,
            MerchantHousePolicyMapper policyMapper,
            AccessControlService accessControlService
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.houseUserMapper = houseUserMapper;
        this.cageMapper = cageMapper;
        this.requestDedupService = requestDedupService;
        this.membershipService = membershipService;
        this.policyMapper = policyMapper;
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
        int n = rabbitHouseMapper.updateBasic(houseId, name, remark, String.valueOf(userId));
        if (n <= 0) {
            throw new BizException(404, "兔舍不存在或已删除");
        }
        return rabbitHouseMapper.selectById(houseId);
    }

    public void deleteHouse(Long userId, Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "houseId不能为空");
        }
        accessControlService.requireHousePermission(userId, houseId, PermissionCode.RABBIT_HOUSES_REMOVE);
        int n = rabbitHouseMapper.markDeleted(houseId, String.valueOf(userId));
        if (n <= 0) {
            throw new BizException(404, "兔舍不存在或已删除");
        }
    }

    @Transactional
    public RabbitHouse createHouse(Long userId, String name, int rows, int cols, int layers, String remark, String requestId) {
        return createHouse(userId, null, name, rows, cols, layers, remark, requestId);
    }

    @Transactional
    public RabbitHouse createHouse(
            Long userId,
            Long requestedMerchantId,
            String name,
            int rows,
            int cols,
            int layers,
            String remark,
            String requestId
    ) {
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

            Long merchantId = membershipService.resolveMerchantId(userId, requestedMerchantId);
            assertCanCreateHouse(userId, merchantId);
            RabbitHouse house = new RabbitHouse();
            house.setName(name);
            house.setLayoutRows(rows);
            house.setLayoutCols(cols);
            house.setLayoutLayers(layers);
            house.setRemark(remark);
            house.setRequestId(requestId);
            house.setMerchantId(merchantId);
            house.setOwnerUserId(userId);
            house.setCreateBy(createBy);
            house.setUpdateBy(createBy);
            try {
                rabbitHouseMapper.insert(house);
            } catch (DuplicateKeyException e) {
                RabbitHouse dup = rabbitHouseMapper.selectByCreatorAndRequestId(createBy, requestId);
                if (dup != null) {
                    requestDedupService.markDone(0L, userId, api, requestId);
                    return dup;
                }
                throw e;
            }

            HouseUser hu = new HouseUser();
            hu.setHouseId(house.getId());
            hu.setUserId(userId);
            hu.setRole(HouseRole.OWNER.code());
            hu.setPerms(HouseRole.OWNER.legacyPermission());
            hu.setIsAdmin(HouseRole.OWNER.administrator());
            hu.setCreateBy(createBy);
            hu.setUpdateBy(createBy);
            houseUserMapper.insert(hu);

            List<Cage> cages = new ArrayList<Cage>();
            for (int r = 1; r <= rows; r++) {
                for (int c = 1; c <= cols; c++) {
                    for (int l = 1; l <= layers; l++) {
                        Cage cage = new Cage();
                        cage.setHouseId(house.getId());
                        cage.setCageNumber(r + "-" + c + "-" + l);
                        cage.setRowCode("R" + r);
                        cage.setPositionIndex(c);
                        cage.setLayerIndex(l);
                        cage.setStatus("0");
                        cage.setRabbitCount(0);
                        cage.setIsFed(Boolean.FALSE);
                        cage.setIsEnabled(Boolean.TRUE);
                        cage.setCreateBy(String.valueOf(userId));
                        cage.setUpdateBy(String.valueOf(userId));
                        cages.add(cage);
                    }
                }
            }
            cageMapper.insertBatch(cages);

            requestDedupService.markDone(0L, userId, api, requestId);
            return house;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(0L, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void assertCanCreateHouse(Long userId, Long merchantId) {
        accessControlService.requireMerchantPermission(userId, merchantId, PermissionCode.MERCHANT_HOUSES_ADD);
        MerchantHousePolicy policy = policyMapper.selectByMerchantId(merchantId);
        if (policy == null || !Boolean.TRUE.equals(policy.getHouseCreationEnabled())) {
            throw new BizException(403, "商户未开通兔场创建权限");
        }
        if (rabbitHouseMapper.countByMerchantId(merchantId) >= policy.getMaxHouseCount()) {
            throw new BizException(409, "已达到商户兔场数量上限");
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
