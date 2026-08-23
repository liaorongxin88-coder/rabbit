package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.rabbit.service.CommodityDailyCareReminderService;
import com.rabbit.app.modules.rabbit.service.CommodityGrowthService;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class CommodityDailyCareReminderIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CommodityDailyCareReminderService commodityDailyCareReminderService;

    @Autowired
    private CommodityGrowthService commodityGrowthService;

    @Test
    void schedulesStageSpecificDailyCareWithContentAndClearsItAtMaturity() {
        UserSession owner = register("commodity_daily_care");
        long houseId = createHouse(owner, "商品兔日常观察兔舍", 1, 2, 1);
        jdbc.update(
            "update global_setting set adaptation_days = 2, growing_days = 2, fattening_days = 2"
                + " where house_id = ?",
            houseId
        );
        long rabbitId = createRabbit(
            owner,
            houseId,
            cageIds(owner, houseId).getFirst(),
            "2",
            "0",
            "商品兔日常观察"
        );
        Date today = new Date();
        jdbc.update(
            "update rabbits set growth_stage = 'ADAPTATION', growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            today,
            houseId,
            rabbitId
        );

        commodityDailyCareReminderService.scheduleHouse(houseId, today);
        commodityDailyCareReminderService.scheduleHouse(houseId, today);
        assertCareTask(
            owner,
            houseId,
            rabbitId,
            "COMMODITY_ADAPTATION_CARE",
            "幼兔适应观察",
            "观察适应情况，按生长和体况分群。"
        );
        assertEquals(1, pendingCareCount(houseId, rabbitId));

        commodityDailyCareReminderService.scheduleHouse(houseId, DateUtil.plusDays(today, 1));
        assertEquals(2, pendingCareCount(houseId, rabbitId), "每天保留一条独立的待处理观察任务");
        assertEquals(2, jdbc.queryForObject(
            "select count(distinct dedup_key) from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'COMMODITY_ADAPTATION_CARE' and status = 'PENDING'",
            Integer.class,
            houseId,
            rabbitId
        ));

        updateGrowthEntry(houseId, rabbitId, 2);
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, new Date()));
        commodityDailyCareReminderService.scheduleHouse(houseId, new Date());
        assertCareTask(
            owner,
            houseId,
            rabbitId,
            "COMMODITY_GROWING_CARE",
            "生长饲喂观察",
            "观察采食、饮水和投料量。"
        );
        assertEquals(1, pendingCareCount(houseId, rabbitId));

        updateGrowthEntry(houseId, rabbitId, 4);
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, new Date()));
        commodityDailyCareReminderService.scheduleHouse(houseId, new Date());
        assertCareTask(
            owner,
            houseId,
            rabbitId,
            "COMMODITY_FATTENING_CARE",
            "育肥饲喂观察",
            "自由采食，检查料槽是否充足或发霉。"
        );
        assertEquals(1, pendingCareCount(houseId, rabbitId));

        updateGrowthEntry(houseId, rabbitId, 6);
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, new Date()));
        commodityDailyCareReminderService.scheduleHouse(houseId, new Date());
        assertEquals("MATURE", jdbc.queryForObject(
            "select growth_stage from rabbits where house_id = ? and id = ?",
            String.class,
            houseId,
            rabbitId
        ));
        assertEquals(0, pendingCareCount(houseId, rabbitId));
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY' and status = 'PENDING'",
            Integer.class,
            houseId,
            rabbitId
        ));
    }

    @Test
    void onlyShowsActiveCommodityCareAndCancelsItWhenTheRabbitLeaves() {
        UserSession owner = register("commodity_daily_care_visibility");
        long houseId = createHouse(owner, "商品兔观察可见性兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long activeCommodity = createRabbit(owner, houseId, cages.get(0), "2", "0", "在栏商品兔");
        long inactiveCommodity = createRabbit(owner, houseId, cages.get(1), "2", "0", "离场商品兔");
        long breeder = createRabbit(owner, houseId, cages.get(2), "0", "0", "种母兔");
        jdbc.update("update rabbits set growth_stage = 'GROWING' where house_id = ? and id in (?, ?)",
            houseId, activeCommodity, inactiveCommodity);
        jdbc.update("update rabbits set is_active = false where house_id = ? and id = ?", houseId, inactiveCommodity);
        insertGrowingCareTask(houseId, activeCommodity, "active");
        insertGrowingCareTask(houseId, inactiveCommodity, "inactive");
        insertGrowingCareTask(houseId, breeder, "breeder");

        JsonNode tasks = api.getOk(
            "/api/tasks?includeFuture=true&type=COMMODITY_GROWING_CARE",
            owner.token,
            houseId
        );
        assertEquals(1, tasks.get("total").asInt());
        assertEquals(activeCommodity, tasks.get("items").get(0).get("rabbitId").asLong());

        JsonNode event = dailyCareEvent(owner, houseId, activeCommodity);
        assertEquals("生长饲喂观察", event.get("eventType").asText());
        assertEquals("观察采食、饮水和投料量。", event.get("content").asText());
        assertFalse(hasDailyCareEvent(owner, houseId, inactiveCommodity));
        assertFalse(hasDailyCareEvent(owner, houseId, breeder));

        api.postOk("/api/rabbits/events", owner.token, houseId, obj(
            "rabbitId", activeCommodity,
            "eventType", "sale",
            "actionDate", oneMinuteAgo(),
            "reason", "出售商品兔",
            "forceExitBatch", false,
            "requestId", requestId("commodity_daily_care_sale")
        ));

        assertEquals(0, pendingCareCount(houseId, activeCommodity));
        assertFalse(hasDailyCareEvent(owner, houseId, activeCommodity));
    }

    private void assertCareTask(
        UserSession owner,
        long houseId,
        long rabbitId,
        String taskType,
        String label,
        String content
    ) {
        JsonNode tasks = api.getOk(
            "/api/tasks?includeFuture=true&rabbitId=" + rabbitId,
            owner.token,
            houseId
        );
        JsonNode task = null;
        for (JsonNode item : tasks.get("items")) {
            if (taskType.equals(item.get("taskType").asText())) {
                task = item;
                break;
            }
        }
        assertTrue(task != null, "当前阶段必须有每日观察待办: " + taskType);
        assertEquals(label, task.get("taskLabel").asText());
        assertEquals(content, task.get("remark").asText());

        JsonNode event = dailyCareEvent(owner, houseId, rabbitId);
        assertEquals(label, event.get("eventType").asText());
        assertEquals(content, event.get("content").asText());
    }

    private void updateGrowthEntry(long houseId, long rabbitId, int daysAgo) {
        jdbc.update(
            "update rabbits set growth_stage_entered_at = date_sub(now(), interval ? day)"
                + " where house_id = ? and id = ?",
            daysAgo,
            houseId,
            rabbitId
        );
    }

    private int pendingCareCount(long houseId, long rabbitId) {
        return jdbc.queryForObject(
            "select count(*) from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type in ('COMMODITY_ADAPTATION_CARE', 'COMMODITY_GROWING_CARE',"
                + " 'COMMODITY_FATTENING_CARE') and status = 'PENDING'",
            Integer.class,
            houseId,
            rabbitId
        );
    }

    private void insertGrowingCareTask(long houseId, long rabbitId, String suffix) {
        jdbc.update(
            "insert into work_tasks (house_id, task_type, subject_type, subject_id, rabbit_id,"
                + " due_date, due_time, status, dedup_key, remark, create_by, update_by)"
                + " values (?, 'COMMODITY_GROWING_CARE', 'RABBIT', ?, ?, curdate(), now(),"
                + " 'PENDING', ?, '观察采食、饮水和投料量。', 'test', 'test')",
            houseId,
            rabbitId,
            rabbitId,
            "commodity-care-visibility:" + suffix
        );
    }

    private JsonNode dailyCareEvent(UserSession owner, long houseId, long rabbitId) {
        for (JsonNode event : api.getOk("/api/events", owner.token, houseId)) {
            if (event.path("rabbitId").asLong() == rabbitId
                && event.path("eventType").asText().contains("观察")) {
                return event;
            }
        }
        throw new AssertionError("找不到商品兔日常观察提醒: " + rabbitId);
    }

    private boolean hasDailyCareEvent(UserSession owner, long houseId, long rabbitId) {
        for (JsonNode event : api.getOk("/api/events", owner.token, houseId)) {
            if (event.path("rabbitId").asLong() == rabbitId
                && event.path("eventType").asText().contains("观察")) {
                return true;
            }
        }
        return false;
    }
}
