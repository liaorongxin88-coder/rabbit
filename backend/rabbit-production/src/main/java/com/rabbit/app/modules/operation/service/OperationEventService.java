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
     * <p>认不出来的原样返回而不是显示「未知」：通用化之后会不断有新事件类型进来，
     * 显示原始类型至少还能被搜到，显示「未知」就断了线索。
     */
    static String eventLabel(String eventType) {
        String value = trimToNull(eventType);
        if (value == null) {
            return "操作";
        }
        try {
            return ReproEventType.valueOf(value.toUpperCase()).label();
        } catch (IllegalArgumentException ignored) {
            return value;
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
