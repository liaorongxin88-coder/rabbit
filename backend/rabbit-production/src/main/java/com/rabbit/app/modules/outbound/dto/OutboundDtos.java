package com.rabbit.app.modules.outbound.dto;

import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
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
            @NotNull(message = "items不能为空")
            List<@NotNull(message = "items不能包含空项") @Valid SelectedRabbitInput> items,
            Date saleTime,
            Double totalWeight,
            BigDecimal unitPrice,
            BigDecimal unitPricePerKg,
            List<@NotNull(message = "batchAllocations不能包含空项") @Valid SaleBatchAllocationInput> batchAllocations,
            String customer,
            String remark
    ) {
        public SaveDraftRequest(
            Long revision,
            String status,
            List<SelectedRabbitInput> items,
            Date saleTime,
            Double totalWeight,
            BigDecimal unitPrice,
            String customer,
            String remark
        ) {
            this(revision, status, items, saleTime, totalWeight, unitPrice, null, null, customer, remark);
        }
    }

    public record SubmitRequest(
            @NotEmpty(message = "rabbitIds不能为空") List<Long> rabbitIds,
            @NotNull(message = "stateVersions不能为空") Map<String, Long> stateVersions,
            Map<String, String> earlySaleReasons,
            @NotNull(message = "saleTime不能为空") Date saleTime,
            @NotNull(message = "totalWeight不能为空") @Positive(message = "totalWeight不合法") Double totalWeight,
            BigDecimal unitPrice,
            BigDecimal unitPricePerKg,
            List<@NotNull(message = "batchAllocations不能包含空项") @Valid SaleBatchAllocationInput> batchAllocations,
            String customer,
            String remark,
            @NotBlank(message = "requestId不能为空") String requestId
    ) {
        public SubmitRequest(
            List<Long> rabbitIds,
            Map<String, Long> stateVersions,
            Map<String, String> earlySaleReasons,
            Date saleTime,
            Double totalWeight,
            BigDecimal unitPrice,
            String customer,
            String remark,
            String requestId
        ) {
            this(
                rabbitIds,
                stateVersions,
                earlySaleReasons,
                saleTime,
                totalWeight,
                unitPrice,
                null,
                null,
                customer,
                remark,
                requestId
            );
        }

        public BigDecimal effectiveUnitPrice() {
            return unitPricePerKg == null ? unitPrice : unitPricePerKg;
        }
    }

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
            BigDecimal unitPricePerKg,
            List<SaleBatchAllocationInput> batchAllocations,
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
