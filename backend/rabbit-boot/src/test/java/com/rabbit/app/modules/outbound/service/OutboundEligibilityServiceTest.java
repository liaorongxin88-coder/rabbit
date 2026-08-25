package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import java.util.Date;
import org.junit.jupiter.api.Test;

class OutboundEligibilityServiceTest {
    private final OutboundEligibilityService service = new OutboundEligibilityService(null);

    @Test
    void canonicalGrowthStageControlsEligibilityWithoutActiveBatch() {
        OutboundCandidateRow mature = commodity("MATURE");
        OutboundDtos.RabbitEligibilityView matureView = service.evaluate(mature);
        assertEquals(OutboundEligibilityService.NORMAL, matureView.eligibility());
        assertEquals("成熟可售", matureView.stage());

        for (String stage : new String[] {"ADAPTATION", "GROWING", "FATTENING", "JUVENILE"}) {
            OutboundCandidateRow early = commodity(stage);
            assertEquals(OutboundEligibilityService.EARLY_SALE, service.evaluate(early).eligibility(), stage);
        }
        assertEquals("适应期", service.evaluate(commodity("JUVENILE")).stage());
    }

    @Test
    void legacyBatchStageAndSaleReadyTaskRemainFallbacks() {
        OutboundCandidateRow batchDue = commodity(null);
        batchDue.setStage("生长期");
        batchDue.setNextEventType("出售");
        batchDue.setNextEventDate(new Date(System.currentTimeMillis() - 1000));
        assertEquals(OutboundEligibilityService.NORMAL, service.evaluate(batchDue).eligibility());

        OutboundCandidateRow taskDue = commodity(null);
        taskDue.setSaleReadyTask(true);
        taskDue.setSaleReadyTaskDue(true);
        assertEquals(OutboundEligibilityService.NORMAL, service.evaluate(taskDue).eligibility());

        OutboundCandidateRow taskFuture = commodity(null);
        taskFuture.setSaleReadyTask(true);
        assertEquals(OutboundEligibilityService.EARLY_SALE, service.evaluate(taskFuture).eligibility());
    }

    @Test
    void parallelConditionsStillBlockBeforeGrowthStageEvaluation() {
        OutboundCandidateRow treatment = commodity("MATURE");
        treatment.setOpenTreatment(true);
        OutboundDtos.RabbitEligibilityView treatmentView = service.evaluate(treatment);
        assertEquals(OutboundEligibilityService.BLOCKED, treatmentView.eligibility());
        assertEquals("RABBIT_IN_TREATMENT", treatmentView.reasonCode());

        OutboundCandidateRow abnormal = commodity("MATURE");
        abnormal.setUnresolvedAbnormal(true);
        assertEquals(OutboundEligibilityService.NEEDS_ACTION, service.evaluate(abnormal).eligibility());
    }

    private OutboundCandidateRow commodity(String growthStage) {
        OutboundCandidateRow row = new OutboundCandidateRow();
        row.setRabbitId(1L);
        row.setHouseId(1L);
        row.setCageId(1L);
        row.setCageNumber("1-1-1");
        row.setRowCode("R1");
        row.setCageEnabled(true);
        row.setRabbitType("2");
        row.setActive(true);
        row.setQuarantined(false);
        row.setStateVersion(0L);
        row.setGrowthStage(growthStage);
        row.setOpenTreatment(false);
        row.setUnresolvedAbnormal(false);
        return row;
    }
}
