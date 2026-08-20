package com.rabbit.app.modules.repro.domain;

/**
 * 下一条待办的到期日锚点（设计 §3.2 转换表最后一列）。
 *
 * <p>把「到期日怎么算」抽成锚点枚举，而不是散落在各操作方法里。生产设置中的
 * 每个时长只对应一段状态转换，避免同一个字段在不同入口被解释成不同含义。
 */
public enum DueAnchor {
    /** 操作日 + estrus_duration_days（原 aphrodisiac_days）。 */
    ESTRUS_DURATION,
    /** 配种日 + palpation_wait_days（原 palpation_days）。 */
    PALPATION_WAIT,
    /** 摸胎确认日 + prepartum_days（待备产时长）。 */
    PREPARTUM_DURATION,
    /** 操作当天产生下一阶段提醒。 */
    SAME_DAY,
    /** 分娩日 + weaning_days。 */
    WEANING_DUE,
    /** 操作日 + postpartum_recovery_days（原 postpartum_days）：子宫复旧后再催情。 */
    POSTPARTUM_RECOVERY,
    /** 立即提醒（空怀后马上催情）。 */
    IMMEDIATE,
    /** 用户在表单里选定的下次提醒时间（推迟、摸胎不确定复查）。 */
    USER_SPECIFIED,
    /** 不产生后续任务（离场）。 */
    NONE
}
