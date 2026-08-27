package com.rabbit.app.modules.appupdate.service;

import com.rabbit.app.modules.appupdate.entity.AppRelease;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 决定 check 接口回给客户端的下载地址：是上游原地址，还是后端代理地址。
 *
 * <p>客户端只收 https（见 app 侧 AppRelease.fromJson），所以 PROXY 模式必须配一个
 * https 的对外地址。配错了在启动时就报出来，而不是等农场设备升级失败才发现。
 */
@Component
public class AppUpdateDownloadPolicy {
    /** 代理下载的路径模板，与 AppUpdateController 上的映射保持一致。 */
    static final String PROXY_PATH_TEMPLATE = "/api/app-updates/%d/download";

    private final AppUpdateDownloadMode mode;
    private final String publicBaseUrl;

    public AppUpdateDownloadPolicy(
            @Value("${app.app-update.download-mode:direct}") String rawMode,
            @Value("${app.app-update.public-base-url:}") String publicBaseUrl
    ) {
        this.mode = AppUpdateDownloadMode.parse(rawMode);
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    }

    @PostConstruct
    public void validate() {
        if (mode != AppUpdateDownloadMode.PROXY) {
            return;
        }
        if (publicBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "app.app-update.download-mode=proxy 时必须配置 app.app-update.public-base-url，"
                            + "否则客户端拿不到可用的下载地址");
        }
        URI uri;
        try {
            uri = URI.create(publicBaseUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "app.app-update.public-base-url 不是合法地址：" + publicBaseUrl, error);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException(
                    "app.app-update.public-base-url 必须是 https 且带主机名，客户端会拒收其他形式，当前是："
                            + publicBaseUrl);
        }
    }

    public boolean isProxyEnabled() {
        return mode == AppUpdateDownloadMode.PROXY;
    }

    /** 上游真实地址，代理下载时用它去取包。 */
    public String upstreamUrlOf(AppRelease release) {
        return release.getDownloadUrl();
    }

    /** 回给客户端的地址。 */
    public String clientFacingUrlOf(AppRelease release) {
        if (mode != AppUpdateDownloadMode.PROXY) {
            return release.getDownloadUrl();
        }
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + String.format(PROXY_PATH_TEMPLATE, release.getId());
    }
}
