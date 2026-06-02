package com.rabbit.app.audit;

import com.rabbit.app.common.BizException;
import com.rabbit.app.common.TraceIdFilter;
import com.rabbit.app.model.AuditLog;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.service.AuditLogService;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AuditLogInterceptor implements HandlerInterceptor {
    private static final String START_TIME_ATTR = "auditStartTimeMs";
    private static final String API_CODE_ATTR = "apiCode";
    private static final String API_MESSAGE_ATTR = "apiMessage";
    private static final int MAX_ERROR_LEN = 500;
    private static final int MAX_QUERY_LEN = 1000;
    private static final int MAX_UA_LEN = 255;
    private static final int MAX_IP_LEN = 64;

    private final AuditLogService auditLogService;

    public AuditLogInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startMs = (Long) request.getAttribute(START_TIME_ATTR);
        long costMs = startMs == null ? -1L : (System.currentTimeMillis() - startMs);

        AuditLog log = new AuditLog();
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR);
        log.setTraceId(traceId == null ? null : String.valueOf(traceId));
        log.setUserId(AuthContext.getUserId());
        log.setHouseId(parseHouseId(request.getHeader("X-House-Id")));
        log.setMethod(truncate(request.getMethod(), 10));
        log.setPath(truncate(request.getRequestURI(), 255));
        log.setQueryString(truncate(request.getQueryString(), MAX_QUERY_LEN));
        log.setStatus(response.getStatus());
        Integer apiCode = parseInt(request.getAttribute(API_CODE_ATTR));
        String apiMessage = parseString(request.getAttribute(API_MESSAGE_ATTR));
        log.setApiCode(apiCode);
        log.setApiMessage(truncate(apiMessage, 255));
        log.setCostMs(costMs);
        String err = buildErrorMessage(ex);
        if ((err == null || err.trim().isEmpty()) && apiCode != null && apiCode != 0) {
            err = apiCode + ":" + apiMessage;
        }
        log.setErrorMessage(truncate(err, MAX_ERROR_LEN));
        log.setIp(truncate(getClientIp(request), MAX_IP_LEN));
        log.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_UA_LEN));

        auditLogService.write(log);
    }

    private static Long parseHouseId(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildErrorMessage(Exception ex) {
        if (ex == null) {
            return null;
        }
        if (ex instanceof BizException) {
            BizException be = (BizException) ex;
            return be.getCode() + ":" + be.getMessage();
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getClass().getSimpleName() + ":" + ex.getMessage();
    }

    private static Integer parseInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String parseString(Object o) {
        if (o == null) {
            return null;
        }
        return String.valueOf(o);
    }

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            String v = xff.split(",")[0];
            if (v != null) {
                return v.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
