package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class V53CommodityGrowthStageConstraintIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v53?createDatabaseIfNotExist=true"
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
    void clearsNonCommodityStagesAndRejectsFutureWrites() throws SQLException {
        Flyway toV52 = flyway(MigrationVersion.fromVersion("52"));
        toV52.clean();
        toV52.migrate();

        long houseId = insert(
            "insert into rabbit_houses"
                + " (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v53 house', 1, 3, 1, 'test', 'test')"
        );
        long breederCage = cage(houseId, "1-1-1", "1");
        long replacementCage = cage(houseId, "1-2-1", "2");
        long commodityCage = cage(houseId, "1-3-1", "3");

        long breederId = rabbit(houseId, breederCage, "0", "MATURE");
        long replacementId = rabbit(houseId, replacementCage, "1", "GROWING");
        long commodityId = rabbit(houseId, commodityCage, "2", "ADAPTATION");

        flyway(null).migrate();

        assertEquals(0, stageValueCount(breederId));
        assertEquals(0, stageValueCount(replacementId));
        assertEquals(2, stageValueCount(commodityId));
        assertThrows(SQLException.class, () -> execute(
            "update rabbits set growth_stage = 'MATURE',"
                + " growth_stage_entered_at = now() where id = ?",
            replacementId
        ));
    }

    private long cage(long houseId, String number, String status) throws SQLException {
        return insert(
            "insert into cages"
                + " (house_id, cage_number, status, rabbit_count, is_enabled, create_by, update_by)"
                + " values (?, ?, ?, 0, true, 'test', 'test')",
            houseId,
            number,
            status
        );
    }

    private long rabbit(long houseId, long cageId, String type, String stage)
            throws SQLException {
        return insert(
            "insert into rabbits"
                + " (house_id, cage_id, type, gender, growth_stage, growth_stage_entered_at,"
                + " is_active, request_id, create_by, update_by)"
                + " values (?, ?, ?, '0', ?, now(), true, ?, 'test', 'test')",
            houseId,
            cageId,
            type,
            stage,
            "v53-rabbit-" + cageId
        );
    }

    private int stageValueCount(long rabbitId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "select (growth_stage is not null) + (growth_stage_entered_at is not null)"
                     + " from rabbits where id = ?"
             )) {
            statement.setLong(1, rabbitId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
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
