package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ReplacementBatchAllocationInput(
    Long batchId,
    @NotNull(message = "rabbitCount不能为空")
    @Positive(message = "rabbitCount必须大于0") Integer rabbitCount,
    @NotNull(message = "totalWeightKg不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "totalWeightKg必须大于0")
    BigDecimal totalWeightKg
) {}
