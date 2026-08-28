package com.rabbit.app.modules.inventory.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.inventory.entity.InventoryItem;
import com.rabbit.app.modules.inventory.entity.InventoryTx;
import com.rabbit.app.modules.inventory.mapper.InventoryItemMapper;
import com.rabbit.app.modules.inventory.mapper.InventoryTxMapper;
import com.rabbit.app.util.DateUtil;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryTxMapper inventoryTxMapper;
    private final RequestDedupService requestDedupService;
    private final boolean forbidNegative;
    private final int casRetryTimes;

    public InventoryService(
            InventoryItemMapper inventoryItemMapper,
            InventoryTxMapper inventoryTxMapper,
            RequestDedupService requestDedupService,
            @Value("${app.inventory.forbid-negative:false}") boolean forbidNegative,
            @Value("${app.inventory.cas-retry-times:5}") int casRetryTimes
    ) {
        this.inventoryItemMapper = inventoryItemMapper;
        this.inventoryTxMapper = inventoryTxMapper;
        this.requestDedupService = requestDedupService;
        this.forbidNegative = forbidNegative;
        this.casRetryTimes = casRetryTimes <= 0 ? 5 : casRetryTimes;
    }

    @Transactional
    public InventoryItem createItem(Long userId, Long houseId, InventoryItem item, BigDecimal initQty, String requestId) {
        String api = "inventory:item:create";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            if (item != null && item.getName() != null) {
                InventoryItem old = inventoryItemMapper.selectByHouseAndName(houseId, item.getName());
                if (old != null) {
                    return old;
                }
            }
            return item;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (item == null) {
                throw new BizException(400, "item不能为空");
            }
            if (item.getName() == null || item.getName().trim().isEmpty()) {
                throw new BizException(400, "name不能为空");
            }
            if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                throw new BizException(400, "unit不能为空");
            }
            if (initQty == null) {
                initQty = BigDecimal.ZERO;
            }
            if (initQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(400, "initQty不能为负数");
            }
            item.setHouseId(houseId);
            item.setCurrentQty(initQty);
            inventoryItemMapper.insert(item);

            if (initQty.compareTo(BigDecimal.ZERO) > 0) {
                InventoryTx tx = new InventoryTx();
                tx.setHouseId(houseId);
                tx.setItemId(item.getId());
                tx.setTxType("IN");
                tx.setQtyDelta(initQty);
                tx.setTxTime(DateUtil.now());
                tx.setRemark("初始化库存");
                tx.setRequestId(requestId);
                inventoryTxMapper.insert(tx);
            }

            requestDedupService.markDone(houseId, userId, api, requestId);
            return item;
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    public List<InventoryItem> listItems(Long houseId) {
        return inventoryItemMapper.selectByHouse(houseId);
    }

    public InventoryItem getItem(Long houseId, Long itemId) {
        if (houseId == null || itemId == null) {
            return null;
        }
        return inventoryItemMapper.selectById(houseId, itemId);
    }

    @Transactional
    public void addTx(Long userId, Long houseId, Long itemId, String txType, BigDecimal qtyDelta, Date txTime, String remark, String requestId, String refTable, Long refId) {
        String api = "inventory:tx:" + (txType == null ? "" : txType);
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            InventoryItem item = inventoryItemMapper.selectById(houseId, itemId);
            if (item == null) {
                throw new BizException(400, "item不存在");
            }
            if (txType == null || txType.trim().isEmpty()) {
                throw new BizException(400, "txType不能为空");
            }
            String t = txType.trim().toUpperCase();
            if (!"IN".equals(t) && !"OUT".equals(t) && !"ADJUST".equals(t) && !"CONSUME".equals(t)) {
                throw new BizException(400, "txType不支持");
            }
            if (qtyDelta == null || qtyDelta.compareTo(BigDecimal.ZERO) == 0) {
                throw new BizException(400, "qtyDelta不能为空");
            }
            validateDeltaByType(t, qtyDelta);
            if (txTime == null) {
                txTime = DateUtil.now();
            }

            InventoryTx tx = new InventoryTx();
            tx.setHouseId(houseId);
            tx.setItemId(itemId);
            tx.setTxType(t);
            tx.setQtyDelta(qtyDelta);
            tx.setTxTime(txTime);
            tx.setRefTable(refTable);
            tx.setRefId(refId);
            tx.setRemark(remark);
            tx.setRequestId(requestId);
            inventoryTxMapper.insert(tx);

            applyQtyDeltaWithPolicy(houseId, itemId, qtyDelta, String.valueOf(userId));

            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void validateDeltaByType(String txType, BigDecimal delta) {
        int s = delta.compareTo(BigDecimal.ZERO);
        if ("IN".equals(txType) && s <= 0) {
            throw new BizException(400, "IN必须为正数");
        }
        if (("OUT".equals(txType) || "CONSUME".equals(txType)) && s >= 0) {
            throw new BizException(400, txType + "必须为负数");
        }
    }

    private void applyQtyDeltaWithPolicy(Long houseId, Long itemId, BigDecimal delta, String updateBy) {
        if (!forbidNegative) {
            int rows = inventoryItemMapper.updateQtyDelta(houseId, itemId, delta, updateBy);
            if (rows <= 0) {
                throw new BizException(400, "item不存在");
            }
            return;
        }
        int tries = casRetryTimes <= 0 ? 5 : casRetryTimes;
        for (int i = 0; i < tries; i++) {
            InventoryItem item = inventoryItemMapper.selectById(houseId, itemId);
            if (item == null) {
                throw new BizException(400, "item不存在");
            }
            BigDecimal oldQty = item.getCurrentQty() == null ? BigDecimal.ZERO : item.getCurrentQty();
            BigDecimal newQty = oldQty.add(delta);
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(400, "库存不足");
            }
            int rows = inventoryItemMapper.updateQtyDeltaIfCurrent(houseId, itemId, delta, oldQty, true, updateBy);
            if (rows > 0) {
                return;
            }
        }
        throw new BizException(409, "库存并发冲突，请重试");
    }

    public List<InventoryTx> listTxByItem(Long houseId, Long itemId, int page, int pageSize) {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 50;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
        int offset = (page - 1) * pageSize;
        return inventoryTxMapper.selectPageByItem(houseId, itemId, offset, pageSize);
    }

    public List<InventoryTx> listTxExportPage(Long houseId, Long itemId, Date from, Date to, int offset, int limit) {
        int lim = limit <= 0 ? 1000 : limit;
        if (lim > 5000) {
            lim = 5000;
        }
        int off = Math.max(0, offset);
        return inventoryTxMapper.selectExportPage(houseId, itemId, from, to, off, lim);
    }
}
