package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.Locale;

/**
 * 母兔生产流程的人工操作动作（设计 §3.2 转换表 T1–T11）。
 *
 * <p>刻意不含 T10「取消」：取消只是关闭客户端弹窗，不发请求、不落事件、任务保持 PENDING，
 * 这样「提醒不会因为误点而消失」。把它建成服务端动作反而会引入一个能悄悄改状态的写路径。
 */
public enum ReproAction {
    /** T1 开始周期，可指定任意入轨阶段。 */
    START_CYCLE("开始周期", false),
    /** T2 催情。 */
    ESTRUS("催情", true),
    /** T3 配种。 */
    MATING("配种", true),
    /** T4 摸胎，按 {@link PalpationResult} 分流三种结果。 */
    PALPATION("摸胎", true),
    /** T5 备产。 */
    PREPARTUM("备产", true),
    /** T6 接产，按是否分娩失败分流。 */
    DELIVERY("接产", true),
    /** T7 分笼（断奶）。 */
    WEANING("分笼", true),
    /** T8 流产：直接执行类操作，无「未执行」分支。 */
    ABORTION("流产", false),
    /** T9 推迟：未执行，阶段不变，仅顺延到期时间并累加 snooze_count。 */
    POSTPONE("推迟", false),
    /** T11 离场：死亡 / 淘汰 / 出售，关闭周期并级联取消该母兔全部待办。 */
    RETIRE("离场", false);

    private final String label;
    private final boolean postponable;

    ReproAction(String label, boolean postponable) {
        this.label = label;
        this.postponable = postponable;
    }

    public String label() {
        return label;
    }

    /** 该操作对应的待办是否支持「未执行 → 选下次提醒时间」（设计 §5.2 三态表单）。 */
    public boolean isPostponable() {
        return postponable;
    }

    public static ReproAction parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, "操作类型不能为空");
        }
        try {
            return ReproAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "操作类型不合法: " + value);
        }
    }
}
