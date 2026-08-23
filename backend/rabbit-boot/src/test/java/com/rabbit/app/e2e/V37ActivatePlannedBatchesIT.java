package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

class V37ActivatePlannedBatchesIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v37?createDatabaseIfNotExist=true"
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
    void activatesPlannedBatchesAndChangesTheDatabaseDefault() throws SQLException {
        Flyway toV36 = flyway(MigrationVersion.fromVersion("36"));
        toV36.clean();
        toV36.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v37 house', 1, 2, 1, 'test', 'test')"
        );
        long planned = insert(
            "insert into batches (house_id, batch_code, status, request_id, create_by, update_by)"
                + " values (?, 'V37-PLANNED', '计划中', 'v37-planned', 'test', 'test')",
            houseId
        );
        long completed = insert(
            "insert into batches (house_id, batch_code, status, start_date, end_date, request_id, create_by, update_by)"
                + " values (?, 'V37-COMPLETED', '已完成', now(), now(), 'v37-completed', 'test', 'test')",
            houseId
        );

        flyway(null).migrate();

        assertEquals("进行中", stringValue("select status from batches where id = ?", planned));
        assertNotNull(stringValue(
            "select date_format(start_date, '%Y-%m-%d %H:%i:%s') from batches where id = ?",
            planned
        ));
        assertEquals("已完成", stringValue("select status from batches where id = ?", completed));
        assertNotNull(stringValue(
            "select date_format(end_date, '%Y-%m-%d %H:%i:%s') from batches where id = ?",
            completed
        ));

        long defaulted = insert(
            "insert into batches (house_id, batch_code, request_id, create_by, update_by)"
                + " values (?, 'V37-DEFAULT', 'v37-default', 'test', 'test')",
            houseId
        );
        assertEquals("进行中", stringValue("select status from batches where id = ?", defaulted));
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
