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

class V34BackfillIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v34?createDatabaseIfNotExist=true"
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
    void backfillsSaleAndReplacementTasksFromV33Data() throws SQLException {
        Flyway toV33 = flyway(MigrationVersion.fromVersion("33"));
        toV33.clean();
        toV33.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v34 house', 1, 2, 1, 'test', 'test')"
        );
        long commodityCage = insert(
            "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                + " values (?, 'V34-C', '3', 1, 'test', 'test')",
            houseId
        );
        long replacementCage = insert(
            "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                + " values (?, 'V34-R', '2', 1, 'test', 'test')",
            houseId
        );
        insert(
            "insert into global_setting (house_id, aphrodisiac_days, palpation_days,"
                + " prepartum_days, weaning_days, postpartum_days, sale_days, replacement_days,"
                + " create_by, update_by) values (?, 2, 12, 3, 25, 10, 30, 45, 'test', 'test')",
            houseId
        );
        long commodityRabbit = insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, arrival_date,"
                + " growth_stage, state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '2', '0', '1', date_sub(now(), interval 40 day), 'GROWING', 0,"
                + " true, false, 'v34-commodity', 'test', 'test')",
            houseId, commodityCage
        );
        long replacementRabbit = insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, arrival_date,"
                + " reproductive_stage, state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '1', '0', '1', date_sub(now(), interval 100 day), 'RESERVE', 0,"
                + " true, false, 'v34-replacement', 'test', 'test')",
            houseId, replacementCage
        );
        insert(
            "insert into replacement_records (house_id, rabbit_id, original_type, replacement_date,"
                + " expected_mature_date, is_mature_notified, create_by, update_by)"
                + " values (?, ?, '2', date_sub(now(), interval 100 day), date_sub(now(), interval 10 day),"
                + " false, 'test', 'test')",
            houseId, replacementRabbit
        );

        flyway(null).migrate();

        assertEquals("SALE_READY", taskType(commodityRabbit));
        assertEquals("REPLACEMENT_MATURE", taskType(replacementRabbit));
        assertEquals("PENDING", stringValue(
            "select status from replacement_records where rabbit_id = ?", replacementRabbit
        ));
        assertNotNull(stringValue(
            "select date_format(growth_stage_entered_at, '%Y-%m-%d') from rabbits where id = ?",
            commodityRabbit
        ));
        assertEquals(15L, longValue(
            "select prepartum_days from global_setting where house_id = ?", houseId
        ));
        assertEquals(90L, longValue(
            "select replacement_days from global_setting where house_id = ?", houseId
        ));
    }

    private String taskType(long rabbitId) throws SQLException {
        return stringValue(
            "select task_type from work_tasks where rabbit_id = ? and status = 'PENDING'",
            rabbitId
        );
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

    private long longValue(String sql, Object value) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
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
