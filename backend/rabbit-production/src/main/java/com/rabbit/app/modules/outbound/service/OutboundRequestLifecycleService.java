package com.rabbit.app.modules.outbound.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.outbound.entity.OutboundRequest;
import com.rabbit.app.modules.outbound.mapper.OutboundRequestMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundRequestLifecycleService {
    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final OutboundRequestMapper requestMapper;

    public OutboundRequestLifecycleService(OutboundRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public OutboundRequest find(Long houseId, String requestId) {
        return requestMapper.selectById(houseId, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claim(OutboundRequest request) {
        if (requestMapper.insertIgnore(request) > 0) {
            return new ClaimResult(true, request);
        }
        OutboundRequest existing = requestMapper.selectById(request.getHouseId(), request.getRequestId());
        if (existing == null) {
            throw new BizException(409, "REQUEST_ID_SCOPE_MISMATCH");
        }
        return new ClaimResult(false, existing);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult reclaimFailed(OutboundRequest request) {
        if (requestMapper.reclaimFailed(
                request.getHouseId(),
                request.getRequestId(),
                request.getTaskId(),
                request.getPayloadHash()
        ) == 1) {
            request.setStatus("PROCESSING");
            request.setErrorCode(null);
            request.setErrorMessage(null);
            request.setConflictsJson(null);
            request.setSaleOrderId(null);
            return new ClaimResult(true, request);
        }
        OutboundRequest current = requestMapper.selectById(
            request.getHouseId(),
            request.getRequestId()
        );
        if (current == null) {
            throw new BizException(409, "REQUEST_ID_SCOPE_MISMATCH");
        }
        return new ClaimResult(false, current);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markConflict(Long houseId, String requestId, String errorCode,
                             String errorMessage, String conflictsJson) {
        int updated = requestMapper.markConflict(houseId, requestId, truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH), conflictsJson);
        verifyFinalState(houseId, requestId, "CONFLICT", null, updated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long houseId, String requestId, String errorCode, String errorMessage) {
        int updated = requestMapper.markFailed(houseId, requestId, truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                truncate(errorMessage, MAX_ERROR_MESSAGE_LENGTH));
        verifyFinalState(houseId, requestId, "FAILED", null, updated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long houseId, String requestId, Long saleOrderId) {
        int updated = requestMapper.markCompleted(houseId, requestId, saleOrderId);
        verifyFinalState(houseId, requestId, "COMPLETED", saleOrderId, updated);
    }

    private void verifyFinalState(Long houseId, String requestId, String expectedStatus,
                                  Long expectedSaleOrderId, int updated) {
        if (updated == 1) {
            return;
        }
        OutboundRequest current = requestMapper.selectById(houseId, requestId);
        if (current != null
                && expectedStatus.equals(current.getStatus())
                && (expectedSaleOrderId == null
                || Objects.equals(expectedSaleOrderId, current.getSaleOrderId()))) {
            return;
        }
        throw new BizException(500, "提交请求最终状态写入失败");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record ClaimResult(boolean claimed, OutboundRequest request) {}
}
