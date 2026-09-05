package com.rabbit.app.modules.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.dto.BatchStatistics.DailyCycleCount;
import com.rabbit.app.modules.batch.dto.BatchStatistics.DateRangeValue;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Metric;
import com.rabbit.app.modules.batch.dto.BatchStatistics.MissingCause;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Operand;
import com.rabbit.app.modules.report.service.BatchStatisticsWorkbookWriter.WorkbookSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class BatchStatisticsWorkbookWriterTest {
    private static final String HOUSE_NAME = "一号验收兔舍";
    private static final String BATCH_CODE = "EXPORT-A";
    private static final List<String> CODES = List.of(
            "MATING_DATE",
            "MATED_DOE_COUNT",
            "CONCEPTION_RATE",
            "DOE_BUCK_RATIO",
            "PREGNANT_DOE_COUNT",
            "ABORTION_RATE",
            "DELIVERED_LITTER_COUNT",
            "TOTAL_KIT_COUNT",
            "AVERAGE_KITS_PER_LITTER",
            "LIVE_KIT_COUNT",
            "LIVE_BIRTH_RATE",
            "KEPT_LITTER_COUNT",
            "KEPT_KIT_COUNT",
            "KEPT_LIVE_RATE",
            "AVERAGE_KEPT_PER_LITTER",
            "WEANED_KIT_COUNT",
            "AVERAGE_WEANING_WEIGHT",
            "WEANING_SURVIVAL_RATE",
            "SOLD_RABBIT_COUNT",
            "OUTBOUND_SURVIVAL_RATE",
            "SOLD_WEIGHT",
            "AVERAGE_SOLD_WEIGHT",
            "TOTAL_SALES_AMOUNT",
            "SALES_PRICE_PER_KG",
            "SALES_PRICE_PER_RABBIT",
            "FULL_FEED_CONVERSION_RATIO",
            "FATTENING_FEED_CONVERSION_RATIO",
            "CARCASS_YIELD_RATE"
    );
    private static final List<String> HEADERS = List.of(
            "配种日期",
            "配种母兔数",
            "受胎率",
            "配种母兔/公兔比例",
            "怀孕数量",
            "流产率",
            "产崽窝数",
            "产崽总数",
            "平均窝产数",
            "活崽总数",
            "平均活崽率",
            "选留窝数",
            "选留总数",
            "选留活崽率",
            "窝均选留",
            "断奶数量",
            "断奶均重",
            "断奶成活率",
            "出栏数量",
            "出栏成活率",
            "出栏总重",
            "出栏均重",
            "总销售金额",
            "销售单价（重量口径）",
            "销售单价（只数口径）",
            "全程料肉比",
            "育肥期料肉比",
            "出肉率"
    );
    private static final List<String> VALUES = List.of(
            "",
            "1230",
            "0.8609756097560975",
            "20.5",
            "1059",
            "0.019830028328611898",
            "1004",
            "10040",
            "10",
            "9870",
            "0.9830677290836654",
            "987",
            "9490",
            "0.961499493414387",
            "9.614994934143871",
            "8604",
            "0.735",
            "0.9066385669125395",
            "6834",
            "0.794281729428173",
            "13095",
            "1.9161545215100966",
            "157140",
            "12",
            "22.99385425812116",
            "3.6846942382467303",
            "3.8447473871828115",
            "0.56"
    );

    private final BatchStatisticsWorkbookWriter writer = new BatchStatisticsWorkbookWriter();

    @Test
    void writesTwoFilterableSheetsWithAllApprovedHeadersAndValues() throws Exception {
        WorkbookSnapshot snapshot = writer.prepare(availableFixture(), 91L, 101L);
        byte[] bytes = write(snapshot);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals(BatchStatisticsWorkbookWriter.SUMMARY_SHEET_NAME, workbook.getSheetName(0));
            assertEquals(BatchStatisticsWorkbookWriter.DETAIL_SHEET_NAME, workbook.getSheetName(1));
            assertFalse(workbook.isSheetHidden(0));
            assertFalse(workbook.isSheetHidden(1));

            Sheet summary = workbook.getSheetAt(0);
            assertEquals(31, summary.getRow(0).getLastCellNum());
            assertEquals(2, summary.getPhysicalNumberOfRows());
            assertEquals("兔舍", summary.getRow(0).getCell(0).getStringCellValue());
            assertEquals("批次编号", summary.getRow(0).getCell(1).getStringCellValue());
            assertEquals("统计时间", summary.getRow(0).getCell(2).getStringCellValue());
            for (int index = 0; index < HEADERS.size(); index++) {
                assertEquals(HEADERS.get(index), summary.getRow(0).getCell(index + 3).getStringCellValue());
            }
            assertNotNull(summary.getPaneInformation());
            assertTrue(summary.getPaneInformation().isFreezePane());
            assertEquals("A1:AE2", autoFilter(summary));

            Row values = summary.getRow(1);
            assertEquals(HOUSE_NAME, values.getCell(0).getStringCellValue());
            assertEquals(BATCH_CODE, values.getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC, values.getCell(2).getCellType());
            assertTrue(DateUtil.isCellDateFormatted(values.getCell(2)));
            assertEquals(CellType.NUMERIC, values.getCell(3).getCellType());
            assertTrue(DateUtil.isCellDateFormatted(values.getCell(3)));
            for (int index = 1; index < VALUES.size(); index++) {
                assertNumericCell(
                        values.getCell(index + 3),
                        new BigDecimal(VALUES.get(index)).doubleValue(),
                        excelFormat(index)
                );
            }

            Sheet detail = workbook.getSheetAt(1);
            assertEquals(29, detail.getPhysicalNumberOfRows());
            assertEquals(13, detail.getRow(0).getLastCellNum());
            List<String> detailHeaders = List.of(
                    "顺序",
                    "指标编码",
                    "阶段",
                    "指标名称",
                    "单位",
                    "原始值",
                    "展示值",
                    "状态",
                    "公式",
                    "分子",
                    "分母",
                    "组成项",
                    "缺失原因"
            );
            for (int index = 0; index < detailHeaders.size(); index++) {
                assertEquals(detailHeaders.get(index), detail.getRow(0).getCell(index).getStringCellValue());
            }
            assertNotNull(detail.getPaneInformation());
            assertTrue(detail.getPaneInformation().isFreezePane());
            assertEquals("A1:M29", autoFilter(detail));
            for (int index = 0; index < CODES.size(); index++) {
                Row row = detail.getRow(index + 1);
                assertEquals((index + 1) * 10D, row.getCell(0).getNumericCellValue());
                assertEquals(CODES.get(index), row.getCell(1).getStringCellValue());
                assertEquals(HEADERS.get(index), row.getCell(3).getStringCellValue());
                assertEquals("AVAILABLE", row.getCell(7).getStringCellValue());
                assertEquals("服务端口径:" + CODES.get(index), row.getCell(8).getStringCellValue());
                assertEquals(
                        index == 0
                                ? "2024-04-22"
                                : displayValue(index, new BigDecimal(VALUES.get(index))),
                        row.getCell(6).getStringCellValue()
                );
                if (index == 0) {
                    assertEquals("2024-04-22", row.getCell(5).getStringCellValue());
                } else {
                    assertNumericCell(
                            row.getCell(5),
                            new BigDecimal(VALUES.get(index)).doubleValue(),
                            excelFormat(index)
                    );
                }
            }
            assertEquals("日期", detail.getRow(1).getCell(4).getStringCellValue());
            assertEquals("只", detail.getRow(2).getCell(4).getStringCellValue());
            assertEquals("%", detail.getRow(3).getCell(4).getStringCellValue());
            assertEquals("确认怀孕周期数 [PREGNANT_CYCLES]: 1059 只",
                    detail.getRow(3).getCell(9).getStringCellValue());
            assertEquals("kg/只", detail.getRow(17).getCell(4).getStringCellValue());
            assertEquals("元/kg", detail.getRow(24).getCell(4).getStringCellValue());
            assertEquals("86.10%", detail.getRow(3).getCell(6).getStringCellValue());
            assertNoFormulaCells(workbook);
        }
    }

    @Test
    void writesMultipleMatingDatesAsTheServerDisplayRange() throws Exception {
        BatchStatistics fixture = availableFixture();
        List<Metric> metrics = new ArrayList<Metric>(fixture.metrics());
        Metric matingDate = metrics.get(0);
        DateRangeValue dateRange = new DateRangeValue(
                LocalDate.parse("2024-04-22"),
                LocalDate.parse("2024-04-24"),
                2,
                List.of(
                        new DailyCycleCount(LocalDate.parse("2024-04-22"), 700),
                        new DailyCycleCount(LocalDate.parse("2024-04-24"), 530)
                )
        );
        metrics.set(0, new Metric(
                matingDate.code(),
                matingDate.name(),
                matingDate.stage(),
                matingDate.stageName(),
                matingDate.order(),
                matingDate.excelColumnName(),
                matingDate.valueType(),
                matingDate.unit(),
                matingDate.format(),
                matingDate.formula(),
                matingDate.status(),
                null,
                "2024-04-22 至 2024-04-24（2个配种日）",
                dateRange,
                matingDate.numerator(),
                matingDate.denominator(),
                matingDate.components(),
                matingDate.missingCauses()
        ));

        byte[] bytes = write(writer.prepare(withMetrics(fixture, metrics), 91L, 101L));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell cell = workbook.getSheetAt(0).getRow(1).getCell(3);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("2024-04-22 至 2024-04-24（2个配种日）", cell.getStringCellValue());
            assertEquals(
                    "2024-04-22: 700 个周期\n2024-04-24: 530 个周期",
                    workbook.getSheetAt(1).getRow(1).getCell(11).getStringCellValue()
            );
        }
    }

    @Test
    void writesUnavailableStatusesAsTextAndKeepsAllMissingCausesInOrder() throws Exception {
        BatchStatistics fixture = availableFixture();
        List<Metric> metrics = new ArrayList<Metric>(fixture.metrics());
        metrics.set(2, unavailable(metrics.get(2), "NOT_APPLICABLE", List.of(
                new MissingCause("ZERO_DENOMINATOR", "分母为零")
        )));
        metrics.set(26, unavailable(metrics.get(26), "DATA_MISSING", List.of(
                new MissingCause("MISSING_FEED_ALLOCATION", "缺少饲料分配"),
                new MissingCause("MISSING_REPLACEMENT_WEIGHT", "缺少转后备重量")
        )));
        metrics.set(27, unavailable(metrics.get(27), "NOT_RECORDED", List.of(
                new MissingCause("CARCASS_YIELD_NOT_RECORDED", "未录入出肉率")
        )));
        BatchStatistics statuses = withMetrics(fixture, metrics);

        byte[] bytes = write(writer.prepare(statuses, 91L, 101L));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet summary = workbook.getSheetAt(0);
            assertEquals("暂无可计算数据", summary.getRow(1).getCell(5).getStringCellValue());
            assertEquals("历史数据缺失", summary.getRow(1).getCell(29).getStringCellValue());
            assertEquals("未录入", summary.getRow(1).getCell(30).getStringCellValue());

            Sheet detail = workbook.getSheetAt(1);
            assertEquals("", detail.getRow(3).getCell(5).getStringCellValue());
            assertEquals("NOT_APPLICABLE", detail.getRow(3).getCell(7).getStringCellValue());
            assertEquals("DATA_MISSING", detail.getRow(27).getCell(7).getStringCellValue());
            assertEquals(
                    "MISSING_FEED_ALLOCATION: 缺少饲料分配\n"
                            + "MISSING_REPLACEMENT_WEIGHT: 缺少转后备重量",
                    detail.getRow(27).getCell(12).getStringCellValue()
            );
            assertEquals("NOT_RECORDED", detail.getRow(28).getCell(7).getStringCellValue());
        }
    }

    @Test
    void rejectsIncompleteUnorderedOrWrongBatchContractsBeforeStreaming() {
        BatchStatistics fixture = availableFixture();
        BizException missingMetric = assertThrows(
                BizException.class,
                () -> writer.prepare(withMetrics(fixture, fixture.metrics().subList(0, 27)), 91L, 101L)
        );
        assertEquals(500, missingMetric.getCode());
        assertEquals("批次统计导出数据不完整", missingMetric.getMessage());

        List<Metric> unordered = new ArrayList<Metric>(fixture.metrics());
        Metric second = unordered.get(1);
        unordered.set(1, copyMetric(second, second.status(), second.numericValue(), second.dateValue(),
                second.missingCauses(), 10));
        assertThrows(BizException.class, () -> writer.prepare(withMetrics(fixture, unordered), 91L, 101L));

        List<Metric> missingValue = new ArrayList<Metric>(fixture.metrics());
        Metric count = missingValue.get(1);
        missingValue.set(1, copyMetric(
                count,
                count.status(),
                null,
                count.dateValue(),
                count.missingCauses(),
                count.order()
        ));
        assertThrows(BizException.class, () -> writer.prepare(withMetrics(fixture, missingValue), 91L, 101L));

        List<Metric> unknownFormat = new ArrayList<Metric>(fixture.metrics());
        Metric countWithUnknownFormat = unknownFormat.get(1);
        unknownFormat.set(1, copyShape(
                countWithUnknownFormat,
                countWithUnknownFormat.valueType(),
                "UNKNOWN",
                countWithUnknownFormat.numericValue(),
                countWithUnknownFormat.dateValue()
        ));
        assertThrows(BizException.class, () -> writer.prepare(withMetrics(fixture, unknownFormat), 91L, 101L));

        List<Metric> swappedValueType = new ArrayList<Metric>(fixture.metrics());
        Metric countAsDate = swappedValueType.get(1);
        swappedValueType.set(1, copyShape(
                countAsDate,
                "DATE_RANGE",
                "DATE_RANGE",
                null,
                fixture.metrics().get(0).dateValue()
        ));
        assertThrows(BizException.class, () -> writer.prepare(withMetrics(fixture, swappedValueType), 91L, 101L));
        assertThrows(BizException.class, () -> writer.prepare(withMetadata(fixture, " ", BATCH_CODE), 91L, 101L));
        assertThrows(BizException.class, () -> writer.prepare(withMetadata(fixture, HOUSE_NAME, " "), 91L, 101L));
        assertThrows(BizException.class, () -> writer.prepare(fixture, 91L, 102L));
        assertThrows(BizException.class, () -> writer.prepare(
                new BatchStatistics(
                        2,
                        fixture.batchId(),
                        fixture.houseName(),
                        fixture.batchCode(),
                        fixture.calculatedAt(),
                        fixture.totalLitters(),
                        fixture.totalKits(),
                        fixture.totalLiveKits(),
                        fixture.totalWeaned(),
                        fixture.metrics()
                ),
                91L,
                101L
        ));
    }

    @Test
    void buildsSanitizedUtf8AndAsciiFilenamesFromTheBatchCode() {
        WorkbookSnapshot snapshot = writer.prepare(availableFixture(), 91L, 101L);

        assertEquals("批次-EXPORT-A-统计-20260904032000.xlsx", snapshot.utf8Filename());
        assertEquals("batch-EXPORT-A-statistics-20260904032000.xlsx", snapshot.asciiFilename());

        BatchStatistics unsafe = withMetadata(availableFixture(), HOUSE_NAME, "EXPORT/A\r\n*");
        WorkbookSnapshot sanitized = writer.prepare(unsafe, 91L, 101L);

        assertEquals("EXPORT/A\r\n*", sanitized.batchCode());
        assertEquals("批次-EXPORT_A_-统计-20260904032000.xlsx", sanitized.utf8Filename());
        assertEquals("batch-EXPORT_A-statistics-20260904032000.xlsx", sanitized.asciiFilename());
        assertFalse(sanitized.utf8Filename().contains("/"));
        assertFalse(sanitized.utf8Filename().contains("\r"));
        assertFalse(sanitized.utf8Filename().contains("\n"));
    }

    private byte[] write(WorkbookSnapshot snapshot) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(snapshot, output);
        return output.toByteArray();
    }

    private BatchStatistics availableFixture() {
        List<Metric> metrics = new ArrayList<Metric>();
        for (int index = 0; index < CODES.size(); index++) {
            metrics.add(metric(index));
        }
        return new BatchStatistics(
                1,
                101L,
                HOUSE_NAME,
                BATCH_CODE,
                Instant.parse("2026-09-04T03:20:00Z"),
                1004,
                10040,
                9870,
                8604,
                metrics
        );
    }

    private Metric metric(int index) {
        String code = CODES.get(index);
        DateRangeValue dateValue = null;
        BigDecimal numericValue = null;
        String displayValue;
        if (index == 0) {
            dateValue = new DateRangeValue(
                    LocalDate.parse("2024-04-22"),
                    LocalDate.parse("2024-04-22"),
                    1,
                    List.of(new DailyCycleCount(LocalDate.parse("2024-04-22"), 1230))
            );
            displayValue = "2024-04-22";
        } else {
            numericValue = new BigDecimal(VALUES.get(index));
            displayValue = displayValue(index, numericValue);
        }
        Operand numerator = index == 2
                ? new Operand("PREGNANT_CYCLES", "确认怀孕周期数", BigDecimal.valueOf(1059), "COUNT")
                : null;
        Operand denominator = index == 2
                ? new Operand("MATED_CYCLES", "已配种周期数", BigDecimal.valueOf(1230), "COUNT")
                : null;
        return new Metric(
                code,
                HEADERS.get(index),
                stage(index),
                stageName(index),
                (index + 1) * 10,
                HEADERS.get(index),
                index == 0 ? "DATE_RANGE" : "NUMBER",
                unit(index),
                format(index),
                "服务端口径:" + code,
                "AVAILABLE",
                numericValue,
                displayValue,
                dateValue,
                numerator,
                denominator,
                List.of(),
                List.of()
        );
    }

    private Metric unavailable(Metric metric, String status, List<MissingCause> causes) {
        return copyMetric(metric, status, null, null, causes, metric.order());
    }

    private Metric copyShape(
            Metric metric,
            String valueType,
            String format,
            BigDecimal numericValue,
            DateRangeValue dateValue
    ) {
        return new Metric(
                metric.code(),
                metric.name(),
                metric.stage(),
                metric.stageName(),
                metric.order(),
                metric.excelColumnName(),
                valueType,
                metric.unit(),
                format,
                metric.formula(),
                metric.status(),
                numericValue,
                metric.displayValue(),
                dateValue,
                metric.numerator(),
                metric.denominator(),
                metric.components(),
                metric.missingCauses()
        );
    }

    private Metric copyMetric(
            Metric metric,
            String status,
            BigDecimal numericValue,
            DateRangeValue dateValue,
            List<MissingCause> causes,
            int order
    ) {
        return new Metric(
                metric.code(),
                metric.name(),
                metric.stage(),
                metric.stageName(),
                order,
                metric.excelColumnName(),
                metric.valueType(),
                metric.unit(),
                metric.format(),
                metric.formula(),
                status,
                numericValue,
                status.equals("AVAILABLE") ? metric.displayValue() : null,
                dateValue,
                metric.numerator(),
                metric.denominator(),
                metric.components(),
                causes
        );
    }

    private BatchStatistics withMetadata(
            BatchStatistics source,
            String houseName,
            String batchCode
    ) {
        return new BatchStatistics(
                source.schemaVersion(),
                source.batchId(),
                houseName,
                batchCode,
                source.calculatedAt(),
                source.totalLitters(),
                source.totalKits(),
                source.totalLiveKits(),
                source.totalWeaned(),
                source.metrics()
        );
    }

    private BatchStatistics withMetrics(BatchStatistics source, List<Metric> metrics) {
        return new BatchStatistics(
                source.schemaVersion(),
                source.batchId(),
                source.houseName(),
                source.batchCode(),
                source.calculatedAt(),
                source.totalLitters(),
                source.totalKits(),
                source.totalLiveKits(),
                source.totalWeaned(),
                metrics
        );
    }

    private String stage(int index) {
        if (index <= 3) {
            return "MATING";
        }
        if (index <= 5) {
            return "PREGNANCY";
        }
        if (index <= 10) {
            return "BIRTH";
        }
        if (index <= 14) {
            return "SELECTION";
        }
        if (index <= 17) {
            return "WEANING";
        }
        if (index <= 21) {
            return "OUTBOUND";
        }
        if (index <= 24) {
            return "SALES";
        }
        return "FEED_CONVERSION";
    }

    private String stageName(int index) {
        return switch (stage(index)) {
            case "MATING" -> "配种";
            case "PREGNANCY" -> "怀孕";
            case "BIRTH" -> "产崽";
            case "SELECTION" -> "选留";
            case "WEANING" -> "断奶";
            case "OUTBOUND" -> "出栏";
            case "SALES" -> "销售";
            default -> "料肉比";
        };
    }

    private String format(int index) {
        if (index == 0) {
            return "DATE_RANGE";
        }
        if (List.of(1, 4, 6, 7, 9, 11, 12, 15, 18).contains(index)) {
            return "INTEGER";
        }
        if (List.of(2, 5, 10, 13, 17, 19, 27).contains(index)) {
            return "PERCENT_2";
        }
        if (index == 3) {
            return "RATIO_TO_ONE";
        }
        return "DECIMAL_2";
    }

    private String unit(int index) {
        return switch (index) {
            case 0 -> "DATE";
            case 6, 11 -> "LITTER";
            case 8, 14 -> "COUNT_PER_LITTER";
            case 16, 21 -> "KG_PER_RABBIT";
            case 20 -> "KG";
            case 22 -> "CNY";
            case 23 -> "CNY_PER_KG";
            case 24 -> "CNY_PER_RABBIT";
            case 2, 5, 10, 13, 17, 19, 27 -> "PERCENT";
            case 3, 25, 26 -> "RATIO";
            default -> "COUNT";
        };
    }

    private String displayValue(int index, BigDecimal value) {
        if (format(index).equals("PERCENT_2")) {
            return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
        }
        if (format(index).equals("INTEGER")) {
            return value.toBigInteger().toString();
        }
        if (format(index).equals("RATIO_TO_ONE")) {
            return value.setScale(2, RoundingMode.HALF_UP) + ":1";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String excelFormat(int index) {
        if (format(index).equals("PERCENT_2")) {
            return "0.00%";
        }
        if (format(index).equals("INTEGER")) {
            return "#,##0";
        }
        if (format(index).equals("RATIO_TO_ONE")) {
            return "#,##0.00\":1\"";
        }
        return "#,##0.00";
    }

    private String autoFilter(Sheet sheet) {
        return ((XSSFSheet) sheet).getCTWorksheet().getAutoFilter().getRef();
    }

    private void assertNoFormulaCells(XSSFWorkbook workbook) {
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    assertFalse(cell.getCellType() == CellType.FORMULA);
                }
            }
        }
    }

    private void assertNumericCell(Cell cell, double value, String format) {
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertEquals(value, cell.getNumericCellValue(), 0.000000000001D);
        assertEquals(format, cell.getCellStyle().getDataFormatString());
    }
}
