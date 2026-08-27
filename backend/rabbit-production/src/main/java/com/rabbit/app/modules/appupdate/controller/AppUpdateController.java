package com.rabbit.app.modules.appupdate.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.appupdate.dto.AppUpdateCheckResponse;
import com.rabbit.app.modules.appupdate.entity.AppRelease;
import com.rabbit.app.modules.appupdate.service.AppUpdateService;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Validated
@RestController
@RequestMapping("/api/app-updates")
public class AppUpdateController {
    private final AppUpdateService appUpdateService;

    public AppUpdateController(AppUpdateService appUpdateService) {
        this.appUpdateService = appUpdateService;
    }

    @GetMapping("/check")
    @RequiresPermission(PermissionCode.ACCOUNT_PROFILE_QUERY)
    public ApiResponse<AppUpdateCheckResponse> check(
            @RequestParam("buildNumber") @Min(value = 1, message = "buildNumber必须大于0") Long buildNumber
    ) {
        return ApiResponse.ok(appUpdateService.checkAndroid(buildNumber));
    }

    /**
     * 代理下载 APK，只在 app.app-update.download-mode=proxy 时开放。
     *
     * <p>场区设备常常只能访问后端，出不了公网。这个端点让它们只连后端，
     * 由后端去取上游的包再转出去。
     *
     * <p>全程流式转发，不把几十 MB 的包读进堆内存；多台设备同时升级时这一点是必须的。
     * 不支持断点续传（客户端也没用），下载中断就重新来。
     * 包的真假仍由客户端原生层校验 sha256，代理不重复算一遍。
     */
    @GetMapping("/{releaseId}/download")
    @RequiresPermission(PermissionCode.ACCOUNT_PROFILE_QUERY)
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("releaseId") Long releaseId) {
        AppRelease release = appUpdateService.requireDownloadableRelease(releaseId);
        String upstream = appUpdateService.upstreamUrlOf(release);

        HttpResponse<InputStream> upstreamResponse;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(upstream))
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();
            upstreamResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException(502, "下载被中断，请重试");
        } catch (IOException | IllegalArgumentException error) {
            throw new BizException(502, "取升级包失败，请稍后重试");
        }
        if (upstreamResponse.statusCode() != 200) {
            closeQuietly(upstreamResponse.body());
            throw new BizException(502, "上游返回 " + upstreamResponse.statusCode() + "，请稍后重试");
        }

        StreamingResponseBody body = getBody(upstreamResponse);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(release.getApkSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rabbit-" + release.getBuildNumber() + ".apk\"")
                .body(body);
    }

    private static StreamingResponseBody getBody(HttpResponse<InputStream> upstreamResponse) {
        return (OutputStream out) -> {
            try (InputStream in = upstreamResponse.body()) {
                in.transferTo(out);
            }
        };
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 关不上就算了，不影响给客户端的错误。
        }
    }
}
