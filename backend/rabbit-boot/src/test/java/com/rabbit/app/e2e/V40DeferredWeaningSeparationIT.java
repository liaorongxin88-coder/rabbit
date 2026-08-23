package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class V40DeferredWeaningSeparationIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v40?createDatabaseIfNotExist=true"
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
    void addsDeferredSexCountsWithoutChangingHistoricalWeaningOrRabbits() throws SQLException {
        Flyway beforeV40 = flyway(MigrationVersion.fromVersion("39"));
        beforeV40.clean();
        beforeV40.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v40 house', 1, 2, 1, 'test', 'test')"
        );
        long cageId = insert(
            "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                + " values (?, 'V40-C', '3', 1, 'test', 'test')",
            houseId
        );
        long motherId = insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, state_version,"
                + " is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '0', '0', '1', 0, true, false, 'v40-mother', 'test', 'test')",
            houseId,
            cageId
        );
        long historicKitId = insert(
            "insert into rabbits (house_id, cage_id, mother_id, type, gender, arrival_method,"
                + " state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, ?, '2', '1', '1', 0, true, false, 'v40-kit', 'test', 'test')",
            houseId,
            cageId,
            motherId
        );
        long batchId = insert(
            "insert into batches (house_id, batch_code, status, request_id, create_by, update_by)"
                + " values (?, 'V40-B', '进行中', 'v40-batch', 'test', 'test')",
            houseId
        );
        long weaningRecordId = insert(
            "insert into weaning_records (house_id, batch_id, rabbit_id, weaning_count, waiting_count,"
                + " remark, create_by, update_by) values (?, ?, ?, 4, 0, 'historic record', 'test', 'test')",
            houseId,
            batchId,
            motherId
        );

        flyway(null).migrate();

        assertEquals(4, intValue("select weaning_count from weaning_records where id = ?", weaningRecordId));
        assertEquals(0, intValue("select waiting_count from weaning_records where id = ?", weaningRecordId));
        assertEquals("historic record", stringValue("select remark from weaning_records where id = ?", weaningRecordId));
        assertNull(nullableIntValue("select male_count from weaning_records where id = ?", weaningRecordId));
        assertNull(nullableIntValue("select female_count from weaning_records where id = ?", weaningRecordId));
        assertEquals(1, intValue("select count(*) from rabbits where id = ? and is_active = true", historicKitId));
        assertEquals(1, intValue("select rabbit_count from cages where id = ?", cageId));
    }

    private long insert(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private int intValue(String sql, Object... values) throws SQLException {
        Integer value = nullableIntValue(sql, values);
        return value == null ? 0 : value;
    }

    private Integer nullableIntValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                int value = rows.getInt(1);
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
