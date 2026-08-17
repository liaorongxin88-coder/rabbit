package com.rabbit.app.modules.repro.dto;

import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.domain.TransitionTable;
import java.util.List;

/**
 * 某个阶段当下允许执行的动作。
 *
 * <p>让客户端问服务端「这头母兔现在能做什么」，而不是各自抄一份规则。
 * 流产是这件事的直接动因：它只在孕期三个阶段成立，入口必须按阶段显隐，
 * 而这个判断只应有一个来源——转换表。
 *
 * <p>同时下发中文名，客户端不必再维护枚举到中文的对照，避免词汇漂移。
 */
public record StageActionsView(
    String stage,
    String stageLabel,
    List<ActionView> actions
) {

    public record ActionView(String action, String label) {
    }

    public static List<StageActionsView> all() {
        return java.util.Arrays.stream(ReproStage.values())
            .map(StageActionsView::of)
            .toList();
    }

    public static StageActionsView of(ReproStage stage) {
        List<ActionView> actions = TransitionTable.actionsFrom(stage).stream()
            .map(action -> new ActionView(action.name(), action.label()))
            .toList();
        return new StageActionsView(stage.name(), stage.label(), actions);
    }

    /** 便于按动作反查阶段，例如「哪些阶段能流产」。 */
    public static List<String> stagesAllowing(ReproAction action) {
        return java.util.Arrays.stream(ReproStage.values())
            .filter(stage -> TransitionTable.actionsFrom(stage).contains(action))
            .map(ReproStage::name)
            .toList();
    }
}
