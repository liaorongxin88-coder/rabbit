package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * V31 把存量笼位编号补成「排-位-层」。
 *
 * <p>改的是工人天天看的编号，所以这里重点不是「改对了几个」，而是**该跳过的都跳过了**：
 * 撞号、坐标重复、坐标不全，一个都不能动。改错一个编号，人就会把兔子放进错的笼子。
 */
class CageNumberBackfillMigrationIT {
    private static final String URL = env(
            "E2E_MIGRATION_DATASOURCE_URL",
            "jdbc:mysql://localhost:3306/rabbit_app_e2e_migration?createDatabaseIfNotExist=true&useUnicode=true"
                    + "&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    );
    private static final String USERNAME = env("E2E_DATASOURCE_USERNAME", "root");
    private static final String PASSWORD = env("E2E_DATASOURCE_PASSWORD", "rabbit_root");

    @AfterEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    @DisplayName("坐标齐全的旧编号补成 排-位-层，旧编号写进备注")
    void rewritesLegacyNumbers() throws SQLException {
        resetTo("30");
        long houseId = createHouse("补号兔舍");

        long appStyle = createCage(houseId, "2(下)1", "R2", 1, 1, null);
        long alreadyCanonical = createCage(houseId, "1-1-1", "R1", 1, 1, null);
        long withRemark = createCage(houseId, "2(上)2", "R2", 2, 2, "靠门第一个");
        long paddedRow = createCage(houseId, "老编号", "R02", 3, 1, null);

        migrateToLatest();

        assertEquals("2-1-1", cageNumber(appStyle));
        assertEquals("原编号 2(下)1", remark(appStyle));

        // 已经规范的不动，备注也不该被加上「原编号」
        assertEquals("1-1-1", cageNumber(alreadyCanonical));
        assertEquals(null, remark(alreadyCanonical));

        // 人写的备注要留着，旧编号往后缀
        assertEquals("2-2-2", cageNumber(withRemark));
        assertEquals("靠门第一个；原编号 2(上)2", remark(withRemark));

        // R02 和 R2 得算成同一排，不然会多出一排来
        assertEquals("2-3-1", cageNumber(paddedRow));
    }

    @Test
    @DisplayName("新号已被别的笼位占了就跳过，绝不覆盖")
    void skipsWhenTargetNumberIsTaken() throws SQLException {
        resetTo("30");
        long houseId = createHouse("撞号兔舍");

        // 占号方没有坐标，自己永远不会被改名，所以这个号会一直被占着
        long squatter = createCage(houseId, "1-2-1", "LEGACY", null, null, null);
        long wouldCollide = createCage(houseId, "旧-1-2-1", "R1", 2, 1, null);

        migrateToLatest();

        assertEquals("1-2-1", cageNumber(squatter));
        assertEquals("旧-1-2-1", cageNumber(wouldCollide), "撞号时必须原样不动");
    }

    @Test
    @DisplayName("被腾出来的号不在同一遍里捡走：宁可少改一个，也不做连锁改名")
    void doesNotChaseFreedNumbers() throws SQLException {
        resetTo("30");
        long houseId = createHouse("连锁兔舍");

        // 占号方自己也要改名（R9/9/9 -> 9-9-9），改完 1-2-1 就空出来了
        long squatter = createCage(houseId, "1-2-1", "R9", 9, 9, null);
        long waiting = createCage(houseId, "旧-1-2-1", "R1", 2, 1, null);

        migrateToLatest();

        assertEquals("9-9-9", cageNumber(squatter));
        // 判断撞号用的是迁移前的快照。连锁改名要靠排序才能不撞唯一键，
        // 排错一步就会中途报错、把整批回滚；剩这一两个留给人工处理更稳。
        assertEquals("旧-1-2-1", cageNumber(waiting));
    }

    @Test
    @DisplayName("两个笼位算出同一个新号时两个都跳过，留给人去查")
    void skipsDuplicateCoordinates() throws SQLException {
        resetTo("30");
        long houseId = createHouse("重坐标兔舍");

        long first = createCage(houseId, "甲", "R1", 1, 1, null);
        long second = createCage(houseId, "乙", "R1", 1, 1, null);

        migrateToLatest();

        // 自动挑一个改名只会把账做平，把坐标重复这个真问题埋掉
        assertEquals("甲", cageNumber(first));
        assertEquals("乙", cageNumber(second));
    }

    @Test
    @DisplayName("坐标不全的笼位不动：推不出编号就别瞎编")
    void skipsCagesWithoutCoordinates() throws SQLException {
        resetTo("30");
        long houseId = createHouse("散笼兔舍");

        long legacyRow = createCage(houseId, "隔离笼-A", "LEGACY", 1, 1, null);
        long noPosition = createCage(houseId, "隔离笼-B", "R1", null, 1, null);
        long noLayer = createCage(houseId, "隔离笼-C", "R1", 1, null, null);

        migrateToLatest();

        assertEquals("隔离笼-A", cageNumber(legacyRow));
        assertEquals("隔离笼-B", cageNumber(noPosition));
        assertEquals("隔离笼-C", cageNumber(noLayer));
    }

    @Test
    @DisplayName("改名不算「有人编辑过」：update_time 保持原值")
    void keepsUpdateTime() throws SQLException {
        resetTo("30");
        long houseId = createHouse("时间兔舍");
        long cageId = createCage(houseId, "3(下)1", "R3", 1, 1, null);
        execute("UPDATE cages SET update_time = '2020-01-02 03:04:05' WHERE id = ?", cageId);

        migrateToLatest();

        assertEquals("3-1-1", cageNumber(cageId));
        assertEquals(
                "2020-01-02 03:04:05",
                queryString("SELECT DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') FROM cages WHERE id = ?", cageId),
                "迁移不是人改的，不该把全场笼位都标成刚刚编辑过"
        );
    }

    private void resetTo(String version) {
        Flyway baseline = flyway(MigrationVersion.fromVersion(version));
        baseline.clean();
        baseline.migrate();
    }

    private void migrateToLatest() {
        flyway(null).migrate();
    }

    private long createHouse(String name) throws SQLException {
        return insertAndReturnId(
                "INSERT INTO rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                        + " VALUES (?, 1, 1, 1, 'test', 'test')",
                name
        );
    }

    private long createCage(long houseId, String cageNumber, String rowCode, Integer positionIndex,
                            Integer layerIndex, String remark) throws SQLException {
        return insertAndReturnId(
                "INSERT INTO cages (house_id, cage_number, row_code, position_index, layer_index, remark,"
                        + " create_by, update_by) VALUES (?, ?, ?, ?, ?, ?, 'test', 'test')",
                houseId, cageNumber, rowCode, positionIndex, layerIndex, remark
        );
    }

    private String cageNumber(long cageId) throws SQLException {
        return queryString("SELECT cage_number FROM cages WHERE id = ?", cageId);
    }

    private String remark(long cageId) throws SQLException {
        return queryString("SELECT remark FROM cages WHERE id = ?", cageId);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(URL, USERNAME, PASSWORD)
                .cleanDisabled(false);
        if (target != null) {
            configuration = configuration.target(target);
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

    private String queryString(String sql, Object... params) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                Object value = result.getObject(1);
                return value == null ? null : String.valueOf(value);
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
