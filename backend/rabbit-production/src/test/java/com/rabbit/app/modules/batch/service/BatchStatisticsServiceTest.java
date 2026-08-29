package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.mapper.BatchStatisticsMapper;
import org.junit.jupiter.api.Test;

class BatchStatisticsServiceTest {

    @Test
    void returnsStatisticsForTheRequestedHouseAndBatch() {
        BatchStatisticsMapper mapper = org.mockito.Mockito.mock(BatchStatisticsMapper.class);
        BatchStatistics expected = new BatchStatistics();
        expected.setTotalLitters(2);
        expected.setTotalKits(15);
        expected.setTotalLiveKits(13);
        expected.setTotalWeaned(11);
        when(mapper.selectByBatch(4L, 9L)).thenReturn(expected);

        BatchStatistics actual = new BatchStatisticsService(mapper).getStatistics(4L, 9L);

        assertSame(expected, actual);
        verify(mapper).selectByBatch(4L, 9L);
    }

    @Test
    void rejectsBatchOutsideTheRequestedHouse() {
        BatchStatisticsMapper mapper = org.mockito.Mockito.mock(BatchStatisticsMapper.class);
        when(mapper.selectByBatch(5L, 9L)).thenReturn(null);

        BizException error = assertThrows(
            BizException.class,
            () -> new BatchStatisticsService(mapper).getStatistics(5L, 9L)
        );

        assertEquals(404, error.getCode());
        assertEquals("批次不存在", error.getMessage());
    }
}
