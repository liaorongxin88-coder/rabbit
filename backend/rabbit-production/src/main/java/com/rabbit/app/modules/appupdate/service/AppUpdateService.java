package com.rabbit.app.modules.appupdate.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.appupdate.dto.AppUpdateCheckResponse;
import com.rabbit.app.modules.appupdate.dto.CreateAppReleaseRequest;
import com.rabbit.app.modules.appupdate.dto.UpdateAppReleaseStatusRequest;
import com.rabbit.app.modules.appupdate.entity.AppRelease;
import com.rabbit.app.modules.appupdate.mapper.AppReleaseMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AppUpdateService {
    private static final String ANDROID = "ANDROID";
    private static final Long PLATFORM_SCOPE_ID = 0L;
    private static final String PUBLISH_API = "platform:app-updates:publish";

    private final AppReleaseMapper appReleaseMapper;
    private final RequestDedupService requestDedupService;
    private final AppUpdateDownloadPolicy downloadPolicy;

    public AppUpdateService(
            AppReleaseMapper appReleaseMapper,
            RequestDedupService requestDedupService,
            AppUpdateDownloadPolicy downloadPolicy
    ) {
        this.appReleaseMapper = appReleaseMapper;
        this.requestDedupService = requestDedupService;
        this.downloadPolicy = downloadPolicy;
    }

    public AppUpdateCheckResponse checkAndroid(Long currentBuild) {
        AppRelease release = appReleaseMapper.selectLatestPublishedNewer(ANDROID, currentBuild);
        if (release == null) {
            return AppUpdateCheckResponse.upToDate(currentBuild);
        }
        // 上游地址原样留在版本清单里，回给客户端的可能是后端代理地址。
        return AppUpdateCheckResponse.available(
                currentBuild, release, downloadPolicy.clientFacingUrlOf(release));
    }

    /**
     * 取一条可供下载的已发布版本。只在代理模式下有意义；直连模式下不该有人调到这里，
     * 所以直接按 404 处理，免得把后端变成任意版本的开放下载源。
     */
    public AppRelease requireDownloadableRelease(Long releaseId) {
        if (!downloadPolicy.isProxyEnabled()) {
            throw new BizException(404, "未开启下载代理");
        }
        AppRelease release = appReleaseMapper.selectById(releaseId);
        if (release == null || !Boolean.TRUE.equals(release.getPublished())) {
            throw new BizException(404, "版本清单不存在");
        }
        return release;
    }

    public String upstreamUrlOf(AppRelease release) {
        return downloadPolicy.upstreamUrlOf(release);
    }

    public AppRelease publish(Long adminId, CreateAppReleaseRequest request) {
        String payloadHash = payloadHash(request);
        RequestDedupService.BeginResult beginResult = requestDedupService.begin(
                PLATFORM_SCOPE_ID,
                adminId,
                PUBLISH_API,
                request.getRequestId(),
                payloadHash
        );
        if (beginResult == RequestDedupService.BeginResult.DONE) {
            return releaseForRequest(request.getRequestId());
        }

        try {
            AppRelease previousAttempt = appReleaseMapper.selectByRequestId(request.getRequestId());
            if (previousAttempt != null) {
                requestDedupService.markDone(PLATFORM_SCOPE_ID, adminId, PUBLISH_API, request.getRequestId());
                return previousAttempt;
            }
            validateDownloadUrl(request.getDownloadUrl());
            AppRelease existing = appReleaseMapper.selectByPlatformAndBuild(ANDROID, request.getBuildNumber());
            if (existing != null) {
                throw new BizException(409, "该构建号已发布");
            }

            AppRelease release = new AppRelease();
            release.setPlatform(ANDROID);
            release.setBuildNumber(request.getBuildNumber());
            release.setVersionName(request.getVersionName().trim());
            release.setDownloadUrl(request.getDownloadUrl().trim());
            release.setSha256(request.getSha256().trim().toLowerCase(Locale.ROOT));
            release.setApkSizeBytes(request.getApkSizeBytes());
            release.setReleaseNotes(request.getReleaseNotes() == null ? "" : request.getReleaseNotes().trim());
            release.setForceUpdate(Boolean.TRUE.equals(request.getForceUpdate()));
            release.setPublished(true);
            release.setRequestId(request.getRequestId());
            release.setCreateBy(operator(adminId));
            release.setUpdateBy(operator(adminId));
            try {
                appReleaseMapper.insert(release);
            } catch (DuplicateKeyException error) {
                AppRelease duplicateRequest = appReleaseMapper.selectByRequestId(request.getRequestId());
                if (duplicateRequest != null) {
                    requestDedupService.markDone(PLATFORM_SCOPE_ID, adminId, PUBLISH_API, request.getRequestId());
                    return duplicateRequest;
                }
                throw new BizException(409, "该构建号已发布");
            }
            requestDedupService.markDone(PLATFORM_SCOPE_ID, adminId, PUBLISH_API, request.getRequestId());
            return release;
        } catch (RuntimeException error) {
            requestDedupService.markFailed(
                    PLATFORM_SCOPE_ID,
                    adminId,
                    PUBLISH_API,
                    request.getRequestId(),
                    error.getMessage()
            );
            throw error;
        }
    }

    public AppRelease updateStatus(
            Long adminId,
            Long releaseId,
            UpdateAppReleaseStatusRequest request
    ) {
        String api = "platform:app-updates:status:" + releaseId;
        String payloadHash = sha256(String.valueOf(request.getPublished()));
        RequestDedupService.BeginResult beginResult = requestDedupService.begin(
                PLATFORM_SCOPE_ID,
                adminId,
                api,
                request.getRequestId(),
                payloadHash
        );
        if (beginResult == RequestDedupService.BeginResult.DONE) {
            return requireRelease(releaseId);
        }

        try {
            if (appReleaseMapper.updatePublishedById(releaseId, request.getPublished(), operator(adminId)) != 1) {
                throw new BizException(404, "版本清单不存在");
            }
            requestDedupService.markDone(PLATFORM_SCOPE_ID, adminId, api, request.getRequestId());
            return requireRelease(releaseId);
        } catch (RuntimeException error) {
            requestDedupService.markFailed(
                    PLATFORM_SCOPE_ID,
                    adminId,
                    api,
                    request.getRequestId(),
                    error.getMessage()
            );
            throw error;
        }
    }

    private AppRelease releaseForRequest(String requestId) {
        AppRelease release = appReleaseMapper.selectByRequestId(requestId);
        if (release == null) {
            throw new BizException(409, "请求结果不存在，请使用新的requestId重试");
        }
        return release;
    }

    private AppRelease requireRelease(Long releaseId) {
        AppRelease release = appReleaseMapper.selectById(releaseId);
        if (release == null) {
            throw new BizException(404, "版本清单不存在");
        }
        return release;
    }

    private void validateDownloadUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BizException(400, "downloadUrl必须是HTTPS地址");
            }
        } catch (IllegalArgumentException error) {
            throw new BizException(400, "downloadUrl不合法");
        }
    }

    private String operator(Long adminId) {
        return "platform:" + adminId;
    }

    private String payloadHash(CreateAppReleaseRequest request) {
        return sha256(String.join(
                "|",
                request.getPlatform(),
                String.valueOf(request.getBuildNumber()),
                request.getVersionName(),
                request.getDownloadUrl(),
                request.getSha256(),
                String.valueOf(request.getApkSizeBytes()),
                request.getReleaseNotes() == null ? "" : request.getReleaseNotes(),
                String.valueOf(Boolean.TRUE.equals(request.getForceUpdate()))
        ));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256不可用", error);
        }
    }
}
