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

class V39SaleReadyTaskCleanupIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v39?createDatabaseIfNotExist=true"
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
    void cancelsOnlyPendingSaleReadyTasksForActiveNonCommodityRabbits() throws SQLException {
        Flyway toV36 = flyway(MigrationVersion.fromVersion("36"));
        toV36.clean();
        toV36.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v39 house', 1, 4, 1, 'test', 'test')"
        );
        long cageId = insert(
            "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                + " values (?, 'V39-C', '3', 4, 'test', 'test')",
            houseId
        );
        long breeder = rabbit(houseId, cageId, "0", null, "breeder");
        long immatureCommodity = rabbit(houseId, cageId, "2", "FATTENING", "immature");
        long matureCommodity = rabbit(houseId, cageId, "2", "MATURE", "mature");
        task(houseId, breeder, "SALE_READY", "breeder-sale");
        task(houseId, immatureCommodity, "SALE_READY", "immature-sale");
        task(houseId, matureCommodity, "SALE_READY", "mature-sale");
        task(houseId, breeder, "ESTRUS", "breeder-estrus");

        flyway(null).migrate();

        assertEquals("CANCELLED", taskStatus(breeder, "SALE_READY"));
        assertEquals("v39", updateBy(breeder, "SALE_READY"));
        assertEquals("PENDING", taskStatus(immatureCommodity, "SALE_READY"));
        assertEquals("PENDING", taskStatus(matureCommodity, "SALE_READY"));
        assertEquals("PENDING", taskStatus(breeder, "ESTRUS"));
    }

    private long rabbit(Long houseId, Long cageId, String type, String growthStage, String suffix)
        throws SQLException {
        return insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, growth_stage,"
                + " state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, ?, '0', '1', ?, 0, true, false, ?, 'test', 'test')",
            houseId,
            cageId,
            type,
            growthStage,
            "v39-" + suffix
        );
    }

    private void task(Long houseId, Long rabbitId, String taskType, String suffix) throws SQLException {
        insert(
            "insert into work_tasks (house_id, task_type, subject_type, subject_id, rabbit_id,"
                + " due_date, due_time, status, dedup_key, create_by, update_by)"
                + " values (?, ?, 'RABBIT', ?, ?, date_add(curdate(), interval 30 day),"
                + " date_add(now(), interval 30 day), 'PENDING', ?, 'test', 'test')",
            houseId,
            taskType,
            rabbitId,
            rabbitId,
            "v39:" + suffix
        );
    }

    private String taskStatus(Long rabbitId, String taskType) throws SQLException {
        return stringValue(
            "select status from work_tasks where rabbit_id = ? and task_type = ?",
            rabbitId,
            taskType
        );
    }

    private String updateBy(Long rabbitId, String taskType) throws SQLException {
        return stringValue(
            "select update_by from work_tasks where rabbit_id = ? and task_type = ?",
            rabbitId,
            taskType
        );
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

    private String stringValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
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
