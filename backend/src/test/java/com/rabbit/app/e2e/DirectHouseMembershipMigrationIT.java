package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class DirectHouseMembershipMigrationIT {
    private static final String URL = env(
            "E2E_MIGRATION_DATASOURCE_URL",
            "jdbc:mysql://localhost:3306/rabbit_app_e2e_migration?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    );
    private static final String USERNAME = env("E2E_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD = env("E2E_DATASOURCE_PASSWORD", "rabbit_root");
    private static final String PHONE_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String RENAMED_PHONE_HASH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @AfterEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void migrationPreservesDirectAccessAndRemovesTheMerchantModel() throws SQLException {
        resetToVersionFourteen();

        long resetRequiredOwner = queryLong(
                "SELECT user_id FROM sys_user WHERE password = '!RESET_REQUIRED_BY_PLATFORM_ADMIN!' LIMIT 1"
        );
        long resetRequiredMerchant = queryLong(
                "SELECT merchant_id FROM sys_user WHERE user_id = ?",
                resetRequiredOwner
        );
        execute("UPDATE merchants SET status = 'ENABLED' WHERE id = ?", resetRequiredMerchant);
        upsertMerchantMember(resetRequiredMerchant, resetRequiredOwner, "OWNER", "ENABLED");
        setMerchantOwner(resetRequiredMerchant, resetRequiredOwner);
        long resetRequiredHouse = createHouse(
                resetRequiredMerchant,
                resetRequiredOwner,
                "待重置所有者兔场"
        );
        addHouseMember(resetRequiredHouse, resetRequiredOwner, "OWNER", "control", true);

        long enabledMerchant = createMerchant("迁移启用商户", "ENABLED");
        long implicitOwner = createUser(enabledMerchant, "migration_implicit_owner", "normal-password");
        long explicitPhoneOwner = createPhoneUser(
                enabledMerchant,
                "mobile_migration_phone_owner",
                PHONE_HASH,
                "random-phone-password"
        );
        long renamedPhoneUser = createPhoneUser(
                enabledMerchant,
                "migration_renamed_phone_user",
                RENAMED_PHONE_HASH,
                "real-password"
        );
        long disabledMember = createUser(enabledMerchant, "migration_disabled_member", "normal-password");
        long sharedUser = createUser(enabledMerchant, "migration_shared_user", "normal-password");
        addMerchantMember(enabledMerchant, implicitOwner, "OWNER", "ENABLED");
        addMerchantMember(enabledMerchant, explicitPhoneOwner, "MEMBER", "ENABLED");
        addMerchantMember(enabledMerchant, renamedPhoneUser, "MEMBER", "ENABLED");
        addMerchantMember(enabledMerchant, disabledMember, "MEMBER", "DISABLED");
        addMerchantMember(enabledMerchant, sharedUser, "MEMBER", "ENABLED");
        setMerchantOwner(enabledMerchant, implicitOwner);

        long enabledHouse = createHouse(enabledMerchant, explicitPhoneOwner, "迁移启用兔场");
        addHouseMember(enabledHouse, explicitPhoneOwner, "OWNER", "control", true);
        addHouseMember(enabledHouse, disabledMember, "STAFF", "edit", false);
        addHouseMember(enabledHouse, sharedUser, "VIEWER", "view", false);
        execute(
                "INSERT INTO cages (house_id, cage_number, create_by, update_by) VALUES (?, 'MIGRATION-CAGE', 'test', 'test')",
                enabledHouse
        );

        long suspendedMerchant = createMerchant("迁移停用商户", "DISABLED");
        long suspendedOwner = createUser(suspendedMerchant, "migration_suspended_owner", "normal-password");
        addMerchantMember(suspendedMerchant, suspendedOwner, "OWNER", "ENABLED");
        addMerchantMember(suspendedMerchant, sharedUser, "MEMBER", "ENABLED");
        setMerchantOwner(suspendedMerchant, suspendedOwner);
        long suspendedHouse = createHouse(suspendedMerchant, suspendedOwner, "迁移停用兔场");
        addHouseMember(suspendedHouse, suspendedOwner, "OWNER", "control", true);
        addHouseMember(suspendedHouse, sharedUser, "VIEWER", "view", false);

        long orphanMerchant = createMerchant("迁移孤儿商户", "ENABLED");
        long disabledCandidate = createUser(orphanMerchant, "migration_disabled_candidate", "normal-password");
        long enabledViewer = createUser(orphanMerchant, "migration_enabled_viewer", "normal-password");
        addMerchantMember(orphanMerchant, disabledCandidate, "MEMBER", "DISABLED");
        addMerchantMember(orphanMerchant, enabledViewer, "MEMBER", "ENABLED");
        long orphanHouse = createHouse(orphanMerchant, null, "迁移孤儿兔场");
        addHouseMember(orphanHouse, disabledCandidate, "MANAGER", "control", true);
        long viewerOnlyHouse = createHouse(orphanMerchant, null, "迁移仅观察员兔场");
        addHouseMember(viewerOnlyHouse, enabledViewer, "VIEWER", "view", false);

        long noHouseMerchant = createMerchant("迁移无兔场商户", "ENABLED");
        long noHouseOwner = createUser(noHouseMerchant, "migration_no_house_owner", "normal-password");
        addMerchantMember(noHouseMerchant, noHouseOwner, "OWNER", "ENABLED");
        setMerchantOwner(noHouseMerchant, noHouseOwner);

        long usersBefore = queryLong("SELECT COUNT(*) FROM sys_user");
        long housesBefore = queryLong("SELECT COUNT(*) FROM rabbit_houses");
        long cagesBefore = queryLong("SELECT COUNT(*) FROM cages");
        assertEquals(2, queryLong("SELECT COUNT(DISTINCT merchant_id) FROM merchant_users WHERE user_id = ?", sharedUser));

        flyway(null).migrate();

        assertEquals(usersBefore, queryLong("SELECT COUNT(*) FROM sys_user"));
        assertEquals(housesBefore, queryLong("SELECT COUNT(*) FROM rabbit_houses"));
        assertEquals(cagesBefore, queryLong("SELECT COUNT(*) FROM cages"));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM cages WHERE house_id = ? AND cage_number = 'MIGRATION-CAGE'", enabledHouse));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM sys_user WHERE user_id = ?", noHouseOwner));

        assertEquals("ENABLED", queryString("SELECT status FROM rabbit_houses WHERE id = ?", enabledHouse));
        assertEquals("SUSPENDED", queryString("SELECT status FROM rabbit_houses WHERE id = ?", suspendedHouse));
        assertEquals("ORPHANED", queryString("SELECT status FROM rabbit_houses WHERE id = ?", orphanHouse));
        assertEquals("ORPHANED", queryString("SELECT status FROM rabbit_houses WHERE id = ?", viewerOnlyHouse));
        assertEquals("ORPHANED", queryString("SELECT status FROM rabbit_houses WHERE id = ?", resetRequiredHouse));

        assertMembership(enabledHouse, explicitPhoneOwner, "OWNER", "ENABLED");
        assertMembership(enabledHouse, implicitOwner, "OWNER", "ENABLED");
        assertMembership(enabledHouse, disabledMember, "STAFF", "DISABLED");
        assertMembership(enabledHouse, sharedUser, "VIEWER", "ENABLED");
        assertEquals(2, queryLong(
                "SELECT COUNT(*) FROM house_users WHERE house_id = ? AND role = 'OWNER' AND status = 'ENABLED'",
                enabledHouse
        ));

        assertMembership(suspendedHouse, suspendedOwner, "OWNER", "DISABLED");
        assertMembership(suspendedHouse, sharedUser, "VIEWER", "DISABLED");
        assertMembership(orphanHouse, disabledCandidate, "MANAGER", "DISABLED");
        assertMembership(viewerOnlyHouse, enabledViewer, "VIEWER", "ENABLED");
        assertMembership(resetRequiredHouse, resetRequiredOwner, "OWNER", "ENABLED");
        assertEquals(0, queryLong(
                "SELECT COUNT(*) FROM house_users WHERE house_id = ? AND role = 'OWNER' AND status = 'ENABLED'",
                orphanHouse
        ));
        assertEquals(0, queryLong(
                "SELECT COUNT(*) FROM house_users WHERE house_id = ? AND role = 'OWNER'",
                viewerOnlyHouse
        ));
        assertEquals(0, queryLong(
                "SELECT COUNT(*) FROM house_users hu "
                        + "JOIN sys_user u ON u.user_id = hu.user_id AND u.status = 'ENABLED' "
                        + "WHERE hu.house_id = ? AND hu.role = 'OWNER' AND hu.status = 'ENABLED'",
                resetRequiredHouse
        ));

        assertEquals(PHONE_HASH, queryString("SELECT phone_hash FROM sys_user WHERE user_id = ?", explicitPhoneOwner));
        assertEquals("138****8000", queryString("SELECT phone_masked FROM sys_user WHERE user_id = ?", explicitPhoneOwner));
        assertEquals(1, queryLong("SELECT password_initialized FROM sys_user WHERE user_id = ?", explicitPhoneOwner));
        assertEquals("ENABLED", queryString("SELECT status FROM sys_user WHERE user_id = ?", explicitPhoneOwner));
        assertEquals(1, queryLong("SELECT password_initialized FROM sys_user WHERE user_id = ?", renamedPhoneUser));
        assertEquals("ENABLED", queryString("SELECT status FROM sys_user WHERE user_id = ?", renamedPhoneUser));
        assertEquals(1, queryLong("SELECT password_initialized FROM sys_user WHERE user_id = ?", implicitOwner));
        assertEquals(
                0,
                queryLong("SELECT password_initialized FROM sys_user WHERE password = '!RESET_REQUIRED_BY_PLATFORM_ADMIN!' LIMIT 1")
        );
        assertEquals(
                "DISABLED",
                queryString("SELECT status FROM sys_user WHERE password = '!RESET_REQUIRED_BY_PLATFORM_ADMIN!' LIMIT 1")
        );
        assertEquals("DISABLED", queryString("SELECT status FROM sys_user WHERE user_id = ?", resetRequiredOwner));

        assertFalse(tableExists("merchants"));
        assertFalse(tableExists("merchant_users"));
        assertFalse(tableExists("merchant_house_policies"));
        assertTrue(tableExists("house_invitations"));
        assertEquals(
                "NO",
                queryString(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'house_invitations' "
                                + "AND column_name = 'request_id'"
                )
        );
        assertEquals(0, queryLong(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'house_invitations' "
                        + "AND index_name = 'uk_house_invitations_house_phone'"
        ));
        assertEquals(1, queryLong(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'house_invitations' "
                        + "AND index_name = 'idx_house_invitations_house_phone_status'"
        ));
        assertFalse(columnExists("sys_user", "merchant_id"));
        assertFalse(columnExists("rabbit_houses", "merchant_id"));
        assertFalse(columnExists("rabbit_houses", "owner_user_id"));
        assertTrue(columnExists("sys_user", "status"));
        assertTrue(columnExists("house_users", "status"));
        assertTrue(columnExists("rabbit_houses", "status"));
    }

    private void resetToVersionFourteen() {
        Flyway baseline = flyway(MigrationVersion.fromVersion("14"));
        baseline.clean();
        baseline.migrate();
    }

    private long createMerchant(String name, String status) throws SQLException {
        return insertAndReturnId(
                "INSERT INTO merchants (name, status, create_by, update_by) VALUES (?, ?, 'test', 'test')",
                name,
                status
        );
    }

    private long createUser(long merchantId, String userName, String password) throws SQLException {
        return insertAndReturnId(
                "INSERT INTO sys_user (merchant_id, user_name, password) VALUES (?, ?, ?)",
                merchantId,
                userName,
                password
        );
    }

    private long createPhoneUser(long merchantId, String userName, String phoneHash, String password) throws SQLException {
        return insertAndReturnId(
                "INSERT INTO sys_user (merchant_id, user_name, password, phone_country_code, phone_hash, phone_masked, phone_bound_time) "
                        + "VALUES (?, ?, ?, '+86', ?, '138****8000', NOW())",
                merchantId,
                userName,
                password,
                phoneHash
        );
    }

    private void addMerchantMember(long merchantId, long userId, String role, String status) throws SQLException {
        execute(
                "INSERT INTO merchant_users (merchant_id, user_id, role, status, create_by, update_by) "
                        + "VALUES (?, ?, ?, ?, 'test', 'test')",
                merchantId,
                userId,
                role,
                status
        );
    }

    private void upsertMerchantMember(long merchantId, long userId, String role, String status) throws SQLException {
        execute(
                "INSERT INTO merchant_users (merchant_id, user_id, role, status, create_by, update_by) "
                        + "VALUES (?, ?, ?, ?, 'test', 'test') "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), status = VALUES(status), update_by = 'test'",
                merchantId,
                userId,
                role,
                status
        );
    }

    private void setMerchantOwner(long merchantId, long userId) throws SQLException {
        execute("UPDATE merchants SET owner_user_id = ? WHERE id = ?", userId, merchantId);
    }

    private long createHouse(long merchantId, Long ownerUserId, String name) throws SQLException {
        return insertAndReturnId(
                "INSERT INTO rabbit_houses (merchant_id, owner_user_id, name, layout_rows, layout_cols, layout_layers, request_id, create_by, update_by) "
                        + "VALUES (?, ?, ?, 1, 1, 1, ?, 'test', 'test')",
                merchantId,
                ownerUserId,
                name,
                "migration-" + name
        );
    }

    private void addHouseMember(
            long houseId,
            long userId,
            String role,
            String perms,
            boolean admin
    ) throws SQLException {
        execute(
                "INSERT INTO house_users (house_id, user_id, role, perms, is_admin, create_by, update_by) "
                        + "VALUES (?, ?, ?, ?, ?, 'test', 'test')",
                houseId,
                userId,
                role,
                perms,
                admin
        );
    }

    private void assertMembership(long houseId, long userId, String role, String status) throws SQLException {
        assertEquals(1, queryLong(
                "SELECT COUNT(*) FROM house_users WHERE house_id = ? AND user_id = ? AND role = ? AND status = ?",
                houseId,
                userId,
                role,
                status
        ));
    }

    private boolean tableExists(String table) throws SQLException {
        return queryLong(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                table
        ) == 1;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        return queryLong(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                table,
                column
        ) == 1;
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

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
