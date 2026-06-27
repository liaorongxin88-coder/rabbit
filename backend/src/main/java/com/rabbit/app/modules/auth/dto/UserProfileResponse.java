package com.rabbit.app.modules.auth.dto;

import com.rabbit.app.modules.auth.entity.SysUser;
import java.util.Date;

public class UserProfileResponse {
    private Long userId;
    private String userName;
    private Boolean openidBound;
    private Date createTime;
    private Date updateTime;

    public UserProfileResponse() {
    }

    public UserProfileResponse(SysUser user) {
        this.userId = user.getUserId();
        this.userName = user.getUserName();
        this.openidBound = user.getOpenid() != null && !user.getOpenid().trim().isEmpty();
        this.createTime = user.getCreateTime();
        this.updateTime = user.getUpdateTime();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Boolean getOpenidBound() {
        return openidBound;
    }

    public void setOpenidBound(Boolean openidBound) {
        this.openidBound = openidBound;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
