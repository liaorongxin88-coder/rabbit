package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.service.BatchStatisticsLegacyWriteService;
import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundTask;
import com.rabbit.app.modules.outbound.entity.OutboundTaskItem;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskBatchAllocationMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskItemMapper;
import com.rabbit.app.modules.outbound.mapper.OutboundTaskMapper;
import com.rabbit.app.modules.sale.dto.SaleBatchAllocationInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
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
    void compatibilityCheckRejectsLegacyMixedBatchBeforeBusinessExecution() {
        OutboundTaskMapper taskMapper = mock(OutboundTaskMapper.class);
        OutboundTaskItemMapper itemMapper = mock(OutboundTaskItemMapper.class);
        OutboundTaskBatchAllocationMapper allocationMapper = mock(
                OutboundTaskBatchAllocationMapper.class
        );
        BatchStatisticsLegacyWriteService legacy = mock(
                BatchStatisticsLegacyWriteService.class
        );
        OutboundTask task = new OutboundTask();
        task.setUnitPrice(BigDecimal.TEN);
        OutboundTaskItem first = new OutboundTaskItem();
        first.setBatchIdSnapshot(101L);
        OutboundTaskItem second = new OutboundTaskItem();
        second.setBatchIdSnapshot(102L);
        when(taskMapper.selectById(8L, 7L, "task")).thenReturn(task);
        when(itemMapper.selectByTask("task")).thenReturn(List.of(first, second));
        when(allocationMapper.selectByTask(8L, "task")).thenReturn(List.of());
        doThrow(new BizException(409, BatchStatisticsLegacyWriteService.UPGRADE_MESSAGE))
                .when(legacy).requireLegacyWriteEnabled();
        OutboundSubmitService guarded = new OutboundSubmitService(
                taskMapper, itemMapper, null, null, null, null, null, null, null, null, null, null,
                new ObjectMapper(), null, null, legacy, allocationMapper
        );

        OutboundDtos.SubmitRequest legacyInput = new OutboundDtos.SubmitRequest(
                List.of(1L, 2L),
                Map.of("1", 0L, "2", 0L),
                Map.of(),
                new Date(),
                4.0,
                BigDecimal.TEN,
                null,
                null,
                UUID.randomUUID().toString()
        );
        BizException error = assertThrows(BizException.class,
                () -> guarded.assertCompatibility(7L, 8L, "task", legacyInput));

        assertEquals(BatchStatisticsLegacyWriteService.UPGRADE_MESSAGE, error.getMessage());
    }

    @Test
    void preservesTheExactPersistedLegacyPayloadHash() {
        Date saleTime = new Date(1_700_000_000_000L);
        OutboundDtos.SubmitRequest request = new OutboundDtos.SubmitRequest(
                List.of(1L),
                Map.of("1", 0L),
                null,
                saleTime,
                1.0,
                new BigDecimal("12.00"),
                null,
                null,
                UUID.randomUUID().toString()
        );

        assertEquals(
                "84cf43307351ef78fe6972e9c95f37ba7b98583058d31895986829b842112d12",
                service.payloadHash("task", List.of(1L), request)
        );
    }

    @Test
    void newPayloadHasAStableTypedCanonicalHash() {
        Map<String, Long> stateVersions = new LinkedHashMap<>();
        stateVersions.put("2", 4L);
        stateVersions.put("1", 3L);
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put("2", "reason|:;");
        reasons.put("1", "");
        OutboundDtos.SubmitRequest request = new OutboundDtos.SubmitRequest(
            List.of(2L, 1L), stateVersions, reasons, new Date(1_700_000_000_000L), 4.0,
            new BigDecimal("12.0"), new BigDecimal("12.00"),
            List.of(
                new SaleBatchAllocationInput(null, new BigDecimal("1.500")),
                new SaleBatchAllocationInput(5L, new BigDecimal("2.500"))
            ),
            " 客|户 ", "", UUID.randomUUID().toString()
        );

        assertEquals(
            "b58d0417dbbcf458e29bf7367aa6e6b82038329cf2b3d953a0f67c75414a2570",
            service.payloadHash("task", List.of(1L, 2L), request)
        );
    }

    @Test
    void newPayloadAliasesShareOneNormalizedPriceHash() {
        Date saleTime = atStartOfDay(LocalDate.now());
        String requestId = UUID.randomUUID().toString();
        List<SaleBatchAllocationInput> allocations = List.of(
            new SaleBatchAllocationInput(101L, new BigDecimal("1.000"))
        );
        OutboundDtos.SubmitRequest legacyAlias = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            new BigDecimal("12.0"), null, allocations, "客户", "备注", requestId
        );
        OutboundDtos.SubmitRequest currentAlias = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            null, new BigDecimal("12.00"), allocations, "客户", "备注", requestId
        );
        OutboundDtos.SubmitRequest bothAliases = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            new BigDecimal("12"), new BigDecimal("12.00"), allocations,
            "客户", "备注", requestId
        );

        String expected = service.prepare("task", legacyAlias).payloadHash();
        assertEquals(expected, service.prepare("task", currentAlias).payloadHash());
        assertEquals(expected, service.prepare("task", bothAliases).payloadHash());
    }

    @Test
    void newPayloadHashIgnoresMapAndAllocationOrdering() {
        Date saleTime = atStartOfDay(LocalDate.now());
        Map<String, Long> forwardVersions = new LinkedHashMap<>();
        forwardVersions.put("1", 3L);
        forwardVersions.put("2", 4L);
        Map<String, Long> reverseVersions = new LinkedHashMap<>();
        reverseVersions.put("2", 4L);
        reverseVersions.put("1", 3L);
        Map<String, String> forwardReasons = new LinkedHashMap<>();
        forwardReasons.put("1", "first");
        forwardReasons.put("2", "second");
        Map<String, String> reverseReasons = new LinkedHashMap<>();
        reverseReasons.put("2", "second");
        reverseReasons.put("1", "first");
        List<SaleBatchAllocationInput> forwardAllocations = List.of(
            new SaleBatchAllocationInput(5L, new BigDecimal("2.500")),
            new SaleBatchAllocationInput(null, new BigDecimal("1.500"))
        );
        List<SaleBatchAllocationInput> reverseAllocations = List.of(
            new SaleBatchAllocationInput(null, new BigDecimal("1.500")),
            new SaleBatchAllocationInput(5L, new BigDecimal("2.500"))
        );
        OutboundDtos.SubmitRequest forward = new OutboundDtos.SubmitRequest(
            List.of(1L, 2L), forwardVersions, forwardReasons, saleTime, 4.0,
            null, new BigDecimal("12.00"), forwardAllocations,
            "客户", "备注", UUID.randomUUID().toString()
        );
        OutboundDtos.SubmitRequest reverse = new OutboundDtos.SubmitRequest(
            List.of(2L, 1L), reverseVersions, reverseReasons, saleTime, 4.0,
            new BigDecimal("12.0"), new BigDecimal("12.00"), reverseAllocations,
            "客户", "备注", UUID.randomUUID().toString()
        );

        assertEquals(
            service.prepare("task", forward).payloadHash(),
            service.prepare("task", reverse).payloadHash()
        );
    }

    @Test
    void newPayloadHashDoesNotCollideOnDelimiterBearingText() {
        Date saleTime = atStartOfDay(LocalDate.now());
        OutboundDtos.SubmitRequest first = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            "a|b", "c", UUID.randomUUID().toString()
        );
        OutboundDtos.SubmitRequest second = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            "a", "b|c", UUID.randomUUID().toString()
        );

        assertNotEquals(
            service.prepare("task", first).payloadHash(),
            service.prepare("task", second).payloadHash()
        );
    }

    @Test
    void newPayloadHashDistinguishesNullAndEmptyCollections() {
        Date saleTime = atStartOfDay(LocalDate.now());
        OutboundDtos.SubmitRequest nullCollections = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), null, saleTime, 1.0,
            null, new BigDecimal("12.00"), null,
            null, null, UUID.randomUUID().toString()
        );
        OutboundDtos.SubmitRequest emptyReasons = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            null, new BigDecimal("12.00"), null,
            null, null, UUID.randomUUID().toString()
        );
        OutboundDtos.SubmitRequest emptyAllocations = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), null, saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            null, null, UUID.randomUUID().toString()
        );

        assertNotEquals(
            service.prepare("task", nullCollections).payloadHash(),
            service.prepare("task", emptyReasons).payloadHash()
        );
        assertNotEquals(
            service.prepare("task", nullCollections).payloadHash(),
            service.prepare("task", emptyAllocations).payloadHash()
        );
    }

    @Test
    void surplusOrMissingMapKeysAreRejectedBeforeHashing() {
        Date saleTime = atStartOfDay(LocalDate.now());
        OutboundDtos.SubmitRequest surplusVersion = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L, "2", 0L), null, saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            null, null, UUID.randomUUID().toString()
        );
        OutboundDtos.SubmitRequest missingVersion = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of(), null, saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            null, null, UUID.randomUUID().toString()
        );
        OutboundDtos.SubmitRequest surplusReason = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of("2", "not selected"), saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            null, null, UUID.randomUUID().toString()
        );

        assertEquals("stateVersions必须与rabbitIds完全一致", assertThrows(
            BizException.class, () -> service.prepare("task", surplusVersion)
        ).getMessage());
        assertEquals("stateVersions必须与rabbitIds完全一致", assertThrows(
            BizException.class, () -> service.prepare("task", missingVersion)
        ).getMessage());
        assertEquals("earlySaleReasons包含未选择的兔只", assertThrows(
            BizException.class, () -> service.prepare("task", surplusReason)
        ).getMessage());

        OutboundDtos.SubmitRequest corrected = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            null, new BigDecimal("12.00"), List.of(),
            null, null, surplusVersion.requestId()
        );
        assertTrue(service.prepare("task", corrected).payloadHash().length() == 64);
    }

    @Test
    void duplicateAndNullAllocationEntriesAreRejectedBeforeHashing() {
        Date saleTime = atStartOfDay(LocalDate.now());
        OutboundDtos.SubmitRequest duplicate = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 2.0,
            null, new BigDecimal("12.00"), List.of(
                new SaleBatchAllocationInput(5L, new BigDecimal("1.000")),
                new SaleBatchAllocationInput(5L, new BigDecimal("1.000"))
            ), null, null, UUID.randomUUID().toString()
        );
        List<SaleBatchAllocationInput> withNull = new ArrayList<>();
        withNull.add(null);
        OutboundDtos.SubmitRequest nullEntry = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), saleTime, 1.0,
            null, new BigDecimal("12.00"), withNull,
            null, null, UUID.randomUUID().toString()
        );

        assertEquals("同一销售批次不能重复分配", assertThrows(
            BizException.class, () -> service.prepare("task", duplicate)
        ).getMessage());
        assertEquals("batchAllocations不能包含空项", assertThrows(
            BizException.class, () -> service.prepare("task", nullEntry)
        ).getMessage());
    }

    @Test
    void conflictingPriceAliasesAreRejectedBeforeHashing() {
        OutboundDtos.SubmitRequest request = new OutboundDtos.SubmitRequest(
            List.of(1L), Map.of("1", 0L), Map.of(), atStartOfDay(LocalDate.now()), 1.0,
            new BigDecimal("12.00"), new BigDecimal("12.01"), List.of(),
            null, null, UUID.randomUUID().toString()
        );

        assertEquals("unitPrice与unitPricePerKg不一致", assertThrows(BizException.class,
            () -> service.prepare("task", request)).getMessage());
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
