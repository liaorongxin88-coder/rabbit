package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.modules.setting.entity.GlobalSetting;

/**
 * 生产周期配置的语义视图（设计 §4.7）。
 *
 * <p>物理表仍是 {@code global_setting}，列名仍是旧的。本记录把旧列翻译成语义明确的名字，
 * 让状态机代码只依赖新词汇；列改名推迟到 V29+，避免本期为了改名去动一大片 mapper XML。
 *
 * <p>{@code gestation_days} 保留为预产期参考值；提醒链按现场流程使用
 * {@code prepartum_days} 作为「摸胎确认 → 待备产」的等待时长。两者不再共同参与
 * 同一条提醒日期计算。
 */
public record ReproSettings(
    /** 催情 → 配种的等待天数（旧列 aphrodisiac_days）。 */
    int estrusDurationDays,
    /** 配种 → 摸胎的等待天数（旧列 palpation_days）。 */
    int palpationWaitDays,
    /** 妊娠天数，决定预产期 = 配种日 + 本值（V26 新列 gestation_days）。 */
    int gestationDays,
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
    private static final int DEFAULT_GESTATION_DAYS = 30;

    public static ReproSettings from(GlobalSetting setting) {
        if (setting == null) {
            throw new IllegalArgumentException("生产周期配置不能为空");
        }
        return new ReproSettings(
            positive(setting.getAphrodisiacDays(), 2),
            positive(setting.getPalpationDays(), 12),
            positive(setting.getGestationDays(), DEFAULT_GESTATION_DAYS),
            positive(setting.getPrepartumDays(), 3),
            positive(setting.getWeaningDays(), 25),
            positive(setting.getPostpartumDays(), 10),
            positive(setting.getSaleDays(), 30),
            positive(setting.getReplacementDays(), 45)
        );
    }

    /**
     * 存量兔舍的 gestation_days 可能还没被显式配置过（V26 只给了列默认值），
     * 因此这里对 null 与非正数都回落到默认值，避免算出一个早于配种日的预产期。
     */
    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
