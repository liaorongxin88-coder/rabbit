package com.rabbit.app.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class AdminFarmItem {
    private Long id;
    private String name;
    private String status;
    private List<String> ownerNames = List.of();
    private int ownerCount;
    private long memberCount;
    private long cageCount;
    private long rabbitCount;
    private Integer layoutRows;
    private Integer layoutCols;
    private Integer layoutLayers;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private String ownerNamesText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getOwnerNames() { return ownerNames; }
    public void setOwnerNames(List<String> ownerNames) { this.ownerNames = ownerNames == null ? List.of() : List.copyOf(ownerNames); }
    public int getOwnerCount() { return ownerCount; }
    public void setOwnerCount(int ownerCount) { this.ownerCount = ownerCount; }
    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }
    public long getCageCount() { return cageCount; }
    public void setCageCount(long cageCount) { this.cageCount = cageCount; }
    public long getRabbitCount() { return rabbitCount; }
    public void setRabbitCount(long rabbitCount) { this.rabbitCount = rabbitCount; }
    public Integer getLayoutRows() { return layoutRows; }
    public void setLayoutRows(Integer layoutRows) { this.layoutRows = layoutRows; }
    public Integer getLayoutCols() { return layoutCols; }
    public void setLayoutCols(Integer layoutCols) { this.layoutCols = layoutCols; }
    public Integer getLayoutLayers() { return layoutLayers; }
    public void setLayoutLayers(Integer layoutLayers) { this.layoutLayers = layoutLayers; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    @JsonIgnore
    public String getOwnerNamesText() { return ownerNamesText; }

    public void setOwnerNamesText(String ownerNamesText) {
        this.ownerNamesText = ownerNamesText;
        this.ownerNames = ownerNamesText == null || ownerNamesText.isBlank()
                ? List.of()
                : Arrays.stream(ownerNamesText.split("\\n")).filter(value -> !value.isBlank()).toList();
    }
}
