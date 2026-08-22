package com.rabbit.app.modules.setting.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.domain.TaskType;
import com.rabbit.app.modules.setting.dto.ReminderPreferenceRequest;
import com.rabbit.app.modules.setting.entity.ReminderPreference;
import com.rabbit.app.modules.setting.mapper.ReminderPreferenceMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderPreferenceService {
    private final ReminderPreferenceMapper mapper;

    public ReminderPreferenceService(ReminderPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    public ReminderPreference getOrCreate(Long userId, Long houseId) {
        ReminderPreference existing = mapper.selectByUserAndHouse(userId, houseId);
        if (existing != null) {
            return existing;
        }
        ReminderPreference defaults = defaults(userId, houseId);
        try {
            mapper.insert(defaults);
            return defaults;
        } catch (DuplicateKeyException e) {
            ReminderPreference raced = mapper.selectByUserAndHouse(userId, houseId);
            if (raced != null) {
                return raced;
            }
            throw e;
        }
    }

    @Transactional
    public void update(Long userId, Long houseId, ReminderPreferenceRequest request) {
        ReminderPreference preference = getOrCreate(userId, houseId);
        preference.setEnabled(request.getEnabled() == null || request.getEnabled());
        preference.setAdvanceDays(request.getAdvanceDays() == null ? 0 : request.getAdvanceDays());
        preference.setNotifyOverdue(request.getNotifyOverdue() == null || request.getNotifyOverdue());
        preference.setTaskTypes(normalizeTaskTypes(request.getTaskTypes()));
        preference.setUpdateBy(String.valueOf(userId));
        if (mapper.update(preference) == 0) {
            throw new BizException(409, "提醒设置已变化，请刷新后重试");
        }
    }

    private ReminderPreference defaults(Long userId, Long houseId) {
        ReminderPreference preference = new ReminderPreference();
        preference.setUserId(userId);
        preference.setHouseId(houseId);
        preference.setEnabled(true);
        preference.setAdvanceDays(0);
        preference.setNotifyOverdue(true);
        preference.setTaskTypes("ALL");
        preference.setCreateBy(String.valueOf(userId));
        preference.setUpdateBy(String.valueOf(userId));
        return preference;
    }

    private String normalizeTaskTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "ALL";
        }
        List<String> normalized = values.stream()
            .filter(Objects::nonNull)
            .map(value -> value.trim().toUpperCase())
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        if (normalized.contains("ALL")) {
            return "ALL";
        }
        for (String value : normalized) {
            try {
                TaskType.valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new BizException(400, "不支持的提醒类型: " + value);
            }
        }
        return normalized.stream().sorted().collect(Collectors.joining(","));
    }
}
