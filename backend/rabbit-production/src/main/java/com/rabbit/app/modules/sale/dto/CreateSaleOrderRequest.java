package com.rabbit.app.modules.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class CreateSaleOrderRequest {
    @NotEmpty(message = "rabbitIds不能为空")
    private List<Long> rabbitIds;

    @NotNull(message = "saleTime不能为空")
    private Date saleTime;

    @NotNull(message = "totalWeight不能为空")
    private Double totalWeight;

    private BigDecimal unitPrice;
    private BigDecimal unitPricePerKg;
    private List<@NotNull(message = "batchAllocations不能包含空项") @Valid SaleBatchAllocationInput> batchAllocations;
    private String customer;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    private String requestId;

    public List<Long> getRabbitIds() {
        return rabbitIds;
    }

    public void setRabbitIds(List<Long> rabbitIds) {
        this.rabbitIds = rabbitIds;
    }

    public Date getSaleTime() {
        return saleTime;
    }

    public void setSaleTime(Date saleTime) {
        this.saleTime = saleTime;
    }

    public Double getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(Double totalWeight) {
        this.totalWeight = totalWeight;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getUnitPricePerKg() {
        return unitPricePerKg;
    }

    public void setUnitPricePerKg(BigDecimal unitPricePerKg) {
        this.unitPricePerKg = unitPricePerKg;
    }

    public List<SaleBatchAllocationInput> getBatchAllocations() {
        return batchAllocations;
    }

    public void setBatchAllocations(List<SaleBatchAllocationInput> batchAllocations) {
        this.batchAllocations = batchAllocations;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
