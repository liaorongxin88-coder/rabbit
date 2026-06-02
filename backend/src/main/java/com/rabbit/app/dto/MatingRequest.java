package com.rabbit.app.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

public class MatingRequest {
    @NotNull(message = "femaleRabbitId不能为空")
    private Long femaleRabbitId;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotNull(message = "maleRabbitId不能为空")
    private Long maleRabbitId;

    @NotNull(message = "matingDate不能为空")
    private Date matingDate;

    public Long getFemaleRabbitId() {
        return femaleRabbitId;
    }

    public void setFemaleRabbitId(Long femaleRabbitId) {
        this.femaleRabbitId = femaleRabbitId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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
}
