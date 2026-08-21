package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class V36InactiveRabbitReminderBackfillIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v36?createDatabaseIfNotExist=true"
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
    void cancelsPendingTasksForInactiveRabbitsOnly() throws SQLException {
        Flyway toV35 = flyway(MigrationVersion.fromVersion("35"));
        toV35.clean();
        toV35.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v36 house', 1, 2, 1, 'test', 'test')"
        );
        long cageId = insert(
            "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                + " values (?, 'V36-C', '3', 2, 'test', 'test')",
            houseId
        );
        long inactiveRabbit = rabbit(houseId, cageId, false, "v36-inactive");
        long activeRabbit = rabbit(houseId, cageId, true, "v36-active");
        task(houseId, inactiveRabbit, "inactive");
        task(houseId, activeRabbit, "active");

        flyway(null).migrate();

        assertEquals("CANCELLED", taskStatus(inactiveRabbit));
        assertEquals("PENDING", taskStatus(activeRabbit));
        assertEquals("v36", stringValue(
            "select update_by from work_tasks where rabbit_id = ?",
            inactiveRabbit
        ));
    }

    private long rabbit(long houseId, long cageId, boolean active, String requestId)
        throws SQLException {
        return insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method,"
                + " state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '2', '0', '1', 0, ?, false, ?, 'test', 'test')",
            houseId,
            cageId,
            active,
            requestId
        );
    }

    private void task(long houseId, long rabbitId, String suffix) throws SQLException {
        insert(
            "insert into work_tasks (house_id, task_type, subject_type, subject_id, rabbit_id,"
                + " due_date, due_time, status, dedup_key, create_by, update_by)"
                + " values (?, 'SALE_READY', 'RABBIT', ?, ?, curdate(), now(), 'PENDING', ?,"
                + " 'test', 'test')",
            houseId,
            rabbitId,
            rabbitId,
            "rabbit:" + rabbitId + ":" + suffix
        );
    }

    private String taskStatus(long rabbitId) throws SQLException {
        return stringValue("select status from work_tasks where rabbit_id = ?", rabbitId);
    }

    private long insert(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 sql,
                 Statement.RETURN_GENERATED_KEYS
             )) {
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

    private String stringValue(String sql, Object value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
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
