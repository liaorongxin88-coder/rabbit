package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.modules.house.service.HouseService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BatchCodeFallbackResolverTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-02-03T04:05:06.007Z"),
            ZoneOffset.UTC
    );

    @Test
    void usesTheValidatedHouseNameAndShanghaiTimeForMissingOrBlankCodes() {
        HouseService houseService = mock(HouseService.class);
        when(houseService.requireHouseName(1L)).thenReturn("东一舍");
        when(houseService.requireHouseName(2L)).thenReturn("西二舍");
        BatchCodeFallbackResolver resolver = new BatchCodeFallbackResolver(houseService, CLOCK);

        assertEquals("东一舍-批次-20260203120506007", resolver.resolve(1L, null));
        assertEquals("西二舍-批次-20260203120506007", resolver.resolve(2L, "  "));
        verify(houseService).requireHouseName(1L);
        verify(houseService).requireHouseName(2L);
    }

    @Test
    void keepsExplicitCodesAndCapsGeneratedCodesAtTheDatabaseLimit() {
        HouseService houseService = mock(HouseService.class);
        BatchCodeFallbackResolver resolver = new BatchCodeFallbackResolver(houseService, CLOCK);

        assertEquals("人工批次-复配", resolver.resolve(1L, "人工批次-复配"));
        verifyNoInteractions(houseService);

        String generated = BatchCodeFallbackResolver.defaultBatchCode(
                "兔".repeat(100),
                CLOCK.instant()
        );
        assertEquals(100, generated.length());
        assertTrue(generated.endsWith("-批次-20260203120506007"));
    }
}
