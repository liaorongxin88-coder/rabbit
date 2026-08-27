package com.rabbit.app.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次写操作的完整坐标：谁、在哪个兔舍、对哪个批次的哪只兔、在哪个笼位、
 * 属于哪个请求。
 *
 * <p>为什么另起一个 ThreadLocal 而不是扩 {@code HouseContext}：后者是<b>鉴权</b>
 * 上下文，生命周期由 {@code AuthorizationInterceptor} 掌控（preHandle 里先 clear
 * 再由 {@code AccessControlService} 按需 set），且业务标识（batchId/cageId/rabbitId）
 * 在鉴权阶段根本还不知道——它们要等切面对方法入参求值才出现。把两者揉在一起，
 * 等于让鉴权的清理时机去决定审计数据的存活时机。
 *
 * <p>播种点是 {@code BusinessAuthenticationInterceptor.preHandle}，清理点是同一个
 * 拦截器的 {@code afterCompletion}。Spring 的 afterCompletion 逆序执行，所以它
 * 排在 {@code AuthorizationInterceptor} 之后，上下文覆盖整个请求。
 *
 * <p>非 Web 线程（定时任务、直接调 service 的测试）不会有播种点，此时
 * {@link OperationContextAspect} 会自己 {@link #bind()} 一个并在退出时清理，
 * 切面因此是自洽的，不依赖 Web 层。
 */
public final class OperationContext {

    private static final ThreadLocal<OperationContext> CTX = new ThreadLocal<>();

    private Long houseId;
    private Long batchId;
    private Long cageId;
    private Long rabbitId;
    private Long userId;
    private String operatorName;
    private String requestId;
    private String traceId;
    private String operationCode;

    /**
     * 当前请求是否命中「已完成的同一 requestId」。由外层切面判定，业务方法据此
     * 直接回放旧结果。之所以由切面判定：去重状态机整体归切面所有，业务方法
     * 若自己再查一次 shouldSkipAsDone，就会看到切面刚写下的 PROCESSING，
     * 把回放语义打坏。
     */
    private boolean dedupReplay;

    /**
     * 请求级操作人姓名缓存。批量端点单次可处理 500 只兔，逐只解析就是 500 次
     * 主键查询；请求结束随 ThreadLocal 一起丢弃，用户改名最迟下个请求生效。
     */
    private final Map<Long, String> operatorNames = new HashMap<>();

    /**
     * 待落库的事件。业务方法在循环里逐只 {@link #recordEvent} 只是往这里塞，
     * 真正的写入由内层切面在事务内一次性批量提交。
     */
    private final List<OperationEvent> pendingEvents = new ArrayList<>();

    private OperationContext() {
    }

    public static OperationContext bind() {
        OperationContext context = new OperationContext();
        CTX.set(context);
        return context;
    }

    public static OperationContext bind(Long userId, Long houseId, String traceId) {
        OperationContext context = bind();
        context.userId = userId;
        context.houseId = houseId;
        context.traceId = traceId;
        return context;
    }

    public static OperationContext current() {
        return CTX.get();
    }

    /**
     * 取当前上下文，没有就地绑定一个。给切面用：让切面在任何线程上都能工作。
     */
    public static OperationContext currentOrBind() {
        OperationContext context = CTX.get();
        return context == null ? bind() : context;
    }

    public static void clear() {
        CTX.remove();
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public boolean isDedupReplay() {
        return dedupReplay;
    }

    public void setDedupReplay(boolean dedupReplay) {
        this.dedupReplay = dedupReplay;
    }

    public String cachedOperatorName(Long userId, java.util.function.Function<Long, String> loader) {
        if (userId == null) {
            return null;
        }
        if (operatorNames.containsKey(userId)) {
            return operatorNames.get(userId);
        }
        String resolved = loader.apply(userId);
        operatorNames.put(userId, resolved);
        return resolved;
    }

    /**
     * 只登记，不落库。落库时机由内层切面统一决定，保证事件与业务写在同一事务里
     * 同生共死。
     */
    public void recordEvent(OperationEvent event) {
        if (event != null) {
            pendingEvents.add(event);
        }
    }

    public List<OperationEvent> drainPendingEvents() {
        if (pendingEvents.isEmpty()) {
            return List.of();
        }
        List<OperationEvent> drained = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return drained;
    }

    public int pendingEventCount() {
        return pendingEvents.size();
    }
}
