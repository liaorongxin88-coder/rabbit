package com.rabbit.app.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 异常到响应体的映射。
 *
 * <p>这层决定用户看到什么错误信息。映射错了不会有任何告警——接口照样返回 200，
 * 只是 body 里的 code 和 message 不对，前端据此做的分支判断随之失效。
 *
 * <p>尤其是 {@code X-House-Id}：它是租户隔离的入口参数，缺失和格式错误要给出
 * 可区分的提示，否则接入方只能盲猜自己哪里错了。
 */
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aBusinessExceptionKeepsItsOwnCodeAndMessage() {
        ApiResponse<Void> response = handler.handleBiz(new BizException(409, "库存不足"));

        assertEquals(409, response.getCode());
        assertEquals("库存不足", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * 校验失败时取第一条约束信息，让用户看到具体哪个字段不对，而不是笼统的「参数错误」。
     */
    @Test
    void aValidationFailureSurfacesTheFirstConstraintMessage() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "form");
        binding.reject("code", "兔舍编号不能为空");

        ApiResponse<Void> response = handler.handleValid(
                new MethodArgumentNotValidException(anyMethodParameter(), binding));

        assertEquals(400, response.getCode());
        assertEquals("兔舍编号不能为空", response.getMessage());
    }

    @Test
    void aValidationFailureWithoutDetailsFallsBackToAGenericMessage() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "form");

        ApiResponse<Void> response = handler.handleValid(
                new MethodArgumentNotValidException(anyMethodParameter(), binding));

        assertEquals(400, response.getCode());
        assertEquals("参数错误", response.getMessage());
    }

    /**
     * 租户头缺失有专门的提示，因为这是接入方最常犯的错。
     */
    @Test
    void aMissingTenantHeaderGetsADedicatedMessage() throws Exception {
        ApiResponse<Void> response = handler.handleMissingHeader(
                new MissingRequestHeaderException("X-House-Id", anyMethodParameter()));

        assertEquals(400, response.getCode());
        assertEquals("缺少X-House-Id", response.getMessage());
    }

    /**
     * HTTP 头名大小写不敏感，容器给出的大小写不该影响提示语。
     */
    @Test
    void theTenantHeaderIsMatchedCaseInsensitively() throws Exception {
        ApiResponse<Void> response = handler.handleMissingHeader(
                new MissingRequestHeaderException("x-house-id", anyMethodParameter()));

        assertEquals("缺少X-House-Id", response.getMessage());
    }

    @Test
    void anyOtherMissingHeaderIsNamedInTheMessage() throws Exception {
        ApiResponse<Void> response = handler.handleMissingHeader(
                new MissingRequestHeaderException("X-Device-Id", anyMethodParameter()));

        assertEquals(400, response.getCode());
        assertEquals("缺少请求头:X-Device-Id", response.getMessage());
    }

    @Test
    void aMissingQueryParameterIsNamedInTheMessage() {
        ApiResponse<Void> response = handler.handleMissingParam(
                new MissingServletRequestParameterException("cageId", "Long"));

        assertEquals(400, response.getCode());
        assertEquals("cageId不能为空", response.getMessage());
    }

    /**
     * 传了但格式不对，和压根没传是两回事，提示语必须能区分开。
     */
    @Test
    void aMalformedTenantHeaderIsDistinguishedFromAMissingOne() throws Exception {
        ApiResponse<Void> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "X-House-Id",
                        anyMethodParameter(), new NumberFormatException()));

        assertEquals(400, response.getCode());
        assertEquals("X-House-Id不合法", response.getMessage());
    }

    @Test
    void theMalformedTenantHeaderIsAlsoMatchedCaseInsensitively() throws Exception {
        ApiResponse<Void> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "x-house-id",
                        anyMethodParameter(), new NumberFormatException()));

        assertEquals("X-House-Id不合法", response.getMessage());
    }

    @Test
    void anyOtherTypeMismatchIsNamedInTheMessage() throws Exception {
        ApiResponse<Void> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("x", Integer.class, "page",
                        anyMethodParameter(), new NumberFormatException()));

        assertEquals(400, response.getCode());
        assertEquals("page不合法", response.getMessage());
    }

    @Test
    void anUnexpectedExceptionBecomesAServerError() {
        ApiResponse<Void> response = handler.handleOther(new IllegalStateException("连接池耗尽"));

        assertEquals(500, response.getCode());
        assertEquals("连接池耗尽", response.getMessage());
    }

    /**
     * 记录既有行为：没有 message 的异常会返回 message 为 null 的 500。
     * 这对前端不算友好，但改动会影响所有调用方，先钉住现状，避免无意识地漂移。
     */
    @Test
    void anExceptionWithoutAMessageYieldsANullMessage() {
        ApiResponse<Void> response = handler.handleOther(new NullPointerException());

        assertEquals(500, response.getCode());
        assertNull(response.getMessage());
    }

    /**
     * BizException 有自己的处理器，不该掉进兜底的 500 分支。
     */
    @Test
    void aBusinessExceptionIsNotSwallowedByTheCatchAll() {
        assertEquals(403, handler.handleBiz(new BizException(403, "无权访问")).getCode());
        assertEquals(500, handler.handleOther(new BizException(403, "无权访问")).getCode());
    }

    /** 构造异常需要一个 MethodParameter，内容不影响被测逻辑。 */
    private MethodParameter anyMethodParameter() throws NoSuchMethodException {
        return new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("placeholder", String.class), 0);
    }

    @SuppressWarnings("unused")
    private void placeholder(String value) {
        // 仅用于取得一个合法的 MethodParameter。
    }
}
