package com.rabbit.app.modules.house.entity;

import java.util.Date;

public class RabbitHouse {
    private Long id;
    private Long merchantId;
    private String name;
    private Integer layoutRows;
    private Integer layoutCols;
    private Integer layoutLayers;
    private String requestId;
    private String remark;
    private Boolean isDeleted;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLayoutRows() {
        return layoutRows;
    }

    public void setLayoutRows(Integer layoutRows) {
        this.layoutRows = layoutRows;
    }

    public Integer getLayoutCols() {
        return layoutCols;
    }

    public void setLayoutCols(Integer layoutCols) {
        this.layoutCols = layoutCols;
    }

    public Integer getLayoutLayers() {
        return layoutLayers;
    }

    public void setLayoutLayers(Integer layoutLayers) {
        this.layoutLayers = layoutLayers;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
