package com.rabbit.app.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class SetCageRabbitCountRequest {
    @NotNull(message = "rabbitCount不能为空")
    @Min(value = 0, message = "rabbitCount不能小于0")
    private Integer rabbitCount;

    public Integer getRabbitCount() {
        return rabbitCount;
    }

    public void setRabbitCount(Integer rabbitCount) {
        this.rabbitCount = rabbitCount;
    }
}
