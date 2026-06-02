package com.rabbit.app.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

public class CreateInventoryTxRequest {
    @NotNull(message = "itemId不能为空")
    private Long itemId;

    @NotBlank(message = "txType不能为空")
    private String txType;

    @NotNull(message = "qtyDelta不能为空")
    private BigDecimal qtyDelta;

    private Date txTime;
    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getTxType() {
        return txType;
    }

    public void setTxType(String txType) {
        this.txType = txType;
    }

    public BigDecimal getQtyDelta() {
        return qtyDelta;
    }

    public void setQtyDelta(BigDecimal qtyDelta) {
        this.qtyDelta = qtyDelta;
    }

    public Date getTxTime() {
        return txTime;
    }

    public void setTxTime(Date txTime) {
        this.txTime = txTime;
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
