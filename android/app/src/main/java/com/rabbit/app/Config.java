package com.rabbit.app;

public class Config {
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080";
    private static volatile String baseUrl;

    public static String getBaseUrl() {
        String u = baseUrl;
        if (u == null || u.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return u;
    }

    public static void setBaseUrl(String url) {
        baseUrl = url;
    }
}
