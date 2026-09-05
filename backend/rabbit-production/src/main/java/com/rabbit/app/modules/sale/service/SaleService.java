package com.rabbit.app.modules.sale.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.BatchRabbit;
import com.rabbit.app.modules.batch.mapper.BatchRabbitMapper;
import com.rabbit.app.modules.batch.support.BatchWritePayloadHasher;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import com.rabbit.app.modules.sale.dto.CreateSaleOrderRequest;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import com.rabbit.app.modules.sale.dto.SaleOrderDetail;
import com.rabbit.app.modules.sale.dto.SaleOrderItemView;
import com.rabbit.app.modules.sale.entity.SaleOrder;
import com.rabbit.app.modules.sale.entity.SaleOrderItem;
import com.rabbit.app.modules.sale.mapper.SaleOrderItemMapper;
import com.rabbit.app.modules.sale.mapper.SaleOrderMapper;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.util.DateUtil;
import com.rabbit.app.util.RequestIdUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final RabbitService rabbitService;
    private final RequestDedupService requestDedupService;
    private final RabbitMapper rabbitMapper;
    private final BatchRabbitMapper batchRabbitMapper;
    private final SaleBatchAllocationService batchAllocationService;

    public SaleService(
        SaleOrderMapper saleOrderMapper,
        SaleOrderItemMapper saleOrderItemMapper,
        RabbitService rabbitService,
        RequestDedupService requestDedupService
    ) {
        this(
            saleOrderMapper,
            saleOrderItemMapper,
            rabbitService,
            requestDedupService,
            null,
            null,
            null
        );
    }

    @Autowired
    public SaleService(
        SaleOrderMapper saleOrderMapper,
        SaleOrderItemMapper saleOrderItemMapper,
        RabbitService rabbitService,
        RequestDedupService requestDedupService,
        RabbitMapper rabbitMapper,
        BatchRabbitMapper batchRabbitMapper,
        SaleBatchAllocationService batchAllocationService
    ) {
        this.saleOrderMapper = saleOrderMapper;
        this.saleOrderItemMapper = saleOrderItemMapper;
        this.rabbitService = rabbitService;
        this.requestDedupService = requestDedupService;
        this.rabbitMapper = rabbitMapper;
        this.batchRabbitMapper = batchRabbitMapper;
        this.batchAllocationService = batchAllocationService;
    }

    public void assertRequestAllowed(
        Long userId,
        Long houseId,
        CreateSaleOrderRequest request
    ) {
        if (batchAllocationService == null) {
            return;
        }
        if (requestDedupService.shouldSkipAsDone(
            houseId, userId, "sale:create", request.getRequestId()
        )) {
            return;
        }
        if (request.getRabbitIds() == null || request.getRabbitIds().isEmpty()) {
            throw new BizException(400, "rabbitIds不能为空");
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long rabbitId : request.getRabbitIds()) {
            if (rabbitId == null || rabbitId <= 0 || !uniqueIds.add(rabbitId)) {
                throw new BizException(400, "rabbitIds包含无效或重复值");
            }
            Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
            if (rabbit == null || !Boolean.TRUE.equals(rabbit.getIsActive())) {
                throw new BizException(400, "兔子不存在或不在场");
            }
            counts.merge(sourceBatchId(houseId, rabbit), 1, Integer::sum);
        }
        batchAllocationService.assertRequestAllowed(
            counts, effectiveUnitPrice(request), request.getBatchAllocations()
        );
    }

    @TrackedOperation(
        code = "sale:create", eventType = "SALE_CREATED", targetType = "SALE_ORDER",
        targetId = "#result.id", requestId = "#request.requestId"
    )
    @Transactional
    public SaleOrder create(Long userId, Long houseId, CreateSaleOrderRequest request) {
        BigDecimal unitPrice = effectiveUnitPrice(request);
        if (request.getBatchAllocations() != null && !request.getBatchAllocations().isEmpty()
            && (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BizException(400, "新版销售必须填写大于0的重量单价");
        }
        return createInternal(
            userId,
            houseId,
            request.getRabbitIds(),
            request.getSaleTime(),
            request.getTotalWeight(),
            unitPrice,
            request.getCustomer(),
            request.getRemark(),
            request.getRequestId(),
            request.getBatchAllocations()
        );
    }

    public SaleOrder create(
        Long userId,
        Long houseId,
        List<Long> rabbitIds,
        Date saleTime,
        Double totalWeight,
        BigDecimal unitPrice,
        String customer,
        String remark,
        String requestId
    ) {
        return createInternal(
            userId, houseId, rabbitIds, saleTime, totalWeight, unitPrice,
            customer, remark, requestId, null
        );
    }

    private SaleOrder createInternal(
        Long userId,
        Long houseId,
        List<Long> rabbitIds,
        Date saleTime,
        Double totalWeight,
        BigDecimal unitPrice,
        String customer,
        String remark,
        String requestId,
        List<SaleBatchAllocationInput> requestedAllocations
    ) {
        String api = "sale:create";
        if (requestDedupService.begin(
            houseId,
            userId,
            api,
            requestId,
            payloadHash(
                rabbitIds, saleTime, totalWeight, unitPrice, customer, remark,
                requestedAllocations
            )
        ) == RequestDedupService.BeginResult.DONE) {
            OperationContext context = OperationContext.current();
            if (context != null) {
                context.setDedupReplay(true);
            }
            return saleOrderMapper.selectByReq(houseId, requestId);
        }
        try {
            if (rabbitIds == null || rabbitIds.isEmpty()) {
                throw new BizException(400, "rabbitIds不能为空");
            }
            Date effectiveSaleTime = saleTime == null ? DateUtil.now() : saleTime;
            if (totalWeight == null || totalWeight <= 0) {
                throw new BizException(400, "totalWeight不合法");
            }
            if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(400, "unitPrice不合法");
            }
            BigDecimal effectivePrice = unitPrice != null
                && unitPrice.compareTo(BigDecimal.ZERO) > 0 ? unitPrice : null;

            LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
            for (Long rabbitId : rabbitIds) {
                if (rabbitId != null && rabbitId > 0) {
                    uniqueIds.add(rabbitId);
                }
            }
            List<Long> normalizedIds = uniqueIds.stream().sorted().toList();
            if (normalizedIds.isEmpty()) {
                throw new BizException(400, "rabbitIds不合法");
            }
            Map<Long, Rabbit> rabbitsById = loadRabbitSnapshots(houseId, normalizedIds);

            SaleOrder order = new SaleOrder();
            order.setHouseId(houseId);
            order.setSaleTime(effectiveSaleTime);
            order.setCustomer(customer);
            order.setTotalWeight(totalWeight);
            order.setUnitPrice(unitPrice);
            order.setTotalAmount(SaleBatchAllocationService.orderAmount(
                BigDecimal.valueOf(totalWeight), unitPrice
            ));
            order.setRemark(remark);
            order.setRequestId(requestId);
            saleOrderMapper.insert(order);

            List<SaleOrderItem> items = new ArrayList<>();
            for (Long rabbitId : normalizedIds) {
                Rabbit rabbit = rabbitsById.get(rabbitId);
                SaleOrderItem item = new SaleOrderItem();
                item.setSaleOrderId(order.getId());
                item.setRabbitId(rabbitId);
                item.setBatchIdSnapshot(sourceBatchId(houseId, rabbit));
                item.setWeight(rabbit == null ? null : rabbit.getWeight());
                item.setPrice(unitPrice);
                items.add(item);
            }
            saleOrderItemMapper.insertBatch(items);

            if (batchAllocationService != null) {
                batchAllocationService.allocateAndSave(
                    userId,
                    houseId,
                    order.getId(),
                    requestId,
                    BigDecimal.valueOf(totalWeight),
                    effectivePrice,
                    items.stream().map(SaleOrderItem::getBatchIdSnapshot).toList(),
                    requestedAllocations,
                    api
                );
            }

            for (SaleOrderItem item : items) {
                String childRequestId = RequestIdUtil.deriveChild(requestId, item.getRabbitId());
                rabbitService.rabbitEvent(
                    userId,
                    houseId,
                    item.getRabbitId(),
                    "sale",
                    effectiveSaleTime,
                    "销售出栏",
                    "saleOrder#" + order.getId(),
                    true,
                    childRequestId
                );
            }

            requestDedupService.markDone(houseId, userId, api, requestId);
            return order;
        } catch (RuntimeException error) {
            requestDedupService.markFailed(houseId, userId, api, requestId, error.getMessage());
            throw error;
        }
    }

    private Map<Long, Rabbit> loadRabbitSnapshots(Long houseId, List<Long> rabbitIds) {
        if (rabbitMapper == null) {
            return Map.of();
        }
        List<Rabbit> rabbits = rabbitMapper.selectByIdsForUpdate(
            houseId, rabbitIds.stream().sorted().toList()
        );
        if (rabbits.size() != rabbitIds.size()) {
            throw new BizException(400, "兔子不存在");
        }
        Map<Long, Rabbit> result = new LinkedHashMap<>();
        rabbits.forEach(rabbit -> result.put(rabbit.getId(), rabbit));
        return result;
    }

    private Long sourceBatchId(Long houseId, Rabbit rabbit) {
        if (rabbit == null || rabbit.getBirthBatchId() != null || batchRabbitMapper == null) {
            return rabbit == null ? null : rabbit.getBirthBatchId();
        }
        List<Long> batchIds = batchRabbitMapper.selectActiveByRabbit(houseId, rabbit.getId()).stream()
            .filter(link -> "fattening".equalsIgnoreCase(link.getBatchRole()))
            .map(BatchRabbit::getBatchId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        if (batchIds.size() > 1) {
            throw new BizException(409, "兔只存在多个活跃批次，无法确定销售归属: " + rabbit.getId());
        }
        return batchIds.isEmpty() ? null : batchIds.get(0);
    }

    private static BigDecimal effectiveUnitPrice(CreateSaleOrderRequest request) {
        BigDecimal legacy = request.getUnitPrice();
        BigDecimal current = request.getUnitPricePerKg();
        if (legacy != null && current != null && legacy.compareTo(current) != 0) {
            throw new BizException(400, "unitPrice与unitPricePerKg不一致");
        }
        return current != null ? current : legacy;
    }

    private static String payloadHash(
        List<Long> rabbitIds,
        Date saleTime,
        Double totalWeight,
        BigDecimal unitPrice,
        String customer,
        String remark,
        List<SaleBatchAllocationInput> allocations
    ) {
        StringBuilder value = new StringBuilder()
            .append(saleTime == null ? null : saleTime.getTime())
            .append('|').append(totalWeight)
            .append('|').append(BatchWritePayloadHasher.decimal(unitPrice))
            .append('|').append(BatchWritePayloadHasher.text(
                customer == null ? null : customer.trim()
            ))
            .append('|').append(BatchWritePayloadHasher.text(
                remark == null ? null : remark.trim()
            ));
        if (rabbitIds != null) {
            rabbitIds.stream().filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> value.append("|rabbit:").append(id));
        }
        if (allocations != null) {
            allocations.stream().sorted(Comparator.comparing(
                SaleBatchAllocationInput::batchId,
                Comparator.nullsLast(Long::compareTo)
            )).forEach(allocation -> value.append("|allocation:")
                .append(allocation.batchId()).append(':')
                .append(BatchWritePayloadHasher.decimal(allocation.actualWeightKg())));
        }
        return BatchWritePayloadHasher.sha256(value.toString());
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
