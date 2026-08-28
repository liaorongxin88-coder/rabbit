package com.rabbit.app.modules.repro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.domain.LitterStatus;
import com.rabbit.app.modules.repro.domain.ReproEventType;
import com.rabbit.app.modules.repro.dto.AdjustKeptKitsRequest;
import com.rabbit.app.modules.repro.dto.KeptKitsAdjustmentResponse;
import com.rabbit.app.modules.repro.dto.LitterView;
import com.rabbit.app.modules.repro.entity.Litter;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.entity.ReproEvent;
import com.rabbit.app.modules.repro.mapper.LitterMapper;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.modules.repro.mapper.ReproEventMapper;
import com.rabbit.app.util.DateUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LitterAdjustmentService {
    private final ReproCycleMapper reproCycleMapper;
    private final ReproEventMapper reproEventMapper;
    private final LitterMapper litterMapper;
    private final RabbitMapper rabbitMapper;
    private final ObjectMapper objectMapper;

    public LitterAdjustmentService(
        ReproCycleMapper reproCycleMapper,
        ReproEventMapper reproEventMapper,
        LitterMapper litterMapper,
        RabbitMapper rabbitMapper,
        ObjectMapper objectMapper
    ) {
        this.reproCycleMapper = reproCycleMapper;
        this.reproEventMapper = reproEventMapper;
        this.litterMapper = litterMapper;
        this.rabbitMapper = rabbitMapper;
        this.objectMapper = objectMapper;
    }

    public LitterView getByCycle(Long houseId, Long cycleId) {
        Litter litter = litterMapper.selectByCycleId(houseId, cycleId);
        if (litter == null) {
            throw new BizException(404, "该生产周期没有窝记录");
        }
        return LitterView.of(litter);
    }

    @Transactional
    public KeptKitsAdjustmentResponse adjust(
        Long houseId,
        Long userId,
        String operatorName,
        Long cycleId,
        AdjustKeptKitsRequest request
    ) {
        if (request.getOccurredAt().after(new java.util.Date(DateUtil.now().getTime() + 5L * 60L * 1000L))) {
            throw new BizException(400, "执行时间不能晚于当前时间");
        }
        ReproEvent replay = replay(houseId, request.getRequestId());
        if (replay != null) {
            return replayResponse(replay);
        }

        ReproCycle cycle = reproCycleMapper.selectByIdForUpdate(houseId, cycleId);
        if (cycle == null) {
            throw new BizException(404, "生产周期不存在");
        }
        replay = replay(houseId, request.getRequestId());
        if (replay != null) {
            return replayResponse(replay);
        }

        Litter litter = litterMapper.selectByCycleIdForUpdate(houseId, cycleId);
        if (litter == null) {
            throw new BizException(404, "该生产周期没有窝记录");
        }
        if (!LitterStatus.NURSING.name().equals(litter.getStatus())) {
            throw new BizException(409, "只有哺乳中的窝可以调整留崽数");
        }

        int previous = zero(litter.getKeptKits());
        int next = request.getKeptKits();
        Long sourceMotherId = request.getSourceMotherRabbitId();
        if (next > previous) {
            requireSourceMother(houseId, litter.getMotherRabbitId(), sourceMotherId);
        } else if (sourceMotherId != null) {
            throw new BizException(400, "留崽数未增加时不能填写来源母兔");
        }

        int delta = next - previous;
        litter.setKeptKits(next);
        litter.setCurrentNursing(next);
        litter.setFosterIn(zero(litter.getFosterIn()) + Math.max(delta, 0));
        litter.setFosterOut(zero(litter.getFosterOut()) + Math.max(-delta, 0));
        if (litterMapper.update(litter) != 1) {
            throw new BizException(409, "窝数据已变化，请刷新后重试");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousKeptKits", previous);
        payload.put("keptKits", next);
        if (sourceMotherId != null) {
            payload.put("sourceMotherRabbitId", sourceMotherId);
        }
        if (request.getRemark() != null && !request.getRemark().isBlank()) {
            payload.put("remark", request.getRemark().trim());
        }

        ReproEvent event = new ReproEvent();
        event.setTenantId(cycle.getTenantId());
        event.setHouseId(houseId);
        event.setCycleId(cycleId);
        event.setLitterId(litter.getId());
        event.setMotherRabbitId(cycle.getMotherRabbitId());
        event.setBatchId(cycle.getBatchId());
        event.setEventType(ReproEventType.KEPT_KITS_ADJUSTED.name());
        event.setFromStage(cycle.getStage());
        event.setToStage(cycle.getStage());
        event.setOccurredAt(request.getOccurredAt());
        event.setPayload(toJson(payload));
        event.setOperatorId(userId);
        event.setOperatorName(operatorOf(operatorName, userId));
        event.setRequestId(request.getRequestId());
        reproEventMapper.insert(event);

        return new KeptKitsAdjustmentResponse(
            cycleId, litter.getId(), event.getId(), previous, next, sourceMotherId, false
        );
    }

    private void requireSourceMother(Long houseId, Long motherRabbitId, Long sourceMotherId) {
        if (sourceMotherId == null || sourceMotherId <= 0) {
            throw new BizException(400, "留崽数增加时请选择留崽来源母兔");
        }
        if (sourceMotherId.equals(motherRabbitId)) {
            throw new BizException(400, "留崽来源母兔不能是当前母兔");
        }
        Rabbit source = rabbitMapper.selectById(houseId, sourceMotherId);
        if (source == null || !"0".equals(source.getType()) || !"0".equals(source.getGender())) {
            throw new BizException(400, "留崽来源必须是当前兔舍的种母兔");
        }
    }

    private ReproEvent replay(Long houseId, String requestId) {
        ReproEvent event = reproEventMapper.selectByRequestId(houseId, requestId);
        if (event == null) {
            return null;
        }
        if (!ReproEventType.KEPT_KITS_ADJUSTED.name().equals(event.getEventType())) {
            throw new BizException(409, "requestId已被其他生产操作使用");
        }
        return event;
    }

    private KeptKitsAdjustmentResponse replayResponse(ReproEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            return new KeptKitsAdjustmentResponse(
                event.getCycleId(),
                event.getLitterId(),
                event.getId(),
                payload.path("previousKeptKits").asInt(),
                payload.path("keptKits").asInt(),
                payload.hasNonNull("sourceMotherRabbitId")
                    ? payload.get("sourceMotherRabbitId").asLong()
                    : null,
                true
            );
        } catch (JsonProcessingException e) {
            throw new BizException(500, "留崽调整幂等结果解析失败");
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "留崽调整内容序列化失败");
        }
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String operatorOf(String name, Long userId) {
        return name == null || name.isBlank() ? String.valueOf(userId) : name;
    }
}
