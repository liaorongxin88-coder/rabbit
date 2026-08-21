package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 配种资格校验的验收：这些判断从 {@code BatchService.matingInternal} 迁到
 * {@link com.rabbit.app.modules.repro.service.BreedingEligibilityValidator}。
 *
 * <p>{@link #bulkMatingIsValidatedToo()} 是本类里最要紧的一条：校验放在状态机内部
 * 而不是编排层，正是因为批量待办直连状态机。批量恰恰是最容易选错兔子的入口，
 * 若这条红了，说明校验又被挪到了绕得过去的位置。
 */
public class ReproMatingEligibilityIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void doeCannotBeMatedWithAnotherDoe() {
        Fixture f = doeAwaitingMating("mate_gender");
        long anotherDoe = createRabbit(f.owner, f.houseId, f.cages.get(3), "0", "0", "not_a_buck");

        expectMatingError(f, anotherDoe, now(), "公兔性别不正确");
    }

    @Test
    void commodityRabbitCannotSire() {
        Fixture f = doeAwaitingMating("mate_type");
        long commodity = createRabbit(f.owner, f.houseId, f.cages.get(3), "2", "1", "commodity_buck");

        expectMatingError(f, commodity, now(), "仅种公兔可用于配种");
    }

    @Test
    void departedBuckCannotSire() {
        Fixture f = doeAwaitingMating("mate_gone");
        jdbc.update("update rabbits set is_active = 0 where id = ?", f.buckId);

        expectMatingError(f, f.buckId, now(), "公兔不在场");
    }

    @Test
    void bloodMatingCannotPredateTheNursingLitter() {
        Fixture f = doeAwaitingMating("mate_blood");
        long birth = now();
        // 同一只母兔另开一个哺乳周期：血配下两个 OPEN 周期是合法的。
        api.postOk("/api/repro/cycles", f.owner.token, f.houseId, obj(
            "motherRabbitId", f.doeId,
            "stage", "AWAIT_WEANING",
            "occurredAt", birth,
            "birthDate", birth,
            "totalKits", 8,
            "liveKits", 6,
            "requestId", requestId("nursing")
        ));

        // 复配日早于产仔日 —— 录入错误，会让产后复配天数变成负数。
        expectMatingError(f, f.buckId, birth - 5L * 24 * 3600 * 1000,
            "二次配种日期不能早于上一窝产仔日期");
    }

    @Test
    void bulkMatingIsValidatedToo() {
        Fixture f = doeAwaitingMating("mate_bulk");
        long anotherDoe = createRabbit(f.owner, f.houseId, f.cages.get(3), "0", "0", "bulk_not_buck");
        long taskId = jdbc.queryForObject(
            "select id from work_tasks where cycle_id = ? and status = 'PENDING'",
            Long.class, f.cycleId);

        JsonNode res = api.postOk("/api/repro/tasks/bulk-actions", f.owner.token, f.houseId, obj(
            "requestId", requestId("bulk_bad"),
            "action", "MATING",
            "occurredAt", now(),
            "maleRabbitId", anotherDoe,
            "matingMethod", "NATURAL",
            "taskIds", List.of(taskId)
        ));

        Assertions.assertEquals(1, res.get("total").asInt());
        Assertions.assertEquals(0, res.get("succeeded").asInt(), "批量不得绕过配种资格校验");
        Assertions.assertEquals(1, res.get("failed").asInt());
        JsonNode item = res.get("items").get(0);
        Assertions.assertEquals(400, item.get("code").asInt());
        Assertions.assertTrue(item.get("message").asText().contains("公兔性别不正确"),
            "实际报错：" + item.get("message").asText());

        // 周期没有被推进。
        Assertions.assertEquals("AWAIT_MATING", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, f.cycleId));
    }

    @Test
    void validMatingStillWorks() {
        Fixture f = doeAwaitingMating("mate_ok");

        api.postOk("/api/repro/cycles/" + f.cycleId + "/actions", f.owner.token, f.houseId, obj(
            "action", "MATING",
            "occurredAt", now(),
            "maleRabbitId", f.buckId,
            "matingMethod", "NATURAL",
            "requestId", requestId("good")
        ));

        Assertions.assertEquals("AWAIT_PALPATION", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, f.cycleId));
    }

    @Test
    void artificialInseminationAllowsNoSpecificBuck() {
        Fixture f = doeAwaitingMating("mate_ai");

        api.postOk("/api/repro/cycles/" + f.cycleId + "/actions", f.owner.token, f.houseId, obj(
            "action", "MATING",
            "occurredAt", now(),
            "matingMethod", "AI",
            "requestId", requestId("ai")
        ));

        Assertions.assertEquals("AWAIT_PALPATION", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, f.cycleId));
    }

    @Test
    void artificialInseminationCanOptionallyRecordTheSelectedBuck() {
        Fixture f = doeAwaitingMating("mate_ai_buck");

        api.postOk("/api/repro/cycles/" + f.cycleId + "/actions", f.owner.token, f.houseId, obj(
            "action", "MATING",
            "occurredAt", now(),
            "maleRabbitId", f.buckId,
            "matingMethod", "AI",
            "requestId", requestId("ai_buck")
        ));

        Assertions.assertEquals(f.buckId, jdbc.queryForObject(
            "select male_rabbit_id from breeding_cycles where id = ?",
            Long.class,
            f.cycleId
        ));
    }

    @Test
    void matingMethodIsRequiredEvenWhenABuckIsSelected() {
        Fixture f = doeAwaitingMating("mate_method_required");

        api.expectError(
            "/api/repro/cycles/" + f.cycleId + "/actions",
            HttpMethod.POST,
            f.owner.token,
            f.houseId,
            obj(
                "action", "MATING",
                "occurredAt", now(),
                "maleRabbitId", f.buckId,
                "requestId", requestId("missing_method")
            ),
            400,
            "请选择配种方式"
        );
    }

    // ---------------------------------------------------------------- helpers

    private void expectMatingError(Fixture f, long buckId, long occurredAt, String message) {
        api.expectError(
            "/api/repro/cycles/" + f.cycleId + "/actions", HttpMethod.POST,
            f.owner.token, f.houseId, obj(
                "action", "MATING",
                "occurredAt", occurredAt,
                "maleRabbitId", buckId,
                "matingMethod", "NATURAL",
                "requestId", requestId("bad")
            ), 400, message);

        Assertions.assertEquals("AWAIT_MATING", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, f.cycleId),
            "校验失败后周期不得被推进");
    }

    private Fixture doeAwaitingMating(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long buckId = createRabbit(owner, houseId, cages.get(1), "0", "1", prefix + "_buck");

        long cycleId = api.postOk("/api/repro/cycles", owner.token, houseId, obj(
            "motherRabbitId", doeId,
            "stage", "AWAIT_MATING",
            "occurredAt", now(),
            "requestId", requestId(prefix + "_cycle")
        )).get("cycleId").asLong();

        return new Fixture(owner, houseId, doeId, buckId, cycleId, cages);
    }

    private record Fixture(
        UserSession owner, long houseId, long doeId, long buckId, long cycleId, List<Long> cages
    ) {
    }
}
