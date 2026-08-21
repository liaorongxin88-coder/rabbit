package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.rabbit.service.CommodityGrowthService;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class ReplacementPromotionIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CommodityGrowthService commodityGrowthService;

    @Test
    void matureReplacementReminderPromotesDoeAndStartsUnassignedCycle() {
        UserSession owner = register("replacement_promote");
        long houseId = createHouse(owner, "后备转种兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long rabbitId = createRabbit(owner, houseId, cages.get(0), "2", "0", "新西兰白兔");

        jdbc.update(
            "update rabbits set growth_stage = 'JUVENILE',"
                + " growth_stage_entered_at = date_sub(now(), interval 40 day) where id = ?",
            rabbitId
        );
        Assertions.assertEquals(1, commodityGrowthService.advanceHouse(houseId, new java.util.Date()));
        Assertions.assertEquals("MATURE", jdbc.queryForObject(
            "select growth_stage from rabbits where house_id = ? and id = ?",
            String.class, houseId, rabbitId
        ));

        api.postOk("/api/rabbits/replacement", owner.token, houseId, obj(
            "rabbitIds", List.of(rabbitId),
            "targetCageId", cages.get(1),
            "forceExitBatch", true,
            "requestId", requestId("to_replacement")
        ));

        Assertions.assertEquals("REPLACEMENT_MATURE", jdbc.queryForObject(
            "select task_type from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'REPLACEMENT_MATURE' and status = 'PENDING'",
            String.class, houseId, rabbitId
        ));
        jdbc.update(
            "update replacement_records set expected_mature_date = date_sub(now(), interval 1 day)"
                + " where house_id = ? and rabbit_id = ? and status = 'PENDING'",
            houseId, rabbitId
        );
        jdbc.update(
            "update work_tasks set due_date = date_sub(curdate(), interval 1 day),"
                + " due_time = date_sub(now(), interval 1 day)"
                + " where house_id = ? and rabbit_id = ? and task_type = 'REPLACEMENT_MATURE'",
            houseId, rabbitId
        );

        JsonNode events = api.getOk("/api/events", owner.token, houseId);
        boolean found = false;
        for (JsonNode event : events) {
            if (event.get("rabbitId").asLong() == rabbitId
                && "后备成熟".equals(event.get("category").asText())) {
                found = true;
                Assertions.assertEquals("后备兔转种", event.get("eventType").asText());
            }
        }
        Assertions.assertTrue(found, "成熟后备兔必须出现在首页提醒适配接口");

        UserSession other = register("replacement_other");
        long otherHouse = createHouse(other, "其它兔舍", 1, 1, 1);
        api.expectError(
            "/api/rabbits/" + rabbitId + "/promote-breeder",
            HttpMethod.POST,
            other.token,
            otherHouse,
            obj("requestId", requestId("cross_house_promote")),
            404,
            "后备兔不存在"
        );

        String requestId = requestId("promote");
        api.postOk(
            "/api/rabbits/" + rabbitId + "/promote-breeder",
            owner.token,
            houseId,
            obj("requestId", requestId)
        );
        api.postOk(
            "/api/rabbits/" + rabbitId + "/promote-breeder",
            owner.token,
            houseId,
            obj("requestId", requestId)
        );

        Assertions.assertEquals("0", jdbc.queryForObject(
            "select type from rabbits where house_id = ? and id = ?",
            String.class, houseId, rabbitId
        ));
        Assertions.assertEquals("1", jdbc.queryForObject(
            "select status from cages where house_id = ? and id = ?",
            String.class, houseId, cages.get(1)
        ));
        Assertions.assertEquals("PROMOTED", jdbc.queryForObject(
            "select status from replacement_records where house_id = ? and rabbit_id = ? order by id desc limit 1",
            String.class, houseId, rabbitId
        ));
        Assertions.assertEquals("DONE", jdbc.queryForObject(
            "select status from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'REPLACEMENT_MATURE'",
            String.class, houseId, rabbitId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and batch_id is null and lifecycle = 'OPEN' and stage = 'AWAIT_ESTRUS'",
            Integer.class, houseId, rabbitId
        ));
    }
}
