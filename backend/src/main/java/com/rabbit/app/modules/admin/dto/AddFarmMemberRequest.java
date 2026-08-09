package com.rabbit.app.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AddFarmMemberRequest {
    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotBlank(message = "角色不能为空")
    private String role;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
