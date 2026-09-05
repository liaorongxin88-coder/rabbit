package com.rabbit.app.modules.outbound.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.service.OutboundSubmitCoordinator;
import com.rabbit.app.modules.outbound.service.OutboundTaskService;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import com.rabbit.app.security.AuthContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboundControllerTest {
    private static final Long USER_ID = 7L;
    private static final Long HOUSE_ID = 8L;
    private static final String TASK_ID = "task-1";

    private final HouseService houseService = mock(HouseService.class);
    private final OutboundTaskService taskService = mock(OutboundTaskService.class);
    private final OutboundSubmitCoordinator submitCoordinator = mock(OutboundSubmitCoordinator.class);
    private final OutboundController controller = new OutboundController(
        houseService, taskService, submitCoordinator
    );
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @AfterEach
    void clearAuthentication() {
        AuthContext.clear();
    }

    @Test
    void saveDelegatesTheHouseScopedAllocationDraftAndReturnsIt() {
        AuthContext.setUserId(USER_ID);
        List<SaleBatchAllocationInput> allocations = List.of(
            new SaleBatchAllocationInput(101L, new BigDecimal("2.500")),
            new SaleBatchAllocationInput(null, new BigDecimal("1.500"))
        );
        OutboundDtos.SaveDraftRequest request = new OutboundDtos.SaveDraftRequest(
            0L,
            "WAITING_CONFIRMATION",
            List.of(),
            null,
            4.0,
            null,
            new BigDecimal("12.00"),
            allocations,
            null,
            null
        );
        OutboundDtos.TaskView view = new OutboundDtos.TaskView(
            TASK_ID,
            HOUSE_ID,
            "HOUSE",
            null,
            null,
            null,
            "WAITING_CONFIRMATION",
            1L,
            null,
            4.0,
            new BigDecimal("12.00"),
            new BigDecimal("12.00"),
            allocations,
            null,
            null,
            null,
            false,
            null,
            List.of(),
            List.of()
        );
        when(taskService.save(USER_ID, HOUSE_ID, TASK_ID, request)).thenReturn(view);

        ApiResponse<OutboundDtos.TaskView> response = controller.save(HOUSE_ID, TASK_ID, request);

        verify(houseService).assertHousePermission(USER_ID, HOUSE_ID, "edit");
        verify(taskService).save(USER_ID, HOUSE_ID, TASK_ID, request);
        assertSame(view, response.getData());
        assertEquals(allocations, response.getData().batchAllocations());
    }

    @Test
    void controllerBoundaryRejectsNullItemsMalformedAllocationsAndPrecision() {
        OutboundDtos.SaveDraftRequest nullItem = new OutboundDtos.SaveDraftRequest(
            0L,
            "SELECTING",
            Collections.singletonList(null),
            null,
            null,
            null,
            null,
            null
        );
        OutboundDtos.SaveDraftRequest malformed = requestWithAllocations(
            Collections.singletonList(null)
        );
        OutboundDtos.SaveDraftRequest excessivePrecision = requestWithAllocations(List.of(
            new SaleBatchAllocationInput(101L, new BigDecimal("1.0001"))
        ));

        assertEquals(Set.of("items不能包含空项"), messages(nullItem));
        assertEquals(Set.of("batchAllocations不能包含空项"), messages(malformed));
        assertEquals(Set.of("actualWeightKg最多保留三位小数"), messages(excessivePrecision));
    }

    private OutboundDtos.SaveDraftRequest requestWithAllocations(
        List<SaleBatchAllocationInput> allocations
    ) {
        return new OutboundDtos.SaveDraftRequest(
            0L,
            "SELECTING",
            List.of(),
            null,
            null,
            null,
            null,
            allocations,
            null,
            null
        );
    }

    private Set<String> messages(OutboundDtos.SaveDraftRequest request) {
        return validator.validate(request).stream()
            .map(violation -> violation.getMessage())
            .collect(Collectors.toSet());
    }
}
