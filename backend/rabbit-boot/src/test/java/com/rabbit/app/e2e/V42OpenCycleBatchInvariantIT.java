package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

class V42OpenCycleBatchInvariantIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v42?createDatabaseIfNotExist=true"
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
    void normalizesOpenCyclesAndPreservesClosedNullHistory() throws SQLException {
        Flyway beforeV42 = flyway(MigrationVersion.fromVersion("41"));
        beforeV42.clean();
        beforeV42.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers,"
                + " create_by, update_by) values ('v42 house', 1, 7, 1, 'test', 'test')"
        );
        long[] cages = new long[7];
        for (int index = 0; index < cages.length; index++) {
            cages[index] = insert(
                "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                    + " values (?, ?, '1', 1, 'test', 'test')",
                houseId,
                "V42-C" + index
            );
        }
        long boundMother = mother(houseId, cages[0], "bound");
        long orphanMother = mother(houseId, cages[1], "orphan");
        long secondOrphanMother = mother(houseId, cages[2], "second-orphan");
        long historicalMother = mother(houseId, cages[3], "historical");
        long completedMother = mother(houseId, cages[4], "completed");
        long missingMemberMother = mother(houseId, cages[5], "missing-member");
        long crossHouseMother = mother(houseId, cages[6], "cross-house");

        long olderBatch = batch(houseId, "OLDER", "older");
        long selectedBatch = batch(houseId, "SELECTED", "selected");
        membership(olderBatch, boundMother, "2026-01-01 08:00:00");
        long selectedMembership = membership(
            selectedBatch, boundMother, "2026-02-01 08:00:00"
        );
        cycle(houseId, selectedBatch, boundMother, 1, "AWAIT_ESTRUS", "CLOSED", "existing");

        long boundCycle = cycle(
            houseId, null, boundMother, 1, "AWAIT_MATING", "OPEN", "bound-open"
        );
        long orphanCycle = cycle(
            houseId, null, orphanMother, 1, "AWAIT_WEANING", "OPEN", "orphan-open"
        );
        long secondOrphanCycle = cycle(
            houseId, null, secondOrphanMother, 1, "AWAIT_ESTRUS", "OPEN", "second-open"
        );
        long closedNullCycle = cycle(
            houseId, null, historicalMother, 1, "AWAIT_PALPATION", "CLOSED", "closed-null"
        );
        long completedBatch = batch(houseId, "COMPLETED", "completed");
        execute("update batches set status = '已完成' where id = ?", completedBatch);
        long completedBatchCycle = cycle(
            houseId,
            completedBatch,
            completedMother,
            1,
            "AWAIT_MATING",
            "OPEN",
            "completed-batch"
        );
        long missingMemberCycle = cycle(
            houseId,
            selectedBatch,
            missingMemberMother,
            1,
            "AWAIT_PALPATION",
            "OPEN",
            "missing-member"
        );
        long otherHouseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers,"
                + " create_by, update_by) values ('v42 other house', 1, 1, 1, 'test', 'test')"
        );
        long otherHouseBatch = batch(otherHouseId, "OTHER", "other-house");
        long crossHouseCycle = cycle(
            houseId,
            otherHouseBatch,
            crossHouseMother,
            1,
            "AWAIT_DELIVERY",
            "OPEN",
            "cross-house-batch"
        );

        long taskId = insert(
            "insert into work_tasks (house_id, task_type, subject_type, subject_id, cycle_id,"
                + " rabbit_id, due_date, due_time, status, dedup_key, create_by, update_by)"
                + " values (?, 'WEANING', 'CYCLE', ?, ?, ?, curdate(), now(), 'PENDING', ?,"
                + " 'test', 'test')",
            houseId,
            orphanCycle,
            orphanCycle,
            orphanMother,
            "v42:task:" + orphanCycle
        );
        long eventId = insert(
            "insert into repro_events (house_id, cycle_id, mother_rabbit_id, event_type,"
                + " occurred_at, operator_name, request_id)"
                + " values (?, ?, ?, 'CYCLE_START', now(), 'test', ?)",
            houseId,
            orphanCycle,
            orphanMother,
            "v42-event-" + orphanCycle
        );
        long litterId = insert(
            "insert into litters (house_id, cycle_id, mother_rabbit_id, birth_date, total_kits,"
                + " live_kits, kept_kits, current_nursing, status, request_id, create_by, update_by)"
                + " values (?, ?, ?, now(), 6, 5, 5, 5, 'NURSING', ?, 'test', 'test')",
            houseId,
            orphanCycle,
            orphanMother,
            "v42-litter-" + orphanCycle
        );

        flyway(null).migrate();

        assertNull(nullableLongValue(
            "select batch_id from breeding_cycles where id = ?", boundCycle
        ));
        assertEquals(selectedBatch, longValue(
            "select planned_batch_id from breeding_cycles where id = ?", boundCycle
        ));
        assertEquals(2, intValue(
            "select cycle_no from breeding_cycles where id = ?", boundCycle
        ));
        assertEquals(boundCycle, longValue(
            "select latest_cycle_id from batch_rabbits where id = ?", selectedMembership
        ));
        assertEquals("待配种", stringValue(
            "select current_status from batch_rabbits where id = ?", selectedMembership
        ));

        long recoveryBatch = longValue(
            "select id from batches where house_id = ? and request_id = ?",
            houseId,
            "v42-recovery-house-" + houseId
        );
        assertEquals("V42-RECOVERY-H" + houseId, stringValue(
            "select batch_code from batches where id = ?", recoveryBatch
        ));
        assertEquals("进行中", stringValue(
            "select status from batches where id = ?", recoveryBatch
        ));
        assertEquals(recoveryBatch, longValue(
            "select batch_id from breeding_cycles where id = ?", orphanCycle
        ));
        assertNull(nullableLongValue(
            "select batch_id from breeding_cycles where id = ?", secondOrphanCycle
        ));
        assertEquals(recoveryBatch, longValue(
            "select planned_batch_id from breeding_cycles where id = ?", secondOrphanCycle
        ));
        assertEquals(1, intValue(
            "select count(*) from batches where house_id = ? and request_id = ?",
            houseId,
            "v42-recovery-house-" + houseId
        ));
        assertNull(nullableLongValue(
            "select batch_id from breeding_cycles where id = ?", completedBatchCycle
        ));
        assertEquals(recoveryBatch, longValue(
            "select planned_batch_id from breeding_cycles where id = ?", completedBatchCycle
        ));
        assertEquals(recoveryBatch, longValue(
            "select batch_id from breeding_cycles where id = ?", crossHouseCycle
        ));
        assertEquals(selectedBatch, longValue(
            "select batch_id from breeding_cycles where id = ?", missingMemberCycle
        ));
        assertEquals(4, intValue(
            "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'breeding'"
                + " and is_active = true",
            recoveryBatch
        ));
        assertEquals(4, intValue(
            "select count(*) from rabbit_status_history where house_id = ? and batch_id = ?"
                + " and create_by = 'v42'",
            houseId,
            recoveryBatch
        ));
        assertEquals(1, intValue(
            "select count(*) from batch_rabbits where batch_id = ? and rabbit_id = ?"
                + " and batch_role = 'breeding' and is_active = true",
            selectedBatch,
            missingMemberMother
        ));

        assertEquals(recoveryBatch, longValue(
            "select batch_id from work_tasks where id = ?", taskId
        ));
        assertEquals(recoveryBatch, longValue(
            "select batch_id from repro_events where id = ?", eventId
        ));
        assertEquals(recoveryBatch, longValue(
            "select batch_id from litters where id = ?", litterId
        ));
        assertNull(nullableLongValue(
            "select batch_id from breeding_cycles where id = ?", closedNullCycle
        ));

        assertEquals(0, intValue(
            "select count(*) from information_schema.table_constraints"
                + " where constraint_schema = database() and table_name = 'breeding_cycles'"
                + " and constraint_name = 'ck_bc_open_batch' and constraint_type = 'CHECK'"
        ));
        long legalOpen = cycle(
            houseId, null, historicalMother, 2, "AWAIT_ESTRUS", "OPEN", "legal-open"
        );
        assertNotNull(nullableLongValue(
            "select id from breeding_cycles where id = ? and batch_id is null", legalOpen
        ));
        long legalClosed = cycle(
            houseId, null, historicalMother, 2, "AWAIT_ESTRUS", "CLOSED", "legal-closed"
        );
        assertNotNull(nullableLongValue(
            "select id from breeding_cycles where id = ? and batch_id is null", legalClosed
        ));
    }

    private long mother(long houseId, long cageId, String suffix) throws SQLException {
        return insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, state_version,"
                + " is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '0', '0', '1', 0, true, false, ?, 'test', 'test')",
            houseId,
            cageId,
            "v42-mother-" + suffix
        );
    }

    private long batch(long houseId, String code, String suffix) throws SQLException {
        return insert(
            "insert into batches (house_id, batch_code, status, start_date, request_id,"
                + " create_by, update_by) values (?, ?, '进行中', now(), ?, 'test', 'test')",
            houseId,
            "V42-" + code,
            "v42-batch-" + suffix
        );
    }

    private long membership(long batchId, long rabbitId, String joinDate) throws SQLException {
        return insert(
            "insert into batch_rabbits (batch_id, rabbit_id, join_reason, batch_role,"
                + " current_status, is_active, join_date, create_by, update_by)"
                + " values (?, ?, 'test', 'breeding', '待催情', true, ?, 'test', 'test')",
            batchId,
            rabbitId,
            joinDate
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
            "v42-cycle-" + suffix
        );
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
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
        Long value = nullableLongValue(sql, values);
        if (value == null) {
            throw new AssertionError("Expected a non-null value for: " + sql);
        }
        return value;
    }

    private Long nullableLongValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                long value = rows.getLong(1);
                return rows.wasNull() ? null : value;
            }
        }
    }

    private String stringValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
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
