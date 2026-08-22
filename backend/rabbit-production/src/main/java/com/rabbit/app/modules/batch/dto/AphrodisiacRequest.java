package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class AphrodisiacRequest {
    @NotEmpty(message = "rabbitIds不能为空")
    private List<Long> rabbitIds;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    private Boolean triggerHardware;

    public List<Long> getRabbitIds() {
        return rabbitIds;
    }

    public void setRabbitIds(List<Long> rabbitIds) {
        this.rabbitIds = rabbitIds;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Boolean getTriggerHardware() {
        return triggerHardware;
    }

    public void setTriggerHardware(Boolean triggerHardware) {
        this.triggerHardware = triggerHardware;
    }
}
