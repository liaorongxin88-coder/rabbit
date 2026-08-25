package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

class V43WeaningAllocationSexIT {
    private static final String URL = env(
        "E2E_MIGRATION_DATASOURCE_URL",
        "jdbc:mysql://localhost:3306/rabbit_app_e2e_v43?createDatabaseIfNotExist=true"
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
    void keepsHistoricalAllocationSexUnknownAndAddsReplayPayload() throws SQLException {
        Flyway beforeV43 = flyway(MigrationVersion.fromVersion("42"));
        beforeV43.clean();
        beforeV43.migrate();

        long houseId = insert(
            "insert into rabbit_houses (name, layout_rows, layout_cols, layout_layers, create_by, update_by)"
                + " values ('v43 house', 1, 2, 1, 'test', 'test')"
        );
        long cageId = insert(
            "insert into cages (house_id, cage_number, status, rabbit_count, create_by, update_by)"
                + " values (?, 'V43-C', '3', 2, 'test', 'test')",
            houseId
        );
        long motherId = insert(
            "insert into rabbits (house_id, cage_id, type, gender, arrival_method, state_version,"
                + " is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, '0', '0', '1', 0, true, false, 'v43-mother', 'test', 'test')",
            houseId,
            cageId
        );
        long batchId = insert(
            "insert into batches (house_id, batch_code, status, request_id, create_by, update_by)"
                + " values (?, 'V43-B', '进行中', 'v43-batch', 'test', 'test')",
            houseId
        );
        long recordId = insert(
            "insert into weaning_records (house_id, batch_id, breeding_cycle_id, rabbit_id,"
                + " weaning_count, waiting_count, male_count, female_count, create_by, update_by)"
                + " values (?, ?, 4301, ?, 4, 2, 2, 2, 'test', 'test')",
            houseId,
            batchId,
            motherId
        );
        insert(
            "insert into weaning_record_allocations (weaning_record_id, cage_id, alloc_count)"
                + " values (?, ?, 2)",
            recordId,
            cageId
        );
        insert(
            "insert into rabbits (house_id, cage_id, birth_batch_id, birth_cycle_id, type, gender,"
                + " arrival_method, state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, ?, 4301, '2', '1', '1', 0, true, false, 'v43-kit-m', 'test', 'test')",
            houseId,
            cageId,
            batchId
        );
        insert(
            "insert into rabbits (house_id, cage_id, birth_batch_id, birth_cycle_id, type, gender,"
                + " arrival_method, state_version, is_active, is_quarantined, request_id, create_by, update_by)"
                + " values (?, ?, ?, 4301, '2', '0', '1', 0, true, false, 'v43-kit-f', 'test', 'test')",
            houseId,
            cageId,
            batchId
        );

        flyway(null).migrate();

        assertNull(nullableIntValue(
            "select male_count from weaning_record_allocations where weaning_record_id = ?",
            recordId
        ));
        assertNull(nullableIntValue(
            "select female_count from weaning_record_allocations where weaning_record_id = ?",
            recordId
        ));

        insert(
            "insert into request_dedup (house_id, user_id, api, request_id, payload_hash, status,"
                + " response_payload) values (?, 43, 'batch.weaning.separate', 'v43-replay',"
                + " 'hash', 'DONE', json_object('waitingCount', 2))",
            houseId
        );
        assertEquals(
            2,
            intValue(
                "select json_unquote(json_extract(response_payload, '$.waitingCount'))"
                    + " from request_dedup where request_id = 'v43-replay'"
            )
        );
    }

    private long insert(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 sql, Statement.RETURN_GENERATED_KEYS
             )) {
            bind(statement, values);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private int intValue(String sql, Object... values) throws SQLException {
        Integer value = nullableIntValue(sql, values);
        return value == null ? 0 : value;
    }

    private Integer nullableIntValue(String sql, Object... values) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                int value = rows.getInt(1);
                return rows.wasNull() ? null : value;
            }
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
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
