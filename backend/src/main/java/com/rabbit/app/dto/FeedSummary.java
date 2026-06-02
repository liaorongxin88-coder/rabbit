package com.rabbit.app.dto;

import java.math.BigDecimal;

public class FeedSummary {
    private Integer recordCount;
    private BigDecimal totalAmount;

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
