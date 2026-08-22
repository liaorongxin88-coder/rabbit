package com.rabbit.app.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

/**
 * 应急闸门的守卫测试。
 *
 * <p>P4 删除旧繁殖写端点后，{@code app.repro.v2.enabled} 已改为默认 true：
 * 旧路径不存在了，再默认关闭就等于产品没有繁殖写入路径。
 * 本类的职责因此从「守住默认关闭」变成「关停开关确实能把新接口整体陛落」——
 * 线上出事时这是唯一的止血手段，它失效而无人发现是不可接受的。
 *
 * <p>单独成类仍然必要：Feature Flag 是启动期属性，同一个 Spring 上下文里
 * 无法既开又关。
 */
@TestPropertySource(properties = "app.repro.v2.enabled=false")
public class ReproFeatureFlagIT extends E2eTestSupport {

    @Test
    void killSwitchTakesEveryV2EndpointOffline() {
        UserSession owner = register("repro_flag_off");
        long houseId = createHouse(owner, "repro_flag_off_house", 1, 2, 1);

        api.expectError("/api/repro/cycles", HttpMethod.POST, owner.token, houseId, obj(
            "motherRabbitId", 1L,
            "requestId", requestId("flag_off")
        ), 404, "未开启");

        api.expectError("/api/tasks", HttpMethod.GET, owner.token, houseId, null, 404, "未开启");

        api.expectError("/api/repro/tasks/bulk-actions", HttpMethod.POST, owner.token, houseId, obj(
            "requestId", requestId("flag_off_bulk"),
            "action", "ESTRUS",
            "occurredAt", now(),
            "taskIds", java.util.List.of(1L)
        ), 404, "未开启");

        api.expectError(
            "/api/repro/cycles/1/kept-kits-adjustments",
            HttpMethod.POST,
            owner.token,
            houseId,
            obj(
                "occurredAt", now(),
                "keptKits", 1,
                "requestId", requestId("flag_off_kept")
            ),
            404,
            "未开启"
        );
    }
}
