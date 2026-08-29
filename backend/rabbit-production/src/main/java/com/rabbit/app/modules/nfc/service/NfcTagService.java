package com.rabbit.app.modules.nfc.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.nfc.dto.NfcResolvedTarget;
import com.rabbit.app.modules.nfc.entity.NfcTag;
import com.rabbit.app.modules.nfc.mapper.NfcTagMapper;
import com.rabbit.app.tracking.TrackedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NfcTagService {
    private final HouseService houseService;
    private final NfcTagMapper nfcTagMapper;
    private final CageMapper cageMapper;
    private final RequestDedupService requestDedupService;

    public NfcTagService(HouseService houseService, NfcTagMapper nfcTagMapper, CageMapper cageMapper, RequestDedupService requestDedupService) {
        this.houseService = houseService;
        this.nfcTagMapper = nfcTagMapper;
        this.cageMapper = cageMapper;
        this.requestDedupService = requestDedupService;
    }

    @TrackedOperation(
        code = "nfc:bind", codeExpression = "'nfc:bind:' + #targetType",
        eventType = "NFC_BOUND", targetType = "NFC_TAG", targetId = "#targetId"
    )
    @Transactional
    public NfcTag bind(Long userId, Long houseId, String tagUid, String targetType, Long targetId, Long rabbitId, Long recordId, String remark, String requestId) {
        String api = "nfc:bind:" + (targetType == null ? "" : targetType);
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            NfcTag old = nfcTagMapper.selectByHouseAndUid(houseId, normalizeUid(tagUid));
            if (old != null) {
                return old;
            }
            NfcTag t = new NfcTag();
            t.setHouseId(houseId);
            t.setTagUid(normalizeUid(tagUid));
            t.setTargetType(normalizeType(targetType));
            t.setTargetId(targetId);
            t.setRabbitId(rabbitId);
            t.setRecordId(recordId);
            t.setRequestId(requestId);
            t.setRemark(remark);
            return t;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            houseService.assertHousePermission(userId, houseId, "control");
            String uid = normalizeUid(tagUid);
            String type = normalizeType(targetType);
            if (uid.isEmpty()) {
                throw new BizException(400, "tagUid不能为空");
            }
            if (type.isEmpty()) {
                throw new BizException(400, "targetType不能为空");
            }
            if (!"CAGE".equals(type) && !"FEED".equals(type) && !"TREATMENT".equals(type) && !"SALE".equals(type)) {
                throw new BizException(400, "targetType不支持");
            }
            if ("CAGE".equals(type)) {
                if (targetId == null || targetId <= 0) {
                    throw new BizException(400, "targetId不能为空");
                }
                Cage cage = cageMapper.selectById(houseId, targetId);
                if (cage == null || !houseId.equals(cage.getHouseId())) {
                    throw new BizException(400, "cage不存在");
                }
            }
            if ("TREATMENT".equals(type)) {
                if (rabbitId == null || rabbitId <= 0) {
                    throw new BizException(400, "rabbitId不能为空");
                }
            }
            NfcTag t = new NfcTag();
            t.setHouseId(houseId);
            t.setTagUid(uid);
            t.setTargetType(type);
            t.setTargetId(targetId);
            t.setRabbitId(rabbitId);
            t.setRecordId(recordId);
            t.setRequestId(requestId);
            t.setRemark(remark);
            nfcTagMapper.upsert(t);
            requestDedupService.markDone(houseId, userId, api, requestId);
            return t;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    public NfcResolvedTarget resolve(Long userId, Long houseId, String tagUid) {
        houseService.assertHousePermission(userId, houseId, "view");
        String uid = normalizeUid(tagUid);
        if (uid.isEmpty()) {
            throw new BizException(400, "tagUid不能为空");
        }
        NfcTag t = nfcTagMapper.selectByHouseAndUid(houseId, uid);
        if (t == null) {
            throw new BizException(404, "未找到该NFC标签绑定信息");
        }
        NfcResolvedTarget res = new NfcResolvedTarget();
        res.setTargetType(t.getTargetType());
        res.setTargetId(t.getTargetId());
        res.setRabbitId(t.getRabbitId());
        res.setRecordId(t.getRecordId());
        if ("CAGE".equals(t.getTargetType()) && t.getTargetId() != null) {
            Cage cage = cageMapper.selectById(houseId, t.getTargetId());
            if (cage != null && houseId.equals(cage.getHouseId())) {
                res.setTargetName(cage.getCageNumber());
            }
        } else if ("FEED".equals(t.getTargetType())) {
            res.setTargetName("投喂");
        } else if ("SALE".equals(t.getTargetType())) {
            res.setTargetName("销售");
        } else if ("TREATMENT".equals(t.getTargetType())) {
            res.setTargetName("治疗");
        }
        return res;
    }

    private String normalizeUid(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private String normalizeType(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
