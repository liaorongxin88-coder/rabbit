package com.rabbit.app.modules.repro.domain;

/**
 * 下一条待办的到期日锚点（设计 §3.2 转换表最后一列）。
 *
 * <p>把「到期日怎么算」抽成锚点枚举，而不是散落在各操作方法里，是为了让飞书
 * recvsrpXPZd3Xg（备产提前天数逻辑不对）这类问题只有一个修改点。旧实现里预产期
 * 是硬编码的 {@code 配种日 + 30}，而 {@code prepartum_days} 同时被当成「提前量」和
 * 「等待时长」两种含义用，所以怎么改都会顾此失彼。
 *
 * <p>新语义：{@link #PREPARTUM_LEAD} 只表示「预产期前 N 天提醒备产」，预产期本身
 * 由可配置的 {@code gestation_days} 决定（{@link #EXPECTED_BIRTH}）。
 */
public enum DueAnchor {
    /** 操作日 + estrus_duration_days（原 aphrodisiac_days）。 */
    ESTRUS_DURATION,
    /** 配种日 + palpation_wait_days（原 palpation_days）。 */
    PALPATION_WAIT,
    /** 预产期 − prepartum_lead_days。预产期 = 配种日 + gestation_days。 */
    PREPARTUM_LEAD,
    /** 预产期当天。 */
    EXPECTED_BIRTH,
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
