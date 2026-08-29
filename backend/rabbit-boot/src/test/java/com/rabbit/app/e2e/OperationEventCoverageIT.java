package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 写操作是否真的留痕。
 *
 * <p>为什么需要这条：`@TrackedOperation` 一直贴在 RabbitService 的四参重载上，
 * 而控制器走的是五参方法，于是单只建兔从来没有进过事件流。注解看着齐全、
 * 代码审查也过得去，缺的只是一条「操作做完，流水里有没有这条」的断言。
 *
 * <p>所以这里一律走真实 HTTP，然后回查 repro_events：只信端到端的结果，
 * 不信注解的存在。新增写入口时应当在这里补一行。
 */
class OperationEventCoverageIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private UserSession user;
    private long houseId;
    private List<Long> cages;

    @BeforeEach
    void prepareHouse() {
        user = register("opcov");
        houseId = createHouse(user, "留痕覆盖兔舍", 1, 4, 1);
        cages = cageIds(user, houseId);
    }

    @Test
    void creatingARabbitLeavesATrail() {
        // 这条正是上面说的那个洞：修复前它是 0 条。
        long rabbitId = createRabbit(user, houseId, cages.get(0), "0", "0", "cov");

        assertEventRecorded("rabbit.create", "RABBIT", rabbitId);
    }

    @Test
    void weighingARabbitLeavesATrail() {
        long rabbitId = createRabbit(user, houseId, cages.get(0), "0", "0", "cov_weight");

        api.postOk("/api/weight-logs", user.token, houseId, obj(
            "rabbitId", rabbitId,
            "weightKg", 2.35,
            "weighTime", System.currentTimeMillis(),
            "requestId", requestId("cov_weight")
        ));

        assertEventRecorded("weight:create", "RABBIT", rabbitId);
    }

    @Test
    void vaccinatingARabbitLeavesATrail() {
        long rabbitId = createRabbit(user, houseId, cages.get(0), "0", "0", "cov_vacc");

        api.postOk("/api/vaccinations", user.token, houseId, obj(
            "rabbitIds", List.of(rabbitId),
            "vaccineName", "兔瘟疫苗",
            "vaccinatedAt", System.currentTimeMillis(),
            "requestId", requestId("cov_vacc")
        ));

        assertEventRecorded("vaccination:create", "RABBIT", rabbitId);
    }

    @Test
    void movingARabbitBetweenCagesLeavesATrail() {
        long rabbitId = createRabbit(user, houseId, cages.get(0), "0", "0", "cov_move");

        api.postOk("/api/rabbits/" + rabbitId + "/cage-transfer", user.token, houseId, obj(
            "targetCageId", cages.get(1),
            "requestId", requestId("cov_move")
        ));

        assertEventRecorded("rabbit.transferCage", "RABBIT", rabbitId);
    }

    @Test
    void creatingABatchLeavesATrail() {
        JsonNode batch = api.postOk("/api/batches", user.token, houseId, obj(
            "batchCode", "COV-" + System.currentTimeMillis() % 100_000,
            "requestId", requestId("cov_batch")
        ));

        assertEventRecorded("batch.create", "BATCH", batch.path("id").asLong());
    }

    /**
     * 一次操作只留一条。
     *
     * <p>切面会在业务方法没有自己登记事件时补一条；如果某天业务方法也开始登记，
     * 两边同时生效就会出现重复留痕，事故复盘时会把一次操作读成两次。
     */
    @Test
    void oneOperationNeverLeavesTwoRows() {
        long rabbitId = createRabbit(user, houseId, cages.get(0), "0", "0", "cov_once");

        Integer count = jdbc.queryForObject(
            "select count(*) from repro_events where house_id = ? and operation_code = 'rabbit.create'"
                + " and target_type = 'RABBIT' and target_id = ?",
            Integer.class, houseId, rabbitId
        );
        Assertions.assertEquals(1, count, "同一次操作不该留下两条事件");
    }

    private void assertEventRecorded(String operationCode, String targetType, long targetId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select operation_code, event_type, target_type, target_id, operator_name"
                + " from repro_events where house_id = ? and operation_code = ?"
                + " and target_type = ? and target_id = ?",
            houseId, operationCode, targetType, targetId
        );

        Assertions.assertEquals(
            1, rows.size(),
            operationCode + " 应当在事件流里留下恰好一条针对 " + targetType + " #" + targetId + " 的记录"
        );
        Assertions.assertEquals(
            user.userName, rows.get(0).get("operator_name"),
            "留痕要能回答「谁做的」，存的必须是当时的展示名"
        );
    }
}
