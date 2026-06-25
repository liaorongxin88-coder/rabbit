package com.rabbit.app.modules.admin.dto;

import com.rabbit.app.modules.audit.entity.AuditLog;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import java.util.List;

public class MerchantOverview {
    private long houseCount;
    private long userCount;
    private long cageCount;
    private long rabbitCount;
    private List<RabbitHouse> houses;
    private List<AuditLog> recentAuditLogs;

    public long getHouseCount() {
        return houseCount;
    }

    public void setHouseCount(long houseCount) {
        this.houseCount = houseCount;
    }

    public long getUserCount() {
        return userCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }

    public long getCageCount() {
        return cageCount;
    }

    public void setCageCount(long cageCount) {
        this.cageCount = cageCount;
    }

    public long getRabbitCount() {
        return rabbitCount;
    }

    public void setRabbitCount(long rabbitCount) {
        this.rabbitCount = rabbitCount;
    }

    public List<RabbitHouse> getHouses() {
        return houses;
    }

    public void setHouses(List<RabbitHouse> houses) {
        this.houses = houses;
    }

    public List<AuditLog> getRecentAuditLogs() {
        return recentAuditLogs;
    }

    public void setRecentAuditLogs(List<AuditLog> recentAuditLogs) {
        this.recentAuditLogs = recentAuditLogs;
    }
}
