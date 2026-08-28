package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchRabbitEntryIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordsMotherAndSellerAndSplitsTotalWeightAcrossTheLot() {
        UserSession owner = register("batch_entry_profile");
        long houseId = createHouse(owner, "批量录入档案", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long motherId = createRabbit(owner, houseId, cages.get(0), "0", "0", "母兔");

        JsonNode retained = api.postOk("/api/rabbits/batch-entry", owner.token, houseId, obj(
            "cageId", cages.get(1),
            "motherId", motherId,
            "type", "2",
            "gender", "0",
            "breed", "新西兰白",
            "arrivalMethod", "1",
            "arrivalDate", now(),
            "quantity", 3,
            "totalWeight", 7.5,
            "requestId", requestId("batch_retained")
        ));

        Assertions.assertEquals(3, retained.get("enteredRabbitCount").asInt());
        Assertions.assertEquals(0, retained.get("skippedCages").size());
        Assertions.assertEquals(3, jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and mother_id = ?",
            Integer.class,
            houseId,
            motherId
        ));
        Assertions.assertEquals(7.5, jdbc.queryForObject(
            "select sum(weight) from rabbits where house_id = ? and mother_id = ?",
            Double.class,
            houseId,
            motherId
        ), 0.0001);

        JsonNode purchased = api.postOk("/api/rabbits/batch-entry", owner.token, houseId, obj(
            "cageId", cages.get(2),
            "type", "2",
            "gender", "1",
            "breed", "加利福尼亚",
            "arrivalMethod", "0",
            "sourceSeller", "测试供应方",
            "arrivalDate", now(),
            "quantity", 2,
            "totalWeight", 5.2,
            "requestId", requestId("batch_purchased")
        ));

        Assertions.assertEquals(2, purchased.get("enteredRabbitCount").asInt());
        Assertions.assertEquals(2, jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and source_seller = ?",
            Integer.class,
            houseId,
            "测试供应方"
        ));
    }

    @Test
    void reportsPartialCapacityAndReplaysWithoutAdditionalWrites() {
        UserSession owner = register("batch_entry_partial");
        long houseId = createHouse(owner, "批量录入部分成功", 1, 1, 1);
        long cageId = cageIds(owner, houseId).getFirst();
        for (int index = 0; index < 8; index++) {
            createRabbit(owner, houseId, cageId, "2", "0", "存量" + index);
        }
        String requestId = requestId("batch_partial");
        var body = obj(
            "cageId", cageId,
            "type", "2",
            "gender", "0",
            "arrivalMethod", "0",
            "arrivalDate", now(),
            "quantity", 4,
            "totalWeight", 8.0,
            "requestId", requestId
        );

        JsonNode first = api.postOk("/api/rabbits/batch-entry", owner.token, houseId, body);
        Assertions.assertEquals(4, first.get("requestedRabbitCount").asInt());
        Assertions.assertEquals(2, first.get("enteredRabbitCount").asInt());
        Assertions.assertEquals(1, first.get("skippedCages").size());
        Assertions.assertEquals(2, first.get("skippedCages").get(0).get("rabbitCount").asInt());
        Assertions.assertTrue(
            first.get("skippedCages").get(0).get("reason").asText().contains("容量不足")
        );

        JsonNode replay = api.postOk("/api/rabbits/batch-entry", owner.token, houseId, body);
        Assertions.assertEquals(0, replay.get("enteredRabbitCount").asInt());
        Assertions.assertEquals(2, replay.get("replayedRabbitCount").asInt());
        Assertions.assertEquals(1, replay.get("skippedCages").size());
        Assertions.assertEquals(10, jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and cage_id = ? and is_active = true",
            Integer.class,
            houseId,
            cageId
        ));

        api.expectError(
            "/api/rabbits/batch-entry",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "cageId", cageId,
                "type", "FEMALE",
                "gender", "0",
                "arrivalMethod", "0",
                "arrivalDate", now(),
                "quantity", 1,
                "totalWeight", 1.0,
                "requestId", requestId("batch_invalid_type")
            ),
            400,
            "兔只类型"
        );
    }
}
