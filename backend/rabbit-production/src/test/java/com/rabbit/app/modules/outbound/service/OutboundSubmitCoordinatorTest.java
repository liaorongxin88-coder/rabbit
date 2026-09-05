package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundRequest;
import com.rabbit.app.modules.outbound.entity.OutboundTask;
import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskItemMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskMapper;
import com.rabbit.app.modules.sale.entity.SaleOrder;
import com.rabbit.app.modules.sale.mapper.SaleOrderMapper;
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
    void failedRequestWithTheSamePayloadIsReclaimedAndExecutedAgain() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(
                new BizException(409, "笼位状态已变化: 10")
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);

        assertEquals("FAILED", coordinator.submit(7L, 8L, "task-1", input).status());
        OutboundDtos.SubmitResult retried = coordinator.submit(7L, 8L, "task-1", input);

        assertEquals("COMPLETED", retried.status());
        assertEquals(99L, retried.saleOrderId());
        assertEquals(1, requestLifecycle.reclaimed);
        assertEquals(true, requestLifecycle.completed);
        assertEquals(2, businessService.executions);
    }

    @Test
    void failedRequestWithChangedPayloadStillConflicts() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(
                new BizException(409, "笼位状态已变化: 10")
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);
        coordinator.submit(7L, 8L, "task-1", input);
        OutboundDtos.SubmitRequest changed = new OutboundDtos.SubmitRequest(
            input.rabbitIds(), input.stateVersions(), input.earlySaleReasons(), input.saleTime(),
            input.totalWeight(), input.unitPrice(), input.unitPricePerKg(), input.batchAllocations(),
            "changed", input.remark(), input.requestId()
        );

        BizException error = assertThrows(BizException.class,
            () -> coordinator.submit(7L, 8L, "task-1", changed));

        assertEquals("REQUEST_ID_PAYLOAD_MISMATCH", error.getMessage());
        assertEquals(0, requestLifecycle.reclaimed);
        assertEquals(1, businessService.executions);
    }

    @Test
    void compatibilityRejectionDoesNotClaimOrReclaimTheRequest() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(null);
        businessService.compatibilityFailure = new BizException(
            409, "当前版本过低，请升级应用后重试"
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);

        BizException error = assertThrows(BizException.class,
            () -> coordinator.submit(7L, 8L, "task-1", input));

        assertEquals("当前版本过低，请升级应用后重试", error.getMessage());
        assertEquals(0, requestLifecycle.claimed);
        assertEquals(0, requestLifecycle.reclaimed);
        assertEquals(0, businessService.executions);
    }

    @Test
    void unexpectedRuntimeRollbackIsFailedAndCanRetryWithTheSameRequestId() {
        OutboundDtos.SubmitRequest input = request();
        IllegalStateException failure = new IllegalStateException("transaction rolled back");
        FakeBusinessService businessService = new FakeBusinessService(failure);
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);

        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> coordinator.submit(7L, 8L, "task-1", input)));
        assertEquals("INTERNAL_ERROR", requestLifecycle.failedCode);
        assertEquals("FAILED", requestLifecycle.stored.getStatus());

        OutboundDtos.SubmitResult retried = coordinator.submit(7L, 8L, "task-1", input);

        assertEquals("COMPLETED", retried.status());
        assertEquals(99L, retried.saleOrderId());
        assertEquals(1, requestLifecycle.reclaimed);
        assertEquals(2, businessService.executions);
    }

    @Test
    void ambiguousRuntimeRecoversACompletedMatchingTaskWithoutReexecution() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(
            new IllegalStateException("commit outcome unknown")
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        CoordinatorFixture fixture = fixture(businessService, requestLifecycle);
        businessService.beforeFailure = () -> complete(fixture.task(), input.requestId());

        OutboundDtos.SubmitResult result = fixture.coordinator().submit(
            7L, 8L, "task-1", input
        );

        assertEquals("COMPLETED", result.status());
        assertEquals(99L, result.saleOrderId());
        assertEquals(true, requestLifecycle.completed);
        assertEquals(null, requestLifecycle.failedCode);
        assertEquals(1, businessService.executions);
    }

    @Test
    void completedTaskOverridesAFailedLifecycleBeforeReclaim() {
        OutboundDtos.SubmitRequest input = request();
        FakeBusinessService businessService = new FakeBusinessService(
            new BizException(409, "笼位状态已变化: 10")
        );
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        CoordinatorFixture fixture = fixture(businessService, requestLifecycle);
        assertEquals("FAILED", fixture.coordinator().submit(
            7L, 8L, "task-1", input
        ).status());
        complete(fixture.task(), input.requestId());

        OutboundDtos.SubmitResult result = fixture.coordinator().submit(
            7L, 8L, "task-1", input
        );

        assertEquals("COMPLETED", result.status());
        assertEquals(0, requestLifecycle.reclaimed);
        assertEquals(1, businessService.executions);
    }

    @Test
    void markFailedFailureDoesNotReplaceTheOriginalRuntime() {
        OutboundDtos.SubmitRequest input = request();
        IllegalStateException original = new IllegalStateException("transaction rolled back");
        IllegalStateException lifecycleFailure = new IllegalStateException("request store unavailable");
        FakeBusinessService businessService = new FakeBusinessService(original);
        FakeRequestLifecycle requestLifecycle = new FakeRequestLifecycle();
        requestLifecycle.markFailedFailure = lifecycleFailure;
        OutboundSubmitCoordinator coordinator = coordinator(businessService, requestLifecycle);

        RuntimeException thrown = assertThrows(RuntimeException.class,
            () -> coordinator.submit(7L, 8L, "task-1", input));

        assertSame(original, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(lifecycleFailure, thrown.getSuppressed()[0]);
    }

    private OutboundSubmitCoordinator coordinator(OutboundSubmitService businessService,
                                                   OutboundRequestLifecycleService requestLifecycle) {
        return fixture(businessService, requestLifecycle).coordinator();
    }

    private CoordinatorFixture fixture(OutboundSubmitService businessService,
                                       OutboundRequestLifecycleService requestLifecycle) {
        OutboundTaskMapper taskMapper = mock(OutboundTaskMapper.class);
        OutboundTaskItemMapper taskItemMapper = mock(OutboundTaskItemMapper.class);
        SaleOrderMapper saleOrderMapper = mock(SaleOrderMapper.class);
        OutboundTask task = new OutboundTask();
        task.setTaskId("task-1");
        task.setStatus("WAITING_CONFIRMATION");
        when(taskMapper.selectById(8L, 7L, "task-1")).thenReturn(task);
        when(taskItemMapper.selectByTask("task-1")).thenReturn(List.of());
        return new CoordinatorFixture(
            new OutboundSubmitCoordinator(
                businessService,
                requestLifecycle,
                taskMapper,
                taskItemMapper,
                saleOrderMapper
            ),
            task
        );
    }

    private void complete(OutboundTask task, String requestId) {
        task.setStatus("COMPLETED");
        task.setRequestId(requestId);
        task.setSaleOrderId(99L);
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

    private record CoordinatorFixture(
        OutboundSubmitCoordinator coordinator,
        OutboundTask task
    ) {}

    private static final class FakeBusinessService extends OutboundSubmitService {
        private RuntimeException failure;
        private RuntimeException compatibilityFailure;
        private Runnable beforeFailure = () -> {};
        private int executions;

        private FakeBusinessService(RuntimeException failure) {
            super(null, null, null, null, null, null, null, null, null, null, null, null,
                    new ObjectMapper(), null);
            this.failure = failure;
        }

        @Override
        PreparedSubmission prepare(String taskId, OutboundDtos.SubmitRequest input) {
            String suffix = input.customer() == null ? "" : ":" + input.customer();
            return new PreparedSubmission(List.of(1L), "payload-hash" + suffix);
        }

        @Override
        public void assertRequestPermission(Long userId, Long houseId, String taskId) {
        }

        @Override
        public void assertCompatibility(
            Long userId,
            Long houseId,
            String taskId,
            OutboundDtos.SubmitRequest input
        ) {
            if (compatibilityFailure != null) {
                throw compatibilityFailure;
            }
        }

        @Override
        public OutboundDtos.SubmitResult executeClaimed(Long userId, Long houseId, String taskId,
                                                        OutboundDtos.SubmitRequest input) {
            executions++;
            if (failure != null) {
                RuntimeException current = failure;
                failure = null;
                beforeFailure.run();
                throw current;
            }
            return new OutboundDtos.SubmitResult(
                "COMPLETED", input.requestId(), taskId, 99L, "SO-99", input.saleTime(),
                1, 1, 1, input.totalWeight(), null, null, "本次出库已完成", List.of()
            );
        }

        @Override
        OutboundDtos.SubmitResult completedResult(
            OutboundTask task,
            SaleOrder order,
            List<OutboundTaskItem> items
        ) {
            return new OutboundDtos.SubmitResult(
                "COMPLETED", task.getRequestId(), task.getTaskId(), task.getSaleOrderId(),
                "SO-" + task.getSaleOrderId(), null, items.size(), 0, 0,
                null, null, null, "本次出库已完成", List.of()
            );
        }
    }

    private static final class FakeRequestLifecycle extends OutboundRequestLifecycleService {
        private String failedCode;
        private String failedMessage;
        private boolean completed;
        private int claimed;
        private int reclaimed;
        private RuntimeException markFailedFailure;
        private OutboundRequest stored;

        private FakeRequestLifecycle() {
            super(null);
        }

        @Override
        public OutboundRequest find(Long houseId, String requestId) {
            return stored;
        }

        @Override
        public ClaimResult claim(OutboundRequest request) {
            claimed++;
            stored = request;
            return new ClaimResult(true, request);
        }

        @Override
        public ClaimResult reclaimFailed(OutboundRequest request) {
            if (stored == null || !"FAILED".equals(stored.getStatus())) {
                return new ClaimResult(false, stored);
            }
            reclaimed++;
            stored.setStatus("PROCESSING");
            return new ClaimResult(true, stored);
        }

        @Override
        public void markFailed(Long houseId, String requestId, String errorCode, String errorMessage) {
            if (markFailedFailure != null) {
                throw markFailedFailure;
            }
            failedCode = errorCode;
            failedMessage = errorMessage;
            stored.setStatus("FAILED");
            stored.setErrorCode(errorCode);
            stored.setErrorMessage(errorMessage);
        }

        @Override
        public void markCompleted(Long houseId, String requestId, Long saleOrderId) {
            completed = true;
            stored.setStatus("COMPLETED");
            stored.setSaleOrderId(saleOrderId);
        }
    }
}
