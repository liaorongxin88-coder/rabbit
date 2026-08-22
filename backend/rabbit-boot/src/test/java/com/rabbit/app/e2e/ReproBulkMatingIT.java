package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code BulkMatingIT} 在新 API 下的等价物：整轮配种及其幂等重放。
 *
 * <p>旧套件暂不删除。旧端点目前仍是线上活路径（{@code app.repro.v2.enabled} 还关着），
 * 先撤测试会让活路径出现一段无覆盖的空窗；等端点删除时两者同批删。
 *
 * <h2>两处有意的语义差异</h2>
 *
 * <p><b>一、重放的形状变了。</b>旧接口按 {@code femaleRabbitIds} 指名道姓，重放会再次
 * 命中同一批母兔。新接口两种目标形式的幂等性质不同：
 * <ul>
 *   <li>{@code filter} 解析的是「此刻仍 PENDING 的待办」。整轮配完后再发一次，
 *       什么也找不到（{@code total=0}）——天然幂等，不依赖 requestId。</li>
 *   <li>{@code taskIds} 会重新加载同一批待办（已 DONE 也照加），靠 requestId 走回放。</li>
 * </ul>
 *
 * <p><b>二、不再做载荷指纹比对。</b>旧实现同 requestId 配不同载荷时报 409
 * 「requestId已用于不同的批量配种请求」；新路径直接返回首次结果（{@code replayed=true}），
 * 这是幂等键的常规语义，原始载荷在 repro_events 里留痕可查。
 * 代价是客户端复用 requestId 的 bug 不再被顶回来，只会静默拿到旧结果。
 */
public class ReproBulkMatingIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void matesWholeRoundByFilter() {
        Round r = roundAwaitingMating("bulk_mate", 4);

        var body = obj(
            "requestId", requestId("round"),
            "action", "MATING",
            "occurredAt", oneMinuteAgo(),
            "maleRabbitId", r.buckId,
            "matingMethod", "NATURAL",
            "filter", obj("batchId", r.batchId, "taskType", "MATING")
        );
        JsonNode first = api.postOk("/api/repro/tasks/bulk-actions", r.owner.token, r.houseId, body);
        JsonNode retry = api.postOk("/api/repro/tasks/bulk-actions", r.owner.token, r.houseId, body);

        Assertions.assertEquals(r.doeIds.size(), first.get("total").asInt());
        Assertions.assertEquals(r.doeIds.size(), first.get("succeeded").asInt());
        Assertions.assertEquals(0, first.get("failed").asInt());

        // 整轮配完后 MATING 待办已 DONE，filter 再解析就什么也找不到 ——
        // 这正是 filter 形式的幂等方式：无事可做，而不是回放。
        Assertions.assertEquals(0, retry.get("total").asInt(),
            "整轮配完后不应再有待配种的待办");

        Assertions.assertEquals(r.doeIds.size(), count(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ?",
            r.houseId, r.batchId), "不得因重放多出周期");
        Assertions.assertEquals(r.doeIds.size(), count(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ? "
                + "and stage = 'AWAIT_PALPATION'", r.houseId, r.batchId), "整轮应推进到待摸胎");
        // 每头母兔一条独立的配种事件，request_id 各不相同。
        Assertions.assertEquals(r.doeIds.size(), count(
            "select count(distinct request_id) from repro_events where house_id = ? "
                + "and event_type = 'MATING_DONE'", r.houseId));
    }

    @Test
    void replayByTaskIdsKeepsTheFirstResult() {
        Round r = roundAwaitingMating("bulk_replay", 2);
        // 用显式 taskIds：filter 形式第二次会解析出 0 项，断言会平凡通过，根本没走到回放。
        List<Long> taskIds = jdbc.queryForList(
            "select id from work_tasks where house_id = ? and batch_id = ? "
                + "and task_type = 'MATING' and status = 'PENDING' order by id",
            Long.class, r.houseId, r.batchId);
        Assertions.assertEquals(2, taskIds.size(), "前置：每头母兔一条待配种待办");

        String rid = requestId("same_id");
        long firstDate = oneMinuteAgo();
        JsonNode first = api.postOk("/api/repro/tasks/bulk-actions", r.owner.token, r.houseId, obj(
            "requestId", rid, "action", "MATING", "occurredAt", firstDate,
            "maleRabbitId", r.buckId, "matingMethod", "NATURAL",
            "taskIds", taskIds
        ));
        Assertions.assertEquals(2, first.get("succeeded").asInt());
        for (JsonNode item : first.get("items")) {
            Assertions.assertFalse(item.get("replayed").asBoolean(), "首次不应是回放");
        }

        // 同 requestId、同 taskIds，但配种日期不同：
        // 旧实现报 409，新实现按幂等键返回首次结果。
        JsonNode second = api.postOk("/api/repro/tasks/bulk-actions", r.owner.token, r.houseId, obj(
            "requestId", rid, "action", "MATING", "occurredAt", firstDate + 86_400_000L,
            "maleRabbitId", r.buckId, "matingMethod", "NATURAL",
            "taskIds", taskIds
        ));
        Assertions.assertEquals(2, second.get("total").asInt(), "重放应确实命中两项");
        Assertions.assertEquals(2, second.get("succeeded").asInt());
        Assertions.assertEquals(0, second.get("failed").asInt());
        for (JsonNode item : second.get("items")) {
            Assertions.assertTrue(item.get("replayed").asBoolean(), "重放项应标记 replayed");
        }

        // 决定性断言：第二次的日期没有覆盖首次落库的配种日期。
        Assertions.assertEquals(2, count(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ? "
                + "and date(mating_date) = date(from_unixtime(? / 1000))",
            r.houseId, r.batchId, firstDate), "重放不得改写已落库的配种日期");
        Assertions.assertEquals(2, count(
            "select count(*) from repro_events where house_id = ? and event_type = 'MATING_DONE'",
            r.houseId), "重放不得多出配种事件");
    }

    /**
     * 接替已删除的 {@code BulkMatingServiceTest} 里幸存的两条保护：批量上限与去重。
     *
     * <p>旧测试的第三条——「一头不合格则整批不写」的全量预校验——是新设计有意反过来的：
     * 一百头里有一头被他人推进了，不应该让另外九十九头白做。
     * 见 {@link #oneIneligibleDoeDoesNotAbortTheRound()}。
     */
    @Test
    void bulkIsCappedAndDeduplicated() {
        Round r = roundAwaitingMating("bulk_guard", 2);
        List<Long> taskIds = jdbc.queryForList(
            "select id from work_tasks where house_id = ? and batch_id = ? and status = 'PENDING'",
            Long.class, r.houseId, r.batchId);

        // 上限：501 个 id 应在加载之前就被顶回，而不是先查 501 次库再报错。
        List<Long> tooMany = new ArrayList<>();
        for (long i = 1; i <= 501; i++) {
            tooMany.add(i);
        }
        api.expectError("/api/repro/tasks/bulk-actions", HttpMethod.POST, r.owner.token, r.houseId,
            obj("requestId", requestId("cap"), "action", "MATING", "occurredAt", oneMinuteAgo(),
                "maleRabbitId", r.buckId, "matingMethod", "NATURAL", "taskIds", tooMany),
            400, "单次批量最多 500 项");

        // 去重：同一个 taskId 传两遍，不得变成两项（否则第二项会回一个假的 replayed 成功）。
        JsonNode res = api.postOk("/api/repro/tasks/bulk-actions", r.owner.token, r.houseId, obj(
            "requestId", requestId("dedup"), "action", "MATING", "occurredAt", oneMinuteAgo(),
            "maleRabbitId", r.buckId, "matingMethod", "NATURAL",
            "taskIds", List.of(taskIds.get(0), taskIds.get(0), taskIds.get(0))
        ));
        Assertions.assertEquals(1, res.get("total").asInt(), "重复 taskId 必须被去重");
        Assertions.assertEquals(1, res.get("succeeded").asInt());
    }

    @Test
    void oneIneligibleDoeDoesNotAbortTheRound() {
        Round r = roundAwaitingMating("bulk_partial", 3);
        // 保持兔只在场，但把一头的性别改成公兔：她仍在筛选范围内，
        // 状态机应拒绝这一项，其余两项照常推进。
        jdbc.update("update rabbits set gender = '1' where id = ?", r.doeIds.get(0));

        JsonNode res = api.postOk("/api/repro/tasks/bulk-actions", r.owner.token, r.houseId, obj(
            "requestId", requestId("partial"),
            "action", "MATING",
            "occurredAt", oneMinuteAgo(),
            "maleRabbitId", r.buckId,
            "matingMethod", "NATURAL",
            "filter", obj("batchId", r.batchId, "taskType", "MATING")
        ));

        Assertions.assertEquals(3, res.get("total").asInt());
        Assertions.assertEquals(2, res.get("succeeded").asInt(), "单头失败不得拖垮整轮");
        Assertions.assertEquals(1, res.get("failed").asInt());
        Assertions.assertEquals(2, count(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ? "
                + "and stage = 'AWAIT_PALPATION'", r.houseId, r.batchId));
    }

    // ---------------------------------------------------------------- helpers

    private int count(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    /** 一个批次的母兔全部停在「待配种」，外加一头种公兔。 */
    private Round roundAwaitingMating(String prefix, int doeCount) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix + "_house", 1, doeCount + 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long buckId = createRabbit(owner, houseId, cages.get(0), "0", "1", prefix + "_buck");

        List<Long> does = new ArrayList<>();
        for (int i = 0; i < doeCount; i++) {
            does.add(createRabbit(owner, houseId, cages.get(i + 1), "0", "0", prefix + "_doe" + i));
        }
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "RBM-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", does,
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();

        // 建批次已经把成员送到待催情，这里用一次真实的批量催情把整轮推到待配种。
        // 比直接开一个待配种周期更贴近真实用法，也顺带验了一道批量链路。
        api.postOk("/api/repro/tasks/bulk-actions", owner.token, houseId, obj(
            "action", "ESTRUS",
            "occurredAt", oneMinuteAgo(),
            "filter", obj("batchId", batchId, "taskType", "ESTRUS"),
            "requestId", requestId(prefix + "_estrus")
        ));
        return new Round(owner, houseId, batchId, buckId, does);
    }

    private record Round(
        UserSession owner, long houseId, long batchId, long buckId, List<Long> doeIds
    ) {
    }
}
