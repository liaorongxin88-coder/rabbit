package com.rabbit.app.modules.report.dto;

import java.util.List;

public class DashboardSummary {
    private Long selectedHouseId;
    private Integer houseCount;
    private Integer year;
    private Integer totalRabbits;
    private Integer seedRabbits;
    private Integer maleRabbits;
    private Integer femaleRabbits;
    private Integer bredRabbits;
    private Integer readyForBreeding;
    private Integer litters;
    private Integer nursingKits;
    private Integer commodityRabbits;
    private Integer replacementRabbits;
    private Double liveRate;
    private List<Integer> monthlyBirths;
    private List<Integer> monthlyWeaned;

    public Long getSelectedHouseId() { return selectedHouseId; }
    public void setSelectedHouseId(Long selectedHouseId) { this.selectedHouseId = selectedHouseId; }
    public Integer getHouseCount() { return houseCount; }
    public void setHouseCount(Integer houseCount) { this.houseCount = houseCount; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getTotalRabbits() { return totalRabbits; }
    public void setTotalRabbits(Integer totalRabbits) { this.totalRabbits = totalRabbits; }
    public Integer getSeedRabbits() { return seedRabbits; }
    public void setSeedRabbits(Integer seedRabbits) { this.seedRabbits = seedRabbits; }
    public Integer getMaleRabbits() { return maleRabbits; }
    public void setMaleRabbits(Integer maleRabbits) { this.maleRabbits = maleRabbits; }
    public Integer getFemaleRabbits() { return femaleRabbits; }
    public void setFemaleRabbits(Integer femaleRabbits) { this.femaleRabbits = femaleRabbits; }
    public Integer getBredRabbits() { return bredRabbits; }
    public void setBredRabbits(Integer bredRabbits) { this.bredRabbits = bredRabbits; }
    public Integer getReadyForBreeding() { return readyForBreeding; }
    public void setReadyForBreeding(Integer readyForBreeding) { this.readyForBreeding = readyForBreeding; }
    public Integer getLitters() { return litters; }
    public void setLitters(Integer litters) { this.litters = litters; }
    public Integer getNursingKits() { return nursingKits; }
    public void setNursingKits(Integer nursingKits) { this.nursingKits = nursingKits; }
    public Integer getCommodityRabbits() { return commodityRabbits; }
    public void setCommodityRabbits(Integer commodityRabbits) { this.commodityRabbits = commodityRabbits; }
    public Integer getReplacementRabbits() { return replacementRabbits; }
    public void setReplacementRabbits(Integer replacementRabbits) { this.replacementRabbits = replacementRabbits; }
    public Double getLiveRate() { return liveRate; }
    public void setLiveRate(Double liveRate) { this.liveRate = liveRate; }
    public List<Integer> getMonthlyBirths() { return monthlyBirths; }
    public void setMonthlyBirths(List<Integer> monthlyBirths) { this.monthlyBirths = monthlyBirths; }
    public List<Integer> getMonthlyWeaned() { return monthlyWeaned; }
    public void setMonthlyWeaned(List<Integer> monthlyWeaned) { this.monthlyWeaned = monthlyWeaned; }
}
