package com.rabbit.app.e2e;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 接产在新 API 下的验收：绩效累加、失败产预警，以及一处<b>有意的业务变更</b>。
 *
 * <p>旧的 {@code BatchService.parturition} 在分娩失败且母兔没有其它哺乳窝时会直接
 * 让她离场。新实现只关周期并挂预警，母兔留场等恢复期后重新催情。
 * {@link #failedDeliveryWarnsButDoesNotCullTheDoe()} 就是把这个差异钉死的地方——
 * 若业务确认要恢复自动淘汰，这条测试会红，改动不会悄无声息地溜过去。
 */
public class ReproDeliveryIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void successfulDeliveryAccumulatesBreedingPerformance() {
        Fixture f = pregnantDoe("deliv_ok");

        api.postOk("/api/repro/cycles/" + f.cycleId + "/actions", f.owner.token, f.houseId, obj(
            "action", "DELIVERY",
            "outcome", "BORN",
            "occurredAt", now(),
            "totalKits", 9,
            "liveKits", 7,
            "keptKits", 7,
            "requestId", requestId("born")
        ));

        Assertions.assertEquals("AWAIT_WEANING", str(
            "select stage from breeding_cycles where id = ?", f.cycleId));
        Assertions.assertEquals(1, num(
            "select total_litters from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId), "窝数");
        Assertions.assertEquals(9, num(
            "select total_kits from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId), "总产仔数");
        Assertions.assertEquals(7, num(
            "select total_live_kits from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId), "活仔数");
        // 失败产才挂预警。
        Assertions.assertEquals(0, num(
            "select count(*) from rabbit_abnormal_conditions where rabbit_id = ?", f.doeId));
    }

    @Test
    void deliveryFormRejectsMissingKeptKitsAndNonzeroFailedCounts() {
        Fixture missingKept = pregnantDoe("deliv_missing_kept");
        api.expectError(
            "/api/repro/cycles/" + missingKept.cycleId + "/actions",
            HttpMethod.POST,
            missingKept.owner.token,
            missingKept.houseId,
            obj(
                "action", "DELIVERY",
                "outcome", "BORN",
                "occurredAt", now(),
                "totalKits", 8,
                "liveKits", 7,
                "requestId", requestId("missing_kept")
            ),
            400,
            "留仔数"
        );
        Assertions.assertEquals("OPEN", str(
            "select lifecycle from breeding_cycles where id = ?", missingKept.cycleId));

        Fixture failedWithCounts = pregnantDoe("deliv_failed_counts");
        api.expectError(
            "/api/repro/cycles/" + failedWithCounts.cycleId + "/actions",
            HttpMethod.POST,
            failedWithCounts.owner.token,
            failedWithCounts.houseId,
            obj(
                "action", "DELIVERY",
                "outcome", "FAILED",
                "occurredAt", now(),
                "totalKits", 1,
                "liveKits", 0,
                "keptKits", 0,
                "requestId", requestId("failed_counts")
            ),
            400,
            "必须为 0"
        );
        Assertions.assertEquals("OPEN", str(
            "select lifecycle from breeding_cycles where id = ?", failedWithCounts.cycleId));
    }

    @Test
    void failedDeliveryWarnsButDoesNotCullTheDoe() {
        Fixture f = pregnantDoe("deliv_fail");
        Long batchId = jdbc.queryForObject(
            "select batch_id from breeding_cycles where id = ?", Long.class, f.cycleId
        );
        String imageId = uploadTestImage(f.owner, f.houseId, "dystocia");

        api.postOk("/api/repro/cycles/" + f.cycleId + "/actions", f.owner.token, f.houseId, obj(
            "action", "DELIVERY",
            "outcome", "FAILED",
            "occurredAt", now(),
            "totalKits", 0,
            "liveKits", 0,
            "keptKits", 0,
            "remark", "全窝死胎",
            "attachmentFileIds", List.of(imageId),
            "requestId", requestId("failed")
        ));

        // 周期关掉，结局记 FAILED，但阶段停在出事的地方以便统计。
        Assertions.assertEquals("CLOSED", str(
            "select lifecycle from breeding_cycles where id = ?", f.cycleId));
        Assertions.assertEquals("FAILED", str(
            "select result from breeding_cycles where id = ?", f.cycleId));
        Assertions.assertEquals("AWAIT_DELIVERY", str(
            "select stage from breeding_cycles where id = ?", f.cycleId));

        // 挂一条「流产」预警，未处理。
        Assertions.assertEquals(1, num(
            "select count(*) from rabbit_abnormal_conditions where rabbit_id = ? "
                + "and warning_status = '流产' and is_deal = 0", f.doeId));

        // 失败产也要进绩效，否则分娩成功率会虚高。
        Assertions.assertEquals(1, num(
            "select total_litters from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId));
        Assertions.assertEquals(0, num(
            "select total_kits from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId));

        // === 与旧实现的差异：母兔留场 ===
        Assertions.assertEquals(1, num(
            "select is_active from rabbits where id = ?", f.doeId), "母兔应仍在栏");
        Assertions.assertEquals(0, num(
            "select count(*) from rabbit_departure_records where rabbit_id = ?", f.doeId),
            "不应产生离场记录");
        // 失败产释放旧批次并进入真实休养期，到期后再自动催情。
        Assertions.assertEquals("READY", str(
            "select stage from breeding_cycles where mother_rabbit_id = ? and lifecycle = 'OPEN'",
            f.doeId), "应自动开出无批次休养周期");
        Assertions.assertEquals(1, num(
            "select count(*) from work_tasks where rabbit_id = ? and task_type = 'RECOVERY'"
                + " and status = 'PENDING'",
            f.doeId));
        Assertions.assertEquals(0, num(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and is_active = true",
            batchId, f.doeId));
    }

    @Test
    void replayedDeliveryDoesNotDoubleCountPerformance() {
        Fixture f = pregnantDoe("deliv_replay");
        String rid = requestId("dup");

        for (int i = 0; i < 2; i++) {
            api.postOk("/api/repro/cycles/" + f.cycleId + "/actions", f.owner.token, f.houseId, obj(
                "action", "DELIVERY",
                "outcome", "BORN",
                "occurredAt", now(),
                "totalKits", 8,
                "liveKits", 6,
                "keptKits", 6,
                "requestId", rid
            ));
        }

        Assertions.assertEquals(1, num(
            "select total_litters from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId), "重放不得把窝数加两遍");
        Assertions.assertEquals(8, num(
            "select total_kits from breeding_performance where house_id = ? and rabbit_id = ?",
            f.houseId, f.doeId), "重放不得把产仔数加两遍");
    }

    // ---------------------------------------------------------------- helpers

    private String str(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private int num(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    /** 造一只走到「待分娩」的母兔。 */
    private Fixture pregnantDoe(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, 6, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", prefix + "-batch",
            "femaleRabbitIds", List.of(),
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();

        long cycleId = api.postOk("/api/repro/cycles", owner.token, houseId, obj(
            "motherRabbitId", doeId,
            "batchId", batchId,
            "stage", "AWAIT_DELIVERY",
            "occurredAt", now(),
            "matingDate", now() - 30L * 24 * 3600 * 1000,
            "expectedBirthDate", now(),
            "requestId", requestId(prefix + "_cycle")
        )).get("cycleId").asLong();

        return new Fixture(owner, houseId, doeId, cycleId);
    }

    private record Fixture(UserSession owner, long houseId, long doeId, long cycleId) {
    }
}
