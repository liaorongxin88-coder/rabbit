package com.rabbit.app.modules.dedup.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.entity.RequestDedup;
import com.rabbit.app.modules.dedup.mapper.RequestDedupMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 请求幂等记账。
 *
 * <p><b>事务语义以前是意外而不是选择。</b>本类原先全类没有任何
 * {@code @Transactional}，所以每个方法都默默加入调用方的事务。
 * {@code WeightService} 等在 {@code @Transactional} 方法内部调 {@link #markFailed}
 * 然后重抛异常，回滚把失败标记一并抹掉——“这个 requestId 试过并且
 * 失败了”从来没能落地，去重记账形同虚设。
 *
 * <p><b>修法不是把 {@code markFailed} 改成 {@code REQUIRES_NEW}。</b>试过，会死锁：
 * 旧调用点在自己的事务里先调 {@code markProcessing} 写下 PROCESSING，
 * 持有该行的写锁；失败时再调 {@code markFailed}，若它另起一个事务去 update
 * 同一行，就要等外层事务释放锁——而外层事务正等着 {@code markFailed} 返回。
 * 自己等自己，直到 MySQL 的 innodb_lock_wait_timeout（50 秒）报
 * {@code Lock wait timeout exceeded}。双域试点跑 e2e 时六个用例全挂在这上面。
 *
 * <p>真正的修法是<b>把幂等记账整体挪出业务事务</b>，而不是给单个方法换传播行为。
 * {@code @TrackedOperation} 的外层切面排在事务通知之前（见
 * {@code OperationTrackingOrder}），markProcessing / markDone / markFailed 全部
 * 在事务外执行：PROCESSING 立即提交并释放锁，markFailed 时无锁可争，
 * 失败标记也不会随业务回滚消失。缺陷因此是被<b>结构</b>消掉的。
 *
 * <p>下面这些 {@code @Transactional} 一律是默认的 {@code REQUIRED}，语义与改造前
 * 完全一致（有事务就加入，没有就自开一个）。加它们只为把「加入调用方事务」
 * 从<b>意外</b>变成<b>明示</b>：写路径上任何人再动传播行为，都得先看见这段说明。
 * 尚未改造的旧调用点仍带原有缺陷，随 T2/T4 逐个迁到注解上时一并消失。
 */
@Service
public class RequestDedupService {
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_MESSAGE_LEN = 255;

    private final RequestDedupMapper requestDedupMapper;

    public RequestDedupService(RequestDedupMapper requestDedupMapper) {
        this.requestDedupMapper = requestDedupMapper;
    }

    public boolean shouldSkipAsDone(Long houseId, Long userId, String api, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        RequestDedup old = requestDedupMapper.selectByKey(houseId, userId, api, requestId);
        return old != null && STATUS_DONE.equals(old.getStatus());
    }

    /**
     * 自身也标注 {@code @Transactional}：它同类调用五参重载，Spring AOP 不拦截
     * 同类自调用，光靠被调方那个注解在这条路径上是失效的。这是 85 处
     * {@code @Transactional} 自调用排查里唯一一处真需要动的。
     */
    @Transactional
    public BeginResult begin(Long houseId, Long userId, String api, String requestId) {
        return begin(houseId, userId, api, requestId, null);
    }

    @Transactional
    public BeginResult begin(Long houseId, Long userId, String api, String requestId,
                             String payloadHash) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return BeginResult.STARTED;
        }
        RequestDedup item = new RequestDedup();
        item.setHouseId(houseId);
        item.setUserId(userId);
        item.setApi(api);
        item.setRequestId(requestId);
        item.setPayloadHash(payloadHash);
        item.setStatus(STATUS_PROCESSING);
        if (requestDedupMapper.insertIgnore(item) > 0) {
            return BeginResult.STARTED;
        }

        RequestDedup old = requestDedupMapper.selectByKey(houseId, userId, api, requestId);
        if (old == null) {
            throw new BizException(409, "请求幂等状态异常，请稍后重试");
        }
        if (!Objects.equals(payloadHash, old.getPayloadHash())) {
            throw new BizException(409, "requestId已用于不同的请求载荷");
        }
        if (STATUS_DONE.equals(old.getStatus())) {
            return BeginResult.DONE;
        }
        if (STATUS_PROCESSING.equals(old.getStatus())) {
            throw new BizException(429, "请求处理中，请稍后重试");
        }
        requestDedupMapper.updateStatus(houseId, userId, api, requestId, STATUS_PROCESSING, null);
        return BeginResult.STARTED;
    }

    @Transactional
    public void markProcessing(Long houseId, Long userId, String api, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        RequestDedup old = requestDedupMapper.selectByKey(houseId, userId, api, requestId);
        if (old != null) {
            if (STATUS_DONE.equals(old.getStatus())) {
                return;
            }
            if (STATUS_PROCESSING.equals(old.getStatus())) {
                throw new BizException(429, "请求处理中，请稍后重试");
            }
            requestDedupMapper.updateStatus(houseId, userId, api, requestId, STATUS_PROCESSING, null);
            return;
        }
        RequestDedup item = new RequestDedup();
        item.setHouseId(houseId);
        item.setUserId(userId);
        item.setApi(api);
        item.setRequestId(requestId);
        item.setStatus(STATUS_PROCESSING);
        item.setErrorMessage(null);
        requestDedupMapper.insert(item);
    }

    @Transactional
    public void markDone(Long houseId, Long userId, String api, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        requestDedupMapper.updateStatus(houseId, userId, api, requestId, STATUS_DONE, null);
    }

    @Transactional
    public void markDone(
        Long houseId,
        Long userId,
        String api,
        String requestId,
        String responsePayload
    ) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        if (requestDedupMapper.updateStatusWithResponse(
            houseId, userId, api, requestId, STATUS_DONE, responsePayload
        ) != 1) {
            throw new BizException(500, "幂等响应保存失败");
        }
    }

    public String getResponsePayload(Long houseId, Long userId, String api, String requestId) {
        RequestDedup item = requestDedupMapper.selectByKey(houseId, userId, api, requestId);
        return item == null ? null : item.getResponsePayload();
    }

    /**
     * 写失败标记。
     *
     * <p>这里<b>不能</b>用 {@code REQUIRES_NEW}：旧调用点是在自己的事务里先
     * markProcessing 再 markFailed 的，两次写的是同一行，另起事务会和外层
     * 事务争同一把行锁而外层又在等本方法返回，直接锁等待超时。
     * 让失败标记活过回滚要靠调用时机（事务外），不是靠传播行为。
     */
    @Transactional
    public void markFailed(Long houseId, Long userId, String api, String requestId, String errorMessage) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        requestDedupMapper.updateStatus(houseId, userId, api, requestId, STATUS_FAILED, truncate(errorMessage));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LEN) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LEN);
    }

    public enum BeginResult {
        STARTED,
        DONE
    }
}
