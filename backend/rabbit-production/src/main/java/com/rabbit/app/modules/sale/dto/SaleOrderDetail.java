package com.rabbit.app.modules.sale.dto;

import com.rabbit.app.modules.sale.entity.SaleOrder;
import java.util.List;

public class SaleOrderDetail {
    private SaleOrder order;
    private List<SaleOrderItemView> items;

    public SaleOrder getOrder() {
        return order;
    }

    public void setOrder(SaleOrder order) {
        this.order = order;
    }

    public List<SaleOrderItemView> getItems() {
        return items;
    }

    public void setItems(List<SaleOrderItemView> items) {
        this.items = items;
    }
}

