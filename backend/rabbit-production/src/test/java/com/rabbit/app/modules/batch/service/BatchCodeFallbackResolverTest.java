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
    void generatesAShanghaiDateAndMinuteCodeForMissingOrBlankCodes() {
        BatchCodeFallbackResolver resolver = new BatchCodeFallbackResolver(CLOCK);

        assertEquals("批次-20260203-1205", resolver.resolve(null));
        assertEquals("批次-20260203-1205", resolver.resolve("  "));
    }

    @Test
    void keepsExplicitCodes() {
        BatchCodeFallbackResolver resolver = new BatchCodeFallbackResolver(CLOCK);

        assertEquals("人工批次-复配", resolver.resolve("人工批次-复配"));
    }

    /**
     * 编号要显示在提醒卡片上，和周期号、日期挤一行，所以生成值必须短。
     * 旧格式带兔舍名加 17 位毫秒戳，兔舍名一长就被省略号截掉。
     */
    @Test
    void staysShortEnoughForTheReminderChip() {
        String generated = BatchCodeFallbackResolver.defaultBatchCode(CLOCK.instant());

        assertEquals(16, generated.length());
        assertTrue(generated.startsWith("批次-"));
    }
}
