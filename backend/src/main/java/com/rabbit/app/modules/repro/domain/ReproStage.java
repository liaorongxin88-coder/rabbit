package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.Set;

/**
 * 母兔繁育阶段——全系统唯一词汇表（设计 §3.1）。
 *
 * <p>存储值用英文，展示映射中文。App / admin / 报表都从 {@code /api/repro/vocabulary}
 * 取这里的映射，杜绝旧模型「三处写点、三套词汇」造成的漂移（飞书 recvsrpMlvu2SC）。
 *
 * <p>阶段分两类：
 * <ul>
 *   <li><b>管线段</b>（{@link #isPipeline()}）：待催情 → 待分娩。一只母兔同时只允许一个
 *       管线段 OPEN 的周期，由 {@code breeding_cycles.pipeline_guard} 生成列 + 唯一键兜底。</li>
 *   <li><b>哺乳段</b>（{@link #AWAIT_WEANING}）：不占管线锁，因此支持血配——上一窝还在哺乳时
 *       新周期已能进入管线（设计 §3.3）。</li>
 * </ul>
 */
public enum ReproStage {
    /** 准备：周期间歇态，通常瞬时。子宫复旧完成后由此入下一轮。 */
    READY("准备"),
    AWAIT_ESTRUS("待催情"),
    AWAIT_MATING("待配种"),
    AWAIT_PALPATION("待摸胎"),
    AWAIT_PREPARTUM("待备产"),
    /** 待分娩，业务口语称「待接产」。 */
    AWAIT_DELIVERY("待分娩"),
    /** 待分笼，业务口语称「哺乳中」。 */
    AWAIT_WEANING("待分笼"),
    /** 非管线态：隔离 / 治疗期间暂停推进。 */
    SUSPENDED("暂停"),
    /** 非管线态：死亡 / 淘汰 / 出售离场。 */
    RETIRED("离场");

    /**
     * 管线段：这几个阶段共用一把「一母兔一管线」的锁。刻意不含 AWAIT_WEANING——
     * 哺乳与下一轮妊娠可并行是兔的真实生理能力（双子宫 + 刺激性排卵，设计 §3.4）。
     */
    private static final Set<ReproStage> PIPELINE = Set.of(
        AWAIT_ESTRUS,
        AWAIT_MATING,
        AWAIT_PALPATION,
        AWAIT_PREPARTUM,
        AWAIT_DELIVERY
    );

    private final String label;

    ReproStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否占用「一母兔一管线周期」的互斥锁；须与 V26 pipeline_guard 生成列保持一致。 */
    public boolean isPipeline() {
        return PIPELINE.contains(this);
    }

    /** 是否为可挂待办任务的等待态（T9 推迟只对这些阶段成立）。 */
    public boolean isAwaiting() {
        return name().startsWith("AWAIT_");
    }

    public static ReproStage parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, "繁育阶段不能为空");
        }
        try {
            return ReproStage.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "繁育阶段不合法: " + value);
        }
    }

    /** 存量数据回填用：把旧的中文状态词映射回统一阶段，找不到时返回 null 交由调用方裁决。 */
    public static ReproStage fromLegacyLabel(String legacy) {
        if (legacy == null) {
            return null;
        }
        return switch (legacy.trim()) {
            case "待催情" -> AWAIT_ESTRUS;
            // 「催情中」表示催情已执行、正等待配种，因此落到待配种而不是待催情。
            case "催情中", "待配种" -> AWAIT_MATING;
            case "已配种", "不确定" -> AWAIT_PALPATION;
            case "哺乳中" -> AWAIT_WEANING;
            default -> null;
        };
    }
}
