package com.rabbit.app.modules.auth.dto;

import java.util.List;

public class AuthTokenResponse {
    private String token;
    private Long userId;
    private String userName;
    private Boolean phoneBound;
    private String maskedPhone;
    private Boolean hasPassword;
    private List<String> permissions = List.of();

    public AuthTokenResponse() {
    }

    public AuthTokenResponse(String token, Long userId, String userName) {
        this(token, userId, userName, false, null, false);
    }

    public AuthTokenResponse(
            String token,
            Long userId,
            String userName,
            Boolean phoneBound,
            String maskedPhone,
            Boolean hasPassword
    ) {
        this.token = token;
        this.userId = userId;
        this.userName = userName;
        this.phoneBound = phoneBound;
        this.maskedPhone = maskedPhone;
        this.hasPassword = hasPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Boolean getPhoneBound() {
        return phoneBound;
    }

    public void setPhoneBound(Boolean phoneBound) {
        this.phoneBound = phoneBound;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public void setMaskedPhone(String maskedPhone) {
        this.maskedPhone = maskedPhone;
    }

    public Boolean getHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(Boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
