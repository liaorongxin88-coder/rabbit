package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CreateBatchRequest {
    @NotBlank(message = "batchCode不能为空")
    @Size(max = 100, message = "batchCode过长")
    private String batchCode;

    // 新口径：可先建空批次，母兔通过 POST /batches/{id}/members 陆续追加。
    @Size(max = 5000, message = "单个批次母兔数量不能超过5000只")
    private List<@NotNull(message = "母兔ID不能为空") @Positive(message = "母兔ID不合法") Long> femaleRabbitIds;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    private String remark;

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
