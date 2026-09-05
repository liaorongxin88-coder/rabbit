package com.rabbit.app.modules.batch.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

public record BatchCarcassYieldView(
    Long id,
    Long houseId,
    Long batchId,
    BigDecimal yieldRate,
    String sourceUnit,
    LocalDate measuredDate,
    String reportNumber,
    String evidenceFileId,
    String remark,
    String changeReason,
    String requestId,
    Long createdBy,
    String createdByName,
    Date createdAt
) {}
