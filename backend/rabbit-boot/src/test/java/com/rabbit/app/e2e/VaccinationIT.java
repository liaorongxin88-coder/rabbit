package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * 接种疫苗端到端用例（飞书 recvt7fpa64K76）。
 *
 * <p>覆盖四件在设计上做过取舍的事：批量一次落 N 行、整批共用一个 requestId 的幂等、
 * 待接种列表随补种自动收口、以及「任一只不合格就整批拒绝」。
 */
class VaccinationIT extends E2eTestSupport {

    @Test
    void vaccinatesAWholeCageInOneCallAndReplaysIdempotently() {
        UserSession owner = register("vacc_owner");
        long houseId = createHouse(owner, "接种兔舍", 2, 2, 1);
        List<Long> cages = cageIds(owner, houseId);

        List<Long> rabbitIds = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            rabbitIds.add(createRabbit(owner, houseId, cages.get(0), "2", "0", "vacc_" + i));
        }

        String reqId = requestId("vacc_batch");
        long vaccinatedAt = oneMinuteAgo();
        JsonNode created = api.postOk("/api/vaccinations", owner.token, houseId, obj(
                "rabbitIds", rabbitIds,
                "vaccineName", "兔瘟疫苗",
                "vaccineBatchNo", "B20260301",
                "dose", "1ml",
                "route", "皮下注射",
                "vaccinatedAt", vaccinatedAt,
                "remark", "整笼接种",
                "requestId", reqId
        ));

        Assertions.assertEquals(3, created.get("created").asInt());
        Assertions.assertEquals(3, created.get("records").size());
        JsonNode first = created.get("records").get(0);
        Assertions.assertEquals("兔瘟疫苗", first.get("vaccineName").asText());
        Assertions.assertEquals("B20260301", first.get("vaccineBatchNo").asText());
        // 没填下次接种日期 -> 不欠针 -> DONE
        Assertions.assertEquals("DONE", first.get("status").asText());

        // 整批重放：不新增，回查原记录
        JsonNode replay = api.postOk("/api/vaccinations", owner.token, houseId, obj(
                "rabbitIds", rabbitIds,
                "vaccineName", "兔瘟疫苗",
                "vaccineBatchNo", "B20260301",
                "dose", "1ml",
                "route", "皮下注射",
                "vaccinatedAt", vaccinatedAt,
                "remark", "整笼接种",
                "requestId", reqId
        ));
        Assertions.assertEquals(0, replay.get("created").asInt());
        Assertions.assertEquals(3, replay.get("records").size());

        // 每只兔各自只有一条历史
        for (Long rabbitId : rabbitIds) {
            JsonNode history = api.getOk(
                    "/api/vaccinations?rabbitId=" + rabbitId, owner.token, houseId
            );
            Assertions.assertEquals(1, history.size(), "兔 " + rabbitId + " 的接种历史");
        }

        // 接种不改变兔只在场状态
        JsonNode rabbit = api.getOk("/api/rabbits/" + rabbitIds.get(0), owner.token, houseId);
        Assertions.assertTrue(rabbit.get("isActive").asBoolean(), "接种后兔只仍应在栏");
    }

    @Test
    void schedulesNextDoseAndClosesItAfterTheBoosterIsRecorded() {
        UserSession owner = register("vacc_due");
        long houseId = createHouse(owner, "补种兔舍", 2, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long rabbitId = createRabbit(owner, houseId, cages.get(0), "2", "0", "vacc_due");

        // 首针：下次接种日期已过 -> 应进「待接种」
        long firstShot = now() - 120_000L;
        long dueAt = now() - 60_000L;
        JsonNode first = api.postOk("/api/vaccinations", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(rabbitId),
                "vaccineName", "巴氏杆菌苗",
                "vaccinatedAt", firstShot,
                "nextDueDate", dueAt,
                "requestId", requestId("vacc_first")
        ));
        Assertions.assertEquals("SCHEDULED", first.get("records").get(0).get("status").asText());

        JsonNode due = api.getOk("/api/vaccinations/due", owner.token, houseId);
        Assertions.assertEquals(1, due.size());
        Assertions.assertEquals(rabbitId, due.get(0).get("rabbitId").asLong());
        Assertions.assertEquals("巴氏杆菌苗", due.get(0).get("vaccineName").asText());

        // 补种同一疫苗 -> 旧记录收口，待接种清空
        api.postOk("/api/vaccinations", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(rabbitId),
                "vaccineName", "巴氏杆菌苗",
                "vaccinatedAt", now(),
                "requestId", requestId("vacc_booster")
        ));
        Assertions.assertEquals(
                0,
                api.getOk("/api/vaccinations/due", owner.token, houseId).size(),
                "补种后不应再出现在待接种列表"
        );
        Assertions.assertEquals(
                2,
                api.getOk("/api/vaccinations?rabbitId=" + rabbitId, owner.token, houseId).size(),
                "两针都应留在历史里"
        );
    }

    @Test
    void rejectsTheWholeBatchWhenAnyTargetIsInvalid() {
        UserSession owner = register("vacc_reject");
        long houseId = createHouse(owner, "拒绝兔舍", 2, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long goodRabbit = createRabbit(owner, houseId, cages.get(0), "2", "0", "vacc_good");
        long soldRabbit = createRabbit(owner, houseId, cages.get(1), "2", "0", "vacc_sold");

        // 混入一个不存在的 id -> 整批拒绝
        api.expectError("/api/vaccinations", HttpMethod.POST, owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(goodRabbit, 99999999L),
                "vaccineName", "兔瘟疫苗",
                "vaccinatedAt", now(),
                "requestId", requestId("vacc_missing")
        ), 400, "兔子不存在");
        Assertions.assertEquals(
                0,
                api.getOk("/api/vaccinations?rabbitId=" + goodRabbit, owner.token, houseId).size(),
                "整批拒绝后合格的那只也不应留下记录"
        );

        // 出售离场后再接种 -> 整批拒绝
        api.postOk("/api/sales", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(soldRabbit),
                "saleTime", now(),
                "totalWeight", 2.5,
                "unitPrice", 20,
                "customer", "e2e customer",
                "requestId", requestId("vacc_sale")
        ));
        api.expectError("/api/vaccinations", HttpMethod.POST, owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(goodRabbit, soldRabbit),
                "vaccineName", "兔瘟疫苗",
                "vaccinatedAt", now(),
                "requestId", requestId("vacc_inactive")
        ), 400, "兔子不在场");

        // 下次接种日期早于本次接种时间 -> 拒绝
        api.expectError("/api/vaccinations", HttpMethod.POST, owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(goodRabbit),
                "vaccineName", "兔瘟疫苗",
                "vaccinatedAt", now(),
                "nextDueDate", oneMinuteAgo(),
                "requestId", requestId("vacc_bad_due")
        ), 400, "下次接种日期");
    }

    @Test
    void keepsVaccinationRecordsInsideTheirOwnHouse() {
        UserSession owner = register("vacc_tenant");
        long houseA = createHouse(owner, "接种A舍", 2, 2, 1);
        long houseB = createHouse(owner, "接种B舍", 2, 2, 1);
        long rabbitInA = createRabbit(owner, houseA, cageIds(owner, houseA).get(0), "2", "0", "vacc_a");

        // 带着 B 舍的 X-House-Id 去给 A 舍的兔接种 -> 拒绝
        api.expectError("/api/vaccinations", HttpMethod.POST, owner.token, houseB, obj(
                "rabbitIds", Arrays.asList(rabbitInA),
                "vaccineName", "兔瘟疫苗",
                "vaccinatedAt", now(),
                "requestId", requestId("vacc_cross")
        ), 400, "兔子不存在");

        UserSession viewer = register("vacc_viewer");
        long viewerHouse = createHouse(viewer, "旁人兔舍", 2, 2, 1);
        Assertions.assertEquals(
                0,
                api.getOk("/api/vaccinations/due", viewer.token, viewerHouse).size(),
                "别人的兔舍看不到本舍的待接种"
        );
    }
}
