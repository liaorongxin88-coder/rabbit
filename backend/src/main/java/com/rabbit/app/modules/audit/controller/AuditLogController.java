package com.rabbit.app.modules.audit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.audit.entity.AuditLog;
import com.rabbit.app.modules.audit.service.AuditLogService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Validated
@RestController
@RequestMapping("/api")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/audit-logs")
    @HousePerm("control")
    public ApiResponse<List<AuditLog>> list(@RequestHeader("X-House-Id") Long houseId,
                                            @RequestParam(value = "userId", required = false) Long userId,
                                            @RequestParam(value = "path", required = false) String path,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "from", required = false) Long from,
                                            @RequestParam(value = "to", required = false) Long to,
                                            @RequestParam(value = "page", required = false) Integer page,
                                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        requireLogin();
        Date fromDate = from == null ? null : new Date(from);
        Date toDate = to == null ? null : new Date(to);
        return ApiResponse.ok(auditLogService.listPage(houseId, userId, path, status, fromDate, toDate, page == null ? 1 : page, pageSize == null ? 50 : pageSize));
    }

    @GetMapping(value = "/audit-logs.csv")
    @HousePerm("control")
    public org.springframework.http.ResponseEntity<StreamingResponseBody> exportCsv(@RequestHeader("X-House-Id") Long houseId,
                                                                                   @RequestParam(value = "userId", required = false) Long userId,
                                                                                   @RequestParam(value = "path", required = false) String path,
                                                                                   @RequestParam(value = "status", required = false) Integer status,
                                                                                   @RequestParam(value = "from", required = false) Long from,
                                                                                   @RequestParam(value = "to", required = false) Long to,
                                                                                   @RequestParam(value = "maxRows", required = false) Integer maxRows) {
        requireLogin();
        Date fromDate = from == null ? null : new Date(from);
        Date toDate = to == null ? null : new Date(to);

        int limitRows = maxRows == null ? 50000 : maxRows.intValue();
        if (limitRows < 0) {
            limitRows = 50000;
        }
        if (limitRows > 500000) {
            limitRows = 500000;
        }
        final int pageSize = 1000;
        int finalLimitRows = limitRows;

        StreamingResponseBody body = outputStream -> {
            writeUtf8Bom(outputStream);
            writeCsvLine(outputStream, "id,create_time,trace_id,user_id,house_id,method,path,query_string,status,api_code,api_message,cost_ms,ip,user_agent,error_message\n");
            int offset = 0;
            int written = 0;
            while (true) {
                int limit = pageSize;
                if (finalLimitRows > 0 && written + limit > finalLimitRows) {
                    limit = finalLimitRows - written;
                }
                if (limit <= 0) {
                    break;
                }
                List<AuditLog> part = auditLogService.listExportPage(houseId, userId, path, status, fromDate, toDate, offset, limit);
                if (part == null || part.isEmpty()) {
                    break;
                }
                for (AuditLog l : part) {
                    String line = v(l.getId()) + ","
                            + v(l.getCreateTime()) + ","
                            + csv(l.getTraceId()) + ","
                            + v(l.getUserId()) + ","
                            + v(l.getHouseId()) + ","
                            + csv(l.getMethod()) + ","
                            + csv(l.getPath()) + ","
                            + csv(l.getQueryString()) + ","
                            + v(l.getStatus()) + ","
                            + v(l.getApiCode()) + ","
                            + csv(l.getApiMessage()) + ","
                            + v(l.getCostMs()) + ","
                            + csv(l.getIp()) + ","
                            + csv(l.getUserAgent()) + ","
                            + csv(l.getErrorMessage())
                            + "\n";
                    writeCsvLine(outputStream, line);
                    written++;
                    if (finalLimitRows > 0 && written >= finalLimitRows) {
                        break;
                    }
                }
                if (finalLimitRows > 0 && written >= finalLimitRows) {
                    break;
                }
                if (part.size() < limit) {
                    break;
                }
                offset += part.size();
            }
            outputStream.flush();
        };

        return org.springframework.http.ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_logs.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    private String v(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String csv(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\n") || t.contains("\r")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    private void writeUtf8Bom(OutputStream os) throws IOException {
        os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }

    private void writeCsvLine(OutputStream os, String s) throws IOException {
        if (s == null) {
            return;
        }
        os.write(s.getBytes(StandardCharsets.UTF_8));
    }
}
