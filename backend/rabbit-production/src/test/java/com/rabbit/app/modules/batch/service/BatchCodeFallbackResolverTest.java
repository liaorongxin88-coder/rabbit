package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void generatesAHouseScopedShanghaiDateAndMinuteCodeForMissingCodes() {
        BatchCodeFallbackResolver resolver = new BatchCodeFallbackResolver(CLOCK);

        assertEquals("东一舍-20260203-1205", resolver.resolve(null, "东一舍"));
        assertEquals("东一舍-20260203-1205", resolver.resolve("  ", "东一舍"));
    }

    @Test
    void keepsExplicitCodes() {
        BatchCodeFallbackResolver resolver = new BatchCodeFallbackResolver(CLOCK);

        assertEquals("人工批次-复配", resolver.resolve("人工批次-复配", "东一舍"));
    }

    @Test
    void normalizesSeparatorsAndFallsBackForBlankHouseNames() {
        assertEquals(
                "东一-舍-A-20260203-1205",
                BatchCodeFallbackResolver.defaultBatchCode("  东一 / 舍--A  ", CLOCK.instant())
        );
        assertEquals(
                "兔舍-20260203-1205",
                BatchCodeFallbackResolver.defaultBatchCode(" /_- ", CLOCK.instant())
        );
    }

    @Test
    void truncatesLongHouseNamesToTheDatabaseLimit() {
        String generated = BatchCodeFallbackResolver.defaultBatchCode(
                "超长兔舍".repeat(30),
                CLOCK.instant()
        );

        assertEquals(100, generated.codePointCount(0, generated.length()));
        assertTrue(generated.endsWith("-20260203-1205"));
    }
}
