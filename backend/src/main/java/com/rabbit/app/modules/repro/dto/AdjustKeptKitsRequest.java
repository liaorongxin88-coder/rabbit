package com.rabbit.app.modules.repro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class AdjustKeptKitsRequest {
    @NotNull(message = "执行时间不能为空")
    private Date occurredAt;

    @NotNull(message = "留崽数量不能为空")
    @Min(value = 0, message = "留崽数量不能小于0")
    private Integer keptKits;

    private Long sourceMotherRabbitId;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Integer getKeptKits() {
        return keptKits;
    }

    public void setKeptKits(Integer keptKits) {
        this.keptKits = keptKits;
    }

    public Long getSourceMotherRabbitId() {
        return sourceMotherRabbitId;
    }

    public void setSourceMotherRabbitId(Long sourceMotherRabbitId) {
        this.sourceMotherRabbitId = sourceMotherRabbitId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
