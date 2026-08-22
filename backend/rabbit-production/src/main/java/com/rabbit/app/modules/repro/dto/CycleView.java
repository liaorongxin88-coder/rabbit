package com.rabbit.app.modules.repro.dto;

import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import java.util.Date;

/**
 * 周期视图。
 *
 * <p>只暴露 V2 语义字段，不外传 {@code status} 等兼容镜像列——那些列
 * 只为未升级的旧 APK 保留，新客户端读到它们就会重新依赖上即将删除的词汇。
 */
public record CycleView(
    Long id,
    Long houseId,
    Long batchId,
    Long motherRabbitId,
    Long maleRabbitId,
    Integer cycleNo,
    String stage,
    String stageLabel,
    String lifecycle,
    String result,
    String matingMethod,
    Long stateVersion,
    Date stageEnteredAt,
    Date matingDate,
    Date pregnancyCheckDate,
    Date expectedBirthDate,
    Date birthDate,
    Date weaningDate
) {

    public static CycleView of(ReproCycle cycle) {
        String stage = cycle.getStage();
        return new CycleView(
            cycle.getId(),
            cycle.getHouseId(),
            cycle.getBatchId(),
            cycle.getMotherRabbitId(),
            cycle.getMaleRabbitId(),
            cycle.getCycleNo(),
            stage,
            stage == null ? null : ReproStage.parse(stage).label(),
            cycle.getLifecycle(),
            cycle.getResult(),
            cycle.getMatingMethod(),
            cycle.getStateVersion(),
            cycle.getStageEnteredAt(),
            cycle.getMatingDate(),
            cycle.getPregnancyCheckDate(),
            cycle.getExpectedBirthDate(),
            cycle.getBirthDate(),
            cycle.getWeaningDate()
        );
    }
}
