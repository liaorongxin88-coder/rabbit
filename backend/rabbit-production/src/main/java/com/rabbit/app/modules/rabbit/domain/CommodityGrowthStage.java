package com.rabbit.app.modules.rabbit.domain;

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
