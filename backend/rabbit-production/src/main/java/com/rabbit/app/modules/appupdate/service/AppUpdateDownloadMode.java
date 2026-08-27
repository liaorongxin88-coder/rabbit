package com.rabbit.app.modules.appupdate.service;

/**
 * 客户端拿到的 APK 下载地址由谁提供。
 *
 * <p>DIRECT 是默认：直接把版本清单里登记的地址（当前是 GitHub Release）交给客户端。
 * 省带宽，但设备必须能出网。
 *
 * <p>PROXY 让客户端只连后端，由后端去取上游再转发。农场内网只放通后端时用这个。
 * 代价是升级流量全部走后端。切换只改配置，不用重新发版。
 */
public enum AppUpdateDownloadMode {
    DIRECT,
    PROXY;

    public static AppUpdateDownloadMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DIRECT;
        }
        return switch (raw.trim().toUpperCase()) {
            case "PROXY" -> PROXY;
            case "DIRECT" -> DIRECT;
            default -> throw new IllegalArgumentException(
                    "app.app-update.download-mode 只能是 direct 或 proxy，当前是：" + raw);
        };
    }
}
