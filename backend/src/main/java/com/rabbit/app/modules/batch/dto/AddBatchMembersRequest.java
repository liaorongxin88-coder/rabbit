package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 向已存在的批次追加母兔。 */
public class AddBatchMembersRequest {
    @NotEmpty(message = "母兔列表不能为空")
    private List<Long> femaleRabbitIds;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public List<Long> getFemaleRabbitIds() {
        return femaleRabbitIds;
    }

    public void setFemaleRabbitIds(List<Long> femaleRabbitIds) {
        this.femaleRabbitIds = femaleRabbitIds;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
