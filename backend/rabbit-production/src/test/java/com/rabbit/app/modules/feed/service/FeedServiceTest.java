package com.rabbit.app.modules.feed.service;

import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.feed.entity.FeedLog;
import com.rabbit.app.modules.feed.mapper.FeedLogMapper;
import com.rabbit.app.modules.feed.mapper.FeedLogRabbitMapper;
import com.rabbit.app.modules.inventory.mapper.InventoryItemMapper;
import com.rabbit.app.modules.inventory.mapper.InventoryTxMapper;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.service.WorkTaskWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FeedServiceTest {
    @Test
    void successfulFeedCompletesCareForExactlyTheSubmittedRabbitsAndDate() {
        FeedLogMapper feedLogMapper = Mockito.mock(FeedLogMapper.class);
        FeedLogRabbitMapper feedLogRabbitMapper = Mockito.mock(FeedLogRabbitMapper.class);
        RabbitMapper rabbitMapper = Mockito.mock(RabbitMapper.class);
        CageMapper cageMapper = Mockito.mock(CageMapper.class);
        InventoryItemMapper inventoryItemMapper = Mockito.mock(InventoryItemMapper.class);
        InventoryTxMapper inventoryTxMapper = Mockito.mock(InventoryTxMapper.class);
        RequestDedupService requestDedupService = Mockito.mock(RequestDedupService.class);
        WorkTaskWriter workTaskWriter = Mockito.mock(WorkTaskWriter.class);
        Mockito.when(rabbitMapper.selectById(8L, 81L)).thenReturn(rabbit(81L, 8L, 11L));
        Mockito.when(rabbitMapper.selectById(8L, 82L)).thenReturn(rabbit(82L, 8L, 11L));
        FeedService service = new FeedService(
            feedLogMapper,
            feedLogRabbitMapper,
            rabbitMapper,
            cageMapper,
            inventoryItemMapper,
            inventoryTxMapper,
            requestDedupService,
            workTaskWriter,
            false,
            5
        );
        Date feedTime = Date.from(Instant.parse("2026-03-10T15:30:00Z"));
        FeedLog log = new FeedLog();
        log.setFeedTime(feedTime);
        log.setAmount(BigDecimal.ONE);
        log.setRequestId("feed-1");

        service.addFeedLog(7L, 8L, log, List.of(81L, 82L, 81L));

        Mockito.verify(workTaskWriter).completeCommodityDailyCareForRabbitOnDate(
            8L, 81L, feedTime, "7"
        );
        Mockito.verify(workTaskWriter).completeCommodityDailyCareForRabbitOnDate(
            8L, 82L, feedTime, "7"
        );
        Mockito.verify(workTaskWriter, Mockito.times(2))
            .completeCommodityDailyCareForRabbitOnDate(
                Mockito.eq(8L), Mockito.anyLong(), Mockito.eq(feedTime), Mockito.eq("7")
            );
    }

    @Test
    void idempotentReplayDoesNotCompleteCareAgain() {
        RequestDedupService requestDedupService = Mockito.mock(RequestDedupService.class);
        WorkTaskWriter workTaskWriter = Mockito.mock(WorkTaskWriter.class);
        Mockito.when(requestDedupService.shouldSkipAsDone(8L, 7L, "feed:add", "feed-1"))
            .thenReturn(true);
        FeedService service = new FeedService(
            Mockito.mock(FeedLogMapper.class),
            Mockito.mock(FeedLogRabbitMapper.class),
            Mockito.mock(RabbitMapper.class),
            Mockito.mock(CageMapper.class),
            Mockito.mock(InventoryItemMapper.class),
            Mockito.mock(InventoryTxMapper.class),
            requestDedupService,
            workTaskWriter,
            false,
            5
        );
        FeedLog log = new FeedLog();
        log.setRequestId("feed-1");

        service.addFeedLog(7L, 8L, log, List.of(81L));

        Mockito.verifyNoInteractions(workTaskWriter);
    }

    private static Rabbit rabbit(Long id, Long houseId, Long cageId) {
        Rabbit rabbit = new Rabbit();
        rabbit.setId(id);
        rabbit.setHouseId(houseId);
        rabbit.setCageId(cageId);
        rabbit.setIsActive(true);
        return rabbit;
    }
}
