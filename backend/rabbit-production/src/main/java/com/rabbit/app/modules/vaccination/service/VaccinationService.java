package com.rabbit.app.modules.vaccination.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.vaccination.dto.VaccinationBatchResult;
import com.rabbit.app.modules.vaccination.entity.VaccinationRecord;
import com.rabbit.app.modules.vaccination.mapper.VaccinationRecordMapper;
import com.rabbit.app.tracking.OperationContext;
import com.rabbit.app.tracking.OperationEvent;
import com.rabbit.app.tracking.TrackedOperation;
import com.rabbit.app.util.DateUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 疫苗接种记录。
 *
 * <p>写入结构照抄 {@code WeightService.create}：先 {@code shouldSkipAsDone} 回查，
 * 再 {@code markProcessing}，成功 {@code markDone}，异常 {@code markFailed} 后重抛。
 *
 * <p>与治疗的关键差异：接种<b>不改兔只状态</b>。打完针的兔子仍然「在栏」，
 * 因此这里既不写 rabbit_status_history，也不 bump state_version——
 * 否则同一笼 60 只兔的一次接种会连带制造 60 条无意义的状态流水。
 */
@Service
public class VaccinationService {
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_DONE = "DONE";

    /** 与 BatchService.BULK_WRITE_SIZE 对齐。 */
    public static final int MAX_BATCH_SIZE = 500;

    private static final int MAX_REPORTED_IDS = 5;

    private final RabbitMapper rabbitMapper;
    private final VaccinationRecordMapper vaccinationRecordMapper;
    private final RequestDedupService requestDedupService;

    public VaccinationService(RabbitMapper rabbitMapper,
                              VaccinationRecordMapper vaccinationRecordMapper,
                              RequestDedupService requestDedupService) {
        this.rabbitMapper = rabbitMapper;
        this.vaccinationRecordMapper = vaccinationRecordMapper;
        this.requestDedupService = requestDedupService;
    }

    @TrackedOperation(code = "vaccination:create", eventType = "VACCINATION_RECORDED")
    @Transactional
    public VaccinationBatchResult create(Long userId,
                                         Long houseId,
                                         List<Long> rabbitIds,
                                         VaccinationRecord template,
                                         String requestId) {
        String api = "vaccination:create";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            List<VaccinationRecord> old = vaccinationRecordMapper.selectByReq(houseId, requestId);
            return new VaccinationBatchResult(0, old);
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            if (template == null) {
                throw new BizException(400, "接种记录不能为空");
            }
            List<Long> targets = normalizeTargets(rabbitIds);
            String vaccineName = requireText(template.getVaccineName(), "疫苗名称不能为空");

            Date vaccinatedAt = template.getVaccinatedAt() == null
                ? DateUtil.now()
                : template.getVaccinatedAt();
            Date nextDueDate = template.getNextDueDate();
            if (nextDueDate != null && !nextDueDate.after(vaccinatedAt)) {
                throw new BizException(400, "下次接种日期必须晚于本次接种时间");
            }

            Map<Long, Rabbit> rabbitsById = assertAllVaccinable(houseId, targets);

            String operator = String.valueOf(userId);
            String status = nextDueDate == null ? STATUS_DONE : STATUS_SCHEDULED;
            List<VaccinationRecord> rows = new ArrayList<VaccinationRecord>(targets.size());
            for (Long rabbitId : targets) {
                VaccinationRecord row = new VaccinationRecord();
                row.setHouseId(houseId);
                row.setRabbitId(rabbitId);
                row.setCageId(rabbitsById.get(rabbitId).getCageId());
                row.setVaccineName(vaccineName);
                row.setVaccineBatchNo(trimToNull(template.getVaccineBatchNo()));
                row.setDose(trimToNull(template.getDose()));
                row.setRoute(trimToNull(template.getRoute()));
                row.setVaccinatedAt(vaccinatedAt);
                row.setNextDueDate(nextDueDate);
                row.setStatus(status);
                row.setRemark(trimToNull(template.getRemark()));
                row.setRequestId(requestId);
                rows.add(row);
            }

            // 先收口同一疫苗的旧待接种记录，再插入本批。
            // excludeRequestId 保证本批刚写入的行不会被自己收口。
            vaccinationRecordMapper.markSupersededDone(
                houseId, targets, vaccineName, requestId, operator
            );
            vaccinationRecordMapper.insertBatch(rows);
            recordEvents(rows);

            requestDedupService.markDone(houseId, userId, api, requestId);
            return new VaccinationBatchResult(rows.size(), rows);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void recordEvents(List<VaccinationRecord> rows) {
        OperationContext context = OperationContext.current();
        if (context == null) {
            return;
        }
        for (VaccinationRecord row : rows) {
            context.recordEvent(OperationEvent.from(context)
                .operationCode("vaccination:create")
                .eventType("VACCINATION_RECORDED")
                .targetType("RABBIT")
                .targetId(row.getRabbitId())
                .cageId(row.getCageId())
                .build());
        }
    }

    public List<VaccinationRecord> listByRabbit(Long houseId, Long rabbitId, int limit) {
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 200) {
            limit = 200;
        }
        return vaccinationRecordMapper.selectByRabbit(houseId, rabbitId, limit);
    }

    public List<VaccinationRecord> listDue(Long houseId) {
        return vaccinationRecordMapper.selectDueByHouse(houseId, DateUtil.now());
    }

    /**
     * 去重并保序。
     *
     * <p>保序是为了让返回结果与客户端提交的顺序对得上；去重是因为笼位多选和
     * 批次多选叠加时很容易把同一只兔选进来两次，而 uk_vr_req 会让重复的
     * (house, rabbit, request) 直接撞唯一键，报出来的错和用户的操作对不上。
     */
    private List<Long> normalizeTargets(List<Long> rabbitIds) {
        if (rabbitIds == null || rabbitIds.isEmpty()) {
            throw new BizException(400, "请选择需要接种的兔只");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<Long>();
        for (Long id : rabbitIds) {
            if (id == null || id <= 0) {
                throw new BizException(400, "rabbitId不合法");
            }
            unique.add(id);
        }
        if (unique.size() > MAX_BATCH_SIZE) {
            throw new BizException(400, "单次接种不能超过" + MAX_BATCH_SIZE + "只兔");
        }
        return new ArrayList<Long>(unique);
    }

    /**
     * 整批校验，任一只不合法就整批拒绝。
     *
     * <p>不做「跳过异常的继续打剩下的」：接种是一次性动作，操作者手上那瓶疫苗
     * 已经打出去了，静默漏掉几只会让人以为整笼都打过。宁可报清楚是哪几只出问题，
     * 让人改完再提交。
     */
    private Map<Long, Rabbit> assertAllVaccinable(Long houseId, List<Long> targets) {
        List<Rabbit> found = rabbitMapper.selectByIdsForUpdate(houseId, targets);
        Map<Long, Rabbit> byId = found.stream()
            .filter(r -> r != null && r.getId() != null)
            .collect(Collectors.toMap(Rabbit::getId, Function.identity(), (a, b) -> a));

        List<Long> missing = new ArrayList<Long>();
        List<Long> inactive = new ArrayList<Long>();
        for (Long id : targets) {
            Rabbit rabbit = byId.get(id);
            if (rabbit == null || !houseId.equals(rabbit.getHouseId())) {
                missing.add(id);
            } else if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
                inactive.add(id);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(400, "兔子不存在：" + describe(missing));
        }
        if (!inactive.isEmpty()) {
            throw new BizException(400, "兔子不在场：" + describe(inactive));
        }
        return byId;
    }

    private String describe(List<Long> ids) {
        String head = ids.stream()
            .limit(MAX_REPORTED_IDS)
            .map(String::valueOf)
            .collect(Collectors.joining("、"));
        return ids.size() > MAX_REPORTED_IDS
            ? head + " 等 " + ids.size() + " 只"
            : head;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(400, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
