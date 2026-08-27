package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RangeRabbitEntryIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void entersFixedCommodityCountAcrossCoordinateRange() {
        UserSession owner = register("range_entry_success");
        long houseId = createHouse(owner, "范围入栏成功", 2, 3, 2);

        JsonNode result = api.postOk("/api/rabbits/range-entry", owner.token, houseId, obj(
            "rowStart", 1,
            "rowEnd", 2,
            "positionStart", 1,
            "positionEnd", 3,
            "layerStart", 1,
            "layerEnd", 2,
            "rabbitsPerCage", 2,
            "type", "2",
            "gender", "0",
            "arrivalMethod", "1",
            "arrivalDate", now(),
            "requestId", requestId("range_success")
        ));

        Assertions.assertEquals(12, result.get("requestedSlotCount").asInt());
        Assertions.assertEquals(0, result.get("missingCageCount").asInt());
        Assertions.assertEquals(12, result.get("enteredCageCount").asInt());
        Assertions.assertEquals(24, result.get("enteredRabbitCount").asInt());
        Assertions.assertEquals(0, result.get("skippedCages").size());
        Assertions.assertEquals(24, jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and is_active = true",
            Integer.class,
            houseId
        ));
        Assertions.assertEquals(12, jdbc.queryForObject(
            "select count(*) from cages where house_id = ? and rabbit_count = 2 and status = '3'",
            Integer.class,
            houseId
        ));
    }

    @Test
    void skipsFullAndDisabledCagesWhileEnteringEligibleCages() {
        UserSession owner = register("range_entry_partial");
        long houseId = createHouse(owner, "范围入栏部分失败", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        for (int index = 0; index < 10; index++) {
            createRabbit(owner, houseId, cages.get(0), "2", "0", "full_" + index);
        }
        jdbc.update("update cages set is_enabled = false where house_id = ? and id = ?", houseId, cages.get(1));

        JsonNode result = api.postOk("/api/rabbits/range-entry", owner.token, houseId, obj(
            "rowStart", 1,
            "rowEnd", 1,
            "positionStart", 1,
            "positionEnd", 3,
            "layerStart", 1,
            "layerEnd", 1,
            "rabbitsPerCage", 1,
            "type", "2",
            "gender", "1",
            "arrivalMethod", "1",
            "arrivalDate", now(),
            "requestId", requestId("range_partial")
        ));

        Assertions.assertEquals(1, result.get("enteredCageCount").asInt());
        Assertions.assertEquals(1, result.get("enteredRabbitCount").asInt());
        Assertions.assertEquals(2, result.get("skippedCages").size());
        Assertions.assertTrue(
            result.get("skippedCages").toString().contains("商品兔笼已满"),
            "满笼必须在部分失败结果中说明"
        );
        Assertions.assertTrue(
            result.get("skippedCages").toString().contains("笼位已停用"),
            "停用笼必须在部分失败结果中说明"
        );
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select rabbit_count from cages where house_id = ? and id = ?",
            Integer.class,
            houseId,
            cages.get(2)
        ));
    }
}
