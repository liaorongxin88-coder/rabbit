package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundSubmitServiceTest {
    private final OutboundSubmitService service = new OutboundSubmitService(
            null, null, null, null, null, null, null, null, null, null, null, null,
            new ObjectMapper(), null
    );

    @Test
    void conflictDetailsRoundTripThroughPersistedJson() {
        List<OutboundDtos.RabbitConflict> conflicts = List.of(
                new OutboundDtos.RabbitConflict(
                        9L,
                        "RABBIT_STATE_CHANGED",
                        "隔离",
                        "状态已变化",
                        "移出"
                )
        );

        List<OutboundDtos.RabbitConflict> restored =
                service.deserializeConflicts(service.serializeConflicts(conflicts));

        assertEquals(1, restored.size());
        assertEquals(conflicts.getFirst(), restored.getFirst());
    }

    @Test
    void frozenEarlySaleItemRequiresControl() {
        OutboundTaskItem normal = new OutboundTaskItem();
        normal.setSelectionType("NORMAL");
        OutboundTaskItem early = new OutboundTaskItem();
        early.setSelectionType("EARLY_SALE");

        assertFalse(OutboundSubmitService.requiresControl(List.of(normal)));
        assertTrue(OutboundSubmitService.requiresControl(List.of(normal, early)));
    }

    @Test
    void requestIdMustUseCanonicalUuidRepresentation() {
        String canonical = UUID.randomUUID().toString();
        OutboundSubmitService.validateRequestId(canonical);
        assertThrows(BizException.class,
                () -> OutboundSubmitService.validateRequestId(canonical.toUpperCase()));
        assertThrows(BizException.class,
                () -> OutboundSubmitService.validateRequestId("not-a-uuid"));
    }

    @Test
    void validatesDocumentedFormBoundariesBeforeClaimingARequest() {
        Date today = atStartOfDay(LocalDate.now());
        Date oldestAllowed = atStartOfDay(LocalDate.now().minusDays(30));
        service.prepare("task", request(today, 1.0, BigDecimal.ZERO, "c".repeat(100), "r".repeat(2000)));
        service.prepare("task", request(oldestAllowed, 100000.0, new BigDecimal("99999.99"), null, null));

        assertThrows(BizException.class,
                () -> service.prepare("task", request(atStartOfDay(LocalDate.now().plusDays(1)), 1.0, null, null, null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(atStartOfDay(LocalDate.now().minusDays(31)), 1.0, null, null, null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(today, 0.0, null, null, null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(today, 100000.01, null, null, null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(today, 1.0, new BigDecimal("-0.01"), null, null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(today, 100000.0, new BigDecimal("99999999.99"), null, null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(today, 1.0, null, "c".repeat(101), null)));
        assertThrows(BizException.class,
                () -> service.prepare("task", request(today, 1.0, null, null, "r".repeat(2001))));
    }

    private OutboundDtos.SubmitRequest request(Date saleTime, double totalWeight, BigDecimal unitPrice,
                                                String customer, String remark) {
        return new OutboundDtos.SubmitRequest(
                List.of(1L),
                Map.of("1", 0L),
                null,
                saleTime,
                totalWeight,
                unitPrice,
                customer,
                remark,
                UUID.randomUUID().toString()
        );
    }

    private Date atStartOfDay(LocalDate value) {
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
