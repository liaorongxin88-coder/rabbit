package com.rabbit.app.modules.rabbit.dto;

public record ReplacementConversionItem(
    Long rabbitId,
    Long replacementRecordId,
    Long targetCageId
) {
}
