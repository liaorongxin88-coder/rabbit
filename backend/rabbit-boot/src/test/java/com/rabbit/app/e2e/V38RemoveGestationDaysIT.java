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

class V38RemoveGestationDaysIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v38?createDatabaseIfNotExist=true"
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
    void removesRetiredGestationAndMigratesPrepartumLeadSemantics() throws SQLException {
        Flyway toV36 = flyway(MigrationVersion.fromVersion("36"));
        toV36.clean();
        toV36.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v38 house', 1, 1, 1, 'test', 'test')"
        );
        insert(
            "insert into global_setting (house_id, aphrodisiac_days, palpation_days, gestation_days,"
                + " prepartum_days, weaning_days, postpartum_days, adaptation_days, growing_days,"
                + " fattening_days, sale_days, replacement_days, create_by, update_by)"
                + " values (?, 2, 12, 31, 15, 30, 10, 3, 18, 12, 33, 90, 'test', 'test')",
            houseId
        );

        flyway(null).migrate();

        assertEquals(0, columnCount("gestation_days"));
        assertEquals(3, integerValue(
            "select prepartum_days from global_setting where house_id = ?",
            houseId
        ));
        assertEquals(3, integerValue(
            "select adaptation_days from global_setting where house_id = ?",
            houseId
        ));
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

    private long columnCount(String columnName) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "select count(*) from information_schema.columns"
                     + " where table_schema = database() and table_name = 'global_setting'"
                     + " and column_name = ?"
             )) {
            statement.setString(1, columnName);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private int integerValue(String sql, long houseId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, houseId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
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
