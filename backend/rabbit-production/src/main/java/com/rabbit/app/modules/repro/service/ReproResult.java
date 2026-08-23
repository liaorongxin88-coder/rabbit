package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.ReproStage;
import java.util.Date;
import java.util.List;

/** Result of one reproduction state transition. */
public record ReproResult(
    Long cycleId,
    Long currentCycleId,
    Long eventId,
    Long litterId,
    Long nextTaskId,
    ReproStage stage,
    String lifecycle,
    Date nextDueTime,
    Long followUpCycleId,
    boolean replayed,
    Long weaningRecordId,
    Integer waitingCount,
    List<Long> generatedRabbitIds
) {
    public ReproResult(
        Long cycleId,
        Long currentCycleId,
        Long eventId,
        Long litterId,
        Long nextTaskId,
        ReproStage stage,
        String lifecycle,
        Date nextDueTime,
        Long followUpCycleId,
        boolean replayed
    ) {
        this(
            cycleId,
            currentCycleId,
            eventId,
            litterId,
            nextTaskId,
            stage,
            lifecycle,
            nextDueTime,
            followUpCycleId,
            replayed,
            null,
            null,
            null
        );
    }

    public ReproResult withWeaning(Long recordId, Integer remainingCount) {
        return new ReproResult(
            cycleId,
            currentCycleId,
            eventId,
            litterId,
            nextTaskId,
            stage,
            lifecycle,
            nextDueTime,
            followUpCycleId,
            replayed,
            recordId,
            remainingCount,
            List.of()
        );
    }
}
