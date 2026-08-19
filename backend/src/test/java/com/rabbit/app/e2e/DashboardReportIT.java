package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

public class DashboardReportIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;
    @Test
    void dashboardAggregatesOnlyAuthorizedHouses() {
        UserSession owner = register("dashboard_owner");
        UserSession outsider = register("dashboard_outsider");
        long firstHouseId = createHouse(owner, "dashboard_first", 1, 2, 1);
        long secondHouseId = createHouse(owner, "dashboard_second", 1, 2, 1);
        List<Long> firstCages = cageIds(owner, firstHouseId);
        List<Long> secondCages = cageIds(owner, secondHouseId);

        createRabbit(owner, firstHouseId, firstCages.get(0), "0", "0", "seed");
        createRabbit(owner, secondHouseId, secondCages.get(0), "2", "1", "commodity");

        int year = LocalDate.now().getYear();
        JsonNode allHouses = api.getOk("/api/reports/dashboard?year=" + year, owner.token, null);
        Assertions.assertEquals(2, allHouses.get("houseCount").asInt());
        Assertions.assertEquals(2, allHouses.get("totalRabbits").asInt());
        Assertions.assertEquals(1, allHouses.get("seedRabbits").asInt());
        Assertions.assertEquals(1, allHouses.get("commodityRabbits").asInt());
        Assertions.assertEquals(12, allHouses.get("monthlyBirths").size());
        Assertions.assertEquals(12, allHouses.get("monthlyWeaned").size());

        JsonNode oneHouse = api.getOk(
                "/api/reports/dashboard?houseId=" + firstHouseId + "&year=" + year,
                owner.token,
                null
        );
        Assertions.assertEquals(firstHouseId, oneHouse.get("selectedHouseId").asLong());
        Assertions.assertEquals(1, oneHouse.get("houseCount").asInt());
        Assertions.assertEquals(1, oneHouse.get("totalRabbits").asInt());

        api.expectError(
                "/api/reports/dashboard?houseId=" + firstHouseId + "&year=" + year,
                HttpMethod.GET,
                outsider.token,
                null,
                null,
                403,
                "无兔场权限"
        );
    }

    @Test
    void dashboardCountsDistinctMothersWithOpenOverlappingCycles() {
        UserSession owner = register("dashboard_cycles");
        long houseId = createHouse(owner, "dashboard_cycles_house", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 30,
                "postpartumDays", 0,
                "saleDays", 30,
                "replacementDays", 45,
                "requestId", requestId("dashboard_cycle_settings")
        ));
        long activeMother = createRabbit(owner, houseId, cages.get(0), "0", "0", "active_cycle_mother");
        long idleMother = createRabbit(owner, houseId, cages.get(1), "0", "0", "idle_cycle_mother");
        long father = createRabbit(owner, houseId, cages.get(2), "0", "1", "cycle_father");
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "DASH-" + requestId("dashboard_cycle_batch").substring(0, 8),
                "femaleRabbitIds", List.of(activeMother, idleMother),
                "requestId", requestId("dashboard_cycle_create")
        ));
        long batchId = batch.get("id").asLong();

        // 第一轮：一路走到哺乳（AWAIT_WEANING，仍 OPEN）
        long firstCycle = openCycleAtMating(owner, houseId, batchId, activeMother, "dash_c1");
        act(owner, houseId, firstCycle, "dash_c1_mate", obj(
                "action", "MATING", "occurredAt", oneMinuteAgo(),
                "maleRabbitId", father, "matingMethod", "NATURAL"));
        act(owner, houseId, firstCycle, "dash_c1_preg", obj(
                "action", "PALPATION", "occurredAt", oneMinuteAgo(), "palpationResult", "PREGNANT"));
        act(owner, houseId, firstCycle, "dash_c1_prep", obj(
                "action", "PREPARTUM", "occurredAt", oneMinuteAgo()));
        act(owner, houseId, firstCycle, "dash_c1_birth", obj(
                "action", "DELIVERY", "outcome", "BORN", "occurredAt", oneMinuteAgo(),
                "totalKits", 6, "liveKits", 6, "keptKits", 6));

        // 第二轮：血配 —— 哺乳未结束就另开一个管线周期，于是同时有两个 OPEN。
        long secondCycle = openCycleAtMating(owner, houseId, batchId, activeMother, "dash_c2");
        act(owner, houseId, secondCycle, "dash_c2_mate", obj(
                "action", "MATING", "occurredAt", oneMinuteAgo(),
                "maleRabbitId", father, "matingMethod", "NATURAL"));
        act(owner, houseId, secondCycle, "dash_c2_preg", obj(
                "action", "PALPATION", "occurredAt", oneMinuteAgo(), "palpationResult", "PREGNANT"));

        JsonNode activeCycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?motherRabbitId=" + activeMother + "&activeOnly=true",
                owner.token,
                houseId
        );
        Assertions.assertEquals(2, activeCycles.size());

        JsonNode summary = api.getOk(
                "/api/reports/dashboard?houseId=" + houseId + "&year=" + LocalDate.now().getYear(),
                owner.token,
                null
        );
        Assertions.assertEquals(3, summary.get("seedRabbits").asInt());
        Assertions.assertEquals(1, summary.get("bredRabbits").asInt());
        Assertions.assertEquals(1, summary.get("readyForBreeding").asInt());
    }

    /**
     * 把一头母兔推到「待配种」。
     *
     * <p>建批次时她已被送进流水线（待催情），所以这里是取那条周期再催情一步，
     * 而不是另开一条——后者会撞上「一头母兔仅一条流水线周期」不变式。
     */
    private long openCycleAtMating(
            UserSession owner, long houseId, long batchId, long motherId, String prefix) {
        List<Long> waiting = jdbc.queryForList(
                "select id from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                        + " and lifecycle = 'OPEN' and stage = 'AWAIT_ESTRUS' order by id desc limit 1",
                Long.class, houseId, motherId);
        if (!waiting.isEmpty()) {
            long cycleId = waiting.get(0);
            act(owner, houseId, cycleId, prefix + "_estrus", obj(
                    "action", "ESTRUS", "occurredAt", oneMinuteAgo()));
            return cycleId;
        }
        // 没有待催情周期，说明这是血配：她正在哺乳，而哺乳周期不占流水线，
        // 所以这里真的要另开一条新的流水线周期——两个 OPEN 周期并存正是血配的形态。
        return api.postOk("/api/repro/cycles", owner.token, houseId, obj(
                "motherRabbitId", motherId,
                "batchId", batchId,
                "stage", "AWAIT_MATING",
                "occurredAt", oneMinuteAgo(),
                "requestId", requestId(prefix + "_open")
        )).get("cycleId").asLong();
    }

    private void act(
            UserSession owner, long houseId, long cycleId, String prefix, Map<String, Object> body) {
        body.put("requestId", requestId(prefix));
        api.postOk("/api/repro/cycles/" + cycleId + "/actions", owner.token, houseId, body);
    }
}
