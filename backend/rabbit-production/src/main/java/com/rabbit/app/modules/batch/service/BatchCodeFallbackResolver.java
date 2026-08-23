package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BatchCodeFallbackResolver {
    private static final int MAX_BATCH_CODE_LENGTH = 100;
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(
            "yyyyMMddHHmmssSSS"
    ).withZone(SHANGHAI_ZONE);

    private final HouseService houseService;
    private final Clock clock;

    @Autowired
    public BatchCodeFallbackResolver(HouseService houseService) {
        this(houseService, Clock.systemUTC());
    }

    BatchCodeFallbackResolver(HouseService houseService, Clock clock) {
        this.houseService = houseService;
        this.clock = clock;
    }

    public String resolve(Long houseId, String batchCode) {
        if (batchCode != null && !batchCode.trim().isEmpty()) {
            return batchCode;
        }
        return defaultBatchCode(houseService.requireHouseName(houseId), clock.instant());
    }

    static String defaultBatchCode(String houseName, Instant instant) {
        if (houseName == null || houseName.trim().isEmpty()) {
            throw new BizException(400, "兔舍名称不能为空");
        }
        String suffix = "-批次-" + TIMESTAMP_FORMAT.format(instant);
        int maxPrefixLength = MAX_BATCH_CODE_LENGTH - suffix.length();
        String housePrefix = houseName.trim();
        if (housePrefix.length() > maxPrefixLength) {
            housePrefix = housePrefix.substring(0, maxPrefixLength);
        }
        return housePrefix + suffix;
    }
}
