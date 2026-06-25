package com.rabbit.app.modules.cage.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class CageSummary {
    private Long cageId;
    private String cageNumber;
    private Integer rabbitCount;
    private Boolean isFed;

    private Date lastFeedTime;
    private String lastFeedType;
    private BigDecimal lastFeedAmount;
    private String lastFeedUnit;

    private Integer abnormalUndealCount;
    private Date lastAbnormalTime;
    private String lastAbnormalStatus;

    private List<CageRabbitBrief> rabbits;

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public String getCageNumber() {
        return cageNumber;
    }

    public void setCageNumber(String cageNumber) {
        this.cageNumber = cageNumber;
    }

    public Integer getRabbitCount() {
        return rabbitCount;
    }

    public void setRabbitCount(Integer rabbitCount) {
        this.rabbitCount = rabbitCount;
    }

    public Boolean getIsFed() {
        return isFed;
    }

    public void setIsFed(Boolean fed) {
        isFed = fed;
    }

    public Date getLastFeedTime() {
        return lastFeedTime;
    }

    public void setLastFeedTime(Date lastFeedTime) {
        this.lastFeedTime = lastFeedTime;
    }

    public String getLastFeedType() {
        return lastFeedType;
    }

    public void setLastFeedType(String lastFeedType) {
        this.lastFeedType = lastFeedType;
    }

    public BigDecimal getLastFeedAmount() {
        return lastFeedAmount;
    }

    public void setLastFeedAmount(BigDecimal lastFeedAmount) {
        this.lastFeedAmount = lastFeedAmount;
    }

    public String getLastFeedUnit() {
        return lastFeedUnit;
    }

    public void setLastFeedUnit(String lastFeedUnit) {
        this.lastFeedUnit = lastFeedUnit;
    }

    public Integer getAbnormalUndealCount() {
        return abnormalUndealCount;
    }

    public void setAbnormalUndealCount(Integer abnormalUndealCount) {
        this.abnormalUndealCount = abnormalUndealCount;
    }

    public Date getLastAbnormalTime() {
        return lastAbnormalTime;
    }

    public void setLastAbnormalTime(Date lastAbnormalTime) {
        this.lastAbnormalTime = lastAbnormalTime;
    }

    public String getLastAbnormalStatus() {
        return lastAbnormalStatus;
    }

    public void setLastAbnormalStatus(String lastAbnormalStatus) {
        this.lastAbnormalStatus = lastAbnormalStatus;
    }

    public List<CageRabbitBrief> getRabbits() {
        return rabbits;
    }

    public void setRabbits(List<CageRabbitBrief> rabbits) {
        this.rabbits = rabbits;
    }
}
