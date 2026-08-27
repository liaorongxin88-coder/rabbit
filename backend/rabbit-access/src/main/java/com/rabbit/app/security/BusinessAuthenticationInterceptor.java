package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import com.rabbit.app.common.TraceIdFilter;
import com.rabbit.app.tracking.OperationContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class BusinessAuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (DispatcherType.ASYNC.equals(request.getDispatcherType())) {
            return true;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        // 操作追踪上下文的播种点。选这里而不是 AuthorizationInterceptor：
        // 后者 preHandle 第一件事就是 HouseContext.clear()，把清理时机和鉴权
        // 绑在一起；审计上下文要覆盖整个请求，不该被鉴权的生命周期左右。
        // 兔舍取请求头而非 HouseContext：此刻鉴权尚未跑，HouseContext 还没绑。
        OperationContext.bind(userId, houseId(request), traceId(request));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        // afterCompletion 逆序执行，本拦截器排在 AuthorizationInterceptor 之后，
        // 上下文因此覆盖整个请求链。ThreadLocal 必须清，线程池会复用线程。
        OperationContext.clear();
    }

    /**
     * 兔舍头缺失或非法在这里一律回落成 null，由后续的鉴权拦截器去报错。
     * 审计上下文不该成为第二个校验入口，那会让同一个错误有两处不同措辞。
     */
    private Long houseId(HttpServletRequest request) {
        String value = request.getHeader("X-House-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTR);
        return traceId == null ? null : String.valueOf(traceId);
    }
}
