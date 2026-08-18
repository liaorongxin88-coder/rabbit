package com.rabbit.app.modules.repro.dto;

import com.rabbit.app.modules.repro.domain.EntryPoint;
import java.util.List;

/**
 * 「录入母兔时可以从哪个阶段入轨、要补录哪些事实」的字典。
 *
 * <p>动因是飞书 recvsrnEJ8bKrk：录入表单没有「进入该阶段的日期」。这个日期不是所有阶段
 * 都需要，待摸胎要的是配种日、待分笼要的是分娩日与活仔数——真相在
 * {@link EntryPoint#requiredFacts()}。客户端各抄一份必然漂移，用户会遇到「填完才 400」。
 * 因此和阶段动作字典一样，由服务端下发。
 */
public record EntryPointView(
    String stage,
    String stageLabel,
    List<RequiredFactView> requiredFacts
) {

    public record RequiredFactView(String fact, String label) {
    }

    public static List<EntryPointView> all() {
        return java.util.Arrays.stream(EntryPoint.values())
            .map(EntryPointView::of)
            .toList();
    }

    public static EntryPointView of(EntryPoint entry) {
        List<RequiredFactView> facts = entry.requiredFacts().stream()
            .map(fact -> new RequiredFactView(fact.name(), fact.label()))
            .sorted(java.util.Comparator.comparing(RequiredFactView::fact))
            .toList();
        return new EntryPointView(entry.stage().name(), entry.stage().label(), facts);
    }
}
