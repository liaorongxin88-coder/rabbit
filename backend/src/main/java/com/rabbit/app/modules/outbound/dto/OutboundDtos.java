package com.rabbit.app.modules.outbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class OutboundDtos {
    private OutboundDtos() {}

    public record CreateTaskRequest(
            @NotBlank(message = "entryType不能为空") String entryType,
            Long rabbitId,
            Long cageId,
            String rowCode,
            Boolean resumeExisting
    ) {}

    public record SelectedRabbitInput(
            @NotNull(message = "rabbitId不能为空") Long rabbitId,
            @NotNull(message = "stateVersion不能为空") Long stateVersion,
            String selectionType,
            String earlySaleReason
    ) {}

    public record SaveDraftRequest(
            @NotNull(message = "revision不能为空") Long revision,
            @NotBlank(message = "status不能为空") String status,
            @NotNull(message = "items不能为空") List<@Valid SelectedRabbitInput> items,
            Date saleTime,
            Double totalWeight,
            BigDecimal unitPrice,
            String customer,
            String remark
    ) {}

    public record SubmitRequest(
            @NotEmpty(message = "rabbitIds不能为空") List<Long> rabbitIds,
            @NotNull(message = "stateVersions不能为空") Map<String, Long> stateVersions,
            Map<String, String> earlySaleReasons,
            @NotNull(message = "saleTime不能为空") Date saleTime,
            @NotNull(message = "totalWeight不能为空") @Positive(message = "totalWeight不合法") Double totalWeight,
            BigDecimal unitPrice,
            String customer,
            String remark,
            @NotBlank(message = "requestId不能为空") String requestId
    ) {}

    public record EligibilitySummary(int normal, int earlySale, int needsAction, int blocked) {}

    public record RabbitEligibilityView(
            Long rabbitId,
            Long cageId,
            String cageNumber,
            String rowCode,
            Integer layerIndex,
            Integer positionIndex,
            String rabbitType,
            String gender,
            Double weight,
            String stage,
            Long batchId,
            Long stateVersion,
            String eligibility,
            String reasonCode,
            String message,
            String recommendedAction,
            boolean defaultSelected
    ) {}

    public record TaskItemView(
            Long rabbitId,
            Long stateVersion,
            String selectionType,
            String earlySaleReason
    ) {}

    public record TaskView(
            String taskId,
            Long houseId,
            String entryType,
            Long sourceRabbitId,
            Long sourceCageId,
            String sourceRowCode,
            String status,
            Long revision,
            Date saleTime,
            Double totalWeight,
            BigDecimal unitPrice,
            String customer,
            String remark,
            Long saleOrderId,
            boolean resumed,
            EligibilitySummary summary,
            List<RabbitEligibilityView> rabbits,
            List<TaskItemView> selectedItems
    ) {}

    public record RabbitConflict(
            Long rabbitId,
            String errorCode,
            String currentState,
            String message,
            String recommendedAction
    ) {}

    public record SubmitResult(
            String status,
            String requestId,
            String taskId,
            Long saleOrderId,
            String saleOrderNumber,
            Date saleTime,
            int rabbitCount,
            int cageCount,
            int rowCount,
            Double totalWeight,
            BigDecimal totalAmount,
            String errorCode,
            String message,
            List<RabbitConflict> conflicts
    ) {}
}
