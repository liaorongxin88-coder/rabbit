package com.rabbit.app.modules.outbound.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundRequest;
import com.rabbit.app.modules.outbound.entity.OutboundTask;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskItemMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskMapper;
import com.rabbit.app.modules.sale.mapper.SaleOrderMapper;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OutboundSubmitCoordinator {
    private final OutboundSubmitService businessService;
    private final OutboundRequestLifecycleService requestLifecycle;
    private final OutboundTaskMapper taskMapper;
    private final OutboundTaskItemMapper taskItemMapper;
    private final SaleOrderMapper saleOrderMapper;

    public OutboundSubmitCoordinator(OutboundSubmitService businessService,
                                     OutboundRequestLifecycleService requestLifecycle,
                                     OutboundTaskMapper taskMapper,
                                     OutboundTaskItemMapper taskItemMapper,
                                     SaleOrderMapper saleOrderMapper) {
        this.businessService = businessService;
        this.requestLifecycle = requestLifecycle;
        this.taskMapper = taskMapper;
        this.taskItemMapper = taskItemMapper;
        this.saleOrderMapper = saleOrderMapper;
    }

    public OutboundDtos.SubmitResult submit(Long userId, Long houseId, String taskId,
                                            OutboundDtos.SubmitRequest input) {
        OutboundSubmitService.PreparedSubmission prepared = businessService.prepare(taskId, input);
        OutboundRequest existing = requestLifecycle.find(houseId, input.requestId());
        if (existing != null) {
            assertMatchingRequest(existing, taskId, prepared.payloadHash());
            OutboundDtos.SubmitResult existingResult = resultForExisting(userId, houseId, existing);
            if (!"FAILED".equals(existingResult.status())) {
                return existingResult;
            }
        }

        // Keep validation and authorization failures that are known to happen before processing
        // outside the durable claim. The transactional business operation repeats both checks.
        businessService.assertRequestPermission(userId, houseId, taskId);
        businessService.assertCompatibility(userId, houseId, taskId, input);

        OutboundRequest request = existing == null
            ? newRequest(houseId, taskId, input.requestId(), prepared.payloadHash())
            : existing;
        OutboundRequestLifecycleService.ClaimResult claim = existing == null
            ? requestLifecycle.claim(request)
            : requestLifecycle.reclaimFailed(request);
        if (!claim.claimed()) {
            assertMatchingRequest(claim.request(), taskId, prepared.payloadHash());
            return resultForExisting(userId, houseId, claim.request());
        }

        OutboundDtos.SubmitResult result;
        try {
            result = businessService.executeClaimed(userId, houseId, taskId, input);
        } catch (BizException error) {
            String errorCode = deterministicErrorCode(error);
            String errorMessage = deterministicErrorMessage(error.getMessage());
            requestLifecycle.markFailed(houseId, input.requestId(), errorCode, errorMessage);
            return failedResult(input.requestId(), taskId, errorCode, errorMessage);
        } catch (RuntimeException error) {
            OutboundDtos.SubmitResult completed = recoverCompletedAfterUnexpectedFailure(
                userId, houseId, request, error
            );
            if (completed != null) {
                return completed;
            }
            try {
                requestLifecycle.markFailed(
                    houseId,
                    input.requestId(),
                    "INTERNAL_ERROR",
                    "本次出库未生效，请使用相同请求重试"
                );
            } catch (RuntimeException lifecycleError) {
                error.addSuppressed(lifecycleError);
            }
            throw error;
        }

        if ("COMPLETED".equals(result.status()) && result.saleOrderId() != null) {
            requestLifecycle.markCompleted(houseId, input.requestId(), result.saleOrderId());
            return result;
        }
        if ("CONFLICT".equals(result.status())) {
            requestLifecycle.markConflict(houseId, input.requestId(), result.errorCode(), result.message(),
                    businessService.serializeConflicts(result.conflicts()));
            return result;
        }
        throw new BizException(500, "出库业务事务返回了未知状态");
    }

    private OutboundRequest newRequest(
        Long houseId,
        String taskId,
        String requestId,
        String payloadHash
    ) {
        OutboundRequest request = new OutboundRequest();
        request.setRequestId(requestId);
        request.setHouseId(houseId);
        request.setTaskId(taskId);
        request.setPayloadHash(payloadHash);
        request.setStatus("PROCESSING");
        return request;
    }

    public OutboundDtos.SubmitResult status(Long userId, Long houseId, String requestId) {
        OutboundSubmitService.validateRequestId(requestId);
        OutboundRequest request = requestLifecycle.find(houseId, requestId);
        if (request == null) {
            throw new BizException(404, "SUBMIT_REQUEST_NOT_FOUND");
        }
        return resultForExisting(userId, houseId, request);
    }

    private OutboundDtos.SubmitResult resultForExisting(Long userId, Long houseId, OutboundRequest request) {
        OutboundTask task = taskMapper.selectById(houseId, userId, request.getTaskId());
        if (task == null) {
            throw new BizException(404, "OUTBOUND_TASK_NOT_FOUND");
        }

        if (Set.of("PROCESSING", "FAILED").contains(request.getStatus())
                && "COMPLETED".equals(task.getStatus())
                && task.getSaleOrderId() != null
                && Objects.equals(request.getRequestId(), task.getRequestId())) {
            requestLifecycle.markCompleted(houseId, request.getRequestId(), task.getSaleOrderId());
            request.setStatus("COMPLETED");
            request.setSaleOrderId(task.getSaleOrderId());
        }

        if ("COMPLETED".equals(request.getStatus()) && request.getSaleOrderId() != null) {
            return businessService.completedResult(task,
                    saleOrderMapper.selectById(houseId, request.getSaleOrderId()),
                    taskItemMapper.selectByTask(task.getTaskId()));
        }
        if ("CONFLICT".equals(request.getStatus())) {
            return new OutboundDtos.SubmitResult("CONFLICT", request.getRequestId(), task.getTaskId(),
                    null, null, null, 0, 0, 0, null, null, request.getErrorCode(),
                    request.getErrorMessage(), businessService.deserializeConflicts(request.getConflictsJson()));
        }
        if ("FAILED".equals(request.getStatus())) {
            return failedResult(request.getRequestId(), task.getTaskId(), request.getErrorCode(),
                    request.getErrorMessage());
        }
        return new OutboundDtos.SubmitResult("PROCESSING", request.getRequestId(), task.getTaskId(),
                null, null, null, 0, 0, 0, null, null, null,
                "正在确认提交结果，请勿重复操作", List.of());
    }

    private OutboundDtos.SubmitResult recoverCompletedAfterUnexpectedFailure(
        Long userId,
        Long houseId,
        OutboundRequest request,
        RuntimeException originalError
    ) {
        try {
            OutboundDtos.SubmitResult result = resultForExisting(userId, houseId, request);
            return "COMPLETED".equals(result.status()) ? result : null;
        } catch (RuntimeException recoveryError) {
            originalError.addSuppressed(recoveryError);
            return null;
        }
    }

    private void assertMatchingRequest(OutboundRequest request, String taskId, String payloadHash) {
        if (!Objects.equals(request.getPayloadHash(), payloadHash)
                || !Objects.equals(request.getTaskId(), taskId)) {
            throw new BizException(409, "REQUEST_ID_PAYLOAD_MISMATCH");
        }
    }

    private OutboundDtos.SubmitResult failedResult(String requestId, String taskId,
                                                   String errorCode, String message) {
        return new OutboundDtos.SubmitResult("FAILED", requestId, taskId, null, null, null,
                0, 0, 0, null, null, errorCode,
                message == null || message.isBlank() ? "本次出库未生效，草稿已保留" : message,
                List.of());
    }

    private String deterministicErrorCode(BizException error) {
        String message = error.getMessage();
        if (message != null) {
            int separator = message.indexOf(':');
            String candidate = separator < 0 ? message : message.substring(0, separator);
            if (candidate.matches("[A-Z][A-Z0-9_]{2,63}")) {
                return candidate;
            }
        }
        return "BUSINESS_" + error.getCode();
    }

    private String deterministicErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "本次出库未生效，草稿已保留";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
