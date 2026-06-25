package com.rabbit.app.modules.admin.dto;

import jakarta.validation.constraints.NotNull;

public class AddMerchantUserRequest {
    @NotNull(message = "userId不能为空")
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
