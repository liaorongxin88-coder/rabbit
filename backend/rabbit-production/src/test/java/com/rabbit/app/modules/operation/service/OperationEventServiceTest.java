package com.rabbit.app.modules.operation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.operation.dto.OperationEventPage;
import com.rabbit.app.modules.operation.mapper.OperationEventMapper;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationEventServiceTest {

    private final RecordingMapper mapper = new RecordingMapper();
    private final OperationEventService service = new OperationEventService(mapper);

    @Test
    void aTargetIdWithoutATargetTypeIsRejected() {
        // 兔只 5 和批次 5 是两回事，只按 id 过滤会跨类型误命中。
        BizException error = assertThrows(
            BizException.class,
            () -> list(null, 5L, null)
        );
        assertEquals(400, error.getCode());
    }

    @Test
    void theLimitIsClampedInsteadOfTrustingTheClient() {
        // 审计表只增不减，不夹上限等于把全表拉出来的开关交给调用方。
        assertEquals(OperationEventService.DEFAULT_LIMIT, OperationEventService.clampLimit(null));
        assertEquals(1, OperationEventService.clampLimit(0));
        assertEquals(1, OperationEventService.clampLimit(-10));
        assertEquals(OperationEventService.MAX_LIMIT, OperationEventService.clampLimit(5_000));
        assertEquals(20, OperationEventService.clampLimit(20));
    }

    @Test
    void anExtraRowIsFetchedToDecideWhetherMoreExist() {
        // 多查一条判断下一页，省掉每次翻页的 count。
        mapper.rows = events(11);
        OperationEventPage page = list("RABBIT", 5L, null);

        assertEquals(11, mapper.lastLimit, "应当按 limit + 1 查询");
        assertEquals(10, page.items().size(), "多出来的那条不能返回给客户端");
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
    }

    @Test
    void theLastPageCarriesNoCursor() {
        mapper.rows = events(3);
        OperationEventPage page = list("RABBIT", 5L, null);

        assertEquals(3, page.items().size());
        assertEquals(false, page.hasMore());
        assertNull(page.nextCursor(), "没有下一页就不该给游标，否则客户端会空转一次");
    }

    @Test
    void theCursorRoundTripsThroughTheQuery() {
        mapper.rows = events(11);
        String cursor = list("RABBIT", 5L, null).nextCursor();

        mapper.rows = events(2);
        list("RABBIT", 5L, cursor);

        // 第 10 条是本页最后一条：occurredAt = 10, id = 10。
        assertEquals(new Date(10L), mapper.lastCursorOccurredAt);
        assertEquals(10L, mapper.lastCursorId);
    }

    @Test
    void aForgedCursorIsAClientErrorNotAServerError() {
        // 手抖或伪造的游标不该看起来像服务故障。
        BizException error = assertThrows(
            BizException.class,
            () -> list("RABBIT", 5L, "not-a-cursor")
        );
        assertEquals(400, error.getCode());
    }

    @Test
    void aReversedTimeRangeIsRejected() {
        BizException error = assertThrows(
            BizException.class,
            () -> service.list(
                1L, null, null, null, null, null,
                new Date(2_000L), new Date(1_000L), null, null
            )
        );
        assertEquals(400, error.getCode());
    }

    @Test
    void unknownEventTypesKeepTheirRawName() {
        // 通用化之后新事件类型会不断出现，显示「未知」等于断掉线索。
        assertEquals("开始周期", OperationEventService.eventLabel("CYCLE_START"));
        assertEquals("FEED_RECORDED", OperationEventService.eventLabel("FEED_RECORDED"));
        assertEquals("操作", OperationEventService.eventLabel("  "));
        assertEquals("操作", OperationEventService.eventLabel(null));
    }

    @Test
    void blankFiltersAreTreatedAsAbsent() {
        mapper.rows = events(1);
        service.list(1L, "  ", null, "  ", null, null, null, null, null, null);

        assertNull(mapper.lastTargetType, "空白过滤条件不该变成 where target_type = ''");
        assertNull(mapper.lastOperationCode);
    }

    private OperationEventPage list(String targetType, Long targetId, String cursor) {
        return service.list(
            1L, targetType, targetId, null, null, null, null, null, cursor, 10
        );
    }

    private static List<ReproEvent> events(int count) {
        List<ReproEvent> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ReproEvent event = new ReproEvent();
            event.setId((long) i);
            event.setOccurredAt(new Date(i));
            event.setEventType("FEED_RECORDED");
            event.setOperationCode("feed:add");
            event.setTargetType("RABBIT");
            event.setTargetId(5L);
            rows.add(event);
        }
        return rows;
    }

    private static final class RecordingMapper implements OperationEventMapper {
        private List<ReproEvent> rows = new ArrayList<>();
        private int lastLimit;
        private String lastTargetType;
        private String lastOperationCode;
        private Date lastCursorOccurredAt;
        private Long lastCursorId;

        @Override
        public List<ReproEvent> selectPage(
            Long houseId,
            String targetType,
            Long targetId,
            String operationCode,
            Long cageId,
            Long batchId,
            Date occurredFrom,
            Date occurredTo,
            Date cursorOccurredAt,
            Long cursorId,
            int limit
        ) {
            this.lastLimit = limit;
            this.lastTargetType = targetType;
            this.lastOperationCode = operationCode;
            this.lastCursorOccurredAt = cursorOccurredAt;
            this.lastCursorId = cursorId;
            return rows;
        }
    }
}
