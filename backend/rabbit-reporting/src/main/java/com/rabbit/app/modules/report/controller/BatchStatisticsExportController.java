package com.rabbit.app.modules.report.controller;

import com.rabbit.app.common.BizException;
import com.rabbit.app.common.TraceIdFilter;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.service.BatchStatisticsService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.report.service.BatchStatisticsWorkbookWriter;
import com.rabbit.app.modules.report.service.BatchStatisticsWorkbookWriter.WorkbookSnapshot;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class BatchStatisticsExportController {
    public static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchStatisticsExportController.class);

    private final HouseService houseService;
    private final BatchStatisticsService batchStatisticsService;
    private final BatchStatisticsWorkbookWriter workbookWriter;

    public BatchStatisticsExportController(
            HouseService houseService,
            BatchStatisticsService batchStatisticsService,
            BatchStatisticsWorkbookWriter workbookWriter
    ) {
        this.houseService = houseService;
        this.batchStatisticsService = batchStatisticsService;
        this.workbookWriter = workbookWriter;
    }

    @GetMapping(
            value = "/api/reports/batches/{batchId}/statistics.xlsx",
            produces = XLSX_MEDIA_TYPE
    )
    @RequiresPermission(PermissionCode.RABBIT_REPORTS_EXPORT)
    public ResponseEntity<StreamingResponseBody> export(
            @RequestHeader("X-House-Id") Long houseId,
            @PathVariable("batchId") Long batchId,
            HttpServletRequest request
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");

        BatchStatistics statistics = batchStatisticsService.getStatistics(houseId, batchId);
        WorkbookSnapshot snapshot = workbookWriter.prepare(statistics, houseId, batchId);
        String traceId = requestTraceId(request);

        StreamingResponseBody body = outputStream -> {
            try {
                workbookWriter.write(snapshot, outputStream);
            } catch (IOException ex) {
                LOGGER.error(
                        "Batch statistics workbook stream failed: traceId={}, houseId={}, batchId={}",
                        traceId,
                        houseId,
                        batchId,
                        ex
                );
                throw ex;
            } catch (RuntimeException ex) {
                LOGGER.error(
                        "Batch statistics workbook generation failed: traceId={}, houseId={}, batchId={}",
                        traceId,
                        houseId,
                        batchId,
                        ex
                );
                throw ex;
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(snapshot))
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(body);
    }

    static String contentDisposition(WorkbookSnapshot snapshot) {
        String fallback = ContentDisposition.attachment()
                .filename(snapshot.asciiFilename())
                .build()
                .toString();
        return fallback + "; filename*=UTF-8''" + encodeRfc5987(snapshot.utf8Filename());
    }

    private static String encodeRfc5987(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte valueByte : bytes) {
            int unsigned = valueByte & 0xFF;
            if (isRfc5987AttrChar(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit((unsigned >>> 4) & 0xF, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
            }
        }
        return encoded.toString();
    }

    private static boolean isRfc5987AttrChar(int value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '!'
                || value == '#'
                || value == '$'
                || value == '&'
                || value == '+'
                || value == '-'
                || value == '.'
                || value == '^'
                || value == '_'
                || value == '`'
                || value == '|'
                || value == '~';
    }

    private static String requestTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR);
        return traceId == null ? "UNKNOWN" : String.valueOf(traceId);
    }

    private static Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
