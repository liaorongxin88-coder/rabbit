package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LargeFarmSchemaMigrationIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_migration?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    );
    private static final String USERNAME = env(
        "E2E_DATASOURCE_USERNAME",
        "root"
    );
    private static final String PASSWORD = env(
        "E2E_DATASOURCE_PASSWORD",
        "rabbit_root"
    );

    @AfterEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void migrationSupportsLargeConflictPayloadsAndFarmQueries()
        throws SQLException {
        Flyway flyway = flyway();
        flyway.clean();
        flyway.migrate();

        assertEquals(
            "mediumtext",
            queryString(
                "SELECT data_type FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'outbound_requests' " +
                "AND column_name = 'conflicts_json'"
            )
        );
        assertTrue(
            queryLong(
                "SELECT character_maximum_length FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'outbound_requests' " +
                "AND column_name = 'conflicts_json'"
            ) >= 16_000_000L
        );
        assertEquals(
            1L,
            indexCount("rabbits", "idx_rabbits_house_birth_batch_id")
        );
        assertEquals(
            1L,
            indexCount(
                "rabbit_abnormal_conditions",
                "idx_rac_house_rabbit_deal"
            )
        );
        assertEquals(
            64L,
            queryLong(
                "SELECT character_maximum_length FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'request_dedup' " +
                "AND column_name = 'payload_hash'"
            )
        );
    }

    private long indexCount(String table, String index) throws SQLException {
        return queryLong(
            "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
            "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
            table,
            index
        );
    }

    private Flyway flyway() {
        return Flyway.configure()
            .dataSource(URL, USERNAME, PASSWORD)
            .cleanDisabled(false)
            .load();
    }

    private long queryLong(String sql, Object... params) throws SQLException {
        try (
            Connection connection = connection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private String queryString(String sql, Object... params)
        throws SQLException {
        try (
            Connection connection = connection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    private void bind(PreparedStatement statement, Object... params)
        throws SQLException {
        for (int index = 0; index < params.length; index++) {
            statement.setObject(index + 1, params[index]);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
