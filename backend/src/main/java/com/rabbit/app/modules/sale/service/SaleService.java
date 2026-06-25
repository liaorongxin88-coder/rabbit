package com.rabbit.app.modules.sale.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import com.rabbit.app.modules.sale.dto.SaleOrderDetail;
import com.rabbit.app.modules.sale.dto.SaleOrderItemView;
import com.rabbit.app.modules.sale.entity.SaleOrder;
import com.rabbit.app.modules.sale.entity.SaleOrderItem;
import com.rabbit.app.modules.sale.mapper.SaleOrderItemMapper;
import com.rabbit.app.modules.sale.mapper.SaleOrderMapper;
import com.rabbit.app.util.DateUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final RabbitService rabbitService;
    private final RequestDedupService requestDedupService;

    public SaleService(SaleOrderMapper saleOrderMapper, SaleOrderItemMapper saleOrderItemMapper, RabbitService rabbitService, RequestDedupService requestDedupService) {
        this.saleOrderMapper = saleOrderMapper;
        this.saleOrderItemMapper = saleOrderItemMapper;
        this.rabbitService = rabbitService;
        this.requestDedupService = requestDedupService;
    }

    @Transactional
    public SaleOrder create(Long userId, Long houseId, List<Long> rabbitIds, Date saleTime, Double totalWeight, BigDecimal unitPrice, String customer, String remark, String requestId) {
        String api = "sale:create";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            SaleOrder old = saleOrderMapper.selectByReq(houseId, requestId);
            if (old != null) {
                return old;
            }
            return null;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (rabbitIds == null || rabbitIds.isEmpty()) {
                throw new BizException(400, "rabbitIds不能为空");
            }
            if (saleTime == null) {
                saleTime = DateUtil.now();
            }
            if (totalWeight == null || totalWeight <= 0) {
                throw new BizException(400, "totalWeight不合法");
            }
            if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(400, "unitPrice不合法");
            }

            SaleOrder order = new SaleOrder();
            order.setHouseId(houseId);
            order.setSaleTime(saleTime);
            order.setCustomer(customer);
            order.setTotalWeight(totalWeight);
            order.setUnitPrice(unitPrice);
            if (unitPrice != null) {
                BigDecimal amt = unitPrice.multiply(new BigDecimal(String.valueOf(totalWeight)));
                order.setTotalAmount(amt);
            }
            order.setRemark(remark);
            order.setRequestId(requestId);
            order.setCreateBy(String.valueOf(userId));
            order.setUpdateBy(String.valueOf(userId));
            saleOrderMapper.insert(order);

            List<SaleOrderItem> items = new ArrayList<SaleOrderItem>();
            LinkedHashSet<Long> unique = new LinkedHashSet<Long>(rabbitIds);
            for (Long rid : unique) {
                if (rid == null || rid <= 0) {
                    continue;
                }
                SaleOrderItem it = new SaleOrderItem();
                it.setSaleOrderId(order.getId());
                it.setRabbitId(rid);
                it.setWeight(null);
                it.setPrice(null);
                it.setCreateBy(String.valueOf(userId));
                it.setUpdateBy(String.valueOf(userId));
                items.add(it);
            }
            if (items.isEmpty()) {
                throw new BizException(400, "rabbitIds不合法");
            }
            saleOrderItemMapper.insertBatch(items);

            for (SaleOrderItem it : items) {
                String ridReq = requestId == null ? null : requestId + "-" + it.getRabbitId();
                rabbitService.rabbitEvent(userId, houseId, it.getRabbitId(), "sale", saleTime, "销售出栏", "saleOrder#" + order.getId(), true, ridReq);
            }

            requestDedupService.markDone(houseId, userId, api, requestId);
            return order;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    public List<SaleOrder> listPage(Long houseId, int page, int pageSize) {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
        int offset = (page - 1) * pageSize;
        return saleOrderMapper.selectPageByHouse(houseId, offset, pageSize);
    }

    public SaleOrderDetail getDetail(Long houseId, Long saleOrderId) {
        if (saleOrderId == null || saleOrderId <= 0) {
            throw new BizException(400, "saleOrderId不能为空");
        }
        SaleOrder order = saleOrderMapper.selectById(houseId, saleOrderId);
        if (order == null) {
            throw new BizException(404, "销售单不存在");
        }
        SaleOrderDetail d = new SaleOrderDetail();
        d.setOrder(order);
        List<SaleOrderItemView> items = saleOrderItemMapper.selectViewByOrder(houseId, saleOrderId);
        d.setItems(items);
        return d;
    }
}
