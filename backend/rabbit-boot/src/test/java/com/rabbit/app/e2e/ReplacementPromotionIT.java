package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.modules.rabbit.service.CommodityGrowthService;
import com.rabbit.app.util.DateUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
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
    void directReplacementReminderStartsWhenTheReplacementIsCreated() {
        UserSession owner = register("replacement_direct");
        long houseId = createHouse(owner, "直接新增后备兔舍", 1, 1, 1);
        long cageId = cageIds(owner, houseId).get(0);
        long beforeCreate = System.currentTimeMillis();

        JsonNode rabbit = api.postOk("/api/rabbits", owner.token, houseId, obj(
            "cageId", cageId,
            "type", "1",
            "gender", "0",
            "breed", "新西兰白兔",
            "arrivalMethod", "1",
            "arrivalDate", beforeCreate - 180L * 24 * 3600 * 1000,
            "weight", 3.2,
            "requestId", requestId("replacement_direct_rabbit")
        ));
        long afterCreate = System.currentTimeMillis();
        long rabbitId = rabbit.get("id").asLong();

        Date replacementDate = jdbc.queryForObject(
            "select replacement_date from replacement_records"
                + " where house_id = ? and rabbit_id = ? and status = 'PENDING'",
            Date.class, houseId, rabbitId
        );
        Date matureDate = jdbc.queryForObject(
            "select expected_mature_date from replacement_records"
                + " where house_id = ? and rabbit_id = ? and status = 'PENDING'",
            Date.class, houseId, rabbitId
        );
        Date taskDue = jdbc.queryForObject(
            "select due_time from work_tasks where house_id = ? and rabbit_id = ?"
                + " and task_type = 'REPLACEMENT_MATURE' and status = 'PENDING'",
            Date.class, houseId, rabbitId
        );

        Assertions.assertNotNull(replacementDate);
        Assertions.assertTrue(replacementDate.getTime() >= beforeCreate - 1000);
        Assertions.assertTrue(replacementDate.getTime() <= afterCreate + 1000);
        Assertions.assertEquals(90, com.rabbit.app.util.DateUtil.daysBetween(replacementDate, matureDate));
        Assertions.assertTrue(Math.abs(matureDate.getTime() - taskDue.getTime()) < 1000);
    }

    @Test
    void matureCommodityRabbitAppearsAsHomepageSaleReminder() {
        UserSession owner = register("commodity_home_reminder");
        long houseId = createHouse(owner, "商品兔成熟提醒兔舍", 1, 1, 1);
        long cageId = cageIds(owner, houseId).get(0);
        JsonNode rabbit = api.postOk("/api/rabbits", owner.token, houseId, obj(
            "cageId", cageId,
            "type", "2",
            "gender", "0",
            "breed", "商品兔",
            "arrivalMethod", "1",
            "arrivalDate", System.currentTimeMillis() - 40L * 24 * 3600 * 1000,
            "weight", 2.5,
            "requestId", requestId("commodity_home_rabbit")
        ));
        long rabbitId = rabbit.get("id").asLong();

        commodityGrowthService.advanceHouse(houseId, new Date());
        Assertions.assertEquals("MATURE", jdbc.queryForObject(
            "select growth_stage from rabbits where house_id = ? and id = ?",
            String.class, houseId, rabbitId
        ));

        JsonNode events = api.getOk("/api/events", owner.token, houseId);
        boolean found = false;
        for (JsonNode event : events) {
            if (event.get("rabbitId").asLong() == rabbitId
                && "生产".equals(event.get("category").asText())
                && "出售".equals(event.get("eventType").asText())) {
                found = true;
            }
        }
        Assertions.assertTrue(found, "成熟商品兔必须出现在首页出售提醒中");
    }

    @Test
    void historicalCommodityEntryDatesPersistAdvanceAndKeepReminderCalendarDays() {
        UserSession owner = register("commodity_entry_dates");
        long houseId = createHouse(owner, "商品兔历史入场日期兔舍", 1, 1, 1);
        long cageId = cageIds(owner, houseId).get(0);
        List<LocalDate> selectedDates = List.of(
            LocalDate.of(2025, 8, 21),
            LocalDate.of(2025, 8, 23)
        );
        List<Long> rabbitIds = new ArrayList<>();
        List<String> dueDates = new ArrayList<>();
        int maturityDays = jdbc.queryForObject(
            "select adaptation_days + growing_days + fattening_days"
                + " from global_setting where house_id = ?",
            Integer.class, houseId
        );

        for (int index = 0; index < selectedDates.size(); index++) {
            LocalDate selectedDate = selectedDates.get(index);
            JsonNode rabbit = api.postOk("/api/rabbits", owner.token, houseId, obj(
                "cageId", cageId,
                "type", "2",
                "gender", "0",
                "breed", "历史入场商品兔",
                "arrivalMethod", "0",
                "arrivalDate", selectedDate.toString(),
                "weight", 2.5,
                "requestId", requestId("commodity_entry_date_" + index)
            ));
            long rabbitId = rabbit.get("id").asLong();
            rabbitIds.add(rabbitId);

            Assertions.assertEquals(selectedDate.toString(), rabbit.get("arrivalDate").asText());
            Assertions.assertEquals(selectedDate.toString(), jdbc.queryForObject(
                "select date_format(arrival_date, '%Y-%m-%d') from rabbits"
                    + " where house_id = ? and id = ?",
                String.class, houseId, rabbitId
            ));
            Assertions.assertEquals(selectedDate.toString(), jdbc.queryForObject(
                "select date_format(growth_stage_entered_at, '%Y-%m-%d') from rabbits"
                    + " where house_id = ? and id = ?",
                String.class, houseId, rabbitId
            ));
            String expectedDueDate = selectedDate.plusDays(maturityDays).toString();
            String dueDate = jdbc.queryForObject(
                "select date_format(due_date, '%Y-%m-%d') from work_tasks"
                    + " where house_id = ? and rabbit_id = ?"
                    + " and task_type = 'SALE_READY' and status = 'PENDING'",
                String.class, houseId, rabbitId
            );
            Assertions.assertEquals(expectedDueDate, dueDate);
            dueDates.add(dueDate);
        }

        Assertions.assertEquals(2,
            com.rabbit.app.util.DateUtil.daysBetween(
                java.sql.Date.valueOf(dueDates.get(0)),
                java.sql.Date.valueOf(dueDates.get(1))
            ));
        Assertions.assertEquals(2, commodityGrowthService.advanceHouse(houseId, new Date()));
        for (Long rabbitId : rabbitIds) {
            Assertions.assertEquals("MATURE", jdbc.queryForObject(
                "select growth_stage from rabbits where house_id = ? and id = ?",
                String.class, houseId, rabbitId
            ));
        }

        JsonNode events = api.getOk("/api/events", owner.token, houseId);
        for (int index = 0; index < rabbitIds.size(); index++) {
            long rabbitId = rabbitIds.get(index);
            String expectedDueDate = dueDates.get(index);
            boolean found = false;
            for (JsonNode event : events) {
                if (event.get("rabbitId").asLong() == rabbitId
                    && "出售".equals(event.get("eventType").asText())) {
                    Assertions.assertEquals(expectedDueDate, event.get("eventDate").asText());
                    found = true;
                }
            }
            Assertions.assertTrue(found, "历史入场商品兔必须保留各自的出售提醒日期");
        }
    }

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
        Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN'",
            Integer.class, houseId, rabbitId
        ), "后备母兔转种后要先选择生产批次，再由入批路径开启周期");
    }

    @Test
    void commodityGrowthUsesConfiguredStageDurations() {
        UserSession owner = register("commodity_growth_stages");
        long houseId = createHouse(owner, "商品兔阶段兔舍", 1, 1, 1);
        long rabbitId = createRabbit(owner, houseId, cageIds(owner, houseId).getFirst(), "2", "0", "新西兰白兔");
        jdbc.update(
            "update global_setting set adaptation_days = 2, growing_days = 15, fattening_days = 12"
                + " where house_id = ?",
            houseId
        );

        assertCommodityGrowthStage(houseId, rabbitId, 2, "GROWING");
        assertCommodityGrowthStage(houseId, rabbitId, 17, "FATTENING");
        assertCommodityGrowthStage(houseId, rabbitId, 29, "MATURE");
    }

    private void assertCommodityGrowthStage(long houseId, long rabbitId, int daysSinceEntry, String expectedStage) {
        // 入栏时间和推进时刻必须来自同一个时钟，而且不能恰好卡在阈值上。
        //
        // 旧写法用 MySQL 的 now() 算入栏时间、JVM 的 new Date() 做推进，而判定是
        // date_add(entered_at, N day) <= #{now}，代入后变成 mysqlNow <= jvmNow，
        // 两个时钟差一点就翻面。改成同一个时刻后仍会间歇失败：
        // DATETIME 是秒精度，写入时对毫秒四舍五入，向上舍入就会把入栏时间推迟不足一秒，
        // 恰好让等号不成立——能不能过取决于当前时刻的毫秒位，实质是抛硬币。
        //
        // 阶段窗口相隔以天计，因此多给一小时余量既能消除秒级歧义，又不会跨到下一阶段。
        Date now = new Date();
        Date enteredAt = new Date(DateUtil.plusDays(now, -daysSinceEntry).getTime() - 3_600_000L);
        jdbc.update(
            "update rabbits set growth_stage = 'JUVENILE',"
                + " growth_stage_entered_at = ?"
                + " where house_id = ? and id = ?",
            enteredAt,
            houseId,
            rabbitId
        );

        Assertions.assertEquals(1, commodityGrowthService.advanceHouse(houseId, now));
        Assertions.assertEquals(expectedStage, jdbc.queryForObject(
            "select growth_stage from rabbits where house_id = ? and id = ?",
            String.class,
            houseId,
            rabbitId
        ));
    }
}
