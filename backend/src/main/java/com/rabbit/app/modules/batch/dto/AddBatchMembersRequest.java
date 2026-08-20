package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 向已存在的批次追加繁育或养育/售卖兔只。 */
public class AddBatchMembersRequest {
    private List<Long> rabbitIds;

    /** 兼容尚未升级的客户端；新客户端统一提交 rabbitIds。 */
    private List<Long> femaleRabbitIds;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public List<Long> getRabbitIds() {
        return rabbitIds;
    }

    public void setRabbitIds(List<Long> rabbitIds) {
        this.rabbitIds = rabbitIds;
    }

    public List<Long> getFemaleRabbitIds() {
        return femaleRabbitIds;
    }

    public void setFemaleRabbitIds(List<Long> femaleRabbitIds) {
        this.femaleRabbitIds = femaleRabbitIds;
    }

    @AssertTrue(message = "兔只列表不能为空")
    public boolean isRabbitListPresent() {
        return (rabbitIds != null && !rabbitIds.isEmpty())
            || (femaleRabbitIds != null && !femaleRabbitIds.isEmpty());
    }

    public List<Long> resolveRabbitIds() {
        return rabbitIds != null && !rabbitIds.isEmpty() ? rabbitIds : femaleRabbitIds;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
