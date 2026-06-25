package com.rabbit.app.modules.cage.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.dto.CageCountRow;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CageAdminService {
    private final HouseService houseService;
    private final CageMapper cageMapper;
    private final RabbitMapper rabbitMapper;

    public CageAdminService(HouseService houseService, CageMapper cageMapper, RabbitMapper rabbitMapper) {
        this.houseService = houseService;
        this.cageMapper = cageMapper;
        this.rabbitMapper = rabbitMapper;
    }

    @Transactional
    public Cage create(Long userId, Long houseId, String cageNumber, String remark, Boolean isEnabled) {
        houseService.assertHousePermission(userId, houseId, "control");
        Cage c = new Cage();
        c.setHouseId(houseId);
        c.setCageNumber(cageNumber);
        c.setStatus("0");
        c.setRabbitCount(0);
        c.setIsFed(Boolean.FALSE);
        c.setIsEnabled(isEnabled == null ? Boolean.TRUE : isEnabled);
        c.setRemark(remark);
        c.setCreateBy(String.valueOf(userId));
        c.setUpdateBy(String.valueOf(userId));
        try {
            cageMapper.insert(c);
        } catch (DuplicateKeyException e) {
            throw new BizException(400, "笼位编号已存在");
        }
        return c;
    }

    public Cage update(Long userId, Long houseId, Long id, String cageNumber, String remark, Boolean isEnabled) {
        houseService.assertHousePermission(userId, houseId, "control");
        Cage existing = cageMapper.selectById(houseId, id);
        if (existing == null) {
            throw new BizException(404, "笼位不存在");
        }
        boolean enabled = isEnabled == null ? (existing.getIsEnabled() == null || existing.getIsEnabled()) : isEnabled;
        if (!enabled) {
            int cnt = rabbitMapper.countActiveByCage(houseId, id);
            if (cnt > 0) {
                throw new BizException(400, "笼位仍有在栏兔子，不能停用");
            }
        }
        try {
            cageMapper.updateBasic(houseId, id, cageNumber, remark, enabled, String.valueOf(userId));
        } catch (DuplicateKeyException e) {
            throw new BizException(400, "笼位编号已存在");
        }
        return cageMapper.selectById(houseId, id);
    }

    public void delete(Long userId, Long houseId, Long id) {
        houseService.assertHousePermission(userId, houseId, "control");
        Cage existing = cageMapper.selectById(houseId, id);
        if (existing == null) {
            throw new BizException(404, "笼位不存在");
        }
        int cnt = rabbitMapper.countActiveByCage(houseId, id);
        if (cnt > 0) {
            throw new BizException(400, "笼位仍有在栏兔子，不能删除");
        }
        int n = cageMapper.deleteById(houseId, id);
        if (n <= 0) {
            throw new BizException(404, "笼位不存在");
        }
    }

    public Cage setRabbitCount(Long userId, Long houseId, Long id, int rabbitCount) {
        houseService.assertHousePermission(userId, houseId, "control");
        Cage existing = cageMapper.selectById(houseId, id);
        if (existing == null) {
            throw new BizException(404, "笼位不存在");
        }
        cageMapper.setRabbitCount(houseId, id, rabbitCount, String.valueOf(userId));
        return cageMapper.selectById(houseId, id);
    }

    @Transactional
    public int recountRabbitCount(Long userId, Long houseId) {
        houseService.assertHousePermission(userId, houseId, "control");
        List<CageCountRow> rows = rabbitMapper.selectActiveCountsByCage(houseId);
        Map<Long, Integer> m = new HashMap<Long, Integer>();
        if (rows != null) {
            for (CageCountRow r : rows) {
                if (r != null && r.getCageId() != null) {
                    m.put(r.getCageId(), r.getRabbitCount() == null ? 0 : r.getRabbitCount());
                }
            }
        }
        List<Cage> cages = cageMapper.selectByHouseId(houseId);
        int updated = 0;
        if (cages != null) {
            for (Cage c : cages) {
                if (c == null || c.getId() == null) {
                    continue;
                }
                int expect = m.containsKey(c.getId()) ? m.get(c.getId()) : 0;
                int cur = c.getRabbitCount() == null ? 0 : c.getRabbitCount();
                if (cur != expect) {
                    cageMapper.setRabbitCount(houseId, c.getId(), expect, String.valueOf(userId));
                    updated++;
                }
            }
        }
        return updated;
    }
}
