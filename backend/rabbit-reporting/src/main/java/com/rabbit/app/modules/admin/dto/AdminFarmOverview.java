package com.rabbit.app.modules.admin.dto;

import com.rabbit.app.modules.audit.entity.AuditLog;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import java.util.List;

public class AdminFarmOverview {
    private AdminFarmItem farm;
    private long memberCount;
    private long cageCount;
    private long rabbitCount;
    private long batchCount;
    private List<HouseMemberItem> members = List.of();
    private List<AuditLog> recentAuditLogs = List.of();

    public AdminFarmItem getFarm() { return farm; }
    public void setFarm(AdminFarmItem farm) { this.farm = farm; }
    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }
    public long getCageCount() { return cageCount; }
    public void setCageCount(long cageCount) { this.cageCount = cageCount; }
    public long getRabbitCount() { return rabbitCount; }
    public void setRabbitCount(long rabbitCount) { this.rabbitCount = rabbitCount; }
    public long getBatchCount() { return batchCount; }
    public void setBatchCount(long batchCount) { this.batchCount = batchCount; }
    public List<HouseMemberItem> getMembers() { return members; }
    public void setMembers(List<HouseMemberItem> members) { this.members = members == null ? List.of() : List.copyOf(members); }
    public List<AuditLog> getRecentAuditLogs() { return recentAuditLogs; }
    public void setRecentAuditLogs(List<AuditLog> logs) { this.recentAuditLogs = logs == null ? List.of() : List.copyOf(logs); }
}
