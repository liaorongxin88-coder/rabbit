package com.rabbit.app.modules.batch.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BatchStatisticsRawSnapshot {
    private Long batchId;
    private String houseName;
    private String batchCode;
    private LocalDate endDate;
    private Integer matedCycleCount;
    private Integer matedDoeCount;
    private Integer pregnantCycleCount;
    private Integer pregnantDoeCount;
    private Integer matedBuckCount;
    private Boolean missingNaturalMale;
    private Integer abortedPregnantCycleCount;
    private Boolean missingPregnancyEvidence;
    private Integer totalLitters;
    private Integer totalKits;
    private Integer totalLiveKits;
    private Integer keptLitterCount;
    private Integer totalKept;
    private Integer totalWeaned;
    private BigDecimal totalWeaningWeightKg;
    private Boolean missingWeaningWeight;
    private Integer soldRabbitCount;
    private Boolean missingBatchAttribution;
    private BigDecimal soldWeightKg;
    private BigDecimal totalSalesAmount;
    private Boolean missingBatchSaleAllocation;
    private Boolean missingSaleUnitPrice;
    private BigDecimal breedingFeedAmountKg;
    private BigDecimal fatteningFeedAmountKg;
    private Boolean missingFeedAllocation;
    private Boolean missingFeedUnit;
    private BigDecimal replacementWeightKg;
    private Boolean missingReplacementWeight;
    private BigDecimal carcassYieldRate;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getHouseName() {
        return houseName;
    }

    public void setHouseName(String houseName) {
        this.houseName = houseName;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getMatedCycleCount() {
        return matedCycleCount;
    }

    public void setMatedCycleCount(Integer matedCycleCount) {
        this.matedCycleCount = matedCycleCount;
    }

    public Integer getMatedDoeCount() {
        return matedDoeCount;
    }

    public void setMatedDoeCount(Integer matedDoeCount) {
        this.matedDoeCount = matedDoeCount;
    }

    public Integer getPregnantCycleCount() {
        return pregnantCycleCount;
    }

    public void setPregnantCycleCount(Integer pregnantCycleCount) {
        this.pregnantCycleCount = pregnantCycleCount;
    }

    public Integer getPregnantDoeCount() {
        return pregnantDoeCount;
    }

    public void setPregnantDoeCount(Integer pregnantDoeCount) {
        this.pregnantDoeCount = pregnantDoeCount;
    }

    public Integer getMatedBuckCount() {
        return matedBuckCount;
    }

    public void setMatedBuckCount(Integer matedBuckCount) {
        this.matedBuckCount = matedBuckCount;
    }

    public Boolean getMissingNaturalMale() {
        return missingNaturalMale;
    }

    public void setMissingNaturalMale(Boolean missingNaturalMale) {
        this.missingNaturalMale = missingNaturalMale;
    }

    public Integer getAbortedPregnantCycleCount() {
        return abortedPregnantCycleCount;
    }

    public void setAbortedPregnantCycleCount(Integer abortedPregnantCycleCount) {
        this.abortedPregnantCycleCount = abortedPregnantCycleCount;
    }

    public Boolean getMissingPregnancyEvidence() {
        return missingPregnancyEvidence;
    }

    public void setMissingPregnancyEvidence(Boolean missingPregnancyEvidence) {
        this.missingPregnancyEvidence = missingPregnancyEvidence;
    }

    public Integer getTotalLitters() {
        return totalLitters;
    }

    public void setTotalLitters(Integer totalLitters) {
        this.totalLitters = totalLitters;
    }

    public Integer getTotalKits() {
        return totalKits;
    }

    public void setTotalKits(Integer totalKits) {
        this.totalKits = totalKits;
    }

    public Integer getTotalLiveKits() {
        return totalLiveKits;
    }

    public void setTotalLiveKits(Integer totalLiveKits) {
        this.totalLiveKits = totalLiveKits;
    }

    public Integer getKeptLitterCount() {
        return keptLitterCount;
    }

    public void setKeptLitterCount(Integer keptLitterCount) {
        this.keptLitterCount = keptLitterCount;
    }

    public Integer getTotalKept() {
        return totalKept;
    }

    public void setTotalKept(Integer totalKept) {
        this.totalKept = totalKept;
    }

    public Integer getTotalWeaned() {
        return totalWeaned;
    }

    public void setTotalWeaned(Integer totalWeaned) {
        this.totalWeaned = totalWeaned;
    }

    public BigDecimal getTotalWeaningWeightKg() {
        return totalWeaningWeightKg;
    }

    public void setTotalWeaningWeightKg(BigDecimal totalWeaningWeightKg) {
        this.totalWeaningWeightKg = totalWeaningWeightKg;
    }

    public Boolean getMissingWeaningWeight() {
        return missingWeaningWeight;
    }

    public void setMissingWeaningWeight(Boolean missingWeaningWeight) {
        this.missingWeaningWeight = missingWeaningWeight;
    }

    public Integer getSoldRabbitCount() {
        return soldRabbitCount;
    }

    public void setSoldRabbitCount(Integer soldRabbitCount) {
        this.soldRabbitCount = soldRabbitCount;
    }

    public Boolean getMissingBatchAttribution() {
        return missingBatchAttribution;
    }

    public void setMissingBatchAttribution(Boolean missingBatchAttribution) {
        this.missingBatchAttribution = missingBatchAttribution;
    }

    public BigDecimal getSoldWeightKg() {
        return soldWeightKg;
    }

    public void setSoldWeightKg(BigDecimal soldWeightKg) {
        this.soldWeightKg = soldWeightKg;
    }

    public BigDecimal getTotalSalesAmount() {
        return totalSalesAmount;
    }

    public void setTotalSalesAmount(BigDecimal totalSalesAmount) {
        this.totalSalesAmount = totalSalesAmount;
    }

    public Boolean getMissingBatchSaleAllocation() {
        return missingBatchSaleAllocation;
    }

    public void setMissingBatchSaleAllocation(Boolean missingBatchSaleAllocation) {
        this.missingBatchSaleAllocation = missingBatchSaleAllocation;
    }

    public Boolean getMissingSaleUnitPrice() {
        return missingSaleUnitPrice;
    }

    public void setMissingSaleUnitPrice(Boolean missingSaleUnitPrice) {
        this.missingSaleUnitPrice = missingSaleUnitPrice;
    }

    public BigDecimal getBreedingFeedAmountKg() {
        return breedingFeedAmountKg;
    }

    public void setBreedingFeedAmountKg(BigDecimal breedingFeedAmountKg) {
        this.breedingFeedAmountKg = breedingFeedAmountKg;
    }

    public BigDecimal getFatteningFeedAmountKg() {
        return fatteningFeedAmountKg;
    }

    public void setFatteningFeedAmountKg(BigDecimal fatteningFeedAmountKg) {
        this.fatteningFeedAmountKg = fatteningFeedAmountKg;
    }

    public Boolean getMissingFeedAllocation() {
        return missingFeedAllocation;
    }

    public void setMissingFeedAllocation(Boolean missingFeedAllocation) {
        this.missingFeedAllocation = missingFeedAllocation;
    }

    public Boolean getMissingFeedUnit() {
        return missingFeedUnit;
    }

    public void setMissingFeedUnit(Boolean missingFeedUnit) {
        this.missingFeedUnit = missingFeedUnit;
    }

    public BigDecimal getReplacementWeightKg() {
        return replacementWeightKg;
    }

    public void setReplacementWeightKg(BigDecimal replacementWeightKg) {
        this.replacementWeightKg = replacementWeightKg;
    }

    public Boolean getMissingReplacementWeight() {
        return missingReplacementWeight;
    }

    public void setMissingReplacementWeight(Boolean missingReplacementWeight) {
        this.missingReplacementWeight = missingReplacementWeight;
    }

    public BigDecimal getCarcassYieldRate() {
        return carcassYieldRate;
    }

    public void setCarcassYieldRate(BigDecimal carcassYieldRate) {
        this.carcassYieldRate = carcassYieldRate;
    }
}
