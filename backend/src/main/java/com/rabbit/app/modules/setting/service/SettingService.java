package com.rabbit.app.modules.setting.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.setting.dto.UpdateSettingRequest;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.mapper.GlobalSettingMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingService {
    /** 家兞妊娠期约 30 天；与 V26 的列默认值保持一致。 */
    private static final int DEFAULT_GESTATION_DAYS = 30;

    private final GlobalSettingMapper globalSettingMapper;

    public SettingService(GlobalSettingMapper globalSettingMapper) {
        this.globalSettingMapper = globalSettingMapper;
    }

    @Transactional
    public GlobalSetting getOrCreateUserSetting(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        GlobalSetting existing = globalSettingMapper.selectByUserId(userId);
        if (existing != null) {
            return existing;
        }
        GlobalSetting legacy = globalSettingMapper.selectFirstByUserHouse(userId);
        GlobalSetting setting = legacy == null ? defaultSetting(userId) : copyForUser(userId, legacy);
        try {
            globalSettingMapper.insert(setting);
        } catch (DuplicateKeyException e) {
            GlobalSetting concurrent = globalSettingMapper.selectByUserId(userId);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
        return globalSettingMapper.selectByUserId(userId);
    }

    @Transactional
    public void updateUserSetting(Long userId, UpdateSettingRequest req) {
        GlobalSetting setting = getOrCreateUserSetting(userId);
        applyRequest(setting, req);
        setting.setUpdateBy(String.valueOf(userId));
        int n = globalSettingMapper.updateByUser(setting);
        if (n == 0) {
            throw new BizException(400, "用户生产周期配置未初始化");
        }
    }

    public GlobalSetting getEffectiveSetting(Long userId, Long houseId) {
        if (houseId != null && houseId > 0) {
            GlobalSetting houseSetting = globalSettingMapper.selectByHouseId(houseId);
            if (houseSetting != null) {
                return houseSetting;
            }
        }
        return getOrCreateUserSetting(userId);
    }

    public GlobalSetting getHouseSettingOrDefault(Long userId, Long houseId) {
        requireHouseId(houseId);
        GlobalSetting houseSetting = globalSettingMapper.selectByHouseId(houseId);
        if (houseSetting != null) {
            return houseSetting;
        }
        GlobalSetting defaultSetting = getOrCreateUserSetting(userId);
        GlobalSetting view = copyForHouse(userId, houseId, defaultSetting);
        view.setId(null);
        return view;
    }

    @Transactional
    public void updateHouseSetting(Long userId, Long houseId, UpdateSettingRequest req) {
        requireHouseId(houseId);
        GlobalSetting setting = globalSettingMapper.selectByHouseId(houseId);
        if (setting == null) {
            setting = copyForHouse(userId, houseId, getOrCreateUserSetting(userId));
            applyRequest(setting, req);
            try {
                globalSettingMapper.insert(setting);
                return;
            } catch (DuplicateKeyException e) {
                setting = globalSettingMapper.selectByHouseId(houseId);
                if (setting == null) {
                    throw e;
                }
            }
        }
        applyRequest(setting, req);
        setting.setUpdateBy(String.valueOf(userId));
        int n = globalSettingMapper.updateByHouse(setting);
        if (n == 0) {
            throw new BizException(400, "兔舍生产周期配置未初始化");
        }
    }

    public boolean hasHouseSetting(Long houseId) {
        if (houseId == null || houseId <= 0) {
            return false;
        }
        return globalSettingMapper.selectByHouseId(houseId) != null;
    }

    private void applyRequest(GlobalSetting setting, UpdateSettingRequest req) {
        setting.setAphrodisiacDays(req.getAphrodisiacDays());
        setting.setPalpationDays(req.getPalpationDays());
        // 旧客户端不会提交 gestationDays，此时保留已有值，仅在从未初始化时落默认。
        setting.setGestationDays(valueOrDefault(
            req.getGestationDays() != null ? req.getGestationDays() : setting.getGestationDays(),
            DEFAULT_GESTATION_DAYS
        ));
        setting.setPrepartumDays(req.getPrepartumDays());
        setting.setWeaningDays(req.getWeaningDays());
        setting.setPostpartumDays(req.getPostpartumDays());
        setting.setSaleDays(req.getSaleDays());
        setting.setReplacementDays(req.getReplacementDays());
        setting.setRemark(req.getRemark());
    }

    private GlobalSetting copyForUser(Long userId, GlobalSetting source) {
        GlobalSetting setting = new GlobalSetting();
        setting.setHouseId(null);
        setting.setUserId(userId);
        setting.setAphrodisiacDays(valueOrDefault(source.getAphrodisiacDays(), 2));
        setting.setPalpationDays(valueOrDefault(source.getPalpationDays(), 12));
        setting.setGestationDays(valueOrDefault(source.getGestationDays(), DEFAULT_GESTATION_DAYS));
        setting.setPrepartumDays(valueOrDefault(source.getPrepartumDays(), 3));
        setting.setWeaningDays(valueOrDefault(source.getWeaningDays(), 25));
        setting.setPostpartumDays(valueOrDefault(source.getPostpartumDays(), 10));
        setting.setSaleDays(valueOrDefault(source.getSaleDays(), 30));
        setting.setReplacementDays(valueOrDefault(source.getReplacementDays(), 45));
        setting.setRemark(source.getRemark());
        setting.setCreateBy(String.valueOf(userId));
        setting.setUpdateBy(String.valueOf(userId));
        return setting;
    }

    private GlobalSetting copyForHouse(Long userId, Long houseId, GlobalSetting source) {
        GlobalSetting setting = new GlobalSetting();
        setting.setHouseId(houseId);
        setting.setUserId(null);
        setting.setAphrodisiacDays(valueOrDefault(source.getAphrodisiacDays(), 2));
        setting.setPalpationDays(valueOrDefault(source.getPalpationDays(), 12));
        setting.setGestationDays(valueOrDefault(source.getGestationDays(), DEFAULT_GESTATION_DAYS));
        setting.setPrepartumDays(valueOrDefault(source.getPrepartumDays(), 3));
        setting.setWeaningDays(valueOrDefault(source.getWeaningDays(), 25));
        setting.setPostpartumDays(valueOrDefault(source.getPostpartumDays(), 10));
        setting.setSaleDays(valueOrDefault(source.getSaleDays(), 30));
        setting.setReplacementDays(valueOrDefault(source.getReplacementDays(), 45));
        setting.setRemark(source.getRemark());
        setting.setCreateBy(String.valueOf(userId));
        setting.setUpdateBy(String.valueOf(userId));
        return setting;
    }

    private GlobalSetting defaultSetting(Long userId) {
        GlobalSetting setting = new GlobalSetting();
        setting.setHouseId(null);
        setting.setUserId(userId);
        setting.setAphrodisiacDays(2);
        setting.setPalpationDays(12);
        setting.setGestationDays(DEFAULT_GESTATION_DAYS);
        setting.setPrepartumDays(3);
        setting.setWeaningDays(25);
        setting.setPostpartumDays(10);
        setting.setSaleDays(30);
        setting.setReplacementDays(45);
        setting.setCreateBy(String.valueOf(userId));
        setting.setUpdateBy(String.valueOf(userId));
        return setting;
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private void requireHouseId(Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "缺少X-House-Id");
        }
    }
}
