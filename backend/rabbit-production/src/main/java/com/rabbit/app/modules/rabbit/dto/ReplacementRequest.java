package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReplacementRequest {
    @NotEmpty(message = "rabbitIds不能为空")
    private List<Long> rabbitIds;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    private String requestId;

    private Boolean forceExitBatch;

    private Long targetCageId;

    private List<@NotNull(message = "batchAllocations不能包含空项") @Valid ReplacementBatchAllocationInput> batchAllocations;

    public List<Long> getRabbitIds() {
        return rabbitIds;
    }

    public void setRabbitIds(List<Long> rabbitIds) {
        this.rabbitIds = rabbitIds;
    }

    public Boolean getForceExitBatch() {
        return forceExitBatch;
    }

    public void setForceExitBatch(Boolean forceExitBatch) {
        this.forceExitBatch = forceExitBatch;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getTargetCageId() {
        return targetCageId;
    }

    public void setTargetCageId(Long targetCageId) {
        this.targetCageId = targetCageId;
    }

    public List<ReplacementBatchAllocationInput> getBatchAllocations() {
        return batchAllocations;
    }

    public void setBatchAllocations(List<ReplacementBatchAllocationInput> batchAllocations) {
        this.batchAllocations = batchAllocations;
    }
}
