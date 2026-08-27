package com.rabbit.app.e2e;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * E2E 用例之间的数据库重置。
 *
 * <p>原来每个 {@code @Test} 前都跑 {@code flyway.clean()} 加 {@code flyway.migrate()}。
 * 46 个迁移每次约 2.6 秒，主库 204 个用例合计约 10 分钟，占 E2E 总时长七成，
 * 而每次重建出来的 schema 完全一样。
 *
 * <p>改成每个 JVM 建一次 schema，用例之间只清数据：先一次往返探测哪些表非空，
 * 再只 TRUNCATE 这些表。选 TRUNCATE 而不是 DELETE，是因为它会把 AUTO_INCREMENT
 * 归位，用例看到的 id 序列和「刚建好的库」一致，断言口径不变。
 *
 * <p>迁移里的种子数据（当前是 V7 和 V10 连锁产生的一行 {@code sys_user}）在建库后
 * 快照下来，每次重置后原样写回。快照是通用的，以后哪个迁移新增种子行也会自动跟上。
 *
 * <p>出问题时可以用 {@code -De2e.reset=migrate} 切回旧行为对照，确认是不是重置方式
 * 的锅。
 */
final class E2eDatabaseReset {
    private static final String MODE_PROPERTY = "e2e.reset";
    private static final String MODE_MIGRATE = "migrate";

    private static final Object LOCK = new Object();
    private static final Map<String, Baseline> BASELINES = new HashMap<String, Baseline>();

    private E2eDatabaseReset() {
    }

    static void reset(DataSource dataSource, Flyway flyway) {
        if (MODE_MIGRATE.equals(System.getProperty(MODE_PROPERTY))) {
            flyway.clean();
            flyway.migrate();
            return;
        }
        try {
            Baseline baseline = baselineFor(dataSource, flyway);
            if (baseline == null) {
                // 刚建好的库本身就是基线，不必再清一次。
                return;
            }
            restore(dataSource, baseline);
        } catch (SQLException e) {
            throw new IllegalStateException("E2E 数据库重置失败", e);
        }
    }

    /**
     * 返回当前库的基线；若本次调用刚把 schema 建好，返回 {@code null} 表示无需重置。
     */
    private static Baseline baselineFor(DataSource dataSource, Flyway flyway) throws SQLException {
        String catalog = catalogOf(dataSource);
        synchronized (LOCK) {
            Baseline known = BASELINES.get(catalog);
            if (known != null) {
                return known;
            }
            flyway.clean();
            flyway.migrate();
            BASELINES.put(catalog, snapshot(dataSource, flyway));
            return null;
        }
    }

    private static String catalogOf(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.isBlank()) {
                throw new IllegalStateException("E2E 数据源没有选定数据库，无法隔离基线");
            }
            return catalog;
        }
    }

    private static Baseline snapshot(DataSource dataSource, Flyway flyway) throws SQLException {
        String historyTable = flyway.getConfiguration().getTable();
        List<String> tables = new ArrayList<String>();
        Map<String, Seed> seeds = new LinkedHashMap<String, Seed>();

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables"
                         + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
                         + " ORDER BY table_name")) {
                while (rs.next()) {
                    String table = rs.getString(1);
                    if (!table.equalsIgnoreCase(historyTable)) {
                        tables.add(table);
                    }
                }
            }
            if (tables.isEmpty()) {
                throw new IllegalStateException("迁移后一张业务表都没有，E2E 数据源多半指错了库");
            }
            for (String table : tables) {
                Seed seed = readSeed(connection, table);
                if (seed != null) {
                    seeds.put(table, seed);
                }
            }
        }
        return new Baseline(tables, seeds);
    }

    private static Seed readSeed(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM `" + table + "`")) {
            int columnCount = rs.getMetaData().getColumnCount();
            List<String> columns = new ArrayList<String>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(rs.getMetaData().getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<List<Object>>();
            while (rs.next()) {
                List<Object> row = new ArrayList<Object>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }
            return rows.isEmpty() ? null : new Seed(columns, rows);
        }
    }

    private static void restore(DataSource dataSource, Baseline baseline) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            List<String> dirty = dirtyTables(connection, baseline);
            if (dirty.isEmpty()) {
                return;
            }
            truncate(connection, dirty);
            reinsertSeeds(connection, baseline, dirty);
        }
    }

    private static List<String> dirtyTables(Connection connection, Baseline baseline) throws SQLException {
        List<String> tables = baseline.tables();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < tables.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("EXISTS(SELECT 1 FROM `").append(tables.get(i)).append("`)");
        }

        List<String> dirty = new ArrayList<String>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql.toString())) {
            if (!rs.next()) {
                throw new IllegalStateException("非空探测没有返回结果");
            }
            for (int i = 0; i < tables.size(); i++) {
                if (rs.getBoolean(i + 1)) {
                    dirty.add(tables.get(i));
                }
            }
        }

        // 有种子行的表一律重置。用例可能把种子行删了，那样探测结果是「空」，
        // 只看探测就会漏掉写回这一步。
        for (String seeded : baseline.seeds().keySet()) {
            if (!dirty.contains(seeded)) {
                dirty.add(seeded);
            }
        }
        return dirty;
    }

    private static void truncate(Connection connection, List<String> tables) throws SQLException {
        // 外键开关是会话级的，所以整批必须走同一个连接；连接还回池之前一定要开回来，
        // 否则会污染后面拿到它的用例。
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) {
                    statement.addBatch("TRUNCATE TABLE `" + table + "`");
                }
                statement.executeBatch();
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private static void reinsertSeeds(Connection connection, Baseline baseline, List<String> truncated)
            throws SQLException {
        for (Map.Entry<String, Seed> entry : baseline.seeds().entrySet()) {
            if (!truncated.contains(entry.getKey())) {
                continue;
            }
            insertSeed(connection, entry.getKey(), entry.getValue());
        }
    }

    private static void insertSeed(Connection connection, String table, Seed seed) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(table).append("` (");
        for (int i = 0; i < seed.columns().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('`').append(seed.columns().get(i)).append('`');
        }
        sql.append(") VALUES (");
        for (int i = 0; i < seed.columns().size(); i++) {
            sql.append(i > 0 ? ", ?" : "?");
        }
        sql.append(')');

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (List<Object> row : seed.rows()) {
                for (int i = 0; i < row.size(); i++) {
                    statement.setObject(i + 1, row.get(i));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private record Baseline(List<String> tables, Map<String, Seed> seeds) {
    }

    private record Seed(List<String> columns, List<List<Object>> rows) {
    }
}
