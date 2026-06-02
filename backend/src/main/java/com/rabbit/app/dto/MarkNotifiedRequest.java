package com.rabbit.app.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

public class MarkNotifiedRequest {
    @NotEmpty(message = "recordIds不能为空")
    private List<Long> recordIds;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public List<Long> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<Long> recordIds) {
        this.recordIds = recordIds;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
