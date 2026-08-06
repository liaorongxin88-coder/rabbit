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
                "无商户权限"
        );
    }
}
