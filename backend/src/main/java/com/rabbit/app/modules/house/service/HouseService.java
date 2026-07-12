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
import com.rabbit.app.security.HouseContext;
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
    private final SysUserMapper sysUserMapper;
    private final RequestDedupService requestDedupService;

    public HouseService(
            RabbitHouseMapper rabbitHouseMapper,
            HouseUserMapper houseUserMapper,
            CageMapper cageMapper,
            SysUserMapper sysUserMapper,
            RequestDedupService requestDedupService
    ) {
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.houseUserMapper = houseUserMapper;
        this.cageMapper = cageMapper;
        this.sysUserMapper = sysUserMapper;
        this.requestDedupService = requestDedupService;
    }

    public List<RabbitHouse> listMyHouses(Long userId) {
        return rabbitHouseMapper.selectByUserId(userId);
    }

    public RabbitHouse updateHouse(Long userId, Long houseId, String name, String remark) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "houseId不能为空");
        }
        assertHousePermission(userId, houseId, "control");
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
        assertHousePermission(userId, houseId, "control");
        HouseUser hu = houseUserMapper.selectByUserAndHouse(userId, houseId);
        if (hu == null || hu.getIsAdmin() == null || !hu.getIsAdmin()) {
            throw new BizException(403, "仅管理员可删除兔舍");
        }
        int n = rabbitHouseMapper.markDeleted(houseId, String.valueOf(userId));
        if (n <= 0) {
            throw new BizException(404, "兔舍不存在或已删除");
        }
    }

    @Transactional
    public RabbitHouse createHouse(Long userId, String name, int rows, int cols, int layers, String remark, String requestId) {
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

            Long merchantId = resolveMerchantIdForNewHouse(userId);
            RabbitHouse house = new RabbitHouse();
            house.setName(name);
            house.setLayoutRows(rows);
            house.setLayoutCols(cols);
            house.setLayoutLayers(layers);
            house.setRemark(remark);
            house.setRequestId(requestId);
            house.setMerchantId(merchantId);
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
            hu.setPerms("control");
            hu.setIsAdmin(Boolean.TRUE);
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

    private Long resolveMerchantIdForNewHouse(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        if (user.getMerchantId() == null) {
            throw new BizException(500, "用户未归属商户");
        }
        return user.getMerchantId();
    }

    public void assertHousePermission(Long userId, Long houseId, String requiredPerm) {
        HouseContext ctx = HouseContext.get();
        boolean admin = false;
        String p = null;
        if (ctx != null && userId != null && houseId != null && userId.equals(ctx.getUserId()) && houseId.equals(ctx.getHouseId())) {
            admin = ctx.isAdmin();
            p = ctx.getPerms();
        } else {
            HouseUser hu = houseUserMapper.selectByUserAndHouse(userId, houseId);
            if (hu == null) {
                throw new BizException(403, "无兔舍权限");
            }
            admin = hu.getIsAdmin() != null && hu.getIsAdmin();
            p = hu.getPerms();
        }
        if (admin) {
            return;
        }
        if ("view".equals(requiredPerm)) {
            return;
        }
        if ("edit".equals(requiredPerm)) {
            if ("edit".equals(p) || "control".equals(p)) {
                return;
            }
        }
        if ("control".equals(requiredPerm)) {
            if ("control".equals(p)) {
                return;
            }
        }
        throw new BizException(403, "权限不足");
    }

    public HousePermissionInfo getMyHousePermission(Long userId, Long houseId) {
        HouseContext ctx = HouseContext.get();
        if (ctx != null && userId != null && houseId != null && userId.equals(ctx.getUserId()) && houseId.equals(ctx.getHouseId())) {
            HousePermissionInfo info = new HousePermissionInfo();
            info.setPerms(ctx.getPerms());
            info.setIsAdmin(ctx.isAdmin());
            return info;
        }
        HouseUser hu = houseUserMapper.selectByUserAndHouse(userId, houseId);
        if (hu == null) {
            throw new BizException(403, "无兔舍权限");
        }
        HousePermissionInfo info = new HousePermissionInfo();
        info.setPerms(hu.getPerms());
        info.setIsAdmin(hu.getIsAdmin() != null && hu.getIsAdmin());
        return info;
    }
}
