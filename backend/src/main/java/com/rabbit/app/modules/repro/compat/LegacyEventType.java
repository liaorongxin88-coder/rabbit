package com.rabbit.app.modules.repro.compat;

import com.rabbit.app.modules.repro.domain.TaskType;

/**
 * 把 V2 的 {@link TaskType} 翻译回 {@code breeding_cycles.next_event_type} 的旧中文词汇。
 *
 * <p><b>与 {@link LegacyCycleStatus} 同属兼容补丁，V28 随旧列一并删除。</b>
 *
 * <p>为什么不能直接用 {@code TaskType.label()}：两套词汇长得像但并不相等。
 * label() 是新 UI 的措辞（「待摸胎」「待分笼」），旧列存的是动作名（「摸胎」「断奶」）。
 * 逐项都不同——待摸胎≠摸胎、待备产≠备产、待分娩≠分娩、待分笼≠断奶。
 *
 * <p>这不只是显示问题，旧代码有<b>按字符串分支的业务逻辑</b>：
 * <ul>
 *   <li>{@code BatchService:1070} — {@code if (!"备产".equals(cycle.getNextEventType()))}
 *       决定备产完成是否放行；写成「待备产」会让这个判断永远为假。</li>
 *   <li>{@code OutboundEligibilityService:98} — 按 {@code contains("出售")} 判定出栏资格。</li>
 *   <li>{@code BatchController:258} — 值原样进 EventItem 返给没有 OTA 的老 APK。</li>
 * </ul>
 *
 * <p>所以 P4 把旧写路径改成适配器之前，这层翻译必须先到位，否则一上线
 * 提醒列表与备产流程会同时静默失效。
 */
public final class LegacyEventType {
    private LegacyEventType() {
    }

    /**
     * @param taskType 下一条待办的类型；为 null（周期已关闭）时返回 null
     * @return 旧词汇；无对应旧值时返回 null，让旧列保持空而不是塞入旧端看不懂的词
     */
    public static String of(TaskType taskType) {
        if (taskType == null) {
            return null;
        }
        return switch (taskType) {
            // 旧周期从配种才开始，这两个阶段在周期级没有旧值；
            // 沿用 batch_rabbits 的动作词，旧端至少能显示出人话。
            case ESTRUS -> "催情";
            case MATING -> "配种";
            case PALPATION -> "摸胎";
            case PREPARTUM -> "备产";
            case DELIVERY -> "分娩";
            // 旧词是「断奶」，新词是「分笼」——同一件事，两个词。
            case WEANING -> "断奶";
            case SALE_READY -> "出售";
            // 后备成熟走 replacement_records.expected_mature_date，周期级无对应列。
            case REPLACEMENT_MATURE, CUSTOM -> null;
        };
    }
}
