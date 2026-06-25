package com.rabbit.app.modules.house.dto;

public class HousePermissionInfo {
    private String perms;
    private Boolean isAdmin;

    public String getPerms() {
        return perms;
    }

    public void setPerms(String perms) {
        this.perms = perms;
    }

    public Boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Boolean admin) {
        isAdmin = admin;
    }
}

