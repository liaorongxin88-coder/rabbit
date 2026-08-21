package com.rabbit.app.modules.repro.dto;

public record KeptKitsAdjustmentResponse(
    Long cycleId,
    Long litterId,
    Long eventId,
    Integer previousKeptKits,
    Integer keptKits,
    Long sourceMotherRabbitId,
    boolean replayed
) {
}
