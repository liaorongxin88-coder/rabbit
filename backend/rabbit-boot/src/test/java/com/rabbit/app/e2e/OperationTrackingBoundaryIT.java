package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.weight.entity.WeightLog;
import com.rabbit.app.modules.weight.mapper.WeightLogMapper;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.OperationEvent;
import com.rabbit.app.tracking.OperationEventSink;
import com.rabbit.app.tracking.TrackedOperation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 证明切面与事务的边界确实落在设计的位置上。
 *
 * <p>这是整套基座唯一无法用单元测试证明的部分：真正的事务边界只有在真实
 * 数据库上才存在，回滚是否抹掉某一行也只有真实回滚才答得出来。所以这些
 * 断言必须是 E2E。
 *
 * <p>三条独立的证明：
 *
 * <ol>
 *   <li>{@link #failureMarkSurvivesTheBusinessRollback()} —— 业务写入被回滚，
 *       而 markFailed 写下的 FAILED 状态还在。两件事在同一个方法调用里发生，
 *       所以它们<b>必然</b>处在不同事务：同事务的话回滚会把两者一起抹掉。</li>
 *   <li>{@link #eventsArePersistedInsideTheBusinessTransaction()} —— sink 被调用时
 *       事务处于激活状态，且拿到的是同一个事务名。</li>
 *   <li>{@link #dedupIsMarkedDoneOnlyAfterTheTransactionCommits()} —— 成功路径上
 *       DONE 与业务行同时可见。</li>
 * </ol>
 *
 * <p>探针 bean 用 {@code @TestConfiguration} + {@code @Import} 而不是
 * {@code @Component}：后者会被组件扫描带进<b>每一个</b> E2E 的上下文，
 * 给另外两百个用例挂上一个它们不需要的 sink。
 */
@Import(OperationTrackingBoundaryIT.ProbeConfiguration.class)
class OperationTrackingBoundaryIT extends E2eTestSupport {

    @Autowired
    private TrackingProbe probe;

    @Autowired
    private RecordingSink sink;

    @Autowired
    private JdbcTemplate jdbc;

    private UserSession user;
    private long houseId;
    private long rabbitId;

    @BeforeEach
    void prepareHouse() {
        user = register("track");
        houseId = createHouse(user, "追踪基座兔舍", 1, 2, 1);
        rabbitId = createRabbit(user, houseId, cageIds(user, houseId).get(0), "0", "0", "tracking");
        sink.clear();
    }

    @Test
    void failureMarkSurvivesTheBusinessRollback() {
        String requestId = requestId("probe_fail");

        BizException error = assertThrows(
                BizException.class,
                () -> probe.recordThenFail(user.userId, houseId, rabbitId, requestId)
        );
        assertEquals("探针刻意失败", error.getMessage());

        // 业务写入确实回滚了。
        assertEquals(
                0,
                countWeightLogs(requestId),
                "业务事务必须回滚，否则下面那条断言就不成立了"
        );
        // 失败标记却活了下来。同一次调用里，一个回滚了、一个没有，
        // 这只可能是因为 markFailed 根本不在业务事务里。
        assertEquals(
                "FAILED",
                dedupStatus(requestId, "probe:fail"),
                "markFailed 必须在业务事务之外提交，否则回滚会把去重记账一并抹掉"
        );
        // 失败不产生事件：事件写在事务内，随回滚一起作废。
        assertTrue(sink.events().isEmpty(), "回滚的操作不该在事件流里留下记录");
    }

    /**
     * 上一条断言的<b>阴性对照</b>。
     *
     * <p>没有它，「FAILED 还在」只能说明当下这么跑着，证明不了是切面顺序
     * 带来的。本用例拿同一张表、同一种失败，只把幂等记账改回改造前的写法
     * （在业务事务内部调 markProcessing / markFailed），结果失败标记连同那一行
     * 一起被回滚掉。两个用例差的只有「在不在事务内」这一件事。
     */
    @Test
    void theOldInlinePatternLosesTheFailureMark() {
        String requestId = requestId("legacy_fail");

        assertThrows(
                BizException.class,
                () -> probe.recordThenFailWithInlineDedup(user.userId, houseId, rabbitId, requestId)
        );

        assertEquals(0, countWeightLogs(requestId));
        assertNull(
                dedupStatus(requestId, "probe:legacy"),
                "改造前的写法里，失败标记与业务写入同事务，回滚会把它一并抹掉"
        );
    }

    @Test
    void eventsArePersistedInsideTheBusinessTransaction() {
        String requestId = requestId("probe_ok");

        probe.recordAndSucceed(user.userId, houseId, rabbitId, requestId);

        assertEquals(1, sink.appendCalls(), "500 只兔也只该有一次批量写入，这里 3 条同理");
        assertEquals(3, sink.events().size(), "批量登记的事件必须一次性提交，不是逐条");
        assertTrue(sink.sawActiveTransaction(), "事件必须写在业务事务内，否则回滚后事件仍在");
        assertNotNull(sink.transactionName());
        assertTrue(
                sink.transactionName().contains("recordAndSucceed"),
                "事件写入所处的事务应当就是业务方法自己的事务，实际为 " + sink.transactionName()
        );
    }

    @Test
    void dedupIsMarkedDoneOnlyAfterTheTransactionCommits() {
        String requestId = requestId("probe_done");

        probe.recordAndSucceed(user.userId, houseId, rabbitId, requestId);

        assertEquals(1, countWeightLogs(requestId));
        assertEquals("DONE", dedupStatus(requestId, "probe:ok"));
    }

    @Test
    void stampingFillsCreateByFromTheOperationContext() {
        String requestId = requestId("probe_stamp");

        probe.recordAndSucceed(user.userId, houseId, rabbitId, requestId);

        Map<String, Object> row = jdbc.queryForMap(
                "select create_by, update_by, house_id from weight_logs where request_id = ?", requestId);
        // 服务方法里已经没有任何 setCreateBy/setUpdateBy，这两列全靠盖章拦截器。
        assertEquals(String.valueOf(user.userId), row.get("create_by"));
        assertEquals(String.valueOf(user.userId), row.get("update_by"));
        assertEquals(houseId, ((Number) row.get("house_id")).longValue());
    }

    @Test
    void weightApiStillWritesCreateByAfterTheBoilerplateWasRemoved() {
        String requestId = requestId("weight");

        api.postOk("/api/weight-logs", user.token, houseId, obj(
                "rabbitId", rabbitId,
                "weighTime", now(),
                "weightKg", 3.5,
                "remark", "追踪基座回归",
                "requestId", requestId
        ));

        Map<String, Object> row = jdbc.queryForMap(
                "select create_by, update_by from weight_logs where request_id = ?", requestId);
        assertEquals(String.valueOf(user.userId), row.get("create_by"));
        assertEquals(String.valueOf(user.userId), row.get("update_by"));
        assertEquals("DONE", dedupStatus(requestId, "weight:create"));
    }

    @Test
    void repeatingTheSameRequestIdReplaysInsteadOfWritingTwice() {
        String requestId = requestId("weight_replay");
        Map<String, Object> body = obj(
                "rabbitId", rabbitId,
                "weighTime", now(),
                "weightKg", 3.5,
                "requestId", requestId
        );

        long first = api.postOk("/api/weight-logs", user.token, houseId, body).get("id").asLong();
        long second = api.postOk("/api/weight-logs", user.token, houseId, body).get("id").asLong();

        assertEquals(first, second, "同一 requestId 必须回放同一行");
        assertEquals(1, countWeightLogs(requestId));
    }

    private int countWeightLogs(String requestId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from weight_logs where request_id = ?", Integer.class, requestId);
        return count == null ? 0 : count;
    }

    private String dedupStatus(String requestId, String api) {
        List<String> rows = jdbc.queryForList(
                "select status from request_dedup where request_id = ? and api = ?",
                String.class, requestId, api);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean
        RecordingSink recordingSink() {
            return new RecordingSink();
        }

        @Bean
        TrackingProbe trackingProbe(WeightLogMapper weightLogMapper, RequestDedupService requestDedupService) {
            return new TrackingProbe(weightLogMapper, requestDedupService);
        }
    }

    /**
     * 记录 sink 被调用时的事务状态。这是「事件写在事务内」这条约束唯一
     * 可机检的证据——没有它，只能靠读代码相信顺序是对的。
     */
    static class RecordingSink implements OperationEventSink {
        private final List<OperationEvent> events = Collections.synchronizedList(new ArrayList<>());
        private volatile int appendCalls;
        private volatile boolean sawActiveTransaction;
        private volatile String transactionName;

        @Override
        public void append(List<OperationEvent> batch) {
            appendCalls++;
            sawActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
            events.addAll(batch);
        }

        void clear() {
            events.clear();
            appendCalls = 0;
            sawActiveTransaction = false;
            transactionName = null;
        }

        List<OperationEvent> events() {
            return List.copyOf(events);
        }

        int appendCalls() {
            return appendCalls;
        }

        boolean sawActiveTransaction() {
            return sawActiveTransaction;
        }

        String transactionName() {
            return transactionName;
        }
    }

    /**
     * 探针写的是真实业务表 {@code weight_logs}，不是临时表：要证明的是回滚
     * 对业务数据的作用，用一张假表证明不了任何事。
     */
    static class TrackingProbe {
        private final WeightLogMapper weightLogMapper;
        private final RequestDedupService requestDedupService;

        TrackingProbe(WeightLogMapper weightLogMapper, RequestDedupService requestDedupService) {
            this.weightLogMapper = weightLogMapper;
            this.requestDedupService = requestDedupService;
        }

        /**
         * 改造前 WeightService / TreatmentService 的写法，逐字保留：幂等记账写在
         * 业务事务内部，异常重抛导致回滚时一并丢失。留在这里当反例。
         */
        @Transactional
        public void recordThenFailWithInlineDedup(Long userId, Long houseId, Long rabbitId, String requestId) {
            String api = "probe:legacy";
            requestDedupService.markProcessing(houseId, userId, api, requestId);
            try {
                WeightLog log = new WeightLog();
                log.setHouseId(houseId);
                log.setRabbitId(rabbitId);
                log.setWeighTime(new java.util.Date());
                log.setWeightKg(3.0);
                log.setRequestId(requestId);
                log.setCreateBy(String.valueOf(userId));
                log.setUpdateBy(String.valueOf(userId));
                weightLogMapper.insert(log);
                throw new BizException(500, "旧写法刻意失败");
            } catch (RuntimeException e) {
                requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
                throw e;
            }
        }

        @TrackedOperation(code = "probe:fail", eventType = "PROBE", rabbitId = "#rabbitId", dedup = true)
        @Transactional
        public void recordThenFail(Long userId, Long houseId, Long rabbitId, String requestId) {
            insert(houseId, rabbitId, requestId, 3.1);
            throw new BizException(500, "探针刻意失败");
        }

        @TrackedOperation(code = "probe:ok", eventType = "PROBE", rabbitId = "#rabbitId", dedup = true)
        @Transactional
        public void recordAndSucceed(Long userId, Long houseId, Long rabbitId, String requestId) {
            insert(houseId, rabbitId, requestId, 3.3);
            OperationContext context = OperationContext.current();
            // 模拟批量端点：逐只登记，一次性提交。
            for (int i = 0; i < 3; i++) {
                context.recordEvent(OperationEvent.from(context)
                        .eventType("PROBE")
                        .rabbitId(rabbitId)
                        .build());
            }
        }

        private void insert(Long houseId, Long rabbitId, String requestId, double weightKg) {
            WeightLog log = new WeightLog();
            log.setRabbitId(rabbitId);
            log.setWeighTime(new java.util.Date());
            log.setWeightKg(weightKg);
            log.setRequestId(requestId);
            // 刻意不设 houseId / createBy / updateBy：全部交给盖章拦截器。
            weightLogMapper.insert(log);
        }
    }
}
