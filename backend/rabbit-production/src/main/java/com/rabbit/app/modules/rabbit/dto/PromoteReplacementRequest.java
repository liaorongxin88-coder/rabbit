package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;

public class PromoteReplacementRequest {
    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
