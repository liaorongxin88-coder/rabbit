package com.rabbit.app.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

public class HardwareAphrodisiacRequest {
    @NotNull
    private Long batchId;

    @NotEmpty
    private List<Long> rabbitIds;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public List<Long> getRabbitIds() {
        return rabbitIds;
    }

    public void setRabbitIds(List<Long> rabbitIds) {
        this.rabbitIds = rabbitIds;
    }
}

