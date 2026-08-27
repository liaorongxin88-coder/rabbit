package com.rabbit.app.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 链路 ID 的产生与清理。
 *
 * <p>这个 filter 是整条可观测链的源头：它写进 request attribute 的值，会被
 * rabbit-reporting 的 {@code AuditLogInterceptor} 原样读走存进审计表。所以这里
 * 有两件事必须钉死 —— 常量名不能改，MDC 必须清干净。
 *
 * <p>MDC 挂在 ThreadLocal 上，而容器线程是复用的。漏清一次，后续所有请求的日志
 * 都会顶着上一个请求的 traceId，排查线上问题时会把人带到完全错误的方向，
 * 而且这种污染在日志里看不出异常，只会让人一直查错单子。
 */
class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void anAbsentHeaderGetsAGeneratedTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR);
        assertEquals(32, traceId.length(), "应是去掉连字符的 UUID");
        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }

    /**
     * 上游传下来的 traceId 必须原样沿用，否则跨服务的日志串不成一条链。
     */
    @Test
    void anIncomingTraceIdIsReused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "upstream-trace-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("upstream-trace-42", request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR));
        assertEquals("upstream-trace-42", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void anIncomingTraceIdIsTrimmed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "  padded-trace  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("padded-trace", request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR));
    }

    /**
     * 空白头等同于没传。若原样沿用，审计表里会出现一批空 traceId 的记录。
     */
    @Test
    void aBlankHeaderIsTreatedAsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR);
        assertEquals(32, traceId.length());
    }

    @Test
    void theTraceIdIsEchoedBackToTheCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR),
                response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    /**
     * 下游能在 MDC 里读到 traceId —— 日志框架靠它输出。
     */
    @Test
    void theTraceIdIsVisibleInMdcDuringTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-in-chain");
        String[] seen = new String[1];

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen[0] = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        assertEquals("trace-in-chain", seen[0]);
    }

    @Test
    void theMdcIsClearedAfterTheChain() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    /**
     * 请求处理抛异常时同样要清 MDC。这条最容易漏 —— 正常路径清了，异常路径没清，
     * 于是只有出错的请求会污染后续线程，反而更难复现。
     */
    @Test
    void theMdcIsClearedEvenWhenTheChainThrows() {
        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new IllegalStateException("下游炸了");
                }));

        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void theMdcIsClearedEvenWhenTheChainThrowsIoException() {
        assertThrows(IOException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new IOException("写响应失败");
                }));

        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void theMdcIsClearedEvenWhenTheChainThrowsServletException() {
        assertThrows(ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("容器异常");
                }));

        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void separateRequestsGetSeparateTraceIds() throws Exception {
        MockHttpServletRequest first = new MockHttpServletRequest();
        MockHttpServletRequest second = new MockHttpServletRequest();

        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());

        assertNotEquals(first.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR),
                second.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR));
    }

    /**
     * 下游模块靠字面量取值，不是靠这个常量引用：{@code AuditLogInterceptor} 里写的是
     * {@code request.getAttribute("traceId")}。改了这里的常量值，审计表的 traceId
     * 会静默变成 null，且编译期毫无提示。
     */
    @Test
    void theAttributeNameIsPartOfTheCrossModuleContract() {
        assertEquals("traceId", TraceIdFilter.TRACE_ID_REQUEST_ATTR);
        assertEquals("traceId", TraceIdFilter.TRACE_ID_MDC_KEY);
        assertEquals("X-Trace-Id", TraceIdFilter.TRACE_ID_HEADER);
    }
}
