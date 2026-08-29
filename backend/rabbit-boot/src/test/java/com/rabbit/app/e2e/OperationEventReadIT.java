package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 统一操作事件读接口。
 *
 * <p>这张表是审计面：翻页要稳、租户要隔离、权限要卡在 MANAGER 档。
 * 三件事都只有走真实数据库和真实 HTTP 才算数，所以放在 IT 而不是单测。
 */
class OperationEventReadIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private UserSession owner;
    private long houseId;
    private long rabbitId;

    @BeforeEach
    void prepareHouse() {
        owner = register("opevt");
        houseId = createHouse(owner, "事件流兔舍", 1, 2, 1);
        rabbitId = createRabbit(owner, houseId, cageIds(owner, houseId).get(0), "0", "0", "opevt");
    }

    @Test
    void aRealOperationLeavesExactlyOneRow() {
        // 接种是目前真正会发射事件的写操作之一，一次接种一只兔只应当只留一条。
        api.postOk("/api/vaccinations", owner.token, houseId, obj(
            "rabbitIds", List.of(rabbitId),
            "vaccineName", "兔瘟疫苗",
            "vaccinatedAt", System.currentTimeMillis(),
            "requestId", requestId("vacc")
        ));

        JsonNode page = api.getOk(
            "/api/operation-events?targetType=RABBIT&targetId=" + rabbitId, owner.token, houseId
        );

        List<JsonNode> shots = new ArrayList<>();
        for (JsonNode item : page.get("items")) {
            if ("vaccination:create".equals(item.path("operationCode").asText())) {
                shots.add(item);
            }
        }
        Assertions.assertEquals(1, shots.size(), "一次业务操作不该在流水里出现两条");

        JsonNode event = shots.get(0);
        Assertions.assertEquals("RABBIT", event.path("targetType").asText());
        Assertions.assertEquals(rabbitId, event.path("targetId").asLong());
        Assertions.assertEquals(owner.userName, event.path("operatorName").asText(),
            "operatorName 是当时的展示名快照");
        Assertions.assertFalse(event.has("payload"), "payload 属于内部结构，不该外泄");
        Assertions.assertFalse(event.has("requestId"), "requestId 是幂等键，不该外泄");
        Assertions.assertFalse(page.has("total"), "游标分页刻意不返回 total");
    }

    /**
     * 当前覆盖面的基准线。
     *
     * <p>D1 给三十多个写方法铺了 {@code @TrackedOperation}，但注解只绑定
     * OperationContext，并不自动写事件行；真正调 {@code recordEvent} 的只有
     * 投喂和接种两处。所以建兔只这类操作目前不会进流水。
     *
     * <p>这条用例钉住现状而不是认可现状：等后续把发射补到其他写入口，
     * 它会失败，提醒人回来改断言，而不是默默地一直缺数据。
     */
    @Test
    void mostWriteOperationsDoNotEmitEventsYet() {
        JsonNode page = api.getOk(
            "/api/operation-events?targetType=RABBIT&targetId=" + rabbitId, owner.token, houseId
        );

        for (JsonNode item : page.get("items")) {
            Assertions.assertNotEquals(
                "rabbit.create", item.path("operationCode").asText(),
                "建兔只已能发事件了，请把这条基准线用例改成正向断言"
            );
        }
    }

    @Test
    void anotherHouseCannotSeeTheseEvents() {
        UserSession outsider = register("opevt_out");
        long otherHouseId = createHouse(outsider, "别人的兔舍", 1, 1, 1);
        seedEvents(houseId, 3, 1_000L);
        seedEvents(otherHouseId, 2, 1_000L);

        // 对方只能看到自己兔舍的那两条，看不到这边的三条。
        JsonNode page = api.getOk(
            "/api/operation-events?operationCode=seed:test&limit=200", outsider.token, otherHouseId
        );
        Assertions.assertEquals(2, page.get("items").size(), "只该看到自己兔舍的事件");

        // 反向：拿别人的 houseId 请求会被兔场权限挡住，而不是读到空列表。
        api.expectError(
            "/api/operation-events", HttpMethod.GET, outsider.token, houseId, null, 403, "无兔场权限"
        );
    }

    @Test
    void viewersCannotReadTheAuditStream() {
        // rabbit:audit:list 是 MANAGER 档：能翻遍全场操作的面不该开给只读成员。
        UserSession viewer = register("opevt_viewer");
        api.postOk("/api/house-members", owner.token, houseId, obj(
            "userName", viewer.userName,
            "role", "VIEWER",
            "requestId", requestId("member_viewer")
        ));

        api.expectError(
            "/api/operation-events", HttpMethod.GET, viewer.token, houseId, null, 403, "权限不足"
        );
    }

    @Test
    void pagingStaysStableWhileNewEventsArrive() {
        // 事件流是追加写。翻页途中来了新事件，已经翻过的那一页不能因此漏行或重复 ——
        // 这正是这里用 keyset 而不是 offset 的原因。
        seedEvents(houseId, 10, 1_000L);

        JsonNode first = api.getOk(
            "/api/operation-events?operationCode=seed:test&limit=4", owner.token, houseId
        );
        Assertions.assertEquals(4, first.get("items").size());
        Assertions.assertTrue(first.get("hasMore").asBoolean());

        // 中途插入更新的事件：它们排在游标之前，不该挤进后续页。
        seedEvents(houseId, 5, 9_000L);

        List<Long> seen = new ArrayList<>();
        collectIds(first, seen);
        String cursor = first.get("nextCursor").asText();
        while (cursor != null && !cursor.isEmpty()) {
            JsonNode next = api.getOk(
                "/api/operation-events?operationCode=seed:test&limit=4&cursor=" + cursor,
                owner.token, houseId
            );
            collectIds(next, seen);
            cursor = next.get("nextCursor").isNull() ? null : next.get("nextCursor").asText();
        }

        Assertions.assertEquals(10, seen.size(), "翻完应当正好是翻页开始时的那 10 条");
        Assertions.assertEquals(seen.size(), seen.stream().distinct().count(), "不能有重复行");
        List<Long> sorted = new ArrayList<>(seen);
        sorted.sort((a, b) -> Long.compare(b, a));
        Assertions.assertEquals(sorted, seen, "必须严格倒序");
    }

    @Test
    void filtersAndLimitsAreEnforced() {
        seedEvents(houseId, 3, 1_000L);

        // 只给 targetId 不给 targetType 会跨类型误命中，必须拒绝。
        api.expectError(
            "/api/operation-events?targetId=5", HttpMethod.GET, owner.token, houseId, null,
            400, "targetType"
        );

        // 伪造游标是客户端错误，不该变成 500。
        api.expectError(
            "/api/operation-events?cursor=not-a-cursor", HttpMethod.GET, owner.token, houseId, null,
            400, "游标"
        );

        // limit 被夹住，客户端要不到整张表。
        JsonNode huge = api.getOk("/api/operation-events?limit=100000", owner.token, houseId);
        Assertions.assertTrue(huge.get("items").size() <= 200, "limit 必须夹到上限内");

        // 目标过滤生效：seed 出来的都挂在 BATCH 上，按 RABBIT 查不该命中它们。
        JsonNode byType = api.getOk(
            "/api/operation-events?targetType=BATCH&operationCode=seed:test", owner.token, houseId
        );
        Assertions.assertEquals(3, byType.get("items").size());
        for (JsonNode item : byType.get("items")) {
            Assertions.assertEquals("BATCH", item.path("targetType").asText());
        }
    }

    private void collectIds(JsonNode page, List<Long> into) {
        for (JsonNode item : page.get("items")) {
            into.add(item.path("id").asLong());
        }
    }

    /**
     * 直接落库造事件。
     *
     * <p>翻页要的是可控的时间序，走业务接口既慢又没法精确排布 occurred_at。
     */
    private void seedEvents(long targetHouseId, int count, long baseMillis) {
        for (int i = 0; i < count; i++) {
            jdbc.update(
                "insert into repro_events (house_id, mother_rabbit_id, operation_code, target_type,"
                    + " target_id, event_type, occurred_at, operator_id, operator_name, request_id,"
                    + " create_time) values (?, null, 'seed:test', 'BATCH', ?, 'SEEDED',"
                    + " from_unixtime(? / 1000), ?, ?, ?, now())",
                targetHouseId,
                (long) (i + 1),
                baseMillis + i * 1_000L,
                owner.userId,
                owner.userName,
                "seed-" + targetHouseId + "-" + baseMillis + "-" + i
            );
        }
    }
}
