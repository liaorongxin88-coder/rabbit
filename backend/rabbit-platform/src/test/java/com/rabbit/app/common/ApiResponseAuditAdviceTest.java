package com.rabbit.app.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 把响应体里的业务码回写到 request，供审计拦截器取用。
 *
 * <p>这是一条隐式的跨模块契约：本类写的是字符串字面量 {@code "apiCode"} 和
 * {@code "apiMessage"}，rabbit-reporting 的 {@code AuditLogInterceptor} 也用同样的
 * 字面量去读。两边没有共享常量，编译器帮不上忙 —— 改任意一侧，审计表里的业务码
 * 会静默变空，接口本身照常返回 200，没有任何地方会报错。
 *
 * <p>审计记录的业务码是事后追责的依据。它一旦静默失效，等到需要查「这单当时到底
 * 成没成」的时候，数据已经缺了。
 */
class ApiResponseAuditAdviceTest {
    private final ApiResponseAuditAdvice advice = new ApiResponseAuditAdvice();

    @Test
    void theBusinessCodeIsHandedToTheAuditInterceptor() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        advice.beforeBodyWrite(ApiResponse.error(5001, "库存不足"), returnsApiResponse(), null, null,
                new ServletServerHttpRequest(servletRequest), mock(ServerHttpResponse.class));

        assertEquals(5001, servletRequest.getAttribute("apiCode"));
        assertEquals("库存不足", servletRequest.getAttribute("apiMessage"));
    }

    @Test
    void aSuccessfulResponseIsRecordedAsCodeZero() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        advice.beforeBodyWrite(ApiResponse.ok("done"), returnsApiResponse(), null, null,
                new ServletServerHttpRequest(servletRequest), mock(ServerHttpResponse.class));

        assertEquals(0, servletRequest.getAttribute("apiCode"));
        assertEquals("ok", servletRequest.getAttribute("apiMessage"));
    }

    /**
     * 这个 advice 只负责旁路记录，不能改写响应体本身。
     */
    @Test
    void theResponseBodyIsPassedThroughUntouched() throws Exception {
        ApiResponse<String> body = ApiResponse.ok("payload");

        Object returned = advice.beforeBodyWrite(body, returnsApiResponse(), null, null,
                new ServletServerHttpRequest(new MockHttpServletRequest()), mock(ServerHttpResponse.class));

        assertSame(body, returned);
    }

    @Test
    void aNonApiResponseBodyIsLeftAlone() throws Exception {
        String body = "plain text";

        Object returned = advice.beforeBodyWrite(body, returnsApiResponse(), null, null,
                new ServletServerHttpRequest(new MockHttpServletRequest()), mock(ServerHttpResponse.class));

        assertSame(body, returned);
    }

    @Test
    void aNullBodyIsLeftAlone() throws Exception {
        Object returned = advice.beforeBodyWrite(null, returnsApiResponse(), null, null,
                new ServletServerHttpRequest(new MockHttpServletRequest()), mock(ServerHttpResponse.class));

        assertNull(returned);
    }

    /**
     * 非 Servlet 的请求实现（例如未来接入的响应式栈）不该让写响应这条路径崩掉。
     */
    @Test
    void aNonServletRequestDoesNotBreakTheWrite() throws Exception {
        ApiResponse<String> body = ApiResponse.ok("payload");

        Object returned = advice.beforeBodyWrite(body, returnsApiResponse(), null, null,
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class));

        assertSame(body, returned);
    }

    @Test
    void onlyApiResponseReturnTypesAreIntercepted() throws Exception {
        assertTrue(advice.supports(returnsApiResponse(), null));
        assertFalse(advice.supports(returnsString(), null));
        assertFalse(advice.supports(returnsVoid(), null));
    }

    /**
     * 契约锁：这两个 attribute 名在 rabbit-reporting 侧是硬编码字面量。
     */
    @Test
    void theAttributeNamesArePartOfTheCrossModuleContract() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        advice.beforeBodyWrite(ApiResponse.error(403, "无权访问"), returnsApiResponse(), null, null,
                new ServletServerHttpRequest(servletRequest), mock(ServerHttpResponse.class));

        assertNull(servletRequest.getAttribute("code"), "读侧用的是 apiCode，不是 code");
        assertNull(servletRequest.getAttribute("message"), "读侧用的是 apiMessage，不是 message");
        assertEquals(403, servletRequest.getAttribute("apiCode"));
        assertEquals("无权访问", servletRequest.getAttribute("apiMessage"));
    }

    private MethodParameter returnsApiResponse() throws NoSuchMethodException {
        return returnTypeOf("apiResponseEndpoint");
    }

    private MethodParameter returnsString() throws NoSuchMethodException {
        return returnTypeOf("stringEndpoint");
    }

    private MethodParameter returnsVoid() throws NoSuchMethodException {
        return returnTypeOf("voidEndpoint");
    }

    /** -1 表示取方法的返回类型而不是某个入参。 */
    private MethodParameter returnTypeOf(String method) throws NoSuchMethodException {
        return new MethodParameter(ApiResponseAuditAdviceTest.class.getDeclaredMethod(method), -1);
    }

    @SuppressWarnings("unused")
    private ApiResponse<String> apiResponseEndpoint() {
        return null;
    }

    @SuppressWarnings("unused")
    private String stringEndpoint() {
        return null;
    }

    @SuppressWarnings("unused")
    private void voidEndpoint() {
        // 用于验证非 ApiResponse 返回类型不被拦截。
    }
}
