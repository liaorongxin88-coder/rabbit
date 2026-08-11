package com.rabbit.app.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PhoneOneTapLoginRequest {
    @NotBlank(message = "provider不能为空")
    @Size(max = 32, message = "provider不合法")
    private String provider;

    @NotBlank(message = "accessToken不能为空")
    @Size(max = 4096, message = "accessToken不合法")
    private String accessToken;

    @NotBlank(message = "requestId不能为空")
    @Pattern(regexp = "^[A-Za-z0-9._:-]{1,64}$", message = "requestId不合法")
    private String requestId;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
