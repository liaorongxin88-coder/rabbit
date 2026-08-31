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
import org.springframework.http.HttpMethod;
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
            "update rabbits set growth_stage = 'JUVENILE', growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            today,
            houseId,
            rabbitId
        );
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, today));
        assertEquals("ADAPTATION", jdbc.queryForObject(
            "select growth_stage from rabbits where house_id = ? and id = ?",
            String.class,
            houseId,
            rabbitId
        ));

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

        Date growingNow = new Date();
        updateGrowthEntry(houseId, rabbitId, "ADAPTATION", growingNow, 3);
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, growingNow));
        assertGrowthStageEnteredAt(
            houseId,
            rabbitId,
            DateUtil.plusDays(growingNow, -1)
        );
        commodityDailyCareReminderService.scheduleHouse(houseId, growingNow);
        assertCareTask(
            owner,
            houseId,
            rabbitId,
            "COMMODITY_GROWING_CARE",
            "生长饲喂观察",
            "观察采食、饮水和投料量。"
        );
        assertEquals(1, pendingCareCount(houseId, rabbitId));

        Date fatteningNow = new Date();
        updateGrowthEntry(houseId, rabbitId, "GROWING", fatteningNow, 2);
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, fatteningNow));
        assertGrowthStageEnteredAt(
            houseId,
            rabbitId,
            DateUtil.plusDays(fatteningNow, -1)
        );
        commodityDailyCareReminderService.scheduleHouse(houseId, fatteningNow);
        assertCareTask(
            owner,
            houseId,
            rabbitId,
            "COMMODITY_FATTENING_CARE",
            "育肥饲喂观察",
            "自由采食，检查料槽是否充足或发霉。"
        );
        assertEquals(1, pendingCareCount(houseId, rabbitId));

        Date maturityNow = new Date();
        updateGrowthEntry(houseId, rabbitId, "FATTENING", maturityNow, 2);
        assertEquals(1, commodityGrowthService.advanceHouse(houseId, maturityNow));
        assertGrowthStageEnteredAt(
            houseId,
            rabbitId,
            DateUtil.plusDays(maturityNow, -1)
        );
        jdbc.update(
            "delete from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY'",
            houseId,
            rabbitId
        );
        commodityDailyCareReminderService.scheduleHouse(houseId, maturityNow);
        commodityDailyCareReminderService.scheduleHouse(houseId, maturityNow);
        assertEquals("MATURE", jdbc.queryForObject(
            "select growth_stage from rabbits where house_id = ? and id = ?",
            String.class,
            houseId,
            rabbitId
        ));
        assertEquals(0, pendingCareCount(houseId, rabbitId));
        assertEquals(1, pendingSaleReadyCount(houseId, rabbitId));
        assertEquals(1, totalSaleReadyCount(houseId, rabbitId));
    }

    @Test
    void feedingCompletesOnlyTheSubmittedRabbitsCareForThatBusinessDate() {
        UserSession owner = register("commodity_feed_completes_care");
        long houseId = createHouse(owner, "商品兔投喂完成观察兔舍", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long fedRabbit = createRabbit(
            owner, houseId, cages.get(0), "2", "0", "已投喂商品兔"
        );
        long untouchedRabbit = createRabbit(
            owner, houseId, cages.get(1), "2", "0", "未投喂商品兔"
        );
        Date feedTime = new Date();
        commodityDailyCareReminderService.scheduleHouse(houseId, feedTime);

        assertEquals(1, pendingCareCount(houseId, fedRabbit));
        assertEquals(1, pendingCareCount(houseId, untouchedRabbit));
        assertTrue(hasDailyCareEvent(owner, houseId, fedRabbit));
        assertTrue(hasDailyCareEvent(owner, houseId, untouchedRabbit));

        String feedRequestId = requestId("complete_daily_care");
        api.postOk("/api/feed-logs", owner.token, houseId, obj(
            "rabbitIds", List.of(fedRabbit),
            "feedTime", feedTime.getTime(),
            "feedType", "日常投喂",
            "unit", "kg",
            "amount", 0.8,
            "requestId", feedRequestId
        ));
        api.postOk("/api/feed-logs", owner.token, houseId, obj(
            "rabbitIds", List.of(fedRabbit),
            "feedTime", feedTime.getTime(),
            "feedType", "日常投喂",
            "unit", "kg",
            "amount", 0.8,
            "requestId", feedRequestId
        ));

        assertEquals(0, pendingCareCount(houseId, fedRabbit));
        assertEquals(1, pendingCareCount(houseId, untouchedRabbit));
        assertFalse(hasDailyCareEvent(owner, houseId, fedRabbit));
        assertTrue(hasDailyCareEvent(owner, houseId, untouchedRabbit));
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from feed_logs where house_id = ? and request_id = ?",
            Integer.class,
            houseId,
            feedRequestId
        ));

        String failedRequestId = requestId("failed_feed_keeps_daily_care");
        api.expectError("/api/feed-logs", HttpMethod.POST, owner.token, houseId, obj(
            "rabbitIds", List.of(untouchedRabbit),
            "feedTime", feedTime.getTime(),
            "feedType", "日常投喂",
            "itemId", Long.MAX_VALUE,
            "unit", "kg",
            "amount", 0.8,
            "requestId", failedRequestId
        ), 400, "物料不存在");

        assertEquals(1, pendingCareCount(houseId, untouchedRabbit));
        assertTrue(hasDailyCareEvent(owner, houseId, untouchedRabbit));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from feed_logs where house_id = ? and request_id = ?",
            Integer.class,
            houseId,
            failedRequestId
        ));
    }

    @Test
    void saleReadyUsesMatureStateBeforeTimeAndElapsedTimeAsFallback() {
        UserSession owner = register("commodity_sale_ready_predicate");
        long houseId = createHouse(owner, "商品兔成熟判定兔舍", 1, 3, 1);
        jdbc.update(
            "update global_setting set adaptation_days = 2, growing_days = 2, fattening_days = 2"
                + " where house_id = ?",
            houseId
        );
        List<Long> cages = cageIds(owner, houseId);
        long matureByState = createRabbit(
            owner, houseId, cages.get(0), "2", "0", "状态提前成熟"
        );
        long matureByTime = createRabbit(
            owner, houseId, cages.get(1), "2", "0", "时间达到成熟"
        );
        long notMature = createRabbit(
            owner, houseId, cages.get(2), "2", "0", "尚未成熟"
        );
        long matureByArrival = createRabbit(
            owner, houseId, cages.get(2), "2", "1", "历史入场时间达到成熟"
        );
        long missingTimeAnchor = createRabbit(
            owner, houseId, cages.get(2), "2", "0", "缺少成熟时间锚点"
        );
        Date schedulerNow = new Date();
        Date fatteningEnteredAt = DateUtil.plusDays(schedulerNow, -3);
        Date arrivalAnchor = DateUtil.plusDays(schedulerNow, -7);
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE', growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            schedulerNow,
            houseId,
            matureByState
        );
        jdbc.update(
            "update rabbits set growth_stage = 'FATTENING', growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            fatteningEnteredAt,
            houseId,
            matureByTime
        );
        jdbc.update(
            "update rabbits set growth_stage = 'GROWING', growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            schedulerNow,
            houseId,
            notMature
        );
        jdbc.update(
            "update rabbits set growth_stage = 'ADAPTATION', growth_stage_entered_at = null,"
                + " arrival_date = ? where house_id = ? and id = ?",
            arrivalAnchor,
            houseId,
            matureByArrival
        );
        jdbc.update(
            "update rabbits set growth_stage = 'GROWING', growth_stage_entered_at = null,"
                + " arrival_date = null where house_id = ? and id = ?",
            houseId,
            missingTimeAnchor
        );
        jdbc.update(
            "delete from work_tasks where house_id = ? and rabbit_id in (?, ?, ?, ?, ?)"
                + " and task_type = 'SALE_READY'",
            houseId,
            matureByState,
            matureByTime,
            notMature,
            matureByArrival,
            missingTimeAnchor
        );

        commodityDailyCareReminderService.scheduleHouse(houseId, schedulerNow);
        commodityDailyCareReminderService.scheduleHouse(houseId, schedulerNow);

        assertEquals(1, pendingSaleReadyCount(houseId, matureByState));
        assertEquals(1, pendingSaleReadyCount(houseId, matureByTime));
        assertEquals(1, totalSaleReadyCount(houseId, matureByState));
        assertEquals(1, totalSaleReadyCount(houseId, matureByTime));
        assertEquals(1, pendingSaleReadyCount(houseId, matureByArrival));
        assertEquals(0, pendingCareCount(houseId, matureByState));
        assertEquals(0, pendingCareCount(houseId, matureByTime));
        assertEquals(0, pendingCareCount(houseId, matureByArrival));
        assertEquals(0, pendingSaleReadyCount(houseId, notMature));
        assertEquals(1, pendingCareCount(houseId, notMature));
        assertEquals(0, pendingSaleReadyCount(houseId, missingTimeAnchor));
        assertEquals(1, pendingCareCount(houseId, missingTimeAnchor));

        Date stateDueTime = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY' and status = 'PENDING'",
            Date.class,
            houseId,
            matureByState
        );
        Date elapsedDueTime = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY' and status = 'PENDING'",
            Date.class,
            houseId,
            matureByTime
        );
        Date arrivalDueTime = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY' and status = 'PENDING'",
            Date.class,
            houseId,
            matureByArrival
        );
        assertTrue(Math.abs(stateDueTime.getTime() - schedulerNow.getTime()) < 1_000L,
            "MATURE 状态必须把待出售时间拉到本次调度时间");
        assertTrue(Math.abs(
            elapsedDueTime.getTime() - DateUtil.plusDays(fatteningEnteredAt, 2).getTime()
        ) < 1_000L, "育肥期应按进入当前阶段日期加剩余时长计算成熟时间");
        assertTrue(Math.abs(
            arrivalDueTime.getTime() - DateUtil.plusDays(arrivalAnchor, 7).getTime()
        ) < 1_000L, "缺少阶段时间时必须回退到断奶日期计算成熟时间");
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

    private void updateGrowthEntry(
        long houseId,
        long rabbitId,
        String growthStage,
        Date now,
        int daysAgo
    ) {
        // 适应期以断奶日为准；后续阶段继续使用各自的进入日期。
        Date enteredAt = DateUtil.plusDays(now, -daysAgo - 1);
        if ("ADAPTATION".equals(growthStage)) {
            jdbc.update(
                "update rabbits set growth_stage = ?, arrival_date = ?,"
                    + " growth_stage_entered_at = ? where house_id = ? and id = ?",
                growthStage,
                enteredAt,
                enteredAt,
                houseId,
                rabbitId
            );
            return;
        }
        jdbc.update(
            "update rabbits set growth_stage = ?, growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            growthStage,
            enteredAt,
            houseId,
            rabbitId
        );
    }

    private void assertGrowthStageEnteredAt(long houseId, long rabbitId, Date expected) {
        Date actual = jdbc.queryForObject(
            "select growth_stage_entered_at from rabbits where house_id = ? and id = ?",
            Date.class,
            houseId,
            rabbitId
        );
        assertTrue(
            Math.abs(actual.getTime() - expected.getTime()) < 1_000L,
            "自动推进应把阶段进入日期设为实际跨阶段阈值"
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

    private int pendingSaleReadyCount(long houseId, long rabbitId) {
        return jdbc.queryForObject(
            "select count(*) from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY' and status = 'PENDING'",
            Integer.class,
            houseId,
            rabbitId
        );
    }

    private int totalSaleReadyCount(long houseId, long rabbitId) {
        return jdbc.queryForObject(
            "select count(*) from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'SALE_READY'",
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
