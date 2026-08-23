package com.rabbit.app.modules.batch.dto;

import java.util.List;

/** Result of one deferred weaning separation request. */
public record WeaningSeparationResult(
    Long weaningRecordId,
    int separatedCount,
    int waitingCount,
    List<Long> generatedRabbitIds,
    boolean replayed
) {
}
