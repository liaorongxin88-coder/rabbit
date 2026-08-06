package com.rabbit.app.modules.auth.dto;

import com.rabbit.app.modules.auth.entity.SysUser;
import java.util.Date;
import java.util.List;

public class UserProfileResponse {
    private Long userId;
    private String userName;
    private Boolean openidBound;
    private Boolean phoneBound;
    private String maskedPhone;
    private Date createTime;
    private Date updateTime;
    private List<String> permissions = List.of();

    public UserProfileResponse() {
    }

    public UserProfileResponse(SysUser user) {
        this.userId = user.getUserId();
        this.userName = user.getUserName();
        this.openidBound = user.getOpenid() != null && !user.getOpenid().trim().isEmpty();
        this.phoneBound = user.getPhoneBoundTime() != null;
        this.maskedPhone = user.getPhoneMasked();
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

    public Boolean getPhoneBound() {
        return phoneBound;
    }

    public void setPhoneBound(Boolean phoneBound) {
        this.phoneBound = phoneBound;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public void setMaskedPhone(String maskedPhone) {
        this.maskedPhone = maskedPhone;
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

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
