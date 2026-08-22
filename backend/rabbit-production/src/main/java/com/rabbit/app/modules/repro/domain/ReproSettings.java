package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.modules.setting.entity.GlobalSetting;

/**
 * 生产周期配置的语义视图（设计 §4.7）。
 *
 * <p>物理表仍是 {@code global_setting}，列名仍是旧的。本记录把仍参与生产流程的列
 * 翻译成语义明确的名字，让状态机代码只依赖有效配置。
 */
public record ReproSettings(
    /** 催情 → 配种的等待天数（旧列 aphrodisiac_days）。 */
    int estrusDurationDays,
    /** 配种 → 摸胎的等待天数（旧列 palpation_days）。 */
    int palpationWaitDays,
    /** 摸胎确认 → 待备产的等待天数（旧列 prepartum_days）。 */
    int prepartumDurationDays,
    /** 分娩 → 分笼的哺乳天数（旧列 weaning_days）。 */
    int weaningDays,
    /** 分笼 / 流产 → 下一轮待催情的子宫复旧天数（旧列 postpartum_days）。 */
    int postpartumRecoveryDays,
    /** 商品兔达到出售日龄的天数（旧列 sale_days）。 */
    int saleDays,
    /** 后备兔成熟天数（旧列 replacement_days）。 */
    int replacementDays
) {
    public static ReproSettings from(GlobalSetting setting) {
        if (setting == null) {
            throw new IllegalArgumentException("生产周期配置不能为空");
        }
        return new ReproSettings(
            positive(setting.getAphrodisiacDays(), 2),
            positive(setting.getPalpationDays(), 12),
            positive(setting.getPrepartumDays(), 15),
            positive(setting.getWeaningDays(), 30),
            positive(setting.getPostpartumDays(), 10),
            setting.commodityMaturityDays(),
            positive(setting.getReplacementDays(), 90)
        );
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
