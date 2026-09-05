package com.rabbit.app.modules.report.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.dto.BatchStatistics.DateRangeValue;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Metric;
import com.rabbit.app.modules.batch.dto.BatchStatistics.MissingCause;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Operand;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class BatchStatisticsWorkbookWriter {
    public static final String SUMMARY_SHEET_NAME = "批次统计";
    public static final String DETAIL_SHEET_NAME = "口径与状态";
    public static final int METRIC_COUNT = 28;

    private static final int ROW_ACCESS_WINDOW_SIZE = 20;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);
    private static final Set<String> STATUSES = Set.of(
            "AVAILABLE",
            "NOT_APPLICABLE",
            "NOT_RECORDED",
            "DATA_MISSING"
    );
    private static final Set<String> FORMATS = Set.of(
            "DATE_RANGE",
            "INTEGER",
            "PERCENT_2",
            "RATIO_TO_ONE",
            "DECIMAL_2"
    );
    private static final List<String> EXPECTED_CODES = List.of(
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
    private static final String[] DETAIL_HEADERS = {
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
    };

    public WorkbookSnapshot prepare(BatchStatistics statistics, Long houseId, Long batchId) {
        if (statistics == null
                || statistics.schemaVersion() != 1
                || houseId == null
                || houseId <= 0
                || statistics.batchId() == null
                || batchId == null
                || batchId <= 0
                || !statistics.batchId().equals(batchId)
                || isBlank(statistics.houseName())
                || isBlank(statistics.batchCode())
                || statistics.calculatedAt() == null) {
            throw invalidContract();
        }
        validateMetrics(statistics.metrics());

        String timestamp = FILE_TIMESTAMP.format(statistics.calculatedAt());
        String utf8BatchCode = sanitizeUtf8FilenameComponent(statistics.batchCode());
        String asciiBatchCode = sanitizeAsciiFilenameComponent(statistics.batchCode());
        return new WorkbookSnapshot(
                statistics.metrics(),
                statistics.houseName(),
                statistics.batchCode(),
                statistics.calculatedAt(),
                "批次-" + utf8BatchCode + "-统计-" + timestamp + ".xlsx",
                "batch-" + asciiBatchCode + "-statistics-" + timestamp + ".xlsx"
        );
    }

    public void write(WorkbookSnapshot snapshot, OutputStream outputStream) throws IOException {
        if (snapshot == null || outputStream == null) {
            throw new IllegalArgumentException("snapshot and outputStream are required");
        }
        validateMetrics(snapshot.metrics());

        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Styles styles = createStyles(workbook);
            writeSummarySheet(workbook, snapshot, styles);
            writeDetailSheet(workbook, snapshot.metrics(), styles);
            workbook.write(outputStream);
            outputStream.flush();
        } finally {
            workbook.dispose();
        }
    }

    private void writeSummarySheet(SXSSFWorkbook workbook, WorkbookSnapshot snapshot, Styles styles) {
        Sheet sheet = workbook.createSheet(SUMMARY_SHEET_NAME);
        Row header = sheet.createRow(0);
        createTextCell(header, 0, "兔舍", styles.header());
        createTextCell(header, 1, "批次编号", styles.header());
        createTextCell(header, 2, "统计时间", styles.header());

        int columnIndex = 3;
        for (Metric metric : snapshot.metrics()) {
            createTextCell(header, columnIndex++, metric.excelColumnName(), styles.header());
        }

        Row values = sheet.createRow(1);
        createTextCell(values, 0, snapshot.houseName(), styles.text());
        createTextCell(values, 1, snapshot.batchCode(), styles.text());
        Cell calculatedAtCell = values.createCell(2);
        calculatedAtCell.setCellValue(GregorianCalendar.from(snapshot.calculatedAt().atZone(ZoneOffset.UTC)));
        calculatedAtCell.setCellStyle(styles.dateTime());

        columnIndex = 3;
        for (Metric metric : snapshot.metrics()) {
            writeSummaryMetric(values, columnIndex++, metric, styles);
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 1, 0, columnIndex - 1));
        setSummaryColumnWidths(sheet, snapshot.metrics());
    }

    private void writeSummaryMetric(Row row, int columnIndex, Metric metric, Styles styles) {
        if (!"AVAILABLE".equals(metric.status())) {
            createTextCell(row, columnIndex, statusText(metric.status()), styles.text());
            return;
        }

        if ("DATE_RANGE".equals(metric.valueType())) {
            writeDateRangeCell(row, columnIndex, metric, styles);
            return;
        }
        if (!"NUMBER".equals(metric.valueType()) || metric.numericValue() == null) {
            throw invalidContract();
        }
        writeNumericCell(row, columnIndex, metric.numericValue(), metric.format(), styles);
    }

    private void writeDateRangeCell(Row row, int columnIndex, Metric metric, Styles styles) {
        DateRangeValue dateValue = metric.dateValue();
        if (dateValue == null || dateValue.firstDate() == null || dateValue.lastDate() == null) {
            throw invalidContract();
        }
        if (dateValue.dateCount() == 1 && dateValue.firstDate().equals(dateValue.lastDate())) {
            Cell cell = row.createCell(columnIndex);
            cell.setCellValue(dateValue.firstDate().atStartOfDay());
            cell.setCellStyle(styles.date());
            return;
        }
        String value = metric.displayValue() == null
                ? dateValue.firstDate() + "至" + dateValue.lastDate()
                    + "（" + dateValue.dateCount() + "个配种日）"
                : metric.displayValue();
        createTextCell(row, columnIndex, value, styles.text());
    }

    private void writeDetailSheet(SXSSFWorkbook workbook, List<Metric> metrics, Styles styles) {
        Sheet sheet = workbook.createSheet(DETAIL_SHEET_NAME);
        Row header = sheet.createRow(0);
        for (int index = 0; index < DETAIL_HEADERS.length; index++) {
            createTextCell(header, index, DETAIL_HEADERS[index], styles.header());
        }

        int rowIndex = 1;
        for (Metric metric : metrics) {
            Row row = sheet.createRow(rowIndex++);
            createNumberCell(row, 0, BigDecimal.valueOf(metric.order()), styles.integer());
            createTextCell(row, 1, metric.code(), styles.text());
            createTextCell(row, 2, firstNonBlank(metric.stageName(), metric.stage()), styles.text());
            createTextCell(row, 3, metric.name(), styles.text());
            createTextCell(row, 4, unitText(metric.unit()), styles.text());
            writeRawValue(row, 5, metric, styles);
            createTextCell(row, 6, metric.displayValue(), styles.text());
            createTextCell(row, 7, metric.status(), styles.text());
            createTextCell(row, 8, metric.formula(), styles.wrappedText());
            createTextCell(row, 9, operandText(metric.numerator()), styles.wrappedText());
            createTextCell(row, 10, operandText(metric.denominator()), styles.wrappedText());
            createTextCell(row, 11, metricComponentsText(metric), styles.wrappedText());
            createTextCell(row, 12, missingCausesText(metric.missingCauses()), styles.wrappedText());
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, DETAIL_HEADERS.length - 1));
        setDetailColumnWidths(sheet);
    }

    private void writeRawValue(Row row, int columnIndex, Metric metric, Styles styles) {
        if (!"AVAILABLE".equals(metric.status())) {
            createTextCell(row, columnIndex, "", styles.text());
            return;
        }
        if ("NUMBER".equals(metric.valueType())) {
            if (metric.numericValue() == null) {
                throw invalidContract();
            }
            writeNumericCell(row, columnIndex, metric.numericValue(), metric.format(), styles);
            return;
        }
        DateRangeValue dateValue = metric.dateValue();
        if (dateValue == null || dateValue.firstDate() == null || dateValue.lastDate() == null) {
            throw invalidContract();
        }
        String rawValue = dateValue.firstDate().equals(dateValue.lastDate())
                ? dateValue.firstDate().toString()
                : dateValue.firstDate() + "/" + dateValue.lastDate();
        createTextCell(row, columnIndex, rawValue, styles.text());
    }

    private void writeNumericCell(
            Row row,
            int columnIndex,
            BigDecimal value,
            String format,
            Styles styles
    ) {
        String normalizedFormat = format.toUpperCase(Locale.ROOT);
        CellStyle style;
        if (normalizedFormat.contains("PERCENT")) {
            style = styles.percent();
        } else if (normalizedFormat.contains("RATIO_TO_ONE")) {
            style = styles.ratioToOne();
        } else if (normalizedFormat.contains("INT")) {
            style = styles.integer();
        } else {
            style = styles.decimal();
        }
        createNumberCell(row, columnIndex, value, style);
    }

    private Styles createStyles(SXSSFWorkbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);

        CellStyle text = workbook.createCellStyle();
        CellStyle wrappedText = workbook.createCellStyle();
        wrappedText.setWrapText(true);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle integer = workbook.createCellStyle();
        integer.setDataFormat(dataFormat.getFormat("#,##0"));
        CellStyle decimal = workbook.createCellStyle();
        decimal.setDataFormat(dataFormat.getFormat("#,##0.00"));
        CellStyle percent = workbook.createCellStyle();
        percent.setDataFormat(dataFormat.getFormat("0.00%"));
        CellStyle ratioToOne = workbook.createCellStyle();
        ratioToOne.setDataFormat(dataFormat.getFormat("#,##0.00\":1\""));
        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
        CellStyle dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm:ss"));
        return new Styles(
                header,
                text,
                wrappedText,
                integer,
                decimal,
                percent,
                ratioToOne,
                date,
                dateTime
        );
    }

    private void setSummaryColumnWidths(Sheet sheet, List<Metric> metrics) {
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        int columnIndex = 3;
        for (Metric metric : metrics) {
            String header = metric.excelColumnName();
            int width = Math.max(14, Math.min(32, header.codePointCount(0, header.length()) * 2 + 6));
            sheet.setColumnWidth(columnIndex++, width * 256);
        }
    }

    private void setDetailColumnWidths(Sheet sheet) {
        int[] widths = {8, 34, 16, 24, 18, 20, 24, 22, 44, 40, 40, 50, 60};
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private void validateMetrics(List<Metric> metrics) {
        if (metrics == null || metrics.size() != METRIC_COUNT) {
            throw invalidContract();
        }
        Set<String> codes = new HashSet<String>();
        int previousOrder = Integer.MIN_VALUE;
        for (int index = 0; index < metrics.size(); index++) {
            Metric metric = metrics.get(index);
            if (metric == null
                    || metric.order() != (index + 1) * 10
                    || metric.order() <= previousOrder
                    || !EXPECTED_CODES.get(index).equals(metric.code())
                    || !codes.add(metric.code())
                    || isBlank(metric.name())
                    || isBlank(metric.stage())
                    || isBlank(metric.stageName())
                    || isBlank(metric.excelColumnName())
                    || isBlank(metric.valueType())
                    || isBlank(metric.unit())
                    || !FORMATS.contains(metric.format())
                    || isBlank(metric.formula())
                    || !STATUSES.contains(metric.status())
                    || index == 0 && !"DATE_RANGE".equals(metric.valueType())
                    || index > 0 && !"NUMBER".equals(metric.valueType())
                    || "DATE_RANGE".equals(metric.valueType()) != "DATE_RANGE".equals(metric.format())
                    || metric.components() == null
                    || metric.missingCauses() == null) {
                throw invalidContract();
            }
            validateMetricValue(metric);
            validateOperand(metric.numerator());
            validateOperand(metric.denominator());
            metric.components().forEach(this::validateOperand);
            metric.missingCauses().forEach(this::validateMissingCause);
            previousOrder = metric.order();
        }
    }

    private void validateMetricValue(Metric metric) {
        if ("AVAILABLE".equals(metric.status())) {
            if (isBlank(metric.displayValue()) || !metric.missingCauses().isEmpty()) {
                throw invalidContract();
            }
            if ("NUMBER".equals(metric.valueType())) {
                if (metric.numericValue() == null || metric.dateValue() != null) {
                    throw invalidContract();
                }
                return;
            }
            DateRangeValue dateValue = metric.dateValue();
            if (metric.numericValue() != null
                    || dateValue == null
                    || dateValue.firstDate() == null
                    || dateValue.lastDate() == null
                    || dateValue.dateCount() <= 0
                    || dateValue.dailyCycleCounts() == null) {
                throw invalidContract();
            }
            validateDateRange(dateValue);
            return;
        }
        if (metric.numericValue() != null
                || metric.dateValue() != null
                || metric.displayValue() != null
                || metric.missingCauses().isEmpty()) {
            throw invalidContract();
        }
    }

    private void validateOperand(Operand operand) {
        if (operand == null) {
            return;
        }
        if (isBlank(operand.code())
                || isBlank(operand.label())
                || operand.value() == null
                || isBlank(operand.unit())) {
            throw invalidContract();
        }
    }

    private void validateMissingCause(MissingCause cause) {
        if (cause == null || isBlank(cause.code()) || isBlank(cause.message())) {
            throw invalidContract();
        }
    }

    private String operandText(Operand operand) {
        if (operand == null) {
            return "";
        }
        String label = firstNonBlank(operand.label(), operand.code());
        StringBuilder text = new StringBuilder(label);
        if (!isBlank(operand.code()) && !operand.code().equals(label)) {
            text.append(" [").append(operand.code()).append(']');
        }
        if (operand.value() != null) {
            text.append(": ").append(operand.value().toPlainString());
        }
        if (!isBlank(operand.unit())) {
            text.append(' ').append(unitText(operand.unit()));
        }
        return text.toString();
    }

    private static String unitText(String unit) {
        return switch (unit) {
            case "DATE" -> "日期";
            case "COUNT" -> "只";
            case "LITTER" -> "窝";
            case "COUNT_PER_LITTER" -> "只/窝";
            case "PERCENT" -> "%";
            case "RATIO" -> "比值";
            case "KG" -> "kg";
            case "KG_PER_RABBIT" -> "kg/只";
            case "CNY" -> "元";
            case "CNY_PER_KG" -> "元/kg";
            case "CNY_PER_RABBIT" -> "元/只";
            default -> unit;
        };
    }

    private String metricComponentsText(Metric metric) {
        if (metric.dateValue() != null) {
            StringBuilder text = new StringBuilder();
            for (BatchStatistics.DailyCycleCount daily : metric.dateValue().dailyCycleCounts()) {
                appendLine(text, daily.date() + ": " + daily.cycleCount() + " 个周期");
            }
            return text.toString();
        }
        StringBuilder text = new StringBuilder();
        for (Operand component : metric.components()) {
            appendLine(text, operandText(component));
        }
        return text.toString();
    }

    private void validateDateRange(DateRangeValue value) {
        if (value.dailyCycleCounts().size() != value.dateCount()
                || value.dailyCycleCounts().isEmpty()) {
            throw invalidContract();
        }
        java.time.LocalDate previous = null;
        for (BatchStatistics.DailyCycleCount daily : value.dailyCycleCounts()) {
            if (daily == null
                    || daily.date() == null
                    || daily.cycleCount() <= 0
                    || previous != null && !daily.date().isAfter(previous)) {
                throw invalidContract();
            }
            previous = daily.date();
        }
        if (!value.firstDate().equals(value.dailyCycleCounts().getFirst().date())
                || !value.lastDate().equals(value.dailyCycleCounts().getLast().date())) {
            throw invalidContract();
        }
    }

    private String missingCausesText(List<MissingCause> causes) {
        StringBuilder text = new StringBuilder();
        for (MissingCause cause : causes) {
            appendLine(text, cause.code() + ": " + cause.message());
        }
        return text.toString();
    }

    private void appendLine(StringBuilder text, String value) {
        if (isBlank(value)) {
            return;
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(value);
    }

    private String statusText(String status) {
        return switch (status) {
            case "NOT_APPLICABLE" -> "暂无可计算数据";
            case "NOT_RECORDED" -> "未录入";
            case "DATA_MISSING" -> "历史数据缺失";
            default -> throw invalidContract();
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String sanitizeUtf8FilenameComponent(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousReplacement = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (isUnsafeFilenameCharacter(current)) {
                if (!previousReplacement) {
                    sanitized.append('_');
                }
                previousReplacement = true;
            } else {
                sanitized.append(current);
                previousReplacement = false;
            }
        }
        String result = sanitized.toString().trim();
        int start = 0;
        int end = result.length();
        while (start < end && result.charAt(start) == '.') {
            start++;
        }
        while (end > start && (result.charAt(end - 1) == '.' || result.charAt(end - 1) == ' ')) {
            end--;
        }
        result = result.substring(start, end);
        return result.isBlank() ? "batch-code" : result;
    }

    private String sanitizeAsciiFilenameComponent(String value) {
        String utf8Safe = sanitizeUtf8FilenameComponent(value);
        StringBuilder ascii = new StringBuilder(utf8Safe.length());
        boolean previousReplacement = false;
        for (int index = 0; index < utf8Safe.length(); index++) {
            char current = utf8Safe.charAt(index);
            boolean allowed = current >= 'a' && current <= 'z'
                    || current >= 'A' && current <= 'Z'
                    || current >= '0' && current <= '9'
                    || current == '-'
                    || current == '_'
                    || current == '.';
            if (allowed) {
                ascii.append(current);
                previousReplacement = false;
            } else if (!previousReplacement) {
                ascii.append('_');
                previousReplacement = true;
            }
        }
        String result = ascii.toString();
        int start = 0;
        int end = result.length();
        while (start < end && isAsciiFilenameSeparator(result.charAt(start))) {
            start++;
        }
        while (end > start && isAsciiFilenameSeparator(result.charAt(end - 1))) {
            end--;
        }
        return start == end ? "batch-code" : result.substring(start, end);
    }

    private boolean isUnsafeFilenameCharacter(char value) {
        return Character.isISOControl(value)
                || value == '<'
                || value == '>'
                || value == ':'
                || value == '"'
                || value == '/'
                || value == '\\'
                || value == '|'
                || value == '?'
                || value == '*';
    }

    private boolean isAsciiFilenameSeparator(char value) {
        return value == '.' || value == '_' || value == '-';
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BizException invalidContract() {
        return new BizException(500, "批次统计导出数据不完整");
    }

    private void createTextCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int columnIndex, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    public record WorkbookSnapshot(
            List<Metric> metrics,
            String houseName,
            String batchCode,
            Instant calculatedAt,
            String utf8Filename,
            String asciiFilename
    ) {
        public WorkbookSnapshot {
            metrics = List.copyOf(metrics);
        }
    }

    private record Styles(
            CellStyle header,
            CellStyle text,
            CellStyle wrappedText,
            CellStyle integer,
            CellStyle decimal,
            CellStyle percent,
            CellStyle ratioToOne,
            CellStyle date,
            CellStyle dateTime
    ) {
    }
}
