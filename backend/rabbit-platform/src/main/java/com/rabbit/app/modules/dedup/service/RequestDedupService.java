package com.rabbit.app.modules.dedup.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.entity.RequestDedup;
import com.rabbit.app.modules.dedup.mapper.RequestDedupMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;

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

    public BeginResult begin(Long houseId, Long userId, String api, String requestId) {
        return begin(houseId, userId, api, requestId, null);
    }

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

    public void markDone(Long houseId, Long userId, String api, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        requestDedupMapper.updateStatus(houseId, userId, api, requestId, STATUS_DONE, null);
    }

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
