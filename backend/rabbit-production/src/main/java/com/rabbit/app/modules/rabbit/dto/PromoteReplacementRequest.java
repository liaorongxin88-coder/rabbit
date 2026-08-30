package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PromoteReplacementRequest {
    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @Size(max = 200, message = "转种原因不能超过200字")
    private String reason;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
