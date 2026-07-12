package com.rabbit.app.e2e;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantAccountMigrationIT {
    private static final String URL = env(
            "E2E_DATASOURCE_URL",
            "jdbc:mysql://localhost:3306/rabbit_app_e2e?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    );
    private static final String USERNAME = env("E2E_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD = env("E2E_DATASOURCE_PASSWORD", "rabbit_root");

    @AfterEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void migrationBackfillsLegacyAccountsAndCreatesMissingOwners() throws SQLException {
        resetToVersionEight();

        long defaultMerchantId = queryLong("SELECT id FROM merchants ORDER BY id LIMIT 1");
        long orphanMerchantId = insertAndReturnId(
                "INSERT INTO merchants (name, status, create_by, update_by) VALUES (?, 'ENABLED', 'test', 'test')",
                "无账号历史商户"
        );
        long boundUserId = insertAndReturnId(
                "INSERT INTO sys_user (user_name, password, openid) VALUES (?, 'test-password', NULL)",
                "legacy_bound"
        );
        long unboundUserId = insertAndReturnId(
                "INSERT INTO sys_user (user_name, password, openid) VALUES (?, 'test-password', NULL)",
                "legacy_unbound"
        );
        execute(
                "INSERT INTO merchant_users (merchant_id, user_id, create_by, update_by) VALUES (?, ?, 'test', 'test')",
                defaultMerchantId,
                boundUserId
        );

        flyway(null).migrate();

        assertEquals(0, queryLong("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'merchant_users'"));
        assertEquals(0, queryLong("SELECT COUNT(*) FROM sys_user WHERE merchant_id IS NULL"));
        assertEquals(defaultMerchantId, queryLong("SELECT merchant_id FROM sys_user WHERE user_id = ?", boundUserId));

        long generatedMerchantId = queryLong("SELECT merchant_id FROM sys_user WHERE user_id = ?", unboundUserId);
        assertNotEquals(defaultMerchantId, generatedMerchantId);
        assertEquals("待完善商户 #" + unboundUserId, queryString("SELECT name FROM merchants WHERE id = ?", generatedMerchantId));

        assertEquals(1, queryLong("SELECT COUNT(*) FROM sys_user WHERE merchant_id = ?", orphanMerchantId));
        assertEquals(
                "!RESET_REQUIRED_BY_PLATFORM_ADMIN!",
                queryString("SELECT password FROM sys_user WHERE merchant_id = ?", orphanMerchantId)
        );
        assertEquals(
                "NO",
                queryString("SELECT is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'merchant_id'")
        );
        assertEquals(
                1,
                queryLong("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() AND table_name = 'sys_user' AND constraint_name = 'fk_sys_user_merchant'")
        );
    }

    @Test
    void migrationStopsBeforeSchemaChangesWhenAnAccountHasMultipleMerchants() throws SQLException {
        resetToVersionEight();

        long firstMerchantId = queryLong("SELECT id FROM merchants ORDER BY id LIMIT 1");
        long secondMerchantId = insertAndReturnId(
                "INSERT INTO merchants (name, status, create_by, update_by) VALUES (?, 'ENABLED', 'test', 'test')",
                "冲突商户"
        );
        long userId = insertAndReturnId(
                "INSERT INTO sys_user (user_name, password, openid) VALUES (?, 'test-password', NULL)",
                "legacy_ambiguous"
        );
        execute(
                "INSERT INTO merchant_users (merchant_id, user_id, create_by, update_by) VALUES (?, ?, 'test', 'test')",
                firstMerchantId,
                userId
        );
        execute(
                "INSERT INTO merchant_users (merchant_id, user_id, create_by, update_by) VALUES (?, ?, 'test', 'test')",
                secondMerchantId,
                userId
        );

        FlywayException exception = assertThrows(FlywayException.class, () -> flyway(null).migrate());

        assertTrue(allMessages(exception).contains("V10 migration blocked"));
        assertEquals(0, queryLong("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'merchant_id'"));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'merchant_users'"));
    }

    private void resetToVersionEight() {
        Flyway baseline = flyway(MigrationVersion.fromVersion("8"));
        baseline.clean();
        baseline.migrate();
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

    private void execute(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
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

    private static String allMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
