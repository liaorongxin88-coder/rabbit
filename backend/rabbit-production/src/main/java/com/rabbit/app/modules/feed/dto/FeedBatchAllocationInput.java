package com.rabbit.app.modules.feed.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FeedBatchAllocationInput(
    Long batchId,
    @NotBlank(message = "phase不能为空") String phase,
    @NotNull(message = "amountKg不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "amountKg必须大于0") BigDecimal amountKg
) {}
