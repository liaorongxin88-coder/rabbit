package com.rabbit.app.modules.apprelease.dto;

public class AppUpdateCheckResponse {
    private boolean hasUpdate;
    private boolean forceUpdate;
    private String id;
    private String channel;
    private String versionName;
    private Integer versionCode;
    private String releaseNotes;
    private Long sizeBytes;
    private String sha256;
    private String downloadPath;

    public static AppUpdateCheckResponse none() {
        AppUpdateCheckResponse response = new AppUpdateCheckResponse();
        response.hasUpdate = false;
        response.forceUpdate = false;
        return response;
    }

    public static AppUpdateCheckResponse of(
            AppReleaseItem release,
            boolean forceUpdate
    ) {
        AppUpdateCheckResponse response = new AppUpdateCheckResponse();
        response.hasUpdate = true;
        response.forceUpdate = forceUpdate;
        response.id = release.getId();
        response.channel = release.getChannel();
        response.versionName = release.getVersionName();
        response.versionCode = release.getVersionCode();
        response.releaseNotes = release.getReleaseNotes();
        response.sizeBytes = release.getSizeBytes();
        response.sha256 = release.getSha256();
        response.downloadPath = "/api/app/updates/" + release.getId() + "/apk";
        return response;
    }

    public boolean isHasUpdate() {
        return hasUpdate;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public String getId() {
        return id;
    }

    public String getChannel() {
        return channel;
    }

    public String getVersionName() {
        return versionName;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getDownloadPath() {
        return downloadPath;
    }
}
