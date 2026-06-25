package com.rabbit.app.modules.nfc.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.nfc.entity.CageNfcTag;
import com.rabbit.app.modules.nfc.mapper.CageNfcTagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CageNfcTagService {
    private final HouseService houseService;
    private final CageMapper cageMapper;
    private final CageNfcTagMapper cageNfcTagMapper;
    private final RequestDedupService requestDedupService;

    public CageNfcTagService(HouseService houseService, CageMapper cageMapper, CageNfcTagMapper cageNfcTagMapper, RequestDedupService requestDedupService) {
        this.houseService = houseService;
        this.cageMapper = cageMapper;
        this.cageNfcTagMapper = cageNfcTagMapper;
        this.requestDedupService = requestDedupService;
    }

    @Transactional
    public CageNfcTag bind(Long userId, Long houseId, Long cageId, String tagUid, String remark, String requestId) {
        String api = "cage:nfc:bind";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            CageNfcTag old = cageNfcTagMapper.selectByHouseAndUid(houseId, tagUid);
            if (old != null) {
                return old;
            }
            CageNfcTag t = new CageNfcTag();
            t.setHouseId(houseId);
            t.setCageId(cageId);
            t.setTagUid(tagUid);
            t.setRequestId(requestId);
            t.setRemark(remark);
            t.setCreateBy(String.valueOf(userId));
            t.setUpdateBy(String.valueOf(userId));
            return t;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            houseService.assertHousePermission(userId, houseId, "control");
            if (cageId == null || cageId <= 0) {
                throw new BizException(400, "cageId不能为空");
            }
            if (tagUid == null || tagUid.trim().isEmpty()) {
                throw new BizException(400, "tagUid不能为空");
            }
            String uid = tagUid.trim().toUpperCase();
            Cage cage = cageMapper.selectById(houseId, cageId);
            if (cage == null || !houseId.equals(cage.getHouseId())) {
                throw new BizException(400, "cage不存在");
            }
            CageNfcTag t = new CageNfcTag();
            t.setHouseId(houseId);
            t.setCageId(cageId);
            t.setTagUid(uid);
            t.setRequestId(requestId);
            t.setRemark(remark);
            t.setCreateBy(String.valueOf(userId));
            t.setUpdateBy(String.valueOf(userId));
            cageNfcTagMapper.upsert(t);
            requestDedupService.markDone(houseId, userId, api, requestId);
            return t;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    public Cage resolveCage(Long userId, Long houseId, String tagUid) {
        houseService.assertHousePermission(userId, houseId, "view");
        if (tagUid == null || tagUid.trim().isEmpty()) {
            throw new BizException(400, "tagUid不能为空");
        }
        Cage cage = cageNfcTagMapper.selectCageByHouseAndUid(houseId, tagUid.trim().toUpperCase());
        if (cage == null) {
            throw new BizException(404, "未找到该NFC标签绑定的笼位");
        }
        if (Boolean.FALSE.equals(cage.getIsEnabled())) {
            throw new BizException(410, "笼位已停用");
        }
        return cage;
    }
}
