package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class BreedingCycleMigrationIT {
    private static final String URL = env(
            "E2E_MIGRATION_DATASOURCE_URL",
            "jdbc:mysql://localhost:3306/rabbit_app_e2e_migration?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    );
    private static final String USERNAME = env("E2E_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD = env("E2E_DATASOURCE_PASSWORD", "rabbit_root");

    @AfterEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void migrationBackfillsAnActiveNursingCycleWithoutInventingHistory() throws SQLException {
        Flyway versionTwenty = flyway(MigrationVersion.fromVersion("20"));
        versionTwenty.clean();
        versionTwenty.migrate();

        long houseId = insertAndReturnId(
                "INSERT INTO rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by) "
                        + "VALUES ('cycle migration house', 1, 2, 1, 'test', 'test')"
        );
        long femaleCageId = insertAndReturnId(
                "INSERT INTO cages (house_id, cage_number, status, rabbit_count, create_by, update_by) "
                        + "VALUES (?, 'M-1', '1', 1, 'test', 'test')",
                houseId
        );
        long maleCageId = insertAndReturnId(
                "INSERT INTO cages (house_id, cage_number, status, rabbit_count, create_by, update_by) "
                        + "VALUES (?, 'M-2', '1', 1, 'test', 'test')",
                houseId
        );
        long motherId = insertAndReturnId(
                "INSERT INTO rabbits (house_id, cage_id, type, gender, is_active, is_quarantined, create_by, update_by) "
                        + "VALUES (?, ?, '0', '0', TRUE, FALSE, 'test', 'test')",
                houseId,
                femaleCageId
        );
        long maleId = insertAndReturnId(
                "INSERT INTO rabbits (house_id, cage_id, type, gender, is_active, is_quarantined, create_by, update_by) "
                        + "VALUES (?, ?, '0', '1', TRUE, FALSE, 'test', 'test')",
                houseId,
                maleCageId
        );
        long batchId = insertAndReturnId(
                "INSERT INTO batches (house_id, batch_code, status, start_date, create_by, update_by) "
                        + "VALUES (?, 'MIGRATION-BATCH', '进行中', NOW(), 'test', 'test')",
                houseId
        );
        long parturitionId = insertAndReturnId(
                "INSERT INTO parturition_records (house_id, batch_id, rabbit_id, birth_date, total_kits, live_kits, create_by, update_by) "
                        + "VALUES (?, ?, ?, '2026-08-01 08:00:00', 8, 6, 'test', 'test')",
                houseId,
                batchId,
                motherId
        );
        long batchRabbitId = insertAndReturnId(
                "INSERT INTO batch_rabbits (batch_id, rabbit_id, male_rabbit_id, join_reason, batch_role, current_status, "
                        + "last_event_date, next_event_date, next_event_type, is_active, join_date, create_by, update_by) "
                        + "VALUES (?, ?, ?, '配种', 'breeding', '哺乳中', '2026-08-01 08:00:00', "
                        + "'2026-08-26 08:00:00', '断奶', TRUE, '2026-07-01 08:00:00', 'test', 'test')",
                batchId,
                motherId,
                maleId
        );

        flyway(null).migrate();

        assertEquals(1, queryLong(
                "SELECT COUNT(*) FROM breeding_cycles WHERE house_id = ? AND mother_rabbit_id = ?",
                houseId,
                motherId
        ));
        long cycleId = queryLong(
                "SELECT id FROM breeding_cycles WHERE house_id = ? AND mother_rabbit_id = ?",
                houseId,
                motherId
        );
        assertNotEquals(0, cycleId);
        // V21 当时写的是中文 status='哺乳中'；V27 把它推导成 stage，V28 删掉了那一列。
        // 这里改断言最终形态，验的仍是同一件事：回填把这头母兔认定为在哺乳。
        assertEquals("AWAIT_WEANING",
            queryString("SELECT stage FROM breeding_cycles WHERE id = ?", cycleId));
        assertEquals(8, queryLong("SELECT total_kits FROM breeding_cycles WHERE id = ?", cycleId));
        assertEquals(6, queryLong("SELECT live_kits FROM breeding_cycles WHERE id = ?", cycleId));
        assertEquals(6, queryLong("SELECT current_nursing_kits FROM breeding_cycles WHERE id = ?", cycleId));
        assertEquals(cycleId, queryLong("SELECT latest_cycle_id FROM batch_rabbits WHERE id = ?", batchRabbitId));
        assertEquals(6, queryLong("SELECT current_nursing_kits FROM batch_rabbits WHERE id = ?", batchRabbitId));
        assertEquals(1, queryLong("SELECT nursing_litter_count FROM batch_rabbits WHERE id = ?", batchRabbitId));
        assertEquals(cycleId, queryLong("SELECT breeding_cycle_id FROM parturition_records WHERE id = ?", parturitionId));
        assertEquals(1, queryLong(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'rabbits' AND column_name = 'birth_cycle_id'"
        ));
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private long insertAndReturnId(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private String queryString(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
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
