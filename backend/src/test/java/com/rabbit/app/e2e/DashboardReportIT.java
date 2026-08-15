package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

public class DashboardReportIT extends E2eTestSupport {
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

        aphrodisiac(owner, houseId, batchId, activeMother, "dashboard_cycle_first");
        mate(owner, houseId, batchId, activeMother, father, "dashboard_cycle_first_mating");
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", activeMother,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "requestId", requestId("dash_c1_preg")
        ));
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, obj(
                "rabbitId", activeMother,
                "actionDate", oneMinuteAgo(),
                "requestId", requestId("dash_c1_prep")
        ));
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, obj(
                "rabbitId", activeMother,
                "birthDate", oneMinuteAgo(),
                "totalKits", 6,
                "liveKits", 6,
                "failed", false,
                "requestId", requestId("dash_c1_birth")
        ));

        aphrodisiac(owner, houseId, batchId, activeMother, "dashboard_cycle_second");
        mate(owner, houseId, batchId, activeMother, father, "dashboard_cycle_second_mating");
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", activeMother,
                "checkDate", oneMinuteAgo(),
                "result", "怀孕",
                "requestId", requestId("dash_c2_preg")
        ));

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

    private void aphrodisiac(UserSession owner, long houseId, long batchId, long motherId, String prefix) {
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", List.of(motherId),
                "requestId", requestId(prefix + "_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", List.of(motherId),
                "requestId", requestId(prefix + "_finish")
        ));
    }

    private void mate(UserSession owner, long houseId, long batchId, long motherId, long fatherId, String prefix) {
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", motherId,
                "maleRabbitId", fatherId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId(prefix)
        ));
    }
}
