package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * V44：同一 (母兔, 批次) 至多一条未结束周期，多出来的外迁到恢复批次。
 *
 * <p>盯的是收敛而不是拒绝。上线时存量库里一定已经有违反新约束的行——V27 当初
 * 明确放行了同批次血配，测试也一直这么断言——所以盲目加唯一键只会让迁移在客户
 * 现场当场失败。这里先把违规行按确定式规则搬走，再加键。
 */
class V44BatchScopedOpenCycleIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v44?createDatabaseIfNotExist=true"
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
    void splitsParallelCyclesIntoSeparateBatchesAndEnforcesTheInvariant() throws SQLException {
        Flyway beforeV44 = flyway(MigrationVersion.fromVersion("43"));
        beforeV44.clean();
        beforeV44.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers,"
                + " create_by, update_by) values ('v44 house', 1, 6, 1, 'test', 'test')"
        );
        long[] cages = new long[6];
        for (int index = 0; index < cages.length; index++) {
            cages[index] = insert(
                "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                    + " values (?, ?, '1', 1, 'test', 'test')",
                houseId,
                "V44-C" + index
            );
        }

        // 血配母兔：同一批次里哺乳 + 待配种两条 OPEN 周期，正是 V27 当初放行的形状。
        long bloodMother = mother(houseId, cages[0], "blood");
        // 三条并行：验证外迁序位递增，两条多余周期不会被塞进同一个恢复批次。
        long tripleMother = mother(houseId, cages[1], "triple");
        // 跨批次各有一条多余周期：两条的「批次内序位」都是 2，若只按批次内序位分配
        // 目标批次，它们会一起落进 R1 而当场再次违反约束。
        long crossBatchMother = mother(houseId, cages[2], "cross");
        // 合规母兔：一批次一条，迁移不得动它。
        long tidyMother = mother(houseId, cages[3], "tidy");

        long batchA = batch(houseId, "A", "a");
        long batchB = batch(houseId, "B", "b");

        long bloodNursing = cycle(houseId, batchA, bloodMother, 1, "AWAIT_WEANING", "OPEN", "blood-1");
        long bloodPipeline = cycle(houseId, batchA, bloodMother, 2, "AWAIT_MATING", "OPEN", "blood-2");
        long bloodClosed = cycle(houseId, batchA, bloodMother, 3, "AWAIT_ESTRUS", "CLOSED", "blood-3");

        // 每只母兔至多一条管线阶段周期：uk_bc_pipeline（V27）仍然有效，多出来的
        // 并行周期在真实数据里只可能是哺乳（AWAIT_WEANING）。种子数据必须照此构造，
        // 否则测的就不是能真实出现的存量形状。
        long tripleFirst = cycle(houseId, batchA, tripleMother, 1, "AWAIT_WEANING", "OPEN", "tri-1");
        long tripleSecond = cycle(houseId, batchA, tripleMother, 2, "AWAIT_WEANING", "OPEN", "tri-2");
        long tripleThird = cycle(houseId, batchA, tripleMother, 3, "AWAIT_PALPATION", "OPEN", "tri-3");

        long crossA1 = cycle(houseId, batchA, crossBatchMother, 1, "AWAIT_WEANING", "OPEN", "cross-a1");
        long crossA2 = cycle(houseId, batchA, crossBatchMother, 2, "AWAIT_WEANING", "OPEN", "cross-a2");
        long crossB1 = cycle(houseId, batchB, crossBatchMother, 1, "AWAIT_WEANING", "OPEN", "cross-b1");
        long crossB2 = cycle(houseId, batchB, crossBatchMother, 2, "AWAIT_ESTRUS", "OPEN", "cross-b2");

        long tidyCycle = cycle(houseId, batchB, tidyMother, 1, "AWAIT_DELIVERY", "OPEN", "tidy-1");

        long membershipA = membership(batchA, bloodMother, bloodPipeline);
        membership(batchB, tidyMother, tidyCycle);

        // 挂在被搬走的周期上的从属数据必须跟着换批次，否则批次维度的查询会漏掉它们。
        long taskId = insert(
            "insert into work_tasks (house_id, task_type, subject_type, subject_id, cycle_id,"
                + " rabbit_id, batch_id, due_date, due_time, status, dedup_key, create_by, update_by)"
                + " values (?, 'MATING', 'CYCLE', ?, ?, ?, ?, curdate(), now(), 'PENDING', ?,"
                + " 'test', 'test')",
            houseId, bloodPipeline, bloodPipeline, bloodMother, batchA,
            "v44:task:" + bloodPipeline
        );
        long eventId = insert(
            "insert into repro_events (house_id, cycle_id, mother_rabbit_id, batch_id, event_type,"
                + " occurred_at, operator_name, request_id)"
                + " values (?, ?, ?, ?, 'CYCLE_START', now(), 'test', ?)",
            houseId, bloodPipeline, bloodMother, batchA, "v44-event-" + bloodPipeline
        );
        long litterId = insert(
            "insert into litters (house_id, cycle_id, mother_rabbit_id, batch_id, birth_date,"
                + " total_kits, live_kits, kept_kits, current_nursing, status, request_id,"
                + " create_by, update_by)"
                + " values (?, ?, ?, ?, now(), 6, 5, 5, 5, 'NURSING', ?, 'test', 'test')",
            houseId, bloodPipeline, bloodMother, batchA, "v44-litter-" + bloodPipeline
        );

        flyway(null).migrate();

        long recoveryR1 = longValue(
            "select id from batches where house_id = ? and request_id = ?",
            houseId, "v44-parallel-house-" + houseId + "-r1"
        );
        long recoveryR2 = longValue(
            "select id from batches where house_id = ? and request_id = ?",
            houseId, "v44-parallel-house-" + houseId + "-r2"
        );
        assertEquals("V44-PARALLEL-H" + houseId + "-R1", stringValue(
            "select batch_code from batches where id = ?", recoveryR1
        ));
        assertEquals("进行中", stringValue("select status from batches where id = ?", recoveryR1));
        assertNotEquals(recoveryR1, recoveryR2);
        // 每舍每序位一个批次，不是每条周期一个。
        assertEquals(2, intValue(
            "select count(*) from batches where house_id = ? and create_by = 'v44'", houseId
        ));

        // 保留 id 最小的那条（血配场景里就是带着窝的哺乳周期），其余外迁。
        assertEquals(batchA, longValue("select batch_id from breeding_cycles where id = ?", bloodNursing));
        assertEquals(recoveryR1, longValue("select batch_id from breeding_cycles where id = ?", bloodPipeline));
        // 已结束的周期不参与去重，批次归属不动。
        assertEquals(batchA, longValue("select batch_id from breeding_cycles where id = ?", bloodClosed));

        assertEquals(batchA, longValue("select batch_id from breeding_cycles where id = ?", tripleFirst));
        assertEquals(recoveryR1, longValue("select batch_id from breeding_cycles where id = ?", tripleSecond));
        assertEquals(recoveryR2, longValue("select batch_id from breeding_cycles where id = ?", tripleThird));

        assertEquals(batchA, longValue("select batch_id from breeding_cycles where id = ?", crossA1));
        assertEquals(batchB, longValue("select batch_id from breeding_cycles where id = ?", crossB1));
        // 两条多余周期的批次内序位都是 2，但按母兔重排后拿到 R1 与 R2，落进不同批次。
        long crossA2Batch = longValue("select batch_id from breeding_cycles where id = ?", crossA2);
        long crossB2Batch = longValue("select batch_id from breeding_cycles where id = ?", crossB2);
        assertNotEquals(crossA2Batch, crossB2Batch, "同一母兔的两条多余周期不得落进同一恢复批次");

        assertEquals(batchB, longValue("select batch_id from breeding_cycles where id = ?", tidyCycle));
        assertEquals(1, intValue("select cycle_no from breeding_cycles where id = ?", tidyCycle),
            "合规行的周期号不得被重排");

        // 收敛结果：任何 (兔舍, 批次, 母兔) 组合都只剩一条 OPEN 周期。
        assertEquals(0, intValue(
            "select count(*) from (select 1 from breeding_cycles where lifecycle = 'OPEN'"
                + " and batch_id is not null group by house_id, batch_id, mother_rabbit_id"
                + " having count(*) > 1) duplicates"
        ));

        // 成员关系由生产周期派生，外迁后必须在恢复批次里补出来，否则状态机会以
        // 「成员关系不存在」拒掉这条周期上的每一次操作。
        assertEquals(1, intValue(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and batch_role = 'breeding' and is_active = true",
            recoveryR1, bloodMother
        ));
        assertEquals("待配种", stringValue(
            "select current_status from batch_rabbits where batch_id = ? and rabbit_id = ?",
            recoveryR1, bloodMother
        ));
        assertEquals(1, intValue(
            "select count(*) from rabbit_status_history where house_id = ? and batch_id = ?"
                + " and rabbit_id = ? and create_by = 'v44'",
            houseId, recoveryR1, bloodMother
        ));

        // 原批次的成员投影原本指着被搬走的周期，必须重算到留下的那一条。
        assertEquals(bloodNursing, longValue(
            "select latest_cycle_id from batch_rabbits where id = ?", membershipA
        ));
        assertEquals("待分笼", stringValue(
            "select current_status from batch_rabbits where id = ?", membershipA
        ));

        assertEquals(recoveryR1, longValue("select batch_id from work_tasks where id = ?", taskId));
        assertEquals(recoveryR1, longValue("select batch_id from repro_events where id = ?", eventId));
        assertEquals(recoveryR1, longValue("select batch_id from litters where id = ?", litterId));

        // 唯一键落地，且只约束 OPEN + 有批次的行。
        assertEquals(1, intValue(
            "select count(*) from information_schema.statistics where table_schema = database()"
                + " and table_name = 'breeding_cycles' and index_name = 'uk_bc_batch_member'"
                + " and non_unique = 0 and column_name = 'batch_member_guard'"
        ));
        // 用 AWAIT_WEANING 而不是管线阶段：管线阶段会先撞上 uk_bc_pipeline，
        // 那样这条断言就通过得毫无意义，测不到新加的 uk_bc_batch_member。
        assertThrows(SQLException.class, () -> cycle(
            houseId, batchB, tidyMother, 9, "AWAIT_WEANING", "OPEN", "rejected-second-open"
        ));
        long legalClosed = cycle(
            houseId, batchB, tidyMother, 9, "AWAIT_WEANING", "CLOSED", "legal-closed"
        );
        assertNull(stringValue(
            "select batch_member_guard from breeding_cycles where id = ?", legalClosed
        ), "已结束的周期不参与去重");
        long otherBatchOpen = cycle(
            houseId, batchA, tidyMother, 1, "AWAIT_WEANING", "OPEN", "legal-other-batch"
        );
        assertEquals(batchA + ":" + tidyMother, stringValue(
            "select batch_member_guard from breeding_cycles where id = ?", otherBatchOpen
        ), "换个批次开第二条周期必须放行——这正是新语义要的血配形状");
    }

    private long mother(long houseId, long cageId, String suffix) throws SQLException {
        return insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, state_version,"
                + " is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '0', '0', '1', 0, true, false, ?, 'test', 'test')",
            houseId,
            cageId,
            "v44-mother-" + suffix
        );
    }

    private long batch(long houseId, String code, String suffix) throws SQLException {
        return insert(
            "insert into batches (house_id, batch_code, status, start_date, request_id,"
                + " create_by, update_by) values (?, ?, '进行中', now(), ?, 'test', 'test')",
            houseId,
            "V44-" + code,
            "v44-batch-" + suffix
        );
    }

    private long membership(long batchId, long rabbitId, long latestCycleId) throws SQLException {
        return insert(
            "insert into batch_rabbits (batch_id, rabbit_id, latest_cycle_id, join_reason,"
                + " batch_role, current_status, is_active, join_date, create_by, update_by)"
                + " values (?, ?, ?, 'test', 'breeding', '待配种', true, now(), 'test', 'test')",
            batchId,
            rabbitId,
            latestCycleId
        );
    }

    private long cycle(
        long houseId,
        Long batchId,
        long motherId,
        int cycleNo,
        String stage,
        String lifecycle,
        String suffix
    ) throws SQLException {
        return insert(
            "insert into breeding_cycles (house_id, batch_id, mother_rabbit_id, cycle_no, stage,"
                + " stage_entered_at, lifecycle, request_id, create_by, update_by)"
                + " values (?, ?, ?, ?, ?, now(), ?, ?, 'test', 'test')",
            houseId,
            batchId,
            motherId,
            cycleNo,
            stage,
            lifecycle,
            "v44-cycle-" + suffix
        );
    }

    private long insert(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 sql, Statement.RETURN_GENERATED_KEYS
             )) {
            bind(statement, values);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private int intValue(String sql, Object... values) throws SQLException {
        return Math.toIntExact(longValue(sql, values));
    }

    private long longValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new AssertionError("No row for: " + sql);
                }
                long value = rows.getLong(1);
                if (rows.wasNull()) {
                    throw new AssertionError("Expected a non-null value for: " + sql);
                }
                return value;
            }
        }
    }

    private String stringValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new AssertionError("No row for: " + sql);
                }
                return rows.getString(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
            .dataSource(URL, USERNAME, PASSWORD)
            .locations("classpath:db/migration")
            .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
