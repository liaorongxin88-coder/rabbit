package com.rabbit.app.modules.admin.dto;

import java.util.Date;

public class AdminBusinessUserItem {
    private Long userId;
    private String userName;
    private Boolean phoneBound;
    private String phoneMasked;
    private String status;
    private Boolean enabled;
    private long houseCount;
    private Date lastLoginTime;
    private Date createTime;
    private Date updateTime;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Boolean getPhoneBound() { return phoneBound; }
    public void setPhoneBound(Boolean phoneBound) { this.phoneBound = phoneBound; }
    public String getPhoneMasked() { return phoneMasked; }
    public void setPhoneMasked(String phoneMasked) { this.phoneMasked = phoneMasked; }
    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = status;
        this.enabled = "ENABLED".equals(status);
    }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public long getHouseCount() { return houseCount; }
    public void setHouseCount(long houseCount) { this.houseCount = houseCount; }
    public Date getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(Date lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
