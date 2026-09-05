package com.rabbit.app.modules.feed.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.support.BatchWritePayloadHasher;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.feed.dto.FeedAllocationPreview;
import com.rabbit.app.modules.feed.dto.FeedBatchAllocationInput;
import com.rabbit.app.modules.feed.entity.FeedLog;
import com.rabbit.app.modules.feed.entity.FeedLogRabbit;
import com.rabbit.app.modules.feed.mapper.FeedLogMapper;
import com.rabbit.app.modules.feed.mapper.FeedLogRabbitMapper;
import com.rabbit.app.modules.inventory.entity.InventoryItem;
import com.rabbit.app.modules.inventory.entity.InventoryTx;
import com.rabbit.app.modules.inventory.mapper.InventoryItemMapper;
import com.rabbit.app.modules.inventory.mapper.InventoryTxMapper;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.OperationEvent;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.util.DateUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService {
    private final FeedLogMapper feedLogMapper;
    private final FeedLogRabbitMapper feedLogRabbitMapper;
    private final RabbitMapper rabbitMapper;
    private final CageMapper cageMapper;
    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryTxMapper inventoryTxMapper;
    private final RequestDedupService requestDedupService;
    private final WorkTaskWriter workTaskWriter;
    private final FeedBatchAllocationService batchAllocationService;
    private final boolean forbidNegative;
    private final int casRetryTimes;

    public FeedService(
            FeedLogMapper feedLogMapper,
            FeedLogRabbitMapper feedLogRabbitMapper,
            RabbitMapper rabbitMapper,
            CageMapper cageMapper,
            InventoryItemMapper inventoryItemMapper,
            InventoryTxMapper inventoryTxMapper,
            RequestDedupService requestDedupService,
            WorkTaskWriter workTaskWriter,
            boolean forbidNegative,
            int casRetryTimes
    ) {
        this(
            feedLogMapper,
            feedLogRabbitMapper,
            rabbitMapper,
            cageMapper,
            inventoryItemMapper,
            inventoryTxMapper,
            requestDedupService,
            workTaskWriter,
            null,
            forbidNegative,
            casRetryTimes
        );
    }

    @Autowired
    public FeedService(
            FeedLogMapper feedLogMapper,
            FeedLogRabbitMapper feedLogRabbitMapper,
            RabbitMapper rabbitMapper,
            CageMapper cageMapper,
            InventoryItemMapper inventoryItemMapper,
            InventoryTxMapper inventoryTxMapper,
            RequestDedupService requestDedupService,
            WorkTaskWriter workTaskWriter,
            FeedBatchAllocationService batchAllocationService,
            @Value("${app.inventory.forbid-negative:false}") boolean forbidNegative,
            @Value("${app.inventory.cas-retry-times:5}") int casRetryTimes
    ) {
        this.feedLogMapper = feedLogMapper;
        this.feedLogRabbitMapper = feedLogRabbitMapper;
        this.rabbitMapper = rabbitMapper;
        this.cageMapper = cageMapper;
        this.inventoryItemMapper = inventoryItemMapper;
        this.inventoryTxMapper = inventoryTxMapper;
        this.requestDedupService = requestDedupService;
        this.workTaskWriter = workTaskWriter;
        this.batchAllocationService = batchAllocationService;
        this.forbidNegative = forbidNegative;
        this.casRetryTimes = casRetryTimes <= 0 ? 5 : casRetryTimes;
    }

    public void assertRequestAllowed(
        Long userId,
        Long houseId,
        String requestId,
        List<Long> rabbitIds,
        Date feedTime,
        String unit,
        List<FeedBatchAllocationInput> allocations
    ) {
        if (requestDedupService.shouldSkipAsDone(
            houseId, userId, "feed:add", requestId
        )) {
            return;
        }
        if (batchAllocationService != null) {
            batchAllocationService.assertRequestAllowed(
                houseId, rabbitIds, feedTime, unit, allocations
            );
        }
    }

    @TrackedOperation(
        code = "feed:add", eventType = "FEED_RECORDED", requestId = "#log.requestId"
    )
    @Transactional
    public void addFeedLog(
        Long userId,
        Long houseId,
        FeedLog log,
        List<Long> rabbitIds,
        List<FeedBatchAllocationInput> allocations
    ) {
        String api = "feed:add";
        String requestId = log == null ? null : log.getRequestId();
        if (requestDedupService.begin(
            houseId, userId, api, requestId, payloadHash(log, rabbitIds, allocations)
        ) == RequestDedupService.BeginResult.DONE) {
            OperationContext context = OperationContext.current();
            if (context != null) {
                context.setDedupReplay(true);
            }
            return;
        }
        try {
        if (rabbitIds == null || rabbitIds.isEmpty()) {
            throw new BizException(400, "rabbitIds不能为空");
        }
        if (log == null) {
            throw new BizException(400, "投喂记录不能为空");
        }
        if (log.getFeedTime() == null) {
            log.setFeedTime(DateUtil.now());
        }
        if (log.getAmount() == null) {
            throw new BizException(400, "amount不能为空");
        }
        if (log.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "amount必须大于0");
        }
        try {
            log.setAmount(log.getAmount().setScale(2, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException error) {
            throw new BizException(400, "amount最多保留两位小数");
        }

        StringBuilder sb = new StringBuilder();
        Set<Long> cageIds = new HashSet<Long>();
        Set<Long> uniqueRabbitIds = new LinkedHashSet<Long>(rabbitIds);
        List<FeedLogRabbit> rels = new ArrayList<FeedLogRabbit>();

        int i = 0;
        for (Long rabbitId : uniqueRabbitIds) {
            if (rabbitId == null) {
                throw new BizException(400, "rabbitId不合法");
            }
            Rabbit r = rabbitMapper.selectById(houseId, rabbitId);
            if (r == null || !houseId.equals(r.getHouseId())) {
                throw new BizException(400, "兔子不存在");
            }
            if (r.getIsActive() == null || !r.getIsActive()) {
                throw new BizException(400, "兔子不在场");
            }
            if (r.getCageId() == null) {
                throw new BizException(400, "兔子未分配笼位");
            }
            if (i > 0) {
                sb.append(",");
            }
            sb.append(rabbitId);
            cageIds.add(r.getCageId());
            FeedLogRabbit rel = new FeedLogRabbit();
            rel.setHouseId(houseId);
            rel.setRabbitId(rabbitId);
            rel.setCageId(r.getCageId());
            rels.add(rel);
            i++;
        }

        FeedBatchAllocationService.AllocationPlan allocationPlan = batchAllocationService == null
            ? new FeedBatchAllocationService.AllocationPlan(List.of())
            : batchAllocationService.prepare(
                userId,
                houseId,
                List.copyOf(uniqueRabbitIds),
                log.getFeedTime(),
                log.getUnit(),
                log.getAmount(),
                log.getRequestId(),
                allocations
            );

        log.setHouseId(houseId);
        log.setFeedingRabbits(sb.toString());
        feedLogMapper.insert(log);
        if (batchAllocationService != null) {
            batchAllocationService.save(log.getId(), allocationPlan);
        }

        for (FeedLogRabbit rel : rels) {
            rel.setFeedLogId(log.getId());
        }
        feedLogRabbitMapper.insertBatch(rels);
        recordEvents(rels);

        if (log.getItemId() != null && log.getItemId() > 0) {
            InventoryItem item = inventoryItemMapper.selectById(houseId, log.getItemId());
            if (item == null) {
                throw new BizException(400, "物料不存在");
            }
            InventoryTx tx = new InventoryTx();
            tx.setHouseId(houseId);
            tx.setItemId(item.getId());
            tx.setTxType("CONSUME");
            tx.setQtyDelta(log.getAmount().negate());
            tx.setTxTime(log.getFeedTime() == null ? DateUtil.now() : log.getFeedTime());
            tx.setRefTable("feed_logs");
            tx.setRefId(log.getId());
            tx.setRemark("投喂消耗:" + log.getFeedingRabbits());
            tx.setRequestId(log.getRequestId());
            inventoryTxMapper.insert(tx);
            applyQtyDeltaWithPolicy(houseId, item.getId(), tx.getQtyDelta(), String.valueOf(userId));
        }

        String operator = String.valueOf(userId);
        for (Long cageId : cageIds) {
            cageMapper.updateIsFed(houseId, cageId, true, operator);
        }
        for (Long rabbitId : uniqueRabbitIds) {
            workTaskWriter.completeCommodityDailyCareForRabbitOnDate(
                houseId, rabbitId, log.getFeedTime(), operator
            );
        }
            requestDedupService.markDone(houseId, userId, api, log.getRequestId());
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, log == null ? null : log.getRequestId(), e.getMessage());
            throw e;
        }
    }

    public FeedAllocationPreview previewAllocations(
        Long houseId,
        List<Long> rabbitIds,
        Date feedTime
    ) {
        for (Long rabbitId : new LinkedHashSet<>(rabbitIds)) {
            Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
            if (rabbit == null || !Boolean.TRUE.equals(rabbit.getIsActive())) {
                throw new BizException(400, "兔子不存在或不在场");
            }
        }
        if (batchAllocationService == null) {
            return new FeedAllocationPreview(List.of());
        }
        return batchAllocationService.preview(houseId, rabbitIds, feedTime);
    }

    private static String payloadHash(
        FeedLog log,
        List<Long> rabbitIds,
        List<FeedBatchAllocationInput> allocations
    ) {
        StringBuilder value = new StringBuilder();
        if (log != null) {
            value.append(log.getFeedTime() == null ? null : log.getFeedTime().getTime())
                .append('|').append(BatchWritePayloadHasher.text(log.getFeedType()))
                .append('|').append(log.getItemId())
                .append('|').append(BatchWritePayloadHasher.text(log.getUnit() == null
                    ? null : log.getUnit().trim().toLowerCase(java.util.Locale.ROOT)))
                .append('|').append(BatchWritePayloadHasher.decimal(log.getAmount()))
                .append('|').append(BatchWritePayloadHasher.text(
                    log.getRemark() == null ? null : log.getRemark().trim()
                ));
        }
        if (rabbitIds != null) {
            rabbitIds.stream().filter(java.util.Objects::nonNull).distinct().sorted()
                .forEach(id -> value.append("|rabbit:").append(id));
        }
        if (allocations != null) {
            allocations.stream().sorted(Comparator
                .comparing(FeedBatchAllocationInput::batchId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(input -> String.valueOf(input.phase())))
                .forEach(input -> value.append("|allocation:")
                    .append(input.batchId()).append(':')
                    .append(input.phase() == null
                        ? null : input.phase().trim().toUpperCase(java.util.Locale.ROOT))
                    .append(':').append(BatchWritePayloadHasher.decimal(input.amountKg())));
        }
        return BatchWritePayloadHasher.sha256(value.toString());
    }

    private void recordEvents(List<FeedLogRabbit> rows) {
        OperationContext context = OperationContext.current();
        if (context == null) {
            return;
        }
        for (FeedLogRabbit row : rows) {
            context.recordEvent(OperationEvent.from(context)
                .operationCode("feed:add")
                .eventType("FEED_RECORDED")
                .targetType("RABBIT")
                .targetId(row.getRabbitId())
                .cageId(row.getCageId())
                .build());
        }
    }

    private void applyQtyDeltaWithPolicy(Long houseId, Long itemId, BigDecimal delta, String updateBy) {
        if (!forbidNegative) {
            int rows = inventoryItemMapper.updateQtyDelta(houseId, itemId, delta, updateBy);
            if (rows <= 0) {
                throw new BizException(400, "物料不存在");
            }
            return;
        }
        int tries = casRetryTimes <= 0 ? 5 : casRetryTimes;
        for (int i = 0; i < tries; i++) {
            InventoryItem item = inventoryItemMapper.selectById(houseId, itemId);
            if (item == null) {
                throw new BizException(400, "物料不存在");
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

    public List<FeedLog> list(Long houseId) {
        return feedLogMapper.selectByHouse(houseId);
    }

    public List<FeedLog> listPage(Long houseId, Date from, Date to, int page, int pageSize) {
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
        return feedLogMapper.selectPageByHouse(houseId, from, to, offset, pageSize);
    }

    public List<FeedLog> listExportPage(Long houseId, Date from, Date to, int offset, int limit) {
        if (offset < 0) {
            offset = 0;
        }
        if (limit <= 0) {
            limit = 500;
        }
        if (limit > 5000) {
            limit = 5000;
        }
        return feedLogMapper.selectPageByHouse(houseId, from, to, offset, limit);
    }

    public List<FeedLog> listForExport(Long houseId, Date from, Date to, int maxRows) {
        if (maxRows <= 0) {
            maxRows = 5000;
        }
        if (maxRows > 50000) {
            maxRows = 50000;
        }
        int pageSize = 500;
        if (pageSize > maxRows) {
            pageSize = maxRows;
        }
        List<FeedLog> all = new ArrayList<FeedLog>();
        int offset = 0;
        while (all.size() < maxRows) {
            int limit = pageSize;
            if (all.size() + limit > maxRows) {
                limit = maxRows - all.size();
            }
            List<FeedLog> part = feedLogMapper.selectPageByHouse(houseId, from, to, offset, limit);
            if (part == null || part.isEmpty()) {
                break;
            }
            all.addAll(part);
            if (part.size() < limit) {
                break;
            }
            offset += part.size();
        }
        return all;
    }
}
