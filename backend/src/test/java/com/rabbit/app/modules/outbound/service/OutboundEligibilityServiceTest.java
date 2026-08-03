package com.rabbit.app.modules.outbound.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbit.app.modules.outbound.dto.OutboundDtos;
import com.rabbit.app.modules.outbound.entity.OutboundCandidateRow;
import java.util.Date;
import org.junit.jupiter.api.Test;

class OutboundEligibilityServiceTest {
    private final OutboundEligibilityService service = new OutboundEligibilityService(null);

    @Test
    void classifiesDueEarlyAndParallelBlockers() {
        OutboundCandidateRow due = commodity();
        due.setNextEventType("出售");
        due.setNextEventDate(new Date(System.currentTimeMillis() - 1000));
        assertEquals(OutboundEligibilityService.NORMAL, service.evaluate(due).eligibility());

        OutboundCandidateRow early = commodity();
        early.setNextEventType("出售");
        early.setNextEventDate(new Date(System.currentTimeMillis() + 86_400_000));
        assertEquals(OutboundEligibilityService.EARLY_SALE, service.evaluate(early).eligibility());

        OutboundCandidateRow treatment = commodity();
        treatment.setOpenTreatment(true);
        OutboundDtos.RabbitEligibilityView treatmentView = service.evaluate(treatment);
        assertEquals(OutboundEligibilityService.BLOCKED, treatmentView.eligibility());
        assertEquals("RABBIT_IN_TREATMENT", treatmentView.reasonCode());

        OutboundCandidateRow abnormal = commodity();
        abnormal.setUnresolvedAbnormal(true);
        assertEquals(OutboundEligibilityService.NEEDS_ACTION, service.evaluate(abnormal).eligibility());
    }

    private OutboundCandidateRow commodity() {
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
        row.setStage("成长期");
        row.setOpenTreatment(false);
        row.setUnresolvedAbnormal(false);
        return row;
    }
}
