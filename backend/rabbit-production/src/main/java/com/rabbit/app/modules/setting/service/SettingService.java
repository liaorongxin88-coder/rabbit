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
    private static final int DEFAULT_PREPARTUM_DAYS = 15;
    private static final int DEFAULT_WEANING_DAYS = 30;
    private static final int DEFAULT_REPLACEMENT_DAYS = 90;
    private static final int DEFAULT_ADAPTATION_DAYS = 3;
    private static final int DEFAULT_GROWING_DAYS = 18;
    private static final int DEFAULT_FATTENING_DAYS = 12;

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

    @Transactional
    public GlobalSetting getEffectiveSetting(Long userId, Long houseId) {
        if (houseId != null && houseId > 0) {
            return getOrCreateHouseSetting(userId, houseId);
        }
        return getOrCreateUserSetting(userId);
    }

    @Transactional
    public GlobalSetting getHouseSettingOrDefault(Long userId, Long houseId) {
        return getOrCreateHouseSetting(userId, houseId);
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

    /**
     * 在建兔场事务内复制一份用户默认配置，之后该兔场与用户默认配置完全隔离。
     * 已存在的历史配置不覆盖，便于幂等重试和存量数据兼容。
     */
    @Transactional
    public void initializeHouseSetting(Long userId, Long houseId) {
        getOrCreateHouseSetting(userId, houseId);
    }

    private GlobalSetting getOrCreateHouseSetting(Long userId, Long houseId) {
        requireHouseId(houseId);
        GlobalSetting existing = globalSettingMapper.selectByHouseId(houseId);
        if (existing != null) {
            return existing;
        }
        GlobalSetting snapshot = copyForHouse(userId, houseId, getOrCreateUserSetting(userId));
        try {
            globalSettingMapper.insert(snapshot);
        } catch (DuplicateKeyException e) {
            GlobalSetting concurrent = globalSettingMapper.selectByHouseId(houseId);
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
        return globalSettingMapper.selectByHouseId(houseId);
    }


    private void applyRequest(GlobalSetting setting, UpdateSettingRequest req) {
        setting.setAphrodisiacDays(req.getAphrodisiacDays());
        setting.setPalpationDays(req.getPalpationDays());
        setting.setPrepartumDays(req.getPrepartumDays());
        setting.setWeaningDays(req.getWeaningDays());
        setting.setPostpartumDays(req.getPostpartumDays());
        boolean hasStageDurations = req.getAdaptationDays() != null
            || req.getGrowingDays() != null
            || req.getFatteningDays() != null;
        setting.setAdaptationDays(valueOrDefault(
            req.getAdaptationDays() != null ? req.getAdaptationDays() : setting.getAdaptationDays(),
            DEFAULT_ADAPTATION_DAYS
        ));
        setting.setGrowingDays(valueOrDefault(
            req.getGrowingDays() != null ? req.getGrowingDays() : setting.getGrowingDays(),
            DEFAULT_GROWING_DAYS
        ));
        setting.setFatteningDays(valueOrDefault(
            req.getFatteningDays() != null ? req.getFatteningDays() : setting.getFatteningDays(),
            DEFAULT_FATTENING_DAYS
        ));
        setting.setSaleDays(hasStageDurations
            ? setting.commodityMaturityDays()
            : req.getSaleDays());
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
        setting.setPrepartumDays(valueOrDefault(source.getPrepartumDays(), DEFAULT_PREPARTUM_DAYS));
        setting.setWeaningDays(valueOrDefault(source.getWeaningDays(), DEFAULT_WEANING_DAYS));
        setting.setPostpartumDays(valueOrDefault(source.getPostpartumDays(), 10));
        copyCommoditySettings(setting, source);
        setting.setReplacementDays(valueOrDefault(source.getReplacementDays(), DEFAULT_REPLACEMENT_DAYS));
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
        setting.setPrepartumDays(valueOrDefault(source.getPrepartumDays(), DEFAULT_PREPARTUM_DAYS));
        setting.setWeaningDays(valueOrDefault(source.getWeaningDays(), DEFAULT_WEANING_DAYS));
        setting.setPostpartumDays(valueOrDefault(source.getPostpartumDays(), 10));
        copyCommoditySettings(setting, source);
        setting.setReplacementDays(valueOrDefault(source.getReplacementDays(), DEFAULT_REPLACEMENT_DAYS));
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
        setting.setPrepartumDays(DEFAULT_PREPARTUM_DAYS);
        setting.setWeaningDays(DEFAULT_WEANING_DAYS);
        setting.setPostpartumDays(10);
        setting.setAdaptationDays(DEFAULT_ADAPTATION_DAYS);
        setting.setGrowingDays(DEFAULT_GROWING_DAYS);
        setting.setFatteningDays(DEFAULT_FATTENING_DAYS);
        setting.setSaleDays(setting.commodityMaturityDays());
        setting.setReplacementDays(DEFAULT_REPLACEMENT_DAYS);
        setting.setCreateBy(String.valueOf(userId));
        setting.setUpdateBy(String.valueOf(userId));
        return setting;
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private void copyCommoditySettings(GlobalSetting target, GlobalSetting source) {
        target.setAdaptationDays(valueOrDefault(source.getAdaptationDays(), DEFAULT_ADAPTATION_DAYS));
        target.setGrowingDays(valueOrDefault(source.getGrowingDays(), DEFAULT_GROWING_DAYS));
        target.setFatteningDays(valueOrDefault(source.getFatteningDays(), DEFAULT_FATTENING_DAYS));
        // sale_days 仍作为旧客户端的响应镜像保留；新写路径只读取三个阶段之和。
        target.setSaleDays(valueOrDefault(source.getSaleDays(), target.commodityMaturityDays()));
    }

    private void requireHouseId(Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "缺少X-House-Id");
        }
    }
}
