package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/**
 * Applies one mating date and one buck to a bounded set of does.
 * The request is deliberately uniform so a house operator can submit a
 * whole mating round without carrying a large per-row payload.
 */
public class BulkMatingRequest {
    @NotEmpty(message = "femaleRabbitIds不能为空")
    @Size(max = 1000, message = "单次最多配种1000只母兔")
    private List<Long> femaleRabbitIds;

    @NotNull(message = "maleRabbitId不能为空")
    private Long maleRabbitId;

    @NotNull(message = "matingDate不能为空")
    private Date matingDate;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64")
    private String requestId;

    public List<Long> getFemaleRabbitIds() {
        return femaleRabbitIds;
    }

    public void setFemaleRabbitIds(List<Long> femaleRabbitIds) {
        this.femaleRabbitIds = femaleRabbitIds;
    }

    public Long getMaleRabbitId() {
        return maleRabbitId;
    }

    public void setMaleRabbitId(Long maleRabbitId) {
        this.maleRabbitId = maleRabbitId;
    }

    public Date getMatingDate() {
        return matingDate;
    }

    public void setMatingDate(Date matingDate) {
        this.matingDate = matingDate;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
