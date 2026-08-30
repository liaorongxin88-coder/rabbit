package com.rabbit.app.modules.operation.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.operation.dto.OperationEventPage;
import com.rabbit.app.modules.operation.dto.OperationEventView;
import com.rabbit.app.modules.operation.mapper.OperationEventMapper;
import com.rabbit.app.modules.repro.domain.ReproEventType;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 统一操作事件读服务。
 *
 * <p>V51 之后 repro_events 同时装繁育状态机事件和各类写操作留痕，这里是它唯一的
 * 对外读口：keyset 翻页、按目标过滤、把机器事件名翻成人话。
 */
@Service
public class OperationEventService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private static final Map<String, String> COMMON_EVENT_LABELS = Map.ofEntries(
        Map.entry("BATCH_COMPLETED", "批次完成"),
        Map.entry("BATCH_CREATED", "新建批次"),
        Map.entry("BATCH_MEMBERS_ADDED", "加入批次"),
        Map.entry("BATCH_MEMBER_REMOVED", "移出批次"),
        Map.entry("BATCH_RENAMED", "批次改名"),
        Map.entry("BATCH_SOLD", "批次出售"),
        Map.entry("CAGE_COUNTS_RECOUNTED", "重算笼位兔数"),
        Map.entry("CAGE_COUNT_RECORDED", "记录笼位兔数"),
        Map.entry("CAGE_CREATED", "新建笼位"),
        Map.entry("CAGE_DELETED", "删除笼位"),
        Map.entry("CAGE_NFC_BOUND", "绑定笼位标签"),
        Map.entry("CAGE_UPDATED", "修改笼位"),
        Map.entry("FEED_RECORDED", "投喂记录"),
        Map.entry("INVENTORY_ITEM_CREATED", "新建物料"),
        Map.entry("INVENTORY_TRANSACTION_RECORDED", "库存出入记录"),
        Map.entry("NFC_BOUND", "绑定标签"),
        Map.entry("NFC_UNBOUND", "解绑标签"),
        Map.entry("RABBITS_CONVERTED_TO_REPLACEMENT", "转为后备兔"),
        Map.entry("RABBIT_ABNORMAL_RECORDED", "异常记录"),
        Map.entry("RABBIT_BATCH_ENTERED", "批量入栏"),
        Map.entry("RABBIT_CAGE_TRANSFERRED", "转笼"),
        Map.entry("RABBIT_CREATED", "兔只入栏"),
        Map.entry("RABBIT_EVENT", "兔只事件"),
        Map.entry("RABBIT_PROMOTED", "后备转种"),
        Map.entry("RABBIT_UPDATED", "修改兔只资料"),
        Map.entry("SALE_CREATED", "创建销售单"),
        Map.entry("TREATMENT_COMPLETED", "结束治疗"),
        Map.entry("TREATMENT_STARTED", "开始治疗"),
        Map.entry("VACCINATION_RECORDED", "接种记录"),
        Map.entry("WEANING_SEPARATED", "断奶分笼"),
        Map.entry("WEIGHT_RECORDED", "称重记录")
    );

    private final OperationEventMapper operationEventMapper;

    public OperationEventService(OperationEventMapper operationEventMapper) {
        this.operationEventMapper = operationEventMapper;
    }

    /**
     * 取一页事件。
     *
     * <p>多查一条来判断有没有下一页，这样不用为每次翻页做一次 count。
     */
    public OperationEventPage list(
        Long houseId,
        String targetType,
        Long targetId,
        String operationCode,
        Long cageId,
        Long batchId,
        Date occurredFrom,
        Date occurredTo,
        String cursor,
        Integer limit
    ) {
        String normalizedTargetType = trimToNull(targetType);
        // 单给 targetId 不给 targetType 会跨类型误命中：兔只 5 和批次 5 是两回事。
        if (targetId != null && normalizedTargetType == null) {
            throw new BizException(400, "按目标筛选时必须同时给出 targetType");
        }
        if (targetId != null && targetId <= 0) {
            throw new BizException(400, "targetId 必须是正整数");
        }
        if (occurredFrom != null && occurredTo != null && occurredFrom.after(occurredTo)) {
            throw new BizException(400, "开始时间不能晚于结束时间");
        }

        int pageSize = clampLimit(limit);
        OperationEventCursor decoded = cursor == null || cursor.isBlank()
            ? null
            : OperationEventCursor.decode(cursor);

        List<ReproEvent> rows = operationEventMapper.selectPage(
            houseId,
            normalizedTargetType,
            targetId,
            trimToNull(operationCode),
            cageId,
            batchId,
            occurredFrom,
            occurredTo,
            decoded == null ? null : new Date(decoded.getOccurredAtMillis()),
            decoded == null ? null : decoded.getId(),
            pageSize + 1
        );

        boolean hasMore = rows.size() > pageSize;
        List<ReproEvent> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

        List<OperationEventView> items = new ArrayList<>(pageRows.size());
        for (ReproEvent row : pageRows) {
            items.add(OperationEventView.of(row, eventLabel(row.getEventType())));
        }

        return new OperationEventPage(items, nextCursor(pageRows, hasMore), hasMore);
    }

    private String nextCursor(List<ReproEvent> rows, boolean hasMore) {
        if (!hasMore || rows.isEmpty()) {
            return null;
        }
        ReproEvent last = rows.get(rows.size() - 1);
        // occurred_at 允许补录，但不该为空；真为空就没法定位下一页，只能收尾。
        if (last.getOccurredAt() == null || last.getId() == null) {
            return null;
        }
        return OperationEventCursor.of(last.getOccurredAt().getTime(), last.getId()).encode();
    }

    static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    /**
     * 事件名翻成人话。
     *
     * <p>通用写操作使用独立映射，繁育状态机沿用 {@link ReproEventType}。未知类型的机器值
     * 仍保留在响应的 eventType 字段里，展示名回退为「操作」，避免把英文枚举放到客户端标题。
     */
    static String eventLabel(String eventType) {
        String value = trimToNull(eventType);
        if (value == null) {
            return "操作";
        }
        String normalized = value.toUpperCase();
        String commonLabel = COMMON_EVENT_LABELS.get(normalized);
        if (commonLabel != null) {
            return commonLabel;
        }
        try {
            return ReproEventType.valueOf(normalized).label();
        } catch (IllegalArgumentException ignored) {
            return "操作";
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
