package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the whole-round mating contract and its idempotent retry. */
public class BulkMatingIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void matesWholeRoundWithOneBuckAndRetriesWithoutDuplicateCycles() {
        UserSession owner = register("bulk_mating");
        long houseId = createHouse(owner, "bulk_mating_house", 1, 5, 1);
        List<Long> cages = cageIds(owner, houseId);
        long maleId = createRabbit(owner, houseId, cages.get(0), "0", "1", "bulk_buck");
        List<Long> mothers = new ArrayList<Long>();
        for (int index = 1; index < cages.size(); index++) {
            mothers.add(createRabbit(owner, houseId, cages.get(index), "0", "0", "bulk_doe_" + index));
        }

        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "BULK-MATING-" + requestId("code").substring(0, 8),
                "femaleRabbitIds", mothers,
                "requestId", requestId("bulk_batch")
        )).get("id").asLong();
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId,
                obj("rabbitIds", mothers, "requestId", requestId("bulk_aph_start")));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId,
                obj("rabbitIds", mothers, "requestId", requestId("bulk_aph_finish")));

        String requestId = requestId("bulk_mating_round");
        long matingDate = oneMinuteAgo();
        var body = obj(
                "femaleRabbitIds", mothers,
                "maleRabbitId", maleId,
                "matingDate", matingDate,
                "requestId", requestId
        );
        JsonNode first = api.postOk("/api/batches/" + batchId + "/mating/bulk", owner.token, houseId, body);
        JsonNode retry = api.postOk("/api/batches/" + batchId + "/mating/bulk", owner.token, houseId, body);

        Assertions.assertEquals(mothers.size(), first.get("count").asInt());
        Assertions.assertEquals(first, retry);
        api.expectError(
                "/api/batches/" + batchId + "/mating/bulk",
                HttpMethod.POST,
                owner.token,
                houseId,
                obj(
                        "femaleRabbitIds", mothers,
                        "maleRabbitId", maleId,
                        "matingDate", matingDate + 1_000,
                        "requestId", requestId
                ),
                409,
                "requestId已用于不同的批量配种请求"
        );
        Assertions.assertEquals(mothers.size(), jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ?",
                Integer.class,
                houseId,
                batchId
        ));
        Assertions.assertEquals(mothers.size(), jdbc.queryForObject(
                "select count(distinct request_id) from breeding_cycles where house_id = ? and batch_id = ?",
                Integer.class,
                houseId,
                batchId
        ));
        Assertions.assertEquals(mothers.size(), jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and current_status = '已配种' and is_active = true",
                Integer.class,
                batchId
        ));
    }
}
