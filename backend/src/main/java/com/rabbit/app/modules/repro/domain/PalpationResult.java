package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.Locale;

/** 摸胎结果（设计 §3.2 T4a/T4b/T4c，§5.2 表单必填字段）。 */
public enum PalpationResult {
    /** T4a 怀孕：进入待备产，提醒日 = 摸胎确认日 + 待备产时长。 */
    PREGNANT("怀孕"),
    /** T4b 空怀：关闭本周期并立即开新周期催情。 */
    EMPTY("空怀"),
    /** T4c 不确定：阶段不变，必须带用户选择的复查日期。 */
    UNSURE("不确定");

    private final String label;

    PalpationResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 解析摸胎结果；<b>缺省返回 null 而不是报错</b>。
     *
     * <p>同 {@code MatingMethod.parse}：只有 PALPATION 动作需要它，必填校验由
     * 状态机的 validateFacts 按动作判定（UNSURE 还额外要求复检日期）。
     * 解析器只负责「给了值就必须合法」。
     */
    public static PalpationResult parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PalpationResult.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "摸胎结果不合法: " + value);
        }
    }

    /** 旧端点 {@code /batches/{id}/pregnancy-check} 传中文，适配层用它映射。 */
    public static PalpationResult fromLegacyLabel(String legacy) {
        if (legacy == null) {
            return null;
        }
        return switch (legacy.trim()) {
            case "怀孕确认", "怀孕" -> PREGNANT;
            case "空怀" -> EMPTY;
            case "不确定" -> UNSURE;
            default -> null;
        };
    }
}
