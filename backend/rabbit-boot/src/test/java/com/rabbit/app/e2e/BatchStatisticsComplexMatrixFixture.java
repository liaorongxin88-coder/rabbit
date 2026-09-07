package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

final class BatchStatisticsComplexMatrixFixture {
    private static final String LOAD_RESOURCE =
            "fixtures/batch_statistics_complex_matrix_fixture.sql";
    private static final String CLEANUP_RESOURCE =
            "fixtures/batch_statistics_complex_matrix_cleanup.sql";
    private static final String CATALOG_RESOURCE =
            "fixtures/batch_statistics_complex_matrix.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private BatchStatisticsComplexMatrixFixture() {
    }

    static Fixture load(JdbcTemplate jdbc) {
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        execute(jdbc, runId, LOAD_RESOURCE);
        JsonNode catalog = readCatalog();
        assertEquals(1, catalog.path("schemaVersion").asInt());
        assertEquals(6, catalog.path("scenarioCount").asInt());
        assertEquals(5, catalog.path("primaryScenarioCount").asInt());
        assertEquals(28, catalog.path("metricCount").asInt());

        Map<String, Scenario> scenarios = new LinkedHashMap<>();
        for (JsonNode expected : catalog.path("scenarios")) {
            String id = expected.path("id").asText();
            String requestId = "bsx-batch-" + runId + "-"
                    + expected.path("batchRequestSuffix").asText();
            Scenario scenario = jdbc.queryForObject(
                    "select id, batch_code from batches where house_id = "
                            + "(select id from rabbit_houses where request_id = ?) "
                            + "and request_id = ?",
                    (resultSet, rowNumber) -> new Scenario(
                            id,
                            expected.path("batchRole").asText(),
                            resultSet.getLong("id"),
                            resultSet.getString("batch_code"),
                            expected
                    ),
                    "bsx-house-" + runId + "-target",
                    requestId
            );
            assertNotNull(scenario, id);
            assertEquals(28, scenario.expected().path("metrics").size(), id);
            scenarios.put(id, scenario);
        }
        assertEquals(6, scenarios.size());

        return jdbc.queryForObject(
                "select h.id as house_id, h.name as house_name, ih.id as isolation_house_id, "
                        + "owner.user_id as owner_id, owner.user_name as owner_name, "
                        + "viewer.user_id as viewer_id, viewer.user_name as viewer_name, "
                        + "outsider.user_id as outsider_id, outsider.user_name as outsider_name "
                        + "from rabbit_houses h "
                        + "inner join rabbit_houses ih on ih.request_id = ? "
                        + "inner join sys_user owner on owner.user_name = ? "
                        + "inner join sys_user viewer on viewer.user_name = ? "
                        + "inner join sys_user outsider on outsider.user_name = ? "
                        + "where h.request_id = ?",
                (resultSet, rowNumber) -> new Fixture(
                        runId,
                        resultSet.getLong("house_id"),
                        resultSet.getString("house_name"),
                        resultSet.getLong("isolation_house_id"),
                        new Account(resultSet.getLong("owner_id"),
                                resultSet.getString("owner_name"), "OWNER"),
                        new Account(resultSet.getLong("viewer_id"),
                                resultSet.getString("viewer_name"), "READ_ONLY"),
                        new Account(resultSet.getLong("outsider_id"),
                                resultSet.getString("outsider_name"), "OUTSIDER"),
                        Collections.unmodifiableMap(new LinkedHashMap<>(scenarios))
                ),
                "bsx-house-" + runId + "-isolation",
                "bsx_" + runId + "_owner",
                "bsx_" + runId + "_viewer",
                "bsx_" + runId + "_outsider",
                "bsx-house-" + runId + "-target"
        );
    }

    static void cleanup(JdbcTemplate jdbc, Fixture fixture) {
        execute(jdbc, fixture.runId(), CLEANUP_RESOURCE);
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from sys_user where user_name in (?, ?, ?)",
                Integer.class,
                fixture.owner().userName(),
                fixture.readOnly().userName(),
                fixture.outsider().userName()
        ));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from rabbit_houses where "
                        + "(request_id = ? and create_by = ?) or "
                        + "(request_id = ? and create_by = ?)",
                Integer.class,
                "bsx-house-" + fixture.runId() + "-target",
                String.valueOf(fixture.owner().userId()),
                "bsx-house-" + fixture.runId() + "-isolation",
                String.valueOf(fixture.outsider().userId())
        ));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from batches where request_id like ? and create_by = ?",
                Integer.class,
                "bsx-batch-" + fixture.runId() + "-%",
                String.valueOf(fixture.owner().userId())
        ));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from request_dedup where house_id in (?, ?) "
                        + "or user_id in (?, ?, ?)",
                Integer.class,
                fixture.houseId(),
                fixture.isolationHouseId(),
                fixture.owner().userId(),
                fixture.readOnly().userId(),
                fixture.outsider().userId()
        ));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from repro_events where house_id in (?, ?) "
                        + "or operator_id in (?, ?, ?)",
                Integer.class,
                fixture.houseId(),
                fixture.isolationHouseId(),
                fixture.owner().userId(),
                fixture.readOnly().userId(),
                fixture.outsider().userId()
        ));
    }

    static void assertStatistics(JsonNode statistics, Fixture fixture, Scenario scenario) {
        JsonNode expected = scenario.expected();
        assertEquals(1, statistics.path("schemaVersion").asInt(), scenario.id());
        assertEquals(scenario.batchId(), statistics.path("batchId").asLong(), scenario.id());
        assertEquals(fixture.houseName(), statistics.path("houseName").asText(), scenario.id());
        assertEquals(scenario.batchCode(), statistics.path("batchCode").asText(), scenario.id());
        assertFalse(statistics.path("calculatedAt").asText().isBlank(), scenario.id());
        JsonNode totals = expected.path("legacyTotals");
        assertEquals(totals.path("totalLitters").asInt(), statistics.path("totalLitters").asInt(), scenario.id());
        assertEquals(totals.path("totalKits").asInt(), statistics.path("totalKits").asInt(), scenario.id());
        assertEquals(totals.path("totalLiveKits").asInt(), statistics.path("totalLiveKits").asInt(), scenario.id());
        assertEquals(totals.path("totalWeaned").asInt(), statistics.path("totalWeaned").asInt(), scenario.id());

        JsonNode actualMetrics = statistics.path("metrics");
        JsonNode expectedMetrics = expected.path("metrics");
        assertEquals(28, actualMetrics.size(), scenario.id());
        for (int index = 0; index < expectedMetrics.size(); index++) {
            JsonNode expectedMetric = expectedMetrics.get(index);
            JsonNode actualMetric = actualMetrics.get(index);
            String code = scenario.id() + ":" + expectedMetric.path("code").asText();
            assertEquals(expectedMetric.path("code").asText(), actualMetric.path("code").asText(), code);
            assertEquals(expectedMetric.path("order").asInt(), actualMetric.path("order").asInt(), code);
            assertEquals(expectedMetric.path("status").asText(), actualMetric.path("status").asText(), code);
            assertNullableText(expectedMetric.get("displayValue"), actualMetric.get("displayValue"), code);
            assertNumeric(expectedMetric.get("numericValue"), actualMetric.get("numericValue"), code);
            assertEquals(expectedMetric.get("dateValue"), actualMetric.get("dateValue"), code);
            assertCauseCodes(expectedMetric.path("missingCauses"), actualMetric.path("missingCauses"), code);
        }
    }

    private static void assertNullableText(JsonNode expected, JsonNode actual, String label) {
        if (expected == null || expected.isNull()) {
            assertTrue(actual == null || actual.isNull(), label);
            return;
        }
        assertEquals(expected.asText(), actual.asText(), label);
    }

    private static void assertNumeric(JsonNode expected, JsonNode actual, String label) {
        if (expected == null || expected.isNull()) {
            assertTrue(actual == null || actual.isNull(), label);
            return;
        }
        BigDecimal expectedValue = expected.decimalValue();
        assertNotNull(actual, label);
        assertTrue(actual.isNumber(), label);
        BigDecimal actualValue = actual.decimalValue();
        assertEquals(
                0,
                expectedValue.compareTo(actualValue),
                label + " expected=" + expectedValue + " actual=" + actualValue
        );
    }

    private static void assertCauseCodes(JsonNode expected, JsonNode actual, String label) {
        assertEquals(expected.size(), actual.size(), label);
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index).asText(), actual.get(index).path("code").asText(), label);
        }
    }

    private static JsonNode readCatalog() {
        Resource resource = new ClassPathResource(CATALOG_RESOURCE);
        if (!resource.exists()) {
            Path current = Path.of("").toAbsolutePath();
            while (current != null && !resource.exists()) {
                Path backendRootCandidate = current.resolve(
                        Path.of("src/test/resources", CATALOG_RESOURCE)
                );
                Path repositoryRootCandidate = current.resolve(
                        Path.of("backend/src/test/resources", CATALOG_RESOURCE)
                );
                if (Files.exists(backendRootCandidate)) {
                    resource = new FileSystemResource(backendRootCandidate);
                } else if (Files.exists(repositoryRootCandidate)) {
                    resource = new FileSystemResource(repositoryRootCandidate);
                }
                current = current.getParent();
            }
        }
        try {
            return MAPPER.readTree(resource.getInputStream());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read complex matrix catalog", exception);
        }
    }

    private static void execute(JdbcTemplate jdbc, String runId, String resource) {
        DataSource dataSource = jdbc.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("Complex matrix fixture requires a data source");
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "set @fixture_run_id = ?"
            )) {
                statement.setString(1, runId);
                statement.execute();
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to execute complex matrix resource " + resource, exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    record Fixture(
            String runId,
            long houseId,
            String houseName,
            long isolationHouseId,
            Account owner,
            Account readOnly,
            Account outsider,
            Map<String, Scenario> scenarios
    ) {
        Scenario scenario(String id) {
            Scenario scenario = scenarios.get(id);
            if (scenario == null) {
                throw new IllegalArgumentException("Unknown complex matrix scenario " + id);
            }
            return scenario;
        }
    }

    record Account(long userId, String userName, String role) {
    }

    record Scenario(
            String id,
            String batchRole,
            long batchId,
            String batchCode,
            JsonNode expected
    ) {
    }
}
