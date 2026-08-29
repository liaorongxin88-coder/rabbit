package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbit.app.tracking.OperationEvent;
import com.rabbit.app.tracking.OperationEventSink;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 事件流是业务写入旁边的追加旁路，不能反过来把业务写弄挂。
 *
 * <p>V51 给 repro_events 加了 uk_re_request_target 唯一键，重复事件会撞 1062。
 * 如果批量插入是普通 insert，这个冲突会变成事务里的 DuplicateKeyException，
 * 把用户本来成功的那次写入一起回滚——和 C1 修掉的重复提交 500 是同一类故障。
 * 这里用真实数据库证明冲突被唯一键吃掉，而不是抛出来。
 */
class OperationEventReplayIT extends E2eTestSupport {

    private static final long HOUSE_ID = 990_001L;
    private static final long TARGET_ID = 5L;

    @Autowired
    private OperationEventSink sink;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aReplayedEventCollapsesInsteadOfBreakingTheWrite() {
        String requestId = "replay-" + System.nanoTime();

        // 同一批次里出现重复目标（例如客户端重复传同一只兔）。
        assertDoesNotThrow(() -> sink.append(List.of(event(requestId, TARGET_ID), event(requestId, TARGET_ID))));
        assertEquals(1, countEvents(requestId), "同批重复目标应折叠成一行");

        // 跨调用重放同一事件。
        assertDoesNotThrow(() -> sink.append(List.of(event(requestId, TARGET_ID))));
        assertEquals(1, countEvents(requestId), "重放不应产生第二行");

        // 去重不能过宽：同一请求里的另一个目标仍然要独立成行。
        assertDoesNotThrow(() -> sink.append(List.of(event(requestId, TARGET_ID + 1))));
        assertEquals(2, countEvents(requestId), "不同目标必须各自留痕");
    }

    private OperationEvent event(String requestId, long targetId) {
        return OperationEvent.from(null)
                .houseId(HOUSE_ID)
                .operationCode("feed:add")
                .eventType("FEED_RECORDED")
                .targetType("RABBIT")
                .targetId(targetId)
                .operatorId(1L)
                .requestId(requestId)
                .build();
    }

    private int countEvents(String requestId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from repro_events where house_id = ? and request_id = ?",
                Integer.class,
                HOUSE_ID,
                requestId);
        return count == null ? 0 : count;
    }
}
