package com.rabbit.app.modules.rabbit.dto;

import java.util.List;

public record RangeRabbitEntryResult(
    int requestedSlotCount,
    int missingCageCount,
    int unplacedCageCount,
    int enteredCageCount,
    int enteredRabbitCount,
    int replayedCageCount,
    List<SkippedCage> skippedCages
) {
    public record SkippedCage(Long cageId, String cageNumber, String reason) {}
}
