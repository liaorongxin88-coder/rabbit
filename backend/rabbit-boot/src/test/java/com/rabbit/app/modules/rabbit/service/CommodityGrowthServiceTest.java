package com.rabbit.app.modules.rabbit.service;

import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.Date;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommodityGrowthServiceTest {
    @Test
    void advancesOneHouseThroughTheMapperBoundary() {
        RabbitMapper mapper = Mockito.mock(RabbitMapper.class);
        Date now = new Date();
        Mockito.when(mapper.advanceCommodityGrowthStages(8L, now, "growth-job"))
            .thenReturn(17);

        int changed = new CommodityGrowthService(mapper).advanceHouse(8L, now);

        Assertions.assertEquals(17, changed);
        Mockito.verify(mapper).advanceCommodityGrowthStages(8L, now, "growth-job");
    }

    @Test
    void rejectsMissingScopeWithoutTouchingTheMapper() {
        RabbitMapper mapper = Mockito.mock(RabbitMapper.class);
        CommodityGrowthService service = new CommodityGrowthService(mapper);

        Assertions.assertEquals(0, service.advanceHouse(null, new Date()));
        Mockito.verifyNoInteractions(mapper);
    }
}
