package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

final class BatchStatisticsAcceptanceFixture {
    static final LocalDate MATING_DATE = LocalDate.of(2024, 4, 22);

    private static final String RESOURCE =
            "fixtures/batch_statistics_acceptance_fixture.sql";
    private static final List<ExpectedMetric> EXPECTED_METRICS = List.of(
            date("MATING_DATE", "2024-04-22"),
            number("MATED_DOE_COUNT", decimal("1230"), "1,230"),
            number("CONCEPTION_RATE", decimal("0.8609756097560975"), "86.10%"),
            number("DOE_BUCK_RATIO", decimal("20.5"), "20.50:1"),
            number("PREGNANT_DOE_COUNT", decimal("1059"), "1,059"),
            number("ABORTION_RATE", decimal("0.019830028328611898"), "1.98%"),
            number("DELIVERED_LITTER_COUNT", decimal("1004"), "1,004"),
            number("TOTAL_KIT_COUNT", decimal("10040"), "10,040"),
            number("AVERAGE_KITS_PER_LITTER", decimal("10"), "10.00"),
            number("LIVE_KIT_COUNT", decimal("9870"), "9,870"),
            number("LIVE_BIRTH_RATE", decimal("0.9830677290836654"), "98.31%"),
            number("KEPT_LITTER_COUNT", decimal("987"), "987"),
            number("KEPT_KIT_COUNT", decimal("9490"), "9,490"),
            number("KEPT_LIVE_RATE", decimal("0.961499493414387"), "96.15%"),
            number("AVERAGE_KEPT_PER_LITTER", decimal("9.614994934143871"), "9.61"),
            number("WEANED_KIT_COUNT", decimal("8604"), "8,604"),
            number("AVERAGE_WEANING_WEIGHT", decimal("0.735"), "0.74 kg"),
            number("WEANING_SURVIVAL_RATE", decimal("0.9066385669125395"), "90.66%"),
            number("SOLD_RABBIT_COUNT", decimal("6834"), "6,834"),
            number("OUTBOUND_SURVIVAL_RATE", decimal("0.794281729428173"), "79.43%"),
            number("SOLD_WEIGHT", decimal("13095"), "13,095.00 kg"),
            number("AVERAGE_SOLD_WEIGHT", decimal("1.9161545215100966"), "1.92 kg"),
            number("TOTAL_SALES_AMOUNT", decimal("157140"), "157,140.00 元"),
            number("SALES_PRICE_PER_KG", decimal("12"), "12.00 元/kg"),
            number("SALES_PRICE_PER_RABBIT", decimal("22.99385425812116"), "22.99 元/只"),
            number("FULL_FEED_CONVERSION_RATIO", decimal("3.6846942382467303"), "3.68"),
            number(
                    "FATTENING_FEED_CONVERSION_RATIO",
                    decimal("3.8447473871828115"),
                    "3.84"
            ),
            number("CARCASS_YIELD_RATE", decimal("0.56"), "56.00%")
    );

    private BatchStatisticsAcceptanceFixture() {
    }

    static Fixture load(JdbcTemplate jdbc) {
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        DataSource dataSource = jdbc.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("Batch statistics fixture requires a data source");
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "set @fixture_run_id = ?"
            )) {
                statement.setString(1, runId);
                statement.execute();
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(RESOURCE));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize batch statistics fixture", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        return jdbc.queryForObject(
                "select u.user_id, u.user_name, h.id as house_id, h.name as house_name, "
                        + "b.id as batch_id, b.batch_code, ih.id as isolation_house_id, "
                        + "ib.id as isolation_batch_id "
                        + "from sys_user u "
                        + "inner join rabbit_houses h on h.request_id = ? "
                        + "inner join batches b on b.house_id = h.id and b.request_id = ? "
                        + "inner join rabbit_houses ih on ih.request_id = ? "
                        + "inner join batches ib on ib.house_id = ih.id and ib.request_id = ? "
                        + "where u.user_name = ?",
                (resultSet, rowNumber) -> new Fixture(
                        runId,
                        resultSet.getLong("user_id"),
                        resultSet.getString("user_name"),
                        resultSet.getLong("house_id"),
                        resultSet.getString("house_name"),
                        resultSet.getLong("batch_id"),
                        resultSet.getString("batch_code"),
                        resultSet.getLong("isolation_house_id"),
                        resultSet.getLong("isolation_batch_id")
                ),
                "bsf-house-" + runId + "-target",
                "bsf-batch-" + runId + "-target",
                "bsf-house-" + runId + "-isolation",
                "bsf-batch-" + runId + "-isolation",
                "bsf_" + runId + "_owner"
        );
    }

    static List<ExpectedMetric> expectedMetrics() {
        return EXPECTED_METRICS;
    }

    static void assertApiStatistics(JsonNode statistics, Fixture fixture) {
        assertEquals(1, statistics.get("schemaVersion").asInt());
        assertEquals(fixture.batchId(), statistics.get("batchId").asLong());
        assertEquals(fixture.houseName(), statistics.get("houseName").asText());
        assertEquals(fixture.batchCode(), statistics.get("batchCode").asText());
        assertFalse(statistics.get("calculatedAt").asText().isBlank());
        assertEquals(1004, statistics.get("totalLitters").asInt());
        assertEquals(10040, statistics.get("totalKits").asInt());
        assertEquals(9870, statistics.get("totalLiveKits").asInt());
        assertEquals(8604, statistics.get("totalWeaned").asInt());

        JsonNode metrics = statistics.get("metrics");
        assertEquals(EXPECTED_METRICS.size(), metrics.size());
        for (int index = 0; index < EXPECTED_METRICS.size(); index++) {
            ExpectedMetric expected = EXPECTED_METRICS.get(index);
            JsonNode actual = metrics.get(index);
            assertEquals(expected.code(), actual.get("code").asText());
            assertEquals((index + 1) * 10, actual.get("order").asInt());
            assertEquals("AVAILABLE", actual.get("status").asText());
            assertEquals(expected.displayValue(), actual.get("displayValue").asText());
            assertTrue(actual.get("missingCauses").isEmpty());
            if (expected.dateValue() != null) {
                assertTrue(actual.get("numericValue").isNull());
                JsonNode dateValue = actual.get("dateValue");
                assertEquals(expected.dateValue().toString(), dateValue.get("firstDate").asText());
                assertEquals(expected.dateValue().toString(), dateValue.get("lastDate").asText());
                assertEquals(1, dateValue.get("dateCount").asInt());
                assertEquals(1, dateValue.get("dailyCycleCounts").size());
                assertEquals(1230, dateValue.get("dailyCycleCounts").get(0)
                        .get("cycleCount").asInt());
            } else {
                assertTrue(actual.get("dateValue").isNull());
                BigDecimal actualValue = actual.get("numericValue").decimalValue();
                assertEquals(
                        0,
                        expected.numericValue().compareTo(actualValue),
                        expected.code() + " expected=" + expected.numericValue()
                                + " actual=" + actualValue
                );
            }
        }
    }

    private static ExpectedMetric date(String code, String displayValue) {
        return new ExpectedMetric(code, null, MATING_DATE, displayValue);
    }

    private static ExpectedMetric number(
            String code,
            BigDecimal numericValue,
            String displayValue
    ) {
        return new ExpectedMetric(code, numericValue, null, displayValue);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    record Fixture(
            String runId,
            long userId,
            String userName,
            long houseId,
            String houseName,
            long batchId,
            String batchCode,
            long isolationHouseId,
            long isolationBatchId
    ) {
    }

    record ExpectedMetric(
            String code,
            BigDecimal numericValue,
            LocalDate dateValue,
            String displayValue
    ) {
    }
}
