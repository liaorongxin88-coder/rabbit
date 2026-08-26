package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RenameBatchRequest {
    @NotBlank(message = "批次编号不能为空")
    @Size(max = 100, message = "batchCode过长")
    private String batchCode;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
