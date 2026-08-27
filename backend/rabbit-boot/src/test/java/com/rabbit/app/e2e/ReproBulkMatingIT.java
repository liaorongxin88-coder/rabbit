package com.rabbit.app.e2e;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class ReproBulkMatingIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bulkMatingIsRejectedWithoutSideEffects() {
        Round round = roundAwaitingMating("bulk_mating_rejected", 2);

        api.expectError("/api/repro/tasks/bulk-actions", HttpMethod.POST, round.owner.token, round.houseId, obj(
            "requestId", requestId("bulk_mating"),
            "action", "MATING",
            "occurredAt", oneMinuteAgo(),
            "maleRabbitId", round.buckId,
            "matingMethod", "NATURAL",
            "filter", obj("batchId", round.batchId, "taskType", "MATING")
        ), 400, "批量配种功能已下线，请逐只提交配种记录");

        Assertions.assertEquals(round.doeIds.size(), count(
            "select count(*) from breeding_cycles where house_id = ? and batch_id is null "
                + "and planned_batch_id = ? and stage = 'AWAIT_MATING' and mating_date is null",
            round.houseId, round.batchId), "拒绝后计划批次和配种日期必须保持不变");
        Assertions.assertEquals(round.doeIds.size(), count(
            "select count(*) from work_tasks where house_id = ? and batch_id = ? "
                + "and task_type = 'MATING' and status = 'PENDING'",
            round.houseId, round.batchId), "拒绝后待配种任务必须保持待处理");
        Assertions.assertEquals(0, count(
            "select count(*) from repro_events where house_id = ? and event_type = 'MATING_DONE'",
            round.houseId), "拒绝后不得写入配种事件");
    }

    @Test
    void individualMatingStillWorks() {
        Round round = roundAwaitingMating("individual_mating", 1);
        long doeId = round.doeIds.get(0);
        long cycleId = jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and planned_batch_id = ?"
                + " and mother_rabbit_id = ?",
            Long.class, round.houseId, round.batchId, doeId);

        api.postOk("/api/repro/cycles/" + cycleId + "/actions", round.owner.token, round.houseId, obj(
            "requestId", requestId("individual_mating"),
            "action", "MATING",
            "occurredAt", oneMinuteAgo(),
            "maleRabbitId", round.buckId,
            "matingMethod", "NATURAL"
        ));

        Assertions.assertEquals("AWAIT_PALPATION", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, cycleId));
        Assertions.assertEquals(1, count(
            "select count(*) from breeding_cycles where id = ? and batch_id = ?"
                + " and planned_batch_id is null",
            cycleId, round.batchId), "单只配种完成后必须正式绑定生产批次");
        Assertions.assertEquals(1, count(
            "select count(*) from repro_events where house_id = ? and event_type = 'MATING_DONE'",
            round.houseId), "单只配种必须写入配种事件");
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Round roundAwaitingMating(String prefix, int doeCount) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, doeCount + 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long buckId = createRabbit(owner, houseId, cages.get(0), "0", "1", prefix + "_buck");

        List<Long> does = new ArrayList<>();
        for (int i = 0; i < doeCount; i++) {
            does.add(createRabbit(owner, houseId, cages.get(i + 1), "0", "0", prefix + "_doe" + i));
        }
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "RBM-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", does,
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();

        for (int i = 0; i < does.size(); i++) {
            api.postOk("/api/repro/cycles", owner.token, houseId, obj(
                "motherRabbitId", does.get(i),
                "batchId", batchId,
                "stage", "AWAIT_ESTRUS",
                "occurredAt", oneMinuteAgo(),
                "requestId", requestId(prefix + "_cycle_" + i)
            ));
        }
        api.postOk("/api/repro/tasks/bulk-actions", owner.token, houseId, obj(
            "action", "ESTRUS",
            "occurredAt", oneMinuteAgo(),
            "filter", obj("batchId", batchId, "taskType", "ESTRUS"),
            "requestId", requestId(prefix + "_estrus")
        ));
        return new Round(owner, houseId, batchId, buckId, does);
    }

    private record Round(
        UserSession owner, long houseId, long batchId, long buckId, List<Long> doeIds
    ) {
    }
}
