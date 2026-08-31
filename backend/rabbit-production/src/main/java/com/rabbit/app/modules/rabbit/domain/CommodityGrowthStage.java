package com.rabbit.app.modules.rabbit.domain;

import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.Locale;

/** Canonical growth stages for an individual commodity rabbit. */
public enum CommodityGrowthStage {
    ADAPTATION("适应期"),
    GROWING("生长期"),
    FATTENING("育肥期"),
    MATURE("成熟可售");

    private final String label;

    CommodityGrowthStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public int daysUntilMature(GlobalSetting setting) {
        if (this == MATURE) {
            return 0;
        }
        StageDurations durations = StageDurations.from(setting);
        return switch (this) {
            // 断奶当天属于适应期，成熟日要比三个配置时长之和再晚一天。
            case ADAPTATION -> durations.totalDays() + 1;
            case GROWING -> durations.growingDays() + durations.fatteningDays();
            case FATTENING -> durations.fatteningDays();
            case MATURE -> 0;
        };
    }

    /** 根据断奶日、兔舍周期和业务日期判断商品兔当前阶段。 */
    public static CommodityGrowthStage onDate(
        Date weaningDate,
        Date asOf,
        GlobalSetting setting
    ) {
        if (weaningDate == null || asOf == null) {
            throw new IllegalArgumentException("断奶日期和判断日期不能为空");
        }
        StageDurations durations = StageDurations.from(setting);
        int elapsedDays = Math.max(0, DateUtil.daysBetween(weaningDate, asOf));
        if (elapsedDays <= durations.adaptationDays()) {
            return ADAPTATION;
        }
        if (elapsedDays <= durations.adaptationDays() + durations.growingDays()) {
            return GROWING;
        }
        if (elapsedDays <= durations.totalDays()) {
            return FATTENING;
        }
        return MATURE;
    }

    /** 当前阶段的开始日期，用于后续成熟任务和定时推进。 */
    public Date enteredAt(Date weaningDate, GlobalSetting setting) {
        if (weaningDate == null) {
            throw new IllegalArgumentException("断奶日期不能为空");
        }
        StageDurations durations = StageDurations.from(setting);
        int offsetDays = switch (this) {
            case ADAPTATION -> 0;
            case GROWING -> durations.adaptationDays() + 1;
            case FATTENING -> durations.adaptationDays() + durations.growingDays() + 1;
            case MATURE -> durations.totalDays() + 1;
        };
        return DateUtil.plusDays(weaningDate, offsetDays);
    }

    private record StageDurations(int adaptationDays, int growingDays, int fatteningDays) {
        private static StageDurations from(GlobalSetting setting) {
            if (setting == null) {
                throw new IllegalArgumentException("商品兔阶段配置不能为空");
            }
            return new StageDurations(
                positive(setting.getAdaptationDays(), 3),
                positive(setting.getGrowingDays(), 18),
                positive(setting.getFatteningDays(), 12)
            );
        }

        private int totalDays() {
            return adaptationDays + growingDays + fatteningDays;
        }

        private static int positive(Integer value, int fallback) {
            return value == null || value <= 0 ? fallback : value;
        }
    }

    public static CommodityGrowthStage fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("growth stage is blank");
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if ("JUVENILE".equals(code)) {
            return ADAPTATION;
        }
        return valueOf(code);
    }

    public static CommodityGrowthStage fromCodeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return fromCode(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String normalizeCode(String value) {
        return fromCode(value).name();
    }
}
