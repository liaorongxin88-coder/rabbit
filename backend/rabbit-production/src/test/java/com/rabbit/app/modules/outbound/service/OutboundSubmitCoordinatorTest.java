package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundSubmitCoordinatorTest {
    @Test
    void knownBusinessRollbackIsFinalizedAsFailed() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(
                new BizException(409, "笼位状态已变化: 10")
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);

        OutboundDtos.SubmitResult result = coordinator.submit(7L, 8L, "task-1", input);

        assertEquals("FAILED", result.status());
        assertEquals("BUSINESS_409", result.errorCode());
        assertEquals("BUSINESS_409", requestLifecycle.failedCode);
        assertEquals("笼位状态已变化: 10", requestLifecycle.failedMessage);
    }

    @Test
    void unexpectedFailureLeavesClaimProcessingForStatusRecovery() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(
                new IllegalStateException("commit outcome unknown")
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);

        assertThrows(IllegalStateException.class,
                () -> coordinator.submit(7L, 8L, "task-1", input));

        assertEquals(null, requestLifecycle.failedCode);
        assertEquals(false, requestLifecycle.completed);
    }

    private OutboundSubmitCoordinator coordinator(OutboundSubmitService businessService,
                                                   OutboundRequestLifecycleService requestLifecycle) {
        return new OutboundSubmitCoordinator(businessService, requestLifecycle, null, null, null);
    }

    private OutboundDtos.SubmitRequest request() {
        return new OutboundDtos.SubmitRequest(
                List.of(1L),
                Map.of("1", 0L),
                Map.of(),
                new Date(),
                3.2,
                null,
                null,
                null,
                UUID.randomUUID().toString()
        );
    }

    private static final class FakeBusinessService extends OutboundSubmitService {
        private final RuntimeException failure;

        private FakeBusinessService(RuntimeException failure) {
            super(null, null, null, null, null, null, null, null, null, null, null, null,
                    new ObjectMapper(), null);
            this.failure = failure;
        }

        @Override
        PreparedSubmission prepare(String taskId, OutboundDtos.SubmitRequest input) {
            return new PreparedSubmission(List.of(1L), "payload-hash");
        }

        @Override
        public void assertRequestPermission(Long userId, Long houseId, String taskId) {
        }

        @Override
        public OutboundDtos.SubmitResult executeClaimed(Long userId, Long houseId, String taskId,
                                                        OutboundDtos.SubmitRequest input) {
            throw failure;
        }
    }

    private static final class FakeRequestLifecycle extends OutboundRequestLifecycleService {
        private String failedCode;
        private String failedMessage;
        private boolean completed;

        private FakeRequestLifecycle() {
            super(null);
        }

        @Override
        public OutboundRequest find(Long houseId, String requestId) {
            return null;
        }

        @Override
        public ClaimResult claim(OutboundRequest request) {
            return new ClaimResult(true, request);
        }

        @Override
        public void markFailed(Long houseId, String requestId, String errorCode, String errorMessage) {
            failedCode = errorCode;
            failedMessage = errorMessage;
        }

        @Override
        public void markCompleted(Long houseId, String requestId, Long saleOrderId) {
            completed = true;
        }
    }
}
