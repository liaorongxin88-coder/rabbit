package com.rabbit.app.modules.setting.entity;

import java.util.Date;

public class GlobalSetting {
    private Long id;
    private Long houseId;
    private Long userId;
    private Integer aphrodisiacDays;
    private Integer palpationDays;
    private Integer prepartumDays;
    private Integer weaningDays;
    private Integer postpartumDays;
    private Integer adaptationDays;
    private Integer growingDays;
    private Integer fatteningDays;
    private Integer saleDays;
    private Integer replacementDays;
    private String remark;
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

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getAphrodisiacDays() {
        return aphrodisiacDays;
    }

    public void setAphrodisiacDays(Integer aphrodisiacDays) {
        this.aphrodisiacDays = aphrodisiacDays;
    }

    public Integer getPalpationDays() {
        return palpationDays;
    }

    public void setPalpationDays(Integer palpationDays) {
        this.palpationDays = palpationDays;
    }

    public Integer getPrepartumDays() {
        return prepartumDays;
    }

    public void setPrepartumDays(Integer prepartumDays) {
        this.prepartumDays = prepartumDays;
    }

    public Integer getWeaningDays() {
        return weaningDays;
    }

    public void setWeaningDays(Integer weaningDays) {
        this.weaningDays = weaningDays;
    }

    public Integer getPostpartumDays() {
        return postpartumDays;
    }

    public void setPostpartumDays(Integer postpartumDays) {
        this.postpartumDays = postpartumDays;
    }

    public Integer getAdaptationDays() {
        return adaptationDays;
    }

    public void setAdaptationDays(Integer adaptationDays) {
        this.adaptationDays = adaptationDays;
    }

    public Integer getGrowingDays() {
        return growingDays;
    }

    public void setGrowingDays(Integer growingDays) {
        this.growingDays = growingDays;
    }

    public Integer getFatteningDays() {
        return fatteningDays;
    }

    public void setFatteningDays(Integer fatteningDays) {
        this.fatteningDays = fatteningDays;
    }

    /** 新模型按三个商品兔阶段之和计算成熟日；旧数据缺列时回落 sale_days。 */
    public int commodityMaturityDays() {
        if (adaptationDays != null && adaptationDays > 0
            && growingDays != null && growingDays > 0
            && fatteningDays != null && fatteningDays > 0) {
            return adaptationDays + growingDays + fatteningDays;
        }
        return saleDays == null || saleDays <= 0 ? 33 : saleDays;
    }

    public Integer getSaleDays() {
        return saleDays;
    }

    public void setSaleDays(Integer saleDays) {
        this.saleDays = saleDays;
    }

    public Integer getReplacementDays() {
        return replacementDays;
    }

    public void setReplacementDays(Integer replacementDays) {
        this.replacementDays = replacementDays;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
