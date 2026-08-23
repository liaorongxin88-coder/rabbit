package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.batch.entity.WeaningRecord;
import com.rabbit.app.modules.batch.entity.WeaningRecordAllocation;
import java.util.Date;
import java.util.List;

/** Inventory work performed after an already-recorded weaning is separated. */
public record KitSeparationCommand(
    Long userId,
    String operator,
    WeaningRecord weaningRecord,
    Long sireRabbitId,
    List<WeaningRecordAllocation> allocations,
    Date separatedAt,
    String requestId
) {
}
