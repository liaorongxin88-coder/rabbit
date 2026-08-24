package com.rabbit.app.modules.apprelease.dto;

import com.rabbit.app.modules.apprelease.entity.AppRelease;
import java.util.Date;

public class AppReleaseItem {
    private String id;
    private String channel;
    private String versionName;
    private Integer versionCode;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String releaseNotes;
    private boolean forceUpdate;
    private String status;
    private Date publishedAt;
    private Date createTime;
    private Date updateTime;

    public static AppReleaseItem from(AppRelease release) {
        AppReleaseItem item = new AppReleaseItem();
        item.id = release.getId();
        item.channel = release.getChannel();
        item.versionName = release.getVersionName();
        item.versionCode = release.getVersionCode();
        item.fileName = release.getFileName();
        item.contentType = release.getContentType();
        item.sizeBytes = release.getSizeBytes();
        item.sha256 = release.getSha256();
        item.releaseNotes = release.getReleaseNotes();
        item.forceUpdate = Boolean.TRUE.equals(release.getForceUpdate());
        item.status = release.getStatus();
        item.publishedAt = release.getPublishedAt();
        item.createTime = release.getCreateTime();
        item.updateTime = release.getUpdateTime();
        return item;
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

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public String getStatus() {
        return status;
    }

    public Date getPublishedAt() {
        return publishedAt;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }
}
