package com.rabbit.app.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.mapper.RequestDedupMapper;
import com.rabbit.app.model.RequestDedup;
import org.springframework.stereotype.Service;

@Service
public class RequestDedupService {
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

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

    public void markFailed(Long houseId, Long userId, String api, String requestId, String errorMessage) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        requestDedupMapper.updateStatus(houseId, userId, api, requestId, STATUS_FAILED, errorMessage);
    }
}
