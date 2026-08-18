package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * V27 历史回填的验收测试。
 *
 * <p>它替代了「回填与线上 API 共用 openCycleAt」这条设计约束所要提供的保障。
 * V27 是集合式 SQL 而非逐只调用状态机（理由见迁移文件头部），因此防漂移的
 * 责任落在这里：断言回填结果与 {@code ReproStateMachineService} 的口径一致，
 * 尤其是投影优先级和任务归属这两处最容易各写各的地方。
 *
 * <p>套路沿用 {@link BreedingCycleMigrationIT}：先迁到 V26 造旧数据，再迁完
 * 剩下的，观察 V27 的产出。
 */
class V27BackfillIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_migration?createDatabaseIfNotExist=true"
            + "&useUnicode=true&characterEncoding=utf8&useSSL=false"
            + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    );
    private static final String USERNAME = env("E2E_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD = env("E2E_DATASOURCE_PASSWORD", "rabbit_root");

    @AfterEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void everyLegacyStatusLandsOnAStageLifecycleAndResult() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();

        // 每个旧状态一只单独的母兔。不能堆在同一只身上：其中八个状态回填后
        // 都是 OPEN 管线周期，uk_bc_pipeline 会（正确地）拒绝它们共存。
        long planned = legacyCycle(f, "计划中", null, null);
        long awaitEstrus = legacyCycle(f, "待催情", null, null);
        long inEstrus = legacyCycle(f, "催情中", null, null);
        long awaitMating = legacyCycle(f, "待配种", null, null);
        long mated = legacyCycle(f, "已配种", "2026-08-01 08:00:00", null);
        long unsure = legacyCycle(f, "不确定", "2026-08-01 08:00:00", null);
        long prepartum = legacyCycle(f, "怀孕确认", "2026-07-01 08:00:00", "备产");
        long delivery = legacyCycle(f, "怀孕确认", "2026-07-01 08:00:00", "分娩");
        long empty = legacyCycle(f, "空怀", "2026-06-01 08:00:00", null);
        long failed = legacyCycle(f, "分娩失败", "2026-06-01 08:00:00", null);
        long terminated = legacyCycle(f, "已终止", "2026-05-01 08:00:00", null);

        flyway(TO_V27).migrate();

        assertStage(planned, "AWAIT_ESTRUS", "OPEN", null);
        assertStage(awaitEstrus, "AWAIT_ESTRUS", "OPEN", null);
        // 催情中意味着催情已经做完，等的是配种 —— 不是「还要再催一次」。
        assertStage(inEstrus, "AWAIT_MATING", "OPEN", null);
        assertStage(awaitMating, "AWAIT_MATING", "OPEN", null);
        assertStage(mated, "AWAIT_PALPATION", "OPEN", null);
        assertStage(unsure, "AWAIT_PALPATION", "OPEN", null);
        // 旧的备产完成不改 status，两段只能靠下一次提醒事件拆开。
        assertStage(prepartum, "AWAIT_PREPARTUM", "OPEN", null);
        assertStage(delivery, "AWAIT_DELIVERY", "OPEN", null);
        // 已结束的周期保留「在哪一步结束」，供失败率按阶段归因。
        assertStage(empty, "AWAIT_PALPATION", "CLOSED", "EMPTY");
        assertStage(failed, "AWAIT_DELIVERY", "CLOSED", "FAILED");
        assertStage(terminated, "AWAIT_PALPATION", "CLOSED", "REMOVED");

        // 回填后存量行不应再有 NULL。
        assertEquals(0, queryLong("SELECT COUNT(*) FROM breeding_cycles WHERE stage IS NULL"));
        assertEquals(0, queryLong(
            "SELECT COUNT(*) FROM breeding_cycles WHERE stage_entered_at IS NULL"));
        // 但列本身仍须可空：旧写路径插入周期时不写 stage，而它要到 P4 才下线。
        // 在 V27 就收紧会让线上每一次旧配种报 Field 'stage' doesn't have a default value。
        assertEquals("YES", queryString(
            "SELECT is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'breeding_cycles' AND column_name = 'stage'"));
        assertEquals(1, queryLong(
            "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'breeding_cycles' "
                + "AND index_name = 'uk_bc_pipeline'"));
        // uk_bc_batch_member 故意不建：它会把同批次内的血配一并挡死，
        // 而 pipeline_guard 已经提供了更严的保证（详见 V27 注释）。
        assertEquals(0, queryLong(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'breeding_cycles' "
                + "AND index_name = 'uk_bc_batch_member'"));
    }

    @Test
    void activeMemberWithoutACycleGetsOneSoSheDoesNotVanish() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();
        // 旧模型直到配种才建周期行，这类母兔在新模型里「无周期」。
        long batchRabbitId = insertId(
            "INSERT INTO batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, "
                + "current_status, last_event_date, is_active, join_date, create_by, update_by) "
                + "VALUES (?, ?, '入群', 'breeding', '催情中', '2026-08-10 09:00:00', TRUE, "
                + "'2026-07-01 08:00:00', 'legacy', 'legacy')",
            f.batchId, f.doeId);

        flyway(TO_V27).migrate();

        long cycleId = queryLong(
            "SELECT id FROM breeding_cycles WHERE house_id = ? AND mother_rabbit_id = ?",
            f.houseId, f.doeId);
        assertTrue(cycleId > 0, "必须补建周期，否则这只母兔在待办中心整体消失");
        assertEquals("AWAIT_MATING",
            queryString("SELECT stage FROM breeding_cycles WHERE id = ?", cycleId));
        assertEquals("待配种",
            queryString("SELECT status FROM breeding_cycles WHERE id = ?", cycleId),
            "兼容镜像列要同步写，老 APK 直读它渲染列表");
        assertEquals("v27-br-" + batchRabbitId,
            queryString("SELECT request_id FROM breeding_cycles WHERE id = ?", cycleId));

        // 补建的周期必须同时带出待办，否则补了也没人看见。
        assertEquals("MATING", queryString(
            "SELECT task_type FROM work_tasks WHERE cycle_id = ? AND status = 'PENDING'", cycleId));
        assertEquals("cycle:" + cycleId + ":MATING", queryString(
            "SELECT dedup_key FROM work_tasks WHERE cycle_id = ?", cycleId));
    }

    @Test
    void nursingCycleProducesALitterAndHangsWeaningOnIt() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();
        long cycleId = insertId(
            "INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, status, "
                + "mating_date, birth_date, total_kits, live_kits, current_nursing_kits, "
                + "next_event_date, next_event_type, create_by, update_by) "
                + "VALUES (?, ?, ?, 1, '哺乳中', '2026-06-20 08:00:00', '2026-07-20 08:00:00', "
                + "9, 7, 7, '2026-08-14 08:00:00', '断奶', 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);

        flyway(TO_V27).migrate();

        long litterId = queryLong("SELECT id FROM litters WHERE cycle_id = ?", cycleId);
        assertTrue(litterId > 0);
        assertEquals(9, queryLong("SELECT total_kits FROM litters WHERE id = ?", litterId));
        assertEquals(7, queryLong("SELECT live_kits FROM litters WHERE id = ?", litterId));
        assertEquals(2, queryLong("SELECT loss_count FROM litters WHERE id = ?", litterId));
        assertEquals("NURSING", queryString("SELECT status FROM litters WHERE id = ?", litterId));

        // 分笼任务挂窝而不是挂周期：血配时同一母兔要能同时持两条待办。
        assertEquals("LITTER", queryString(
            "SELECT subject_type FROM work_tasks WHERE task_type = 'WEANING'"));
        assertEquals(litterId, queryLong(
            "SELECT subject_id FROM work_tasks WHERE task_type = 'WEANING'"));
        assertEquals("litter:" + litterId + ":WEANING", queryString(
            "SELECT dedup_key FROM work_tasks WHERE task_type = 'WEANING'"));
    }

    @Test
    void pipelineCycleWinsOverNursingInTheRabbitProjection() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();
        // 血配：一边还在哺乳，一边已经重新配上种。
        insertId("INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, "
            + "status, birth_date, total_kits, live_kits, current_nursing_kits, create_by, update_by) "
            + "VALUES (?, ?, ?, 1, '哺乳中', '2026-07-20 08:00:00', 8, 8, 8, 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);
        long pipeline = insertId(
            "INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, status, "
                + "mating_date, create_by, update_by) "
                + "VALUES (?, ?, ?, 2, '已配种', '2026-08-05 08:00:00', 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);

        flyway(TO_V27).migrate();

        // 必须与 ReproStateMachineService.projectMother 同口径：管线周期优先。
        assertEquals("AWAIT_PALPATION",
            queryString("SELECT current_stage FROM rabbits WHERE id = ?", f.doeId));
        assertEquals(pipeline,
            queryLong("SELECT current_cycle_id FROM rabbits WHERE id = ?", f.doeId));
        assertEquals("2026-08-05", queryString(
            "SELECT DATE(last_mating_date) FROM rabbits WHERE id = ?", f.doeId));
    }

    @Test
    void eventBackfillKeepsRecentHistoryOnly() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();
        long recent = insertId(
            "INSERT INTO parturition_records (house_id, batch_id, rabbit_id, birth_date, "
                + "total_kits, live_kits, create_by, update_by) "
                + "VALUES (?, ?, ?, DATE_SUB(NOW(), INTERVAL 10 DAY), 8, 6, 'zhangsan', 'zhangsan')",
            f.houseId, f.batchId, f.doeId);
        long ancient = insertId(
            "INSERT INTO parturition_records (house_id, batch_id, rabbit_id, birth_date, "
                + "total_kits, live_kits, create_by, update_by) "
                + "VALUES (?, ?, ?, DATE_SUB(NOW(), INTERVAL 18 MONTH), 7, 5, 'zhangsan', 'zhangsan')",
            f.houseId, f.batchId, f.doeId);
        // 活仔为 0 的分娩要落到 DELIVERY_FAILED。
        insertId("INSERT INTO parturition_records (house_id, batch_id, rabbit_id, birth_date, "
                + "total_kits, live_kits, create_by, update_by) "
                + "VALUES (?, ?, ?, DATE_SUB(NOW(), INTERVAL 5 DAY), 4, 0, 'zhangsan', 'zhangsan')",
            f.houseId, f.batchId, f.doeId);

        flyway(TO_V27).migrate();

        assertEquals(1, queryLong(
            "SELECT COUNT(*) FROM repro_events WHERE request_id = ?", "v27-par-" + recent));
        assertEquals(0, queryLong(
            "SELECT COUNT(*) FROM repro_events WHERE request_id = ?", "v27-par-" + ancient),
            "超过 6 个月的历史留在只读旧表，不灌进事件流");
        assertEquals(1, queryLong(
            "SELECT COUNT(*) FROM repro_events WHERE event_type = 'DELIVERY_FAILED'"));
        // operator_name 取旧表 create_by，保持「谁做的」可追溯。
        assertEquals("zhangsan", queryString(
            "SELECT operator_name FROM repro_events WHERE request_id = ?", "v27-par-" + recent));
        assertEquals(8, queryLong(
            "SELECT payload->>'$.totalKits' FROM repro_events WHERE request_id = ?",
            "v27-par-" + recent));
    }

    @Test
    void reRunningTheBackfillChangesNothing() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();
        insertId("INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, "
            + "status, birth_date, total_kits, live_kits, current_nursing_kits, create_by, update_by) "
            + "VALUES (?, ?, ?, 1, '哺乳中', '2026-07-20 08:00:00', 8, 6, 6, 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);
        insertId("INSERT INTO batch_rabbits (batch_id, rabbit_id, join_reason, batch_role, "
            + "current_status, is_active, join_date, create_by, update_by) "
            + "VALUES (?, ?, '入群', 'breeding', '待催情', TRUE, '2026-07-01 08:00:00', "
            + "'legacy', 'legacy')", f.batchId, f.otherDoeId);
        insertId("INSERT INTO parturition_records (house_id, batch_id, rabbit_id, birth_date, "
            + "total_kits, live_kits, create_by, update_by) "
            + "VALUES (?, ?, ?, DATE_SUB(NOW(), INTERVAL 3 DAY), 8, 6, 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);

        flyway(TO_V27).migrate();

        long cycles = queryLong("SELECT COUNT(*) FROM breeding_cycles");
        long litters = queryLong("SELECT COUNT(*) FROM litters");
        long events = queryLong("SELECT COUNT(*) FROM repro_events");
        long tasks = queryLong("SELECT COUNT(*) FROM work_tasks");
        assertTrue(cycles >= 2 && litters == 1 && events == 1 && tasks >= 2,
            "前置条件：首轮确实产生了数据 cycles=" + cycles + " litters=" + litters
                + " events=" + events + " tasks=" + tasks);

        // 手工重放整个 V27（Flyway 不会自动重跑已成功的迁移）。
        // 失败后的标准动作就是「修数据 → repair → 重跑」，所以幂等必须成立。
        executeMigrationScript();

        assertEquals(cycles, queryLong("SELECT COUNT(*) FROM breeding_cycles"));
        assertEquals(litters, queryLong("SELECT COUNT(*) FROM litters"));
        assertEquals(events, queryLong("SELECT COUNT(*) FROM repro_events"));
        assertEquals(tasks, queryLong("SELECT COUNT(*) FROM work_tasks"));
        assertEquals(0, queryLong(
            "SELECT COUNT(*) FROM work_tasks WHERE snooze_count <> 0"),
            "重跑不得把已推迟次数清零或翻倍");
    }

    @Test
    void duplicateOpenCyclesAbortTheMigrationBeforeAnyConstraintIsAdded() throws SQLException {
        Fixture f = migrateToV26AndSeedBase();
        // 同一只母兔两条在途管线周期 —— V2 的核心不变式禁止这种状态。
        insertId("INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, "
            + "status, mating_date, create_by, update_by) "
            + "VALUES (?, ?, ?, 1, '已配种', '2026-08-01 08:00:00', 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);
        insertId("INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, "
            + "status, mating_date, create_by, update_by) "
            + "VALUES (?, ?, ?, 2, '已配种', '2026-08-02 08:00:00', 'legacy', 'legacy')",
            f.houseId, f.batchId, f.doeId);

        Exception failure = assertThrows(Exception.class, () -> flyway(TO_V27).migrate());
        assertTrue(rootMessage(failure).contains("Duplicate entry"),
            "唯一键本身就是对账闸门，报错要直接point出冲突值，实际: " + rootMessage(failure));

        // 关键：中止发生在任何 DDL 落地之前，重跑前的库仍是可修复状态。
        assertEquals(0, queryLong(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'breeding_cycles' "
                + "AND index_name = 'uk_bc_pipeline'"));
    }

    // ---------------------------------------------------------------- helpers

    private record Fixture(long houseId, long batchId, long doeId, long otherDoeId) {
    }

    private Fixture migrateToV26AndSeedBase() throws SQLException {
        Flyway toV26 = flyway(MigrationVersion.fromVersion("26"));
        toV26.clean();
        toV26.migrate();

        long houseId = insertId(
            "INSERT INTO rabbit_houses (name, layout_rows, layout_cols, layout_layers, "
                + "create_by, update_by) VALUES ('v27 house', 1, 4, 1, 'test', 'test')");
        long cage1 = insertId(
            "INSERT INTO cages (house_id, cage_number, status, rabbit_count, create_by, update_by) "
                + "VALUES (?, 'V27-1', '1', 1, 'test', 'test')", houseId);
        long cage2 = insertId(
            "INSERT INTO cages (house_id, cage_number, status, rabbit_count, create_by, update_by) "
                + "VALUES (?, 'V27-2', '1', 1, 'test', 'test')", houseId);
        long doeId = insertId(
            "INSERT INTO rabbits (house_id, cage_id, type, gender, is_active, is_quarantined, "
                + "create_by, update_by) VALUES (?, ?, '0', '0', TRUE, FALSE, 'test', 'test')",
            houseId, cage1);
        long otherDoeId = insertId(
            "INSERT INTO rabbits (house_id, cage_id, type, gender, is_active, is_quarantined, "
                + "create_by, update_by) VALUES (?, ?, '0', '0', TRUE, FALSE, 'test', 'test')",
            houseId, cage2);
        long batchId = insertId(
            "INSERT INTO batches (house_id, batch_code, status, start_date, create_by, update_by) "
                + "VALUES (?, 'V27-BATCH', '进行中', NOW(), 'test', 'test')", houseId);
        return new Fixture(houseId, batchId, doeId, otherDoeId);
    }

    /** 造一只专属母兔并给她一条旧周期，避免多个待测状态互相冲撞。 */
    private long legacyCycle(Fixture f, String status, String matingDate, String nextType)
            throws SQLException {
        long doeId = newDoe(f);
        return insertId(
            "INSERT INTO breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, status, "
                + "mating_date, next_event_type, closed_at, create_by, update_by) "
                + "VALUES (?, ?, ?, 1, ?, ?, ?, NULL, 'legacy', 'legacy')",
            f.houseId, f.batchId, doeId, status, matingDate, nextType);
    }

    private long newDoe(Fixture f) throws SQLException {
        long cageId = insertId(
            "INSERT INTO cages (house_id, cage_number, status, rabbit_count, create_by, update_by) "
                + "VALUES (?, CONCAT('V27-X-', UUID_SHORT()), '1', 1, 'test', 'test')", f.houseId);
        return insertId(
            "INSERT INTO rabbits (house_id, cage_id, type, gender, is_active, is_quarantined, "
                + "create_by, update_by) VALUES (?, ?, '0', '0', TRUE, FALSE, 'test', 'test')",
            f.houseId, cageId);
    }

    private void assertStage(long cycleId, String stage, String lifecycle, String result)
            throws SQLException {
        assertEquals(stage, queryString("SELECT stage FROM breeding_cycles WHERE id = ?", cycleId));
        assertEquals(lifecycle,
            queryString("SELECT lifecycle FROM breeding_cycles WHERE id = ?", cycleId));
        if (result == null) {
            assertNull(queryString("SELECT result FROM breeding_cycles WHERE id = ?", cycleId));
        } else {
            assertEquals(result,
                queryString("SELECT result FROM breeding_cycles WHERE id = ?", cycleId));
        }
    }

    /**
     * 逐条重放 V27，用于验证幂等。
     *
     * <p>按分号切而不是按行切：文件里有
     * {@code PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;} 这种一行三句。
     * 这里成立的前提是该脚本的字符串字面量里不含分号（已人工核对），
     * 因此不需要完整的 SQL 词法分析。
     */
    private void executeMigrationScript() throws SQLException {
        String withoutComments = readMigration().replaceAll("(?m)--.*$", "");
        List<String> statements = new ArrayList<>();
        for (String raw : withoutComments.split(";")) {
            String sql = raw.trim();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private String readMigration() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V27__doe_breeding_v2_backfill.sql")) {
            assertNotNull(in, "V27 迁移脚本必须在 classpath 上");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("无法读取 V27 迁移脚本", e);
        }
    }

    private static String rootMessage(Throwable t) {
        StringBuilder all = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            all.append(c.getMessage()).append(" | ");
        }
        return all.toString();
    }

    /**
     * 本套件验证 V27 回填本身，因此必须停在 V27。
     *
     * <p>再往后跑到 V28 会删掉 status / next_event_* 等兼容列，
     * 而那正是这里要断言的对象——到那时断言失败并不说明 V27 错了。
     */
    private static final MigrationVersion TO_V27 = MigrationVersion.fromVersion("27");

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(URL, USERNAME, PASSWORD).cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private long insertId(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement =
                 connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private long queryLong(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private String queryString(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
