package com.rabbit.app.modules.appupdate.dto;

import com.rabbit.app.modules.appupdate.entity.AppRelease;

public class AppUpdateCheckResponse {
    private Long currentBuild;
    private boolean updateAvailable;
    private Long buildNumber;
    private String versionName;
    private String downloadUrl;
    private String sha256;
    private Long apkSizeBytes;
    private String releaseNotes;
    private boolean forceUpdate;

    public static AppUpdateCheckResponse upToDate(Long currentBuild) {
        AppUpdateCheckResponse response = new AppUpdateCheckResponse();
        response.setCurrentBuild(currentBuild);
        response.setUpdateAvailable(false);
        return response;
    }

    public static AppUpdateCheckResponse available(Long currentBuild, AppRelease release) {
        return available(currentBuild, release, release.getDownloadUrl());
    }

    /**
     * @param downloadUrl 回给客户端的地址。代理模式下它指向后端，
     *                    与版本清单里登记的上游地址不同
     */
    public static AppUpdateCheckResponse available(
            Long currentBuild, AppRelease release, String downloadUrl) {
        AppUpdateCheckResponse response = new AppUpdateCheckResponse();
        response.setCurrentBuild(currentBuild);
        response.setUpdateAvailable(true);
        response.setBuildNumber(release.getBuildNumber());
        response.setVersionName(release.getVersionName());
        response.setDownloadUrl(downloadUrl);
        response.setSha256(release.getSha256());
        response.setApkSizeBytes(release.getApkSizeBytes());
        response.setReleaseNotes(release.getReleaseNotes());
        response.setForceUpdate(Boolean.TRUE.equals(release.getForceUpdate()));
        return response;
    }

    public Long getCurrentBuild() {
        return currentBuild;
    }

    public void setCurrentBuild(Long currentBuild) {
        this.currentBuild = currentBuild;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public void setUpdateAvailable(boolean updateAvailable) {
        this.updateAvailable = updateAvailable;
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

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }
}
