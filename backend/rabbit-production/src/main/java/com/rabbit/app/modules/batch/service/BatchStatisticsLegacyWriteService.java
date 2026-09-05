package com.rabbit.app.modules.batch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import com.rabbit.app.util.DateUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class BatchStatisticsLegacyWriteService {
    public static final String UPGRADE_MESSAGE = "当前版本过低，请升级应用后重试";
    public static final String LEGACY_FEED_ALLOCATION_GAP = "LEGACY_FEED_ALLOCATION_GAP";
    public static final String LEGACY_WEANING_WEIGHT_GAP = "LEGACY_WEANING_WEIGHT_GAP";
    public static final String LEGACY_SALE_ALLOCATION_GAP = "LEGACY_SALE_ALLOCATION_GAP";
    public static final String LEGACY_SALE_PRICE_GAP = "LEGACY_SALE_PRICE_GAP";
    public static final String LEGACY_REPLACEMENT_WEIGHT_GAP = "LEGACY_REPLACEMENT_WEIGHT_GAP";
    private static final String APP_BUILD_HEADER = "X-App-Build";
    private static final String UNKNOWN_BUILD = "UNKNOWN";
    private static final Set<String> GAP_EVENT_TYPES = Set.of(
        LEGACY_FEED_ALLOCATION_GAP,
        LEGACY_WEANING_WEIGHT_GAP,
        LEGACY_SALE_ALLOCATION_GAP,
        LEGACY_SALE_PRICE_GAP,
        LEGACY_REPLACEMENT_WEIGHT_GAP
    );

    private final ReproEventMapper reproEventMapper;
    private final ObjectMapper objectMapper;
    private final boolean legacyWriteEnabled;

    public BatchStatisticsLegacyWriteService(
        ReproEventMapper reproEventMapper,
        ObjectMapper objectMapper,
        @Value("${app.batch-statistics.legacy-write-enabled:true}") boolean legacyWriteEnabled
    ) {
        this.reproEventMapper = reproEventMapper;
        this.objectMapper = objectMapper;
        this.legacyWriteEnabled = legacyWriteEnabled;
    }

    public boolean isLegacyWriteEnabled() {
        return legacyWriteEnabled;
    }

    public void requireLegacyWriteEnabled() {
        if (!legacyWriteEnabled) {
            throw new BizException(409, UPGRADE_MESSAGE);
        }
    }

    public void recordGap(
        Long userId,
        Long houseId,
        Long batchId,
        String requestId,
        String operationCode,
        String eventType
    ) {
        if (batchId == null) {
            return;
        }
        if (!GAP_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("不支持的旧客户端缺口事件: " + eventType);
        }
        ReproEvent event = new ReproEvent();
        event.setHouseId(houseId);
        event.setBatchId(batchId);
        event.setOperationCode(operationCode);
        event.setTargetType("BATCH");
        event.setTargetId(batchId);
        event.setEventType(eventType);
        event.setOccurredAt(DateUtil.now());
        event.setPayload(payload());
        event.setOperatorId(userId);
        event.setOperatorName(userId == null ? null : String.valueOf(userId));
        event.setRequestId(requestId);
        reproEventMapper.insertBatch(List.of(event));
    }

    private String payload() {
        try {
            return objectMapper.writeValueAsString(Map.of("clientBuild", clientBuild()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化旧客户端缺口事件", error);
        }
    }

    private String clientBuild() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return UNKNOWN_BUILD;
        }
        HttpServletRequest request = attributes.getRequest();
        String value = request.getHeader(APP_BUILD_HEADER);
        if (value == null || value.isBlank()) {
            return UNKNOWN_BUILD;
        }
        String normalized = value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
