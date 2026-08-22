package com.rabbit.app.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateInventoryItemRequest {
    @NotBlank(message = "name不能为空")
    private String name;

    @NotBlank(message = "unit不能为空")
    private String unit;

    @NotNull(message = "initQty不能为空")
    private BigDecimal initQty;

    private BigDecimal lowStockQty;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getInitQty() {
        return initQty;
    }

    public void setInitQty(BigDecimal initQty) {
        this.initQty = initQty;
    }

    public BigDecimal getLowStockQty() {
        return lowStockQty;
    }

    public void setLowStockQty(BigDecimal lowStockQty) {
        this.lowStockQty = lowStockQty;
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
