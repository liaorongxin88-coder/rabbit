package com.rabbit.app.modules.rabbit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.dto.BatchRabbitEntryRequest;
import com.rabbit.app.modules.rabbit.dto.BatchRabbitEntryResult;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchRabbitEntryService {
    private static final String API = "rabbit.batchEntry";

    private final CageMapper cageMapper;
    private final RabbitMapper rabbitMapper;
    private final RabbitService rabbitService;
    private final RequestDedupService requestDedupService;
    private final ObjectMapper objectMapper;
    private final int commodityCageCapacity;

    public BatchRabbitEntryService(
        CageMapper cageMapper,
        RabbitMapper rabbitMapper,
        RabbitService rabbitService,
        RequestDedupService requestDedupService,
        ObjectMapper objectMapper,
        @Value("${app.cage.commodity-capacity:10}") int commodityCageCapacity
    ) {
        this.cageMapper = cageMapper;
        this.rabbitMapper = rabbitMapper;
        this.rabbitService = rabbitService;
        this.requestDedupService = requestDedupService;
        this.objectMapper = objectMapper;
        this.commodityCageCapacity = commodityCageCapacity <= 0 ? 10 : commodityCageCapacity;
    }

    @Transactional
    public BatchRabbitEntryResult create(
        Long userId,
        Long houseId,
        BatchRabbitEntryRequest request
    ) {
        if (!"2".equals(request.getType()) && request.getQuantity() != 1) {
            throw new BizException(400, "种兔和后备兔一次只能录入 1 只");
        }
        String requestId = request.getRequestId().trim();
        RequestDedupService.BeginResult dedup = requestDedupService.begin(
            houseId,
            userId,
            API,
            requestId,
            payloadHash(request)
        );
        if (dedup == RequestDedupService.BeginResult.DONE) {
            return replayedResult(houseId, userId, requestId);
        }

        Cage cage = cageMapper.selectByIdForUpdate(houseId, request.getCageId());
        if (cage == null || !houseId.equals(cage.getHouseId())) {
            throw new BizException(400, "笼位不存在");
        }
        List<String> childRequestIds = childRequestIds(requestId, cage.getId(), request.getQuantity());
        List<Rabbit> existing = rabbitMapper.selectByHouseAndRequestIds(houseId, childRequestIds);
        Set<String> existingRequestIds = new HashSet<>();
        for (Rabbit rabbit : existing) {
            existingRequestIds.add(rabbit.getRequestId());
        }

        String rejection = rejection(cage, request.getType());
        if (rejection != null) {
            return complete(
                houseId,
                userId,
                requestId,
                result(request.getQuantity(), 0, existingRequestIds.size(), cage,
                    request.getQuantity() - existingRequestIds.size(), rejection)
            );
        }

        int available = Math.max(0, capacity(cage, request.getType()) - rabbitCount(cage));
        int entered = 0;
        double individualWeight = request.getTotalWeight() / request.getQuantity();
        for (String childRequestId : childRequestIds) {
            if (existingRequestIds.contains(childRequestId) || entered >= available) {
                continue;
            }
            rabbitService.createRabbit(
                userId,
                houseId,
                rabbit(request, cage.getId(), individualWeight),
                childRequestId
            );
            entered++;
        }

        int skipped = request.getQuantity() - existingRequestIds.size() - entered;
        String reason = "商品兔笼剩余容量不足";
        return complete(
            houseId,
            userId,
            requestId,
            result(request.getQuantity(), entered, existingRequestIds.size(), cage, skipped, reason)
        );
    }

    private BatchRabbitEntryResult complete(
        Long houseId,
        Long userId,
        String requestId,
        BatchRabbitEntryResult result
    ) {
        requestDedupService.markDone(
            houseId,
            userId,
            API,
            requestId,
            responsePayload(result)
        );
        return result;
    }

    private BatchRabbitEntryResult replayedResult(Long houseId, Long userId, String requestId) {
        String payload = requestDedupService.getResponsePayload(houseId, userId, API, requestId);
        if (payload == null || payload.isBlank()) {
            throw new BizException(409, "首次批量录入结果不可用，请使用新的requestId");
        }
        try {
            BatchRabbitEntryResult original = objectMapper.readValue(
                payload,
                BatchRabbitEntryResult.class
            );
            return new BatchRabbitEntryResult(
                original.requestedRabbitCount(),
                0,
                original.enteredRabbitCount() + original.replayedRabbitCount(),
                original.skippedCages()
            );
        } catch (JsonProcessingException exception) {
            throw new BizException(500, "首次批量录入结果解析失败");
        }
    }

    private String responsePayload(BatchRabbitEntryResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BizException(500, "批量录入结果保存失败");
        }
    }

    private BatchRabbitEntryResult result(
        int requested,
        int entered,
        int replayed,
        Cage cage,
        int skipped,
        String reason
    ) {
        List<BatchRabbitEntryResult.SkippedCage> skippedCages = skipped <= 0
            ? List.of()
            : List.of(new BatchRabbitEntryResult.SkippedCage(
                cage.getId(),
                cage.getCageNumber(),
                skipped,
                reason
            ));
        return new BatchRabbitEntryResult(requested, entered, replayed, skippedCages);
    }

    private String rejection(Cage cage, String type) {
        if (!Boolean.TRUE.equals(cage.getIsEnabled())) {
            return "笼位已停用";
        }
        String status = cage.getStatus() == null ? "0" : cage.getStatus();
        String targetStatus = targetStatus(type);
        if (!"0".equals(status) && !targetStatus.equals(status)) {
            return "笼位用途不匹配";
        }
        return null;
    }

    private int capacity(Cage cage, String type) {
        String status = cage.getStatus() == null ? "0" : cage.getStatus();
        if ("1".equals(status) || "2".equals(status) || !"2".equals(type)) {
            return 1;
        }
        return commodityCageCapacity;
    }

    private int rabbitCount(Cage cage) {
        return cage.getRabbitCount() == null ? 0 : cage.getRabbitCount();
    }

    private String targetStatus(String type) {
        return switch (type) {
            case "0" -> "1";
            case "1" -> "2";
            case "2" -> "3";
            default -> throw new BizException(400, "兔只类型不支持");
        };
    }

    private Rabbit rabbit(BatchRabbitEntryRequest request, Long cageId, double individualWeight) {
        Rabbit rabbit = new Rabbit();
        rabbit.setCageId(cageId);
        rabbit.setMotherId(request.getMotherId());
        rabbit.setType(request.getType());
        rabbit.setGender(request.getGender());
        rabbit.setBreed(request.getBreed());
        rabbit.setArrivalMethod(request.getArrivalMethod());
        rabbit.setSourceSeller(request.getSourceSeller());
        rabbit.setArrivalDate(request.getArrivalDate());
        rabbit.setWeight(individualWeight);
        rabbit.setGrowthStage(request.getGrowthStage());
        rabbit.setReproductiveStage(request.getReproductiveStage());
        return rabbit;
    }

    private List<String> childRequestIds(String requestId, Long cageId, int quantity) {
        List<String> ids = new ArrayList<>();
        for (int index = 1; index <= quantity; index++) {
            ids.add(UUID.nameUUIDFromBytes(
                (requestId + ":" + cageId + ":" + index).getBytes(StandardCharsets.UTF_8)
            ).toString());
        }
        return ids;
    }

    private String payloadHash(BatchRabbitEntryRequest request) {
        String value = String.join(
            "|",
            String.valueOf(request.getCageId()),
            String.valueOf(request.getMotherId()),
            request.getType(),
            request.getGender(),
            String.valueOf(request.getBreed()),
            String.valueOf(request.getArrivalMethod()),
            String.valueOf(request.getSourceSeller()),
            String.valueOf(request.getArrivalDate() == null ? null : request.getArrivalDate().getTime()),
            String.valueOf(request.getQuantity()),
            String.valueOf(request.getTotalWeight()),
            String.valueOf(request.getGrowthStage()),
            String.valueOf(request.getReproductiveStage())
        );
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
