package com.rabbit.app.modules.setting.dto;

import com.rabbit.app.modules.setting.entity.ReminderPreference;
import java.util.Arrays;
import java.util.List;

public record ReminderPreferenceResponse(
    Long id,
    Long houseId,
    Boolean enabled,
    Integer advanceDays,
    Boolean notifyOverdue,
    List<String> taskTypes
) {
    public static ReminderPreferenceResponse from(ReminderPreference preference) {
        List<String> types = Arrays.stream(
                (preference.getTaskTypes() == null ? "ALL" : preference.getTaskTypes()).split(",")
            )
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        return new ReminderPreferenceResponse(
            preference.getId(),
            preference.getHouseId(),
            Boolean.TRUE.equals(preference.getEnabled()),
            preference.getAdvanceDays() == null ? 0 : preference.getAdvanceDays(),
            Boolean.TRUE.equals(preference.getNotifyOverdue()),
            types.isEmpty() ? List.of("ALL") : types
        );
    }
}
