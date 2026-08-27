package com.rabbit.app.modules.appupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.modules.appupdate.entity.AppRelease;
import com.rabbit.app.modules.appupdate.service.AppUpdateDownloadPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 下载地址由 direct 还是 proxy 决定，是一个纯配置分支：配错了要到农场设备升级失败
 * 才会暴露。这里把两种模式和配置校验都钉住。
 */
class AppUpdateDownloadPolicyTest {

    private static AppRelease release() {
        AppRelease release = new AppRelease();
        release.setId(7L);
        release.setBuildNumber(4200L);
        release.setDownloadUrl("https://github.com/example/rabbit/releases/download/v1/app.apk");
        return release;
    }

    @Test
    @DisplayName("默认直连：原样把版本清单里的地址给客户端")
    void directModeHandsOutTheUpstreamUrl() {
        AppUpdateDownloadPolicy policy = new AppUpdateDownloadPolicy("direct", "");
        policy.validate();

        assertFalse(policy.isProxyEnabled());
        assertEquals(
                "https://github.com/example/rabbit/releases/download/v1/app.apk",
                policy.clientFacingUrlOf(release()));
    }

    @Test
    @DisplayName("代理模式：客户端只看到后端地址，上游地址仍可单独取到")
    void proxyModeRewritesOnlyTheClientFacingUrl() {
        AppUpdateDownloadPolicy policy =
                new AppUpdateDownloadPolicy("proxy", "https://api.dzht.top");
        policy.validate();

        assertTrue(policy.isProxyEnabled());
        assertEquals(
                "https://api.dzht.top/api/app-updates/7/download",
                policy.clientFacingUrlOf(release()));
        // 上游地址不能被改写，否则代理自己就取不到包了。
        assertEquals(
                "https://github.com/example/rabbit/releases/download/v1/app.apk",
                policy.upstreamUrlOf(release()));
    }

    @Test
    @DisplayName("代理地址结尾多一个斜杠也不会拼出双斜杠")
    void proxyModeNormalizesTrailingSlash() {
        AppUpdateDownloadPolicy policy =
                new AppUpdateDownloadPolicy("proxy", "https://api.dzht.top/");
        policy.validate();

        assertEquals(
                "https://api.dzht.top/api/app-updates/7/download",
                policy.clientFacingUrlOf(release()));
    }

    @Test
    @DisplayName("代理模式缺对外地址，启动就失败")
    void proxyModeRequiresPublicBaseUrl() {
        AppUpdateDownloadPolicy policy = new AppUpdateDownloadPolicy("proxy", "  ");

        IllegalStateException error = assertThrows(IllegalStateException.class, policy::validate);
        assertTrue(error.getMessage().contains("public-base-url"));
    }

    @Test
    @DisplayName("对外地址不是 https 就失败，因为客户端只收 https")
    void proxyModeRejectsPlainHttp() {
        AppUpdateDownloadPolicy policy =
                new AppUpdateDownloadPolicy("proxy", "http://192.168.1.10:8080");

        IllegalStateException error = assertThrows(IllegalStateException.class, policy::validate);
        assertTrue(error.getMessage().contains("https"));
    }

    @Test
    @DisplayName("模式写错不静默退回默认值，直接报出来")
    void unknownModeFailsLoudly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppUpdateDownloadPolicy("relay", ""));
    }
}
