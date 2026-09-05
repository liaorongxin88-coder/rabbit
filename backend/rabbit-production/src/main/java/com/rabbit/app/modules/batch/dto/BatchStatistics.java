package com.rabbit.app.modules.batch.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BatchStatistics(
    int schemaVersion,
    Long batchId,
    String houseName,
    String batchCode,
    Instant calculatedAt,
    Integer totalLitters,
    Integer totalKits,
    Integer totalLiveKits,
    Integer totalWeaned,
    List<Metric> metrics
) {
    public BatchStatistics {
        metrics = List.copyOf(metrics);
    }

    public record Metric(
        String code,
        String name,
        String stage,
        String stageName,
        int order,
        String excelColumnName,
        String valueType,
        String unit,
        String format,
        String formula,
        String status,
        BigDecimal numericValue,
        String displayValue,
        DateRangeValue dateValue,
        Operand numerator,
        Operand denominator,
        List<Operand> components,
        List<MissingCause> missingCauses
    ) {
        public Metric {
            components = List.copyOf(components);
            missingCauses = List.copyOf(missingCauses);
        }
    }

    public record DateRangeValue(
        LocalDate firstDate,
        LocalDate lastDate,
        int dateCount,
        List<DailyCycleCount> dailyCycleCounts
    ) {
        public DateRangeValue {
            dailyCycleCounts = List.copyOf(dailyCycleCounts);
        }
    }

    public record DailyCycleCount(LocalDate date, int cycleCount) {
    }

    public record Operand(String code, String label, BigDecimal value, String unit) {
    }

    public record MissingCause(String code, String message) {
    }
}
