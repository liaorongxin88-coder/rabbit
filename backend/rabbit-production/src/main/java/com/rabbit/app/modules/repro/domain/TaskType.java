package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.Locale;

/**
 * {@code work_tasks.task_type}——统一任务中心的任务种类（设计 §4.4）。
 *
 * <p>把繁育六步、商品兔出售、后备兔成熟收敛成同一张表的不同 task_type，是修复
 * 飞书 recvsrq7rGZHdi 的关键：旧模型里这三类提醒分别散落在 batch_rabbits.next_event_*、
 * breeding_cycles.next_event_*、replacement_records.expected_mature_date，
 * 首页要三路合并、还各有各的 ack 语义。
 */
public enum TaskType {
    RECOVERY("休养到期", ReproAction.START_CYCLE),
    ESTRUS("待催情", ReproAction.ESTRUS),
    MATING("待配种", ReproAction.MATING),
    PALPATION("待摸胎", ReproAction.PALPATION),
    PREPARTUM("待备产", ReproAction.PREPARTUM),
    DELIVERY("待分娩", ReproAction.DELIVERY),
    WEANING("待分笼", ReproAction.WEANING),
    /** 商品兔达到出售日龄（sale_days）。 */
    SALE_READY("待出售", null),
    /** 后备兔达到成熟日龄（replacement_days），可转种兔。 */
    REPLACEMENT_MATURE("后备成熟", null),
    /** 幼兔适应期的每日观察。 */
    COMMODITY_ADAPTATION_CARE("幼兔适应观察", null),
    /** 生长期的每日饲喂观察。 */
    COMMODITY_GROWING_CARE("生长饲喂观察", null),
    /** 育肥期的每日饲喂观察。 */
    COMMODITY_FATTENING_CARE("育肥饲喂观察", null),
    CUSTOM("自定义", null);

    private final String label;
    private final ReproAction action;

    TaskType(String label, ReproAction action) {
        this.label = label;
        this.action = action;
    }

    public String label() {
        return label;
    }

    /** 繁育类任务对应的状态机动作；非繁育任务（出售 / 后备成熟）返回 null。 */
    public ReproAction action() {
        return action;
    }

    public boolean isCommodityDailyCare() {
        return this == COMMODITY_ADAPTATION_CARE
            || this == COMMODITY_GROWING_CARE
            || this == COMMODITY_FATTENING_CARE;
    }

    public String commodityDailyCareContent() {
        return switch (this) {
            case COMMODITY_ADAPTATION_CARE -> "观察适应情况，按生长和体况分群。";
            case COMMODITY_GROWING_CARE -> "观察采食、饮水和投料量。";
            case COMMODITY_FATTENING_CARE -> "自由采食，检查料槽是否充足或发霉。";
            default -> null;
        };
    }

    /** 商品兔所处生长阶段的每日观察任务；成熟后只保留待出售。 */
    public static TaskType forCommodityGrowthStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        return switch (stage.trim().toUpperCase(Locale.ROOT)) {
            case "JUVENILE", "ADAPTATION" -> COMMODITY_ADAPTATION_CARE;
            case "GROWING" -> COMMODITY_GROWING_CARE;
            case "FATTENING" -> COMMODITY_FATTENING_CARE;
            default -> null;
        };
    }

    public static boolean isCommodityDailyCare(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return false;
        }
        return COMMODITY_ADAPTATION_CARE.name().equals(taskType)
            || COMMODITY_GROWING_CARE.name().equals(taskType)
            || COMMODITY_FATTENING_CARE.name().equals(taskType);
    }

    /** 该阶段对应的待办类型；暂停与离场不挂待办。 */
    public static TaskType forStage(ReproStage stage) {
        return switch (stage) {
            case READY -> RECOVERY;
            case AWAIT_ESTRUS -> ESTRUS;
            case AWAIT_MATING -> MATING;
            case AWAIT_PALPATION -> PALPATION;
            case AWAIT_PREPARTUM -> PREPARTUM;
            case AWAIT_DELIVERY -> DELIVERY;
            case AWAIT_WEANING -> WEANING;
            case SUSPENDED, RETIRED -> null;
        };
    }

    public static TaskType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, "任务类型不能为空");
        }
        try {
            return TaskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "任务类型不合法: " + value);
        }
    }
}
