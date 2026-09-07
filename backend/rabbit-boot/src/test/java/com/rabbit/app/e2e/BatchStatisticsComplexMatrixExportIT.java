package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbit.app.security.JwtUtil;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchStatisticsComplexMatrixExportIT extends E2eTestSupport {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void exportsAllScenariosAndEnforcesExportScope() throws Exception {
        BatchStatisticsComplexMatrixFixture.Fixture fixture =
                BatchStatisticsComplexMatrixFixture.load(jdbc);
        try {
            String ownerToken = jwtUtil.generateToken(fixture.owner().userId());
            String readOnlyToken = jwtUtil.generateToken(fixture.readOnly().userId());
            String outsiderToken = jwtUtil.generateToken(fixture.outsider().userId());
            BatchStatisticsComplexMatrixFixture.Scenario security =
                    fixture.scenario("security-and-retry");
            api.postOk(
                    carcassEndpoint(security.batchId()),
                    ownerToken,
                    fixture.houseId(),
                    carcassPayload("bsx-carcass-" + fixture.runId() + "-security-export")
            );

            for (BatchStatisticsComplexMatrixFixture.Scenario scenario
                    : fixture.scenarios().values()) {
                JsonNode statistics = api.getOk(
                        statisticsEndpoint(scenario.batchId()),
                        ownerToken,
                        fixture.houseId()
                );
                BatchStatisticsComplexMatrixFixture.assertStatistics(
                        statistics,
                        fixture,
                        scenario
                );
                E2eApiClient.Download download = api.download(
                        exportEndpoint(scenario.batchId()),
                        ownerToken,
                        fixture.houseId()
                );
                assertWorkbook(download, statistics, scenario);
            }

            api.expectError(
                    exportEndpoint(security.batchId()),
                    HttpMethod.GET,
                    ownerToken,
                    null,
                    null,
                    400,
                    "缺少X-House-Id"
            );
            api.expectError(
                    exportEndpoint(security.batchId()),
                    HttpMethod.GET,
                    outsiderToken,
                    fixture.isolationHouseId(),
                    null,
                    404,
                    "批次不存在"
            );
            JsonNode readOnlyStatistics = api.getOk(
                    statisticsEndpoint(security.batchId()),
                    readOnlyToken,
                    fixture.houseId()
            );
            BatchStatisticsComplexMatrixFixture.assertStatistics(
                    readOnlyStatistics,
                    fixture,
                    security
            );
            assertWorkbook(
                    api.download(
                            exportEndpoint(security.batchId()),
                            readOnlyToken,
                            fixture.houseId()
                    ),
                    readOnlyStatistics,
                    security
            );
        } finally {
            BatchStatisticsComplexMatrixFixture.cleanup(jdbc, fixture);
        }
    }

    private void assertWorkbook(
            E2eApiClient.Download download,
            JsonNode statistics,
            BatchStatisticsComplexMatrixFixture.Scenario scenario
    ) throws Exception {
        assertEquals(XLSX_MEDIA_TYPE, download.contentType.toString(), scenario.id());
        assertTrue(download.contentDisposition.contains(
                "filename=\"batch-" + scenario.batchCode() + "-statistics-"
        ));
        assertTrue(download.contentDisposition.contains(
                "filename*=UTF-8''%E6%89%B9%E6%AC%A1-" + scenario.batchCode()
                        + "-%E7%BB%9F%E8%AE%A1-"
        ));
        assertTrue(download.bytes.length > 0, scenario.id());
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(download.bytes)
        )) {
            assertEquals(2, workbook.getNumberOfSheets(), scenario.id());
            assertEquals("批次统计", workbook.getSheetName(0), scenario.id());
            assertEquals("口径与状态", workbook.getSheetName(1), scenario.id());
            assertFalse(workbook.isSheetHidden(0), scenario.id());
            assertFalse(workbook.isSheetHidden(1), scenario.id());
            Sheet summary = workbook.getSheetAt(0);
            Sheet detail = workbook.getSheetAt(1);
            Row header = summary.getRow(0);
            Row values = summary.getRow(1);
            assertEquals(31, header.getLastCellNum(), scenario.id());
            assertEquals(29, detail.getPhysicalNumberOfRows(), scenario.id());

            for (int index = 0; index < statistics.path("metrics").size(); index++) {
                JsonNode actualMetric = statistics.path("metrics").get(index);
                JsonNode expectedMetric = scenario.expected().path("metrics").get(index);
                String label = scenario.id() + ":" + expectedMetric.path("code").asText();
                int summaryColumn = index + 3;
                Row detailRow = detail.getRow(index + 1);
                assertEquals(actualMetric.path("excelColumnName").asText(),
                        header.getCell(summaryColumn).getStringCellValue(), label);
                assertEquals(expectedMetric.path("order").asInt(),
                        (int) detailRow.getCell(0).getNumericCellValue(), label);
                assertEquals(expectedMetric.path("code").asText(),
                        detailRow.getCell(1).getStringCellValue(), label);
                assertEquals(expectedMetric.path("status").asText(),
                        detailRow.getCell(7).getStringCellValue(), label);
                assertEquals(CellType.STRING, detailRow.getCell(6).getCellType(), label);
                JsonNode expectedDisplay = expectedMetric.get("displayValue");
                assertEquals(
                        expectedDisplay == null || expectedDisplay.isNull()
                                ? ""
                                : expectedDisplay.asText(),
                        detailRow.getCell(6).getStringCellValue(),
                        label
                );
                assertDetailCauses(expectedMetric, detailRow.getCell(12), label);
                assertSummaryValue(
                        expectedMetric,
                        actualMetric,
                        values.getCell(summaryColumn),
                        detailRow.getCell(5),
                        label
                );
            }
        }
    }

    private void assertSummaryValue(
            JsonNode expected,
            JsonNode actual,
            Cell summaryCell,
            Cell detailRawCell,
            String label
    ) {
        if (!"AVAILABLE".equals(expected.path("status").asText())) {
            assertEquals(CellType.STRING, summaryCell.getCellType(), label);
            assertEquals(statusText(expected.path("status").asText()),
                    summaryCell.getStringCellValue(), label);
            assertEquals(CellType.STRING, detailRawCell.getCellType(), label);
            assertTrue(detailRawCell.getStringCellValue().isEmpty(), label);
            return;
        }
        JsonNode dateValue = expected.get("dateValue");
        if (dateValue != null && !dateValue.isNull()) {
            if (dateValue.path("dateCount").asInt() == 1) {
                assertEquals(CellType.NUMERIC, summaryCell.getCellType(), label);
                assertEquals(LocalDate.parse(dateValue.path("firstDate").asText()),
                        summaryCell.getLocalDateTimeCellValue().toLocalDate(), label);
                assertEquals("yyyy-mm-dd", summaryCell.getCellStyle().getDataFormatString(), label);
            } else {
                assertEquals(CellType.STRING, summaryCell.getCellType(), label);
                assertEquals(expected.path("displayValue").asText(),
                        summaryCell.getStringCellValue(), label);
            }
            assertEquals(CellType.STRING, detailRawCell.getCellType(), label);
            String expectedRawDate = dateValue.path("dateCount").asInt() == 1
                    ? dateValue.path("firstDate").asText()
                    : dateValue.path("firstDate").asText() + "/"
                            + dateValue.path("lastDate").asText();
            assertEquals(expectedRawDate, detailRawCell.getStringCellValue(), label);
            return;
        }
        double expectedNumber = expected.path("numericValue").decimalValue().doubleValue();
        assertEquals(CellType.NUMERIC, summaryCell.getCellType(), label);
        assertEquals(expectedNumber, summaryCell.getNumericCellValue(), label);
        assertEquals(expectedNumber, detailRawCell.getNumericCellValue(), label);
        String numberFormat = expectedNumberFormat(actual.path("format").asText());
        assertEquals(numberFormat, summaryCell.getCellStyle().getDataFormatString(), label);
        assertEquals(numberFormat, detailRawCell.getCellStyle().getDataFormatString(), label);
    }

    private void assertDetailCauses(JsonNode expected, Cell cell, String label) {
        List<String> actualCodes = new ArrayList<>();
        String text = cell.getStringCellValue();
        if (!text.isBlank()) {
            for (String line : text.split("\\R")) {
                actualCodes.add(line.substring(0, line.indexOf(':')));
            }
        }
        List<String> expectedCodes = new ArrayList<>();
        expected.path("missingCauses").forEach(cause -> expectedCodes.add(cause.asText()));
        assertEquals(expectedCodes, actualCodes, label);
    }

    private Object carcassPayload(String requestId) {
        return obj(
                "yieldRate", new BigDecimal("0.580000"),
                "sourceUnit", "复杂矩阵测试场",
                "measuredDate", LocalDate.of(2024, 8, 1).toString(),
                "changeReason", "首次录入",
                "requestId", requestId
        );
    }

    private static String statusText(String status) {
        return switch (status) {
            case "NOT_APPLICABLE" -> "暂无可计算数据";
            case "NOT_RECORDED" -> "未录入";
            case "DATA_MISSING" -> "历史数据缺失";
            default -> throw new IllegalArgumentException("Unexpected status " + status);
        };
    }

    private static String expectedNumberFormat(String format) {
        return switch (format) {
            case "INTEGER" -> "#,##0";
            case "PERCENT_2" -> "0.00%";
            case "RATIO_TO_ONE" -> "#,##0.00\":1\"";
            case "DECIMAL_2" -> "#,##0.00";
            default -> throw new IllegalArgumentException("Unexpected format " + format);
        };
    }

    private static String statisticsEndpoint(long batchId) {
        return "/api/batches/" + batchId + "/statistics";
    }

    private static String exportEndpoint(long batchId) {
        return "/api/reports/batches/" + batchId + "/statistics.xlsx";
    }

    private static String carcassEndpoint(long batchId) {
        return "/api/batches/" + batchId + "/carcass-yields";
    }
}
