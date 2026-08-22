package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 分笼落位在新 API 下的验收测试 —— {@code WeaningCageConsistencyIT} 的等价物。
 *
 * <p>旧测试守的是同一组不变式，只是入口从 {@code /batches/{id}/weaning} 换成了
 * {@code /repro/cycles/{id}/actions}。这些不变式必须随重构存活，否则拆掉的
 * 就不只是重复代码，还有这次重构最该保住的安全网：
 *
 * <ul>
 *   <li>笼位容量在并发下不被突破（靠带容量判据的原子递增，不是先查后写）</li>
 *   <li>落位失败必须整体回滚，不留「窝已断奶、仔兔不存在」的悬空态</li>
 *   <li>cages.rabbit_count 与实际在栏兔数始终一致</li>
 * </ul>
 */
public class ReproWeaningPlacementIT extends E2eTestSupport {

    private static final int CAGE_CAPACITY = 10;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void overCapacityTargetCageRollsBackTheWholeWeaning() {
        Scenario s = nursingScenario("wean_cap", 1, 4);
        long cycleId = s.cycleIds.get(0);
        long targetCage = s.spareCage;
        // 先把目标笼填到 8 只，再要求塞进 4 只 —— 超出容量 10。
        for (int i = 0; i < 8; i++) {
            createRabbit(s.owner, s.houseId, targetCage, "2", i % 2 == 0 ? "0" : "1", "occupied_" + i);
        }

        api.expectError(
            "/api/repro/cycles/" + cycleId + "/actions", HttpMethod.POST,
            s.owner.token, s.houseId, obj(
                "action", "WEANING",
                "occurredAt", now(),
                "weanedCount", 4,
                "targetCageId", targetCage,
                "avgWeight", 1.1,
                "requestId", requestId("over_cap")
            ), 400, "容量不足");

        // 关键：周期必须还开着。落位失败却把窝关了，就是悬空数据。
        Assertions.assertEquals("AWAIT_WEANING", jdbc.queryForObject(
            "select stage from breeding_cycles where id = ?", String.class, cycleId));
        Assertions.assertEquals("OPEN", jdbc.queryForObject(
            "select lifecycle from breeding_cycles where id = ?", String.class, cycleId));
        Assertions.assertEquals("NURSING", jdbc.queryForObject(
            "select status from litters where cycle_id = ?", String.class, cycleId));

        assertNoTrace(s, cycleId);
        assertCageCountMatchesReality(targetCage, 8);
    }

    @Test
    void concurrentWeaningIntoOneCageNeverExceedsCapacity() throws Exception {
        // 4 只母兔各分 4 只仔兔进同一个笼：16 > 容量 10，必须有请求失败。
        int does = 4;
        int perDoe = 4;
        Scenario s = nursingScenario("wean_race", does, perDoe);
        long targetCage = s.spareCage;

        List<Callable<Integer>> jobs = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < does; i++) {
            long cycleId = s.cycleIds.get(i);
            int index = i;
            jobs.add(() -> {
                start.await();
                return api.postResponse(
                    "/api/repro/cycles/" + cycleId + "/actions", s.owner.token, s.houseId, obj(
                        "action", "WEANING",
                        "occurredAt", now(),
                        "weanedCount", perDoe,
                        "targetCageId", targetCage,
                        "requestId", requestId("race_" + index)
                    )).get("code").asInt();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(does);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> job : jobs) {
            futures.add(pool.submit(job));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> future : futures) {
            if (future.get(60, TimeUnit.SECONDS) == 0) {
                succeeded++;
            }
        }
        pool.shutdown();

        Assertions.assertTrue(succeeded >= 1 && succeeded <= 2,
            "容量 10 每次 4 只，最多容得下 2 次，实际成功 " + succeeded);

        int kits = jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and cage_id = ? and is_active = 1",
            Integer.class, s.houseId, targetCage);
        Assertions.assertEquals(succeeded * perDoe, kits, "成功次数与实际落位仔兔数必须吻合");
        Assertions.assertTrue(kits <= CAGE_CAPACITY, "笼位超员：" + kits);
        assertCageCountMatchesReality(targetCage, kits);
    }

    @Test
    void automaticAllocationKeepsEveryCageWithinCapacity() throws Exception {
        int does = 5;
        int perDoe = 6;
        Scenario s = nursingScenario("wean_auto", does, perDoe);

        List<Callable<Integer>> jobs = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < does; i++) {
            long cycleId = s.cycleIds.get(i);
            int index = i;
            jobs.add(() -> {
                start.await();
                // 不指定笼位，走自动选笼。
                return api.postResponse(
                    "/api/repro/cycles/" + cycleId + "/actions", s.owner.token, s.houseId, obj(
                        "action", "WEANING",
                        "occurredAt", now(),
                        "weanedCount", perDoe,
                        "requestId", requestId("auto_" + index)
                    )).get("code").asInt();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(does);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> job : jobs) {
            futures.add(pool.submit(job));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> future : futures) {
            if (future.get(60, TimeUnit.SECONDS) == 0) {
                succeeded++;
            }
        }
        pool.shutdown();
        Assertions.assertTrue(succeeded > 0, "自动选笼应当至少成功一次");

        Integer overfilled = jdbc.queryForObject(
            "select count(*) from cages where house_id = ? and rabbit_count > ?",
            Integer.class, s.houseId, CAGE_CAPACITY);
        Assertions.assertEquals(0, overfilled.intValue(), "自动选笼把某个笼位塞超了");

        // 每个笼的计数都要等于该笼实际在栏兔数。
        Integer mismatched = jdbc.queryForObject(
            "select count(*) from cages c where c.house_id = ? and c.rabbit_count <> "
                + "(select count(*) from rabbits r where r.cage_id = c.id and r.is_active = 1)",
            Integer.class, s.houseId);
        Assertions.assertEquals(0, mismatched.intValue(), "笼位计数与实际在栏数漂移");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * 分笼生成的仔兔必须带齐父、母、出生周期三项血缘。
     *
     * <p>这条盯的是一个完全静默的断链：配种时选了公兔、服务端也校验了公兔资格，
     * 但若周期更新语句漏写 male_rabbit_id，该值就只存在于内存；等到分笼时从周期
     * 读回来已是 NULL，于是每只仔兔的 father_id 都为空。全程不报任何错，
     * 只是谱系永久丢失——而谱系正是种兽管理的根本。真机验收时它就是这样被发现的。
     */
    @Test
    void weanedKitsCarryTheirSireAndDamLineage() {
        Scenario s = nursingScenario("lineage", 1, 4);
        long doe = s.doeIds().get(0);
        long buck = createRabbit(
            s.owner(), s.houseId(), s.spareCage(), "0", "1", "lineage_buck");

        // 建批次时已给她开了一条待催情周期（哺乳周期不占流水线，所以两者并存），
        // 这里直接拿它推进，而不是再开一条——后者会撞上流水线唯一性不变式。
        Long cycleId = jdbc.queryForObject(
            "select id from breeding_cycles where house_id = ? and mother_rabbit_id = ?"
                + " and lifecycle = 'OPEN' and stage = 'AWAIT_ESTRUS' order by id desc limit 1",
            Long.class, s.houseId(), doe);
        act(s, cycleId, "lineage_estrus", obj(
            "action", "ESTRUS", "occurredAt", oneMinuteAgo()));

        act(s, cycleId, "lineage_mate", obj("action", "MATING", "occurredAt", oneMinuteAgo(),
            "maleRabbitId", buck, "matingMethod", "NATURAL"));

        Assertions.assertEquals(buck, jdbc.queryForObject(
            "select male_rabbit_id from breeding_cycles where id = ?", Long.class, cycleId),
            "配种选定的种公兔必须落库，否则分笼时无从得知父代");

        act(s, cycleId, "lineage_preg", obj("action", "PALPATION", "occurredAt", oneMinuteAgo(),
            "palpationResult", "PREGNANT"));
        act(s, cycleId, "lineage_prep", obj("action", "PREPARTUM", "occurredAt", oneMinuteAgo()));
        act(s, cycleId, "lineage_birth", obj("action", "DELIVERY", "outcome", "BORN",
            "occurredAt", oneMinuteAgo(), "totalKits", 5, "liveKits", 4, "keptKits", 4));
        JsonNode weaned = act(s, cycleId, "lineage_wean", obj(
            "action", "WEANING", "occurredAt", oneMinuteAgo(), "weanedCount", 4
        ));

        Assertions.assertEquals(4, count(
            "select count(*) from rabbits where birth_cycle_id = ? and mother_id = ? and father_id = ?",
            cycleId, doe, buck),
            "每只仔兔都要能追溯到父、母与出生周期");
        Assertions.assertEquals(4, count(
            "select count(*) from work_tasks wt inner join rabbits r on r.id = wt.rabbit_id"
                + " where r.birth_cycle_id = ? and wt.task_type = 'SALE_READY' and wt.status = 'PENDING'",
            cycleId),
            "每只分笼生成的商品兔都要同步建立出售任务");
        long followUpCycleId = weaned.get("followUpCycleId").asLong();
        Assertions.assertEquals(1, count(
            "select count(*) from breeding_cycles where id = ? and batch_id is null"
                + " and lifecycle = 'OPEN' and stage = 'AWAIT_ESTRUS'",
            followUpCycleId),
            "下一轮待催情可以保留，但默认不继承已结束的批次；并行周期不应被误关");

        // 模拟三段生长期已经过去；仔兔来源仍是上面的真实分笼写路径。
        List<Long> kits = jdbc.queryForList(
            "select id from rabbits where birth_cycle_id = ? order by id",
            Long.class,
            cycleId
        );
        jdbc.update(
            "update rabbits set growth_stage = 'MATURE', state_version = state_version + 1"
                + " where birth_cycle_id = ?",
            cycleId
        );
        jdbc.update(
            "update batch_rabbits set next_event_date = date_sub(now(), interval 1 day)"
                + " where batch_id = ? and rabbit_id in (select id from rabbits where birth_cycle_id = ?)",
            s.batchId(), cycleId
        );

        JsonNode outbound = api.postOk("/api/outbound/tasks", s.owner().token, s.houseId(), obj(
            "entryType", "HOUSE", "resumeExisting", true
        ));
        List<java.util.Map<String, Object>> selected = new ArrayList<>();
        java.util.Map<String, Object> versions = obj();
        for (Long kitId : kits) {
            long version = outboundVersion(outbound, kitId);
            selected.add(obj(
                "rabbitId", kitId, "stateVersion", version, "selectionType", "NORMAL"
            ));
            versions.put(String.valueOf(kitId), version);
        }
        JsonNode frozen = api.putOk(
            "/api/outbound/tasks/" + outbound.get("taskId").asText(),
            s.owner().token,
            s.houseId(),
            obj(
                "revision", outbound.get("revision").asLong(),
                "status", "WAITING_CONFIRMATION",
                "items", selected,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 8.0,
                "unitPrice", 18.0,
                "customer", "分笼来源验收"
            )
        );
        JsonNode sold = api.postOk(
            "/api/outbound/tasks/" + frozen.get("taskId").asText() + "/submit",
            s.owner().token,
            s.houseId(),
            obj(
                "rabbitIds", kits,
                "stateVersions", versions,
                "saleTime", LocalDate.now().toString(),
                "totalWeight", 8.0,
                "unitPrice", 18.0,
                "customer", "分笼来源验收",
                "requestId", UUID.randomUUID().toString()
            )
        );
        Assertions.assertEquals("COMPLETED", sold.get("status").asText());
        Assertions.assertEquals(4, sold.get("rabbitCount").asInt());
        Assertions.assertEquals(4, count(
            "select count(*) from sale_order_items soi inner join rabbits r on r.id = soi.rabbit_id"
                + " where soi.sale_order_id = ? and r.birth_cycle_id = ? and r.mother_id = ?",
            sold.get("saleOrderId").asLong(), cycleId, doe
        ));
        Assertions.assertEquals(4, count(
            "select count(*) from work_tasks wt inner join rabbits r on r.id = wt.rabbit_id"
                + " where r.birth_cycle_id = ? and wt.task_type = 'SALE_READY' and wt.status = 'DONE'",
            cycleId
        ));
    }

    private JsonNode act(Scenario s, long cycleId, String prefix, java.util.Map<String, Object> body) {
        body.put("requestId", requestId(prefix));
        return api.postOk("/api/repro/cycles/" + cycleId + "/actions",
            s.owner().token, s.houseId(), body);
    }

    private long outboundVersion(JsonNode task, long rabbitId) {
        for (JsonNode rabbit : task.get("rabbits")) {
            if (rabbit.get("rabbitId").asLong() == rabbitId) {
                return rabbit.get("stateVersion").asLong();
            }
        }
        throw new AssertionError("rabbit missing from outbound precheck: " + rabbitId);
    }

    private void assertNoTrace(Scenario s, long cycleId) {
        Assertions.assertEquals(0, count(
            "select count(*) from weaning_records where breeding_cycle_id = ?", cycleId));
        Assertions.assertEquals(0, count(
            "select count(*) from rabbits where birth_cycle_id = ?", cycleId));
        Assertions.assertEquals(0, count(
            "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'fattening'",
            s.batchId));
        Assertions.assertEquals(0, count(
            "select coalesce(max(total_weaned), 0) from breeding_performance "
                + "where house_id = ? and rabbit_id = ?", s.houseId, s.doeIds.get(0)));
    }

    private void assertCageCountMatchesReality(long cageId, int expected) {
        Assertions.assertEquals(expected, count(
            "select rabbit_count from cages where id = ?", cageId), "cages.rabbit_count");
        Assertions.assertEquals(expected, count(
            "select count(*) from rabbits where cage_id = ? and is_active = 1", cageId),
            "实际在栏兔数");
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 造 N 只处于「待分笼」的母兔，外加一个空的商品兔笼备用。 */
    private Scenario nursingScenario(String prefix, int doeCount, int kitsPerDoe) {
        UserSession owner = register(prefix);
        // 笼位要足够：母兔各占一个，仔兔另需商品笼。
        long houseId = createHouse(owner, prefix + "_house", 1, doeCount * 4 + 4, 1);
        List<Long> cages = cageIds(owner, houseId);

        List<Long> does = new ArrayList<>();
        for (int i = 0; i < doeCount; i++) {
            does.add(createRabbit(owner, houseId, cages.get(i), "0", "0", prefix + "_doe" + i));
        }
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "WP-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", does,
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();

        List<Long> cycles = new ArrayList<>();
        for (int i = 0; i < doeCount; i++) {
            cycles.add(api.postOk("/api/repro/cycles", owner.token, houseId, obj(
                "motherRabbitId", does.get(i),
                "batchId", batchId,
                "stage", "AWAIT_WEANING",
                "occurredAt", now(),
                "birthDate", now() - 25L * 24 * 3600 * 1000,
                "totalKits", kitsPerDoe,
                "liveKits", kitsPerDoe,
                "requestId", requestId(prefix + "_cycle" + i)
            )).get("cycleId").asLong());
        }
        // 最后一个笼子留空，作为指定落位目标。
        return new Scenario(owner, houseId, batchId, does, cycles, cages.get(cages.size() - 1));
    }

    private record Scenario(
        UserSession owner, long houseId, long batchId,
        List<Long> doeIds, List<Long> cycleIds, long spareCage
    ) {
    }
}
