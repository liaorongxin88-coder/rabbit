package com.rabbit.app.modules.nfc.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.nfc.dto.NfcTagView;
import com.rabbit.app.modules.nfc.mapper.CageNfcTagMapper;
import com.rabbit.app.modules.nfc.mapper.NfcTagMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NfcTagAdminService {
    private final HouseService houseService;
    private final NfcTagMapper nfcTagMapper;
    private final CageNfcTagMapper cageNfcTagMapper;

    public NfcTagAdminService(HouseService houseService, NfcTagMapper nfcTagMapper, CageNfcTagMapper cageNfcTagMapper) {
        this.houseService = houseService;
        this.nfcTagMapper = nfcTagMapper;
        this.cageNfcTagMapper = cageNfcTagMapper;
    }

    public List<NfcTagView> list(Long userId, Long houseId, String tagUid, String targetType, Long targetId, int page, int pageSize) {
        houseService.assertHousePermission(userId, houseId, "control");
        int p = page <= 0 ? 1 : page;
        int ps = pageSize <= 0 ? 50 : pageSize;
        if (ps > 200) {
            ps = 200;
        }
        int offset = (p - 1) * ps;
        return nfcTagMapper.selectViewPage(houseId, normalizeUid(tagUid), normalizeType(targetType), targetId, offset, ps);
    }

    @Transactional
    public void unbind(Long userId, Long houseId, String tagUid) {
        houseService.assertHousePermission(userId, houseId, "control");
        String uid = normalizeUid(tagUid);
        if (uid.isEmpty()) {
            throw new BizException(400, "tagUid不能为空");
        }
        nfcTagMapper.deleteByHouseAndUid(houseId, uid);
        cageNfcTagMapper.deleteByHouseAndUid(houseId, uid);
    }

    private String normalizeUid(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }

    private String normalizeType(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }
}

