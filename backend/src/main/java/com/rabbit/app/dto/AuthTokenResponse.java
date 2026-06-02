package com.rabbit.app.dto;

public class AuthTokenResponse {
    private String token;
    private Long userId;
    private String userName;

    public AuthTokenResponse() {
    }

    public AuthTokenResponse(String token, Long userId, String userName) {
        this.token = token;
        this.userId = userId;
        this.userName = userName;
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
}
