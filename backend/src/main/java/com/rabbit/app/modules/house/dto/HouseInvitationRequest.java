package com.rabbit.app.modules.house.dto;

import jakarta.validation.constraints.NotBlank;

public class HouseInvitationRequest {
    /**
     * 老客户端只会发 phone。保留它，并且不能再挂 @NotBlank：
     * 新客户端改发 identifier，两者有一个就行，缺一不可的校验放到 service 里做。
     */
    private String phone;

    /** 手机号或兔号。两种形态在服务端自动识别，客户端只需要一个输入框。 */
    private String identifier;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /** 新字段优先，没填就回退到老客户端的 phone。 */
    public String identifierOrPhone() {
        if (identifier != null && !identifier.trim().isEmpty()) {
            return identifier;
        }
        return phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
