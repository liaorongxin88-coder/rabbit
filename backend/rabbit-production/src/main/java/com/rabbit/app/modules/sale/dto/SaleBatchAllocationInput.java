package com.rabbit.app.modules.sale.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SaleBatchAllocationInput(
    @Positive(message = "batchId不合法") Long batchId,
    @NotNull(message = "actualWeightKg不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "actualWeightKg必须大于0")
    @DecimalMax(value = "100000.000", message = "actualWeightKg不能超过100000")
    @Digits(integer = 6, fraction = 3, message = "actualWeightKg最多保留三位小数")
    BigDecimal actualWeightKg
) {}
