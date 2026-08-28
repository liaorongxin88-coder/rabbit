package com.rabbit.app.modules.rabbit.dto;

import java.util.List;

/** Result of an idempotent, same-cage batch intake. */
public record BatchRabbitEntryResult(
    int requestedRabbitCount,
    int enteredRabbitCount,
    int replayedRabbitCount,
    List<SkippedCage> skippedCages
) {
    public record SkippedCage(Long cageId, String cageNumber, int rabbitCount, String reason) {}
}
