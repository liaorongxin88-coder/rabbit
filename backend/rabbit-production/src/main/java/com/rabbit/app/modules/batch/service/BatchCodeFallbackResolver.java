package com.rabbit.app.modules.batch.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 客户端没传批次编号时，按兔舍名称和农场时间补一个默认值。 */
@Service
public class BatchCodeFallbackResolver {
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmm"
    ).withZone(SHANGHAI_ZONE);
    private static final Pattern HOUSE_NAME_SEPARATORS = Pattern.compile(
            "[\\s\\-_/\\u2013\\u2014]+"
    );
    private static final int MAX_BATCH_CODE_LENGTH = 100;
    private static final int BATCH_TIMESTAMP_LENGTH = 13;
    private static final String DEFAULT_HOUSE_NAME = "兔舍";

    private final Clock clock;

    @Autowired
    public BatchCodeFallbackResolver() {
        this(Clock.systemUTC());
    }

    BatchCodeFallbackResolver(Clock clock) {
        this.clock = clock;
    }

    public String resolve(String batchCode, String houseName) {
        if (batchCode != null && !batchCode.trim().isEmpty()) {
            return batchCode;
        }
        return defaultBatchCode(houseName, clock.instant());
    }

    static String defaultBatchCode(String houseName, Instant instant) {
        String normalizedHouseName = normalizeHouseName(houseName);
        int maxHouseNameLength = MAX_BATCH_CODE_LENGTH - BATCH_TIMESTAMP_LENGTH - 1;
        String safeHouseName = truncateCodePoints(normalizedHouseName, maxHouseNameLength);
        return safeHouseName + "-" + TIMESTAMP_FORMAT.format(instant);
    }

    private static String normalizeHouseName(String value) {
        String normalized = value == null
                ? ""
                : HOUSE_NAME_SEPARATORS.matcher(value.trim()).replaceAll("-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? DEFAULT_HOUSE_NAME : normalized;
    }

    private static String truncateCodePoints(String value, int maxLength) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}
