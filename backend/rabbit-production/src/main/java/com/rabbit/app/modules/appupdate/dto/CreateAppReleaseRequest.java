package com.rabbit.app.modules.appupdate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateAppReleaseRequest {
    @NotBlank(message = "platform不能为空")
    @Pattern(regexp = "ANDROID", message = "仅支持ANDROID版本清单")
    private String platform;

    @NotNull(message = "buildNumber不能为空")
    @Min(value = 1, message = "buildNumber必须大于0")
    private Long buildNumber;

    @NotBlank(message = "versionName不能为空")
    @Size(max = 64, message = "versionName不能超过64个字符")
    private String versionName;

    @NotBlank(message = "downloadUrl不能为空")
    @Size(max = 2048, message = "downloadUrl不能超过2048个字符")
    private String downloadUrl;

    @NotBlank(message = "sha256不能为空")
    @Pattern(regexp = "(?i)[0-9a-f]{64}", message = "sha256必须是64位十六进制摘要")
    private String sha256;

    @NotNull(message = "apkSizeBytes不能为空")
    @Min(value = 1, message = "apkSizeBytes必须大于0")
    @Max(value = 536870912, message = "APK不能超过512MB")
    private Long apkSizeBytes;

    @Size(max = 1000, message = "releaseNotes不能超过1000个字符")
    private String releaseNotes;

    private Boolean forceUpdate;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "requestId不合法")
    private String requestId;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Long getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(Long buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Long getApkSizeBytes() {
        return apkSizeBytes;
    }

    public void setApkSizeBytes(Long apkSizeBytes) {
        this.apkSizeBytes = apkSizeBytes;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public Boolean getForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(Boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
