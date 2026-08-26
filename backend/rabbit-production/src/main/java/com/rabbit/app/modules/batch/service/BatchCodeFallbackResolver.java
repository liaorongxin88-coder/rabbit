package com.rabbit.app.modules.batch.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 客户端没传批次编号时补一个默认值。
 *
 * <p>格式是 {@code 批次-20260220-1530}，固定 16 个字符。这个编号会出现在提醒卡片上，
 * 和周期号、日期挤在同一行，所以必须短到不被截断。提醒卡片自己已经单独显示了兔舍名，
 * 编号里不必再带一遍。
 *
 * <p>只精确到分钟。同一兔舍在同一分钟内建两个批次才会撞名，而这只是个预填草稿，
 * 输入框里可以直接改掉；批次编号也没有唯一约束，撞名不会导致写入失败。
 */
@Service
public class BatchCodeFallbackResolver {
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmm"
    ).withZone(SHANGHAI_ZONE);

    private final Clock clock;

    @Autowired
    public BatchCodeFallbackResolver() {
        this(Clock.systemUTC());
    }

    BatchCodeFallbackResolver(Clock clock) {
        this.clock = clock;
    }

    public String resolve(String batchCode) {
        if (batchCode != null && !batchCode.trim().isEmpty()) {
            return batchCode;
        }
        return defaultBatchCode(clock.instant());
    }

    static String defaultBatchCode(Instant instant) {
        return "批次-" + TIMESTAMP_FORMAT.format(instant);
    }
}
