package com.rabbit.app.modules.audit.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbit.app.common.BizException;
import com.rabbit.app.common.TraceIdFilter;
import com.rabbit.app.modules.audit.entity.AuditLog;
import com.rabbit.app.modules.audit.service.AuditLogService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.tracking.OperationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 审计拦截器。
 *
 * <p>这层跑在 {@code afterCompletion}，业务已经执行完了，所以它的正确性标准是「不管前面
 * 发生了什么都要能记下来且不抛异常」：请求头是脏的、异常是任意类型的、字段超长的，都得
 * 落成一条合法记录。下面按这几类各测一遍。
 *
 * <p>{@link AuthContext} 是 ThreadLocal，用例之间必须清干净。
 */
class AuditLogInterceptorTest {
    private AuditLogService auditLogService;
    private AuditLogInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        interceptor = new AuditLogInterceptor(auditLogService);
        response = new MockHttpServletResponse();
        AuthContext.clear();
        OperationContext.clear();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        OperationContext.clear();
    }

    @Test
    void preHandleStampsTheStartTimeAndAlwaysProceeds() {
        MockHttpServletRequest request = get("/api/admin/farms");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNotNull(request.getAttribute("auditStartTimeMs"));
    }

    @Test
    void aNormalRequestIsRecordedWithItsContext() {
        AuthContext.setUserId(42L);
        MockHttpServletRequest request = get("/api/admin/farms");
        request.setQueryString("page=2");
        request.addHeader("X-House-Id", "7");
        request.addHeader("User-Agent", "rabbit-admin/1.0");
        request.setRemoteAddr("10.1.2.3");
        request.setAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR, "trace-abc");
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        AuditLog log = captured();
        assertEquals("trace-abc", log.getTraceId());
        assertEquals(42L, log.getUserId());
        assertEquals(7L, log.getHouseId());
        assertEquals("GET", log.getMethod());
        assertEquals("/api/admin/farms", log.getPath());
        assertEquals("page=2", log.getQueryString());
        assertEquals(200, log.getStatus());
        assertEquals("10.1.2.3", log.getIp());
        assertEquals("rabbit-admin/1.0", log.getUserAgent());
        assertNull(log.getErrorMessage());
    }

    /**
     * 没跑过 preHandle 时（例如更早的过滤器就把请求打回了）耗时记 -1，而不是拿
     * 当前时间去减一个不存在的起点、算出一个荒谬的巨大值。
     */
    @Test
    void operationCoordinatesComeFromTheOperationContext() {
        OperationContext context = OperationContext.bind(42L, 7L, "trace-abc");
        context.setBatchId(8L);
        context.setCageId(9L);
        context.setRabbitId(10L);
        MockHttpServletRequest request = get("/api/rabbits/10/weight");

        interceptor.afterCompletion(request, response, new Object(), null);

        AuditLog log = captured();
        assertEquals(42L, log.getUserId());
        assertEquals(7L, log.getHouseId());
        assertEquals(8L, log.getBatchId());
        assertEquals(9L, log.getCageId());
        assertEquals(10L, log.getRabbitId());
    }

    @Test
    void missingStartTimeYieldsMinusOneInsteadOfGarbage() {
        MockHttpServletRequest request = get("/api/admin/farms");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertEquals(-1L, captured().getCostMs());
    }

    @Test
    void nonNumericHouseHeaderIsDroppedRatherThanBlowingUp() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.addHeader("X-House-Id", "not-a-number");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(captured().getHouseId());
    }

    @Test
    void blankHouseHeaderIsDropped() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.addHeader("X-House-Id", "   ");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(captured().getHouseId());
    }

    @Test
    void paddedHouseHeaderIsStillParsed() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.addHeader("X-House-Id", "  7  ");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertEquals(7L, captured().getHouseId());
    }

    @Test
    void bizExceptionIsRecordedAsCodeAndMessage() {
        MockHttpServletRequest request = get("/api/admin/farms");

        interceptor.afterCompletion(request, response, new Object(), new BizException(403, "无权访问"));

        assertEquals("403:无权访问", captured().getErrorMessage());
    }

    @Test
    void genericExceptionIsRecordedAsTypeAndMessage() {
        MockHttpServletRequest request = get("/api/admin/farms");

        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("连接超时"));

        assertEquals("IllegalStateException:连接超时", captured().getErrorMessage());
    }

    /**
     * NPE 这类异常经常没有 message，只记一个空串等于什么都没记，所以退回类名。
     */
    @Test
    void exceptionWithoutMessageStillRecordsItsType() {
        MockHttpServletRequest request = get("/api/admin/farms");

        interceptor.afterCompletion(request, response, new Object(), new NullPointerException());

        assertEquals("NullPointerException", captured().getErrorMessage());
    }

    /**
     * 业务把错误码写进了 request 属性但没抛异常（统一异常处理器已经接住了），
     * 这时错误信息要从属性里补回来，否则审计里只剩一个 200 看不出问题。
     */
    @Test
    void businessErrorCodeIsRecoveredWhenNoExceptionReachedTheInterceptor() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.setAttribute("apiCode", 5001);
        request.setAttribute("apiMessage", "库存不足");

        interceptor.afterCompletion(request, response, new Object(), null);

        AuditLog log = captured();
        assertEquals(5001, log.getApiCode());
        assertEquals("库存不足", log.getApiMessage());
        assertEquals("5001:库存不足", log.getErrorMessage());
    }

    @Test
    void successCodeZeroIsNotTreatedAsAnError() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.setAttribute("apiCode", 0);
        request.setAttribute("apiMessage", "ok");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(captured().getErrorMessage());
    }

    @Test
    void stringCodeAttributeIsParsed() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.setAttribute("apiCode", "4001");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertEquals(4001, captured().getApiCode());
    }

    @Test
    void unparsableCodeAttributeIsDropped() {
        MockHttpServletRequest request = get("/api/admin/farms");
        request.setAttribute("apiCode", "not-a-code");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(captured().getApiCode());
    }

    /**
     * 超长字段必须在写库前截断。列宽是固定的，不截就是一条插入失败，
     * 而失败会被 AuditLogService 吞掉 —— 表现为审计悄悄少了一条，最难发现。
     */
    @Test
    void oversizedFieldsAreTruncatedToTheColumnWidths() {
        MockHttpServletRequest request = get("/api/admin/" + "x".repeat(400));
        request.setQueryString("q=" + "y".repeat(2000));
        request.addHeader("User-Agent", "z".repeat(500));

        interceptor.afterCompletion(
                request, response, new Object(), new IllegalStateException("e".repeat(900)));

        AuditLog log = captured();
        assertEquals(255, log.getPath().length());
        assertEquals(1000, log.getQueryString().length());
        assertEquals(255, log.getUserAgent().length());
        assertEquals(500, log.getErrorMessage().length());
    }

    @Test
    void anonymousRequestIsRecordedWithoutAUser() {
        MockHttpServletRequest request = get("/api/admin/auth/login");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(captured().getUserId());
    }

    private MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private AuditLog captured() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).write(captor.capture());
        return captor.getValue();
    }
}
