package com.rabbit.app.modules.outbound.service;

import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import com.rabbit.app.modules.outbound.mapper.OutboundCandidateMapper;
import com.rabbit.app.modules.rabbit.domain.CommodityGrowthStage;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OutboundEligibilityService {
    public static final String NORMAL = "NORMAL";
    public static final String EARLY_SALE = "EARLY_SALE";
    public static final String NEEDS_ACTION = "NEEDS_ACTION";
    public static final String BLOCKED = "BLOCKED";

    private final OutboundCandidateMapper candidateMapper;

    public OutboundEligibilityService(OutboundCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    public List<OutboundCandidateRow> scopeRows(Long houseId, String entryType, Long rabbitId, Long cageId, String rowCode) {
        return candidateMapper.selectScope(houseId, entryType, rabbitId, cageId, rowCode);
    }

    public List<OutboundCandidateRow> rowsByIds(Long houseId, List<Long> rabbitIds) {
        return candidateMapper.selectByIds(houseId, rabbitIds);
    }

    public List<Long> lockRabbitIds(Long houseId, List<Long> rabbitIds) {
        return candidateMapper.lockRabbitIds(houseId, rabbitIds);
    }

    public List<OutboundDtos.RabbitEligibilityView> evaluate(List<OutboundCandidateRow> rows) {
        List<OutboundDtos.RabbitEligibilityView> result = new ArrayList<>();
        for (OutboundCandidateRow row : rows) {
            result.add(evaluate(row));
        }
        return result;
    }

    public OutboundDtos.RabbitEligibilityView evaluate(OutboundCandidateRow row) {
        Decision decision = decide(row);
        return new OutboundDtos.RabbitEligibilityView(
                row.getRabbitId(), row.getCageId(), empty(row.getCageNumber(), "#" + row.getCageId()),
                empty(row.getRowCode(), "LEGACY"), row.getLayerIndex(), row.getPositionIndex(),
                row.getRabbitType(), row.getGender(), row.getWeight(), displayStage(row),
                row.getBatchId(), row.getStateVersion() == null ? 0L : row.getStateVersion(),
                decision.eligibility, decision.code, decision.message, decision.action,
                NORMAL.equals(decision.eligibility)
        );
    }

    public OutboundDtos.EligibilitySummary summary(List<OutboundDtos.RabbitEligibilityView> items) {
        int normal = 0;
        int early = 0;
        int needsAction = 0;
        int blocked = 0;
        for (OutboundDtos.RabbitEligibilityView item : items) {
            switch (item.eligibility()) {
                case NORMAL -> normal++;
                case EARLY_SALE -> early++;
                case NEEDS_ACTION -> needsAction++;
                default -> blocked++;
            }
        }
        return new OutboundDtos.EligibilitySummary(normal, early, needsAction, blocked);
    }

    private Decision decide(OutboundCandidateRow row) {
        if (!Boolean.TRUE.equals(row.getActive())) {
            return blocked("RABBIT_NOT_PRESENT", "兔只已出栏或不在场", "查看兔只历史");
        }
        if (!"2".equals(row.getRabbitType())) {
            return blocked("RABBIT_NOT_COMMODITY", "非商品兔不可从商品出库流程出售", "继续生产或后备管理");
        }
        if (row.getCageId() == null) {
            return blocked("RABBIT_UNASSIGNED", "兔只尚未分配笼位", "先分配笼位");
        }
        if (!Boolean.TRUE.equals(row.getCageEnabled())) {
            return blocked("CAGE_DISABLED", "所在笼位已停用", "处理笼位状态后重试");
        }
        if (Boolean.TRUE.equals(row.getQuarantined())) {
            return blocked("RABBIT_QUARANTINED", "兔只处于隔离状态", "解除隔离后重新预检");
        }
        if (Boolean.TRUE.equals(row.getOpenTreatment())) {
            return blocked("RABBIT_IN_TREATMENT", "兔只正在治疗", "完成治疗后重新预检");
        }
        if (Boolean.TRUE.equals(row.getUnresolvedAbnormal())) {
            return new Decision(NEEDS_ACTION, "RABBIT_ABNORMAL_UNRESOLVED", "兔只有未处理异常", "处理异常后重新预检");
        }
        CommodityGrowthStage growthStage = CommodityGrowthStage.fromCodeOrNull(row.getGrowthStage());
        if (growthStage == CommodityGrowthStage.MATURE) {
            return new Decision(NORMAL, "ELIGIBLE", "可正常出库", "纳入或移出本次出库");
        }
        if (growthStage != null) {
            return new Decision(EARLY_SALE, "EARLY_SALE_CONFIRMATION_REQUIRED", "当前阶段仅允许提前出售", "逐兔填写提前出售原因");
        }

        String stage = empty(row.getStage(), "");
        boolean batchSaleEvent = row.getNextEventType() != null && row.getNextEventType().contains("出售");
        boolean saleDue = batchSaleEvent
                && row.getNextEventDate() != null && !row.getNextEventDate().after(new Date());
        saleDue = saleDue || Boolean.TRUE.equals(row.getSaleReadyTaskDue());
        if (stage.contains("可出售") || stage.contains("待出售") || saleDue) {
            return new Decision(NORMAL, "ELIGIBLE", "可正常出库", "纳入或移出本次出库");
        }
        if (stage.contains("适应") || stage.contains("生长") || stage.contains("育肥")
                || batchSaleEvent || Boolean.TRUE.equals(row.getSaleReadyTask())) {
            return new Decision(EARLY_SALE, "EARLY_SALE_CONFIRMATION_REQUIRED", "当前阶段仅允许提前出售", "逐兔填写提前出售原因");
        }
        if (stage.isEmpty() || stage.contains("待分配")) {
            return blocked("COMMODITY_STAGE_MISSING", "商品兔缺少有效生产阶段", "补齐商品兔生长阶段");
        }
        return blocked("COMMODITY_STAGE_BLOCKED", "当前生产阶段不允许出库", "继续当前生产流程");
    }

    String displayStage(OutboundCandidateRow row) {
        CommodityGrowthStage growthStage = CommodityGrowthStage.fromCodeOrNull(row.getGrowthStage());
        if (growthStage != null) {
            return growthStage.label();
        }
        return empty(row.getStage(), "阶段未设置");
    }

    private Decision blocked(String code, String message, String action) {
        return new Decision(BLOCKED, code, message, action);
    }

    private String empty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Decision(String eligibility, String code, String message, String action) {}
}
