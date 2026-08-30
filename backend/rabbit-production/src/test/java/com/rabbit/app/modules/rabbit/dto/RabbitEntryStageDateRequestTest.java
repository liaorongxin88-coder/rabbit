package com.rabbit.app.modules.rabbit.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import org.junit.jupiter.api.Test;

class RabbitEntryStageDateRequestTest {
    private static final long STAGE_DATE = 1_706_745_600_000L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void singleEntryAcceptsGrowthStageEnteredAt() throws Exception {
        CreateRabbitRequest request = objectMapper.readValue(
            "{\"growthStageEnteredAt\":" + STAGE_DATE + "}",
            CreateRabbitRequest.class
        );

        assertEquals(new Date(STAGE_DATE), request.getGrowthStageEnteredAt());
    }

    @Test
    void sameCageBatchEntryAcceptsGrowthStageEnteredAt() throws Exception {
        BatchRabbitEntryRequest request = objectMapper.readValue(
            "{\"growthStageEnteredAt\":" + STAGE_DATE + "}",
            BatchRabbitEntryRequest.class
        );

        assertEquals(new Date(STAGE_DATE), request.getGrowthStageEnteredAt());
    }

    @Test
    void rangeEntryAcceptsGrowthStageEnteredAt() throws Exception {
        RangeRabbitEntryRequest request = objectMapper.readValue(
            "{\"growthStageEnteredAt\":" + STAGE_DATE + "}",
            RangeRabbitEntryRequest.class
        );

        assertEquals(new Date(STAGE_DATE), request.getGrowthStageEnteredAt());
    }
}
