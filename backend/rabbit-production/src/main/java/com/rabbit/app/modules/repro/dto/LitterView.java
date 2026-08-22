package com.rabbit.app.modules.repro.dto;

import com.rabbit.app.modules.repro.entity.Litter;

public record LitterView(
    Long id,
    Long cycleId,
    Long motherRabbitId,
    Long batchId,
    Integer keptKits,
    Integer currentNursing,
    String status
) {
    public static LitterView of(Litter litter) {
        return new LitterView(
            litter.getId(),
            litter.getCycleId(),
            litter.getMotherRabbitId(),
            litter.getBatchId(),
            litter.getKeptKits(),
            litter.getCurrentNursing(),
            litter.getStatus()
        );
    }
}
