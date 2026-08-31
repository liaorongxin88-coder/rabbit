package com.rabbit.app.modules.report.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardReportMapperContractTest {
    private static final String RESOURCE = "mapper/modules/report/DashboardReportMapper.xml";

    @Test
    void everyDashboardStatisticSupportsTheSameBatchScope() throws IOException {
        String mapper = mapperXml();
        List<String> statementIds = List.of(
            "selectRabbitStats",
            "countActiveBreedingMothers",
            "selectBreedingSummary",
            "sumCurrentNursingKits",
            "selectMonthlyBirths",
            "selectMonthlyWeaned"
        );

        for (String statementId : statementIds) {
            String statement = selectStatement(mapper, statementId);
            assertTrue(
                statement.contains("batchId"),
                statementId + " must apply the dashboard batch scope"
            );
        }
    }

    private static String mapperXml() throws IOException {
        try (InputStream stream = DashboardReportMapperContractTest.class
                .getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            assertNotNull(stream, RESOURCE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String selectStatement(String mapper, String statementId) {
        String opening = "<select id=\"" + statementId + "\"";
        int start = mapper.indexOf(opening);
        assertTrue(start >= 0, statementId + " statement is missing");
        int end = mapper.indexOf("</select>", start);
        assertTrue(end > start, statementId + " statement is incomplete");
        return mapper.substring(start, end);
    }
}
