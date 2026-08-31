package com.rabbit.app.modules.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BreedingSummary;
import com.rabbit.app.modules.batch.entity.Batch;
import com.rabbit.app.modules.batch.mapper.BatchMapper;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.report.dto.DashboardSummary;
import com.rabbit.app.modules.report.dto.MonthlyCount;
import com.rabbit.app.modules.report.dto.RabbitDashboardStats;
import com.rabbit.app.modules.report.mapper.DashboardReportMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardReportServiceTest {
    @Test
    void aggregatesAuthorizedHousesWithoutLoadingRabbitRows() {
        FakeHouseService houses = new FakeHouseService(8L, 9L);
        FakeDashboardReportMapper mapper = new FakeDashboardReportMapper();
        DashboardReportService service = new DashboardReportService(houses, mock(BatchMapper.class), mapper);

        DashboardSummary result = service.load(3L, null, null, 2026);

        assertEquals(Arrays.asList(8L, 9L), mapper.requestedHouseIds);
        assertEquals(2, result.getHouseCount());
        assertEquals(12, result.getTotalRabbits());
        assertEquals(4, result.getSeedRabbits());
        assertEquals(3, result.getBredRabbits());
        assertEquals(0, result.getReadyForBreeding());
        assertEquals(6, result.getNursingKits());
        assertEquals(0.8D, result.getLiveRate());
        assertEquals(7, result.getMonthlyBirths().get(1));
        assertEquals(5, result.getMonthlyWeaned().get(2));
    }

    @Test
    void checksPermissionBeforeLoadingOneHouse() {
        FakeHouseService houses = new FakeHouseService(8L, 9L);
        FakeDashboardReportMapper mapper = new FakeDashboardReportMapper();
        DashboardReportService service = new DashboardReportService(houses, mock(BatchMapper.class), mapper);

        DashboardSummary result = service.load(3L, 9L, null, 2026);

        assertEquals(9L, houses.assertedHouseId);
        assertEquals(Collections.singletonList(9L), mapper.requestedHouseIds);
        assertEquals(1, result.getHouseCount());
        assertEquals(9L, result.getSelectedHouseId());
    }

    @Test
    void appliesOneValidatedBatchScopeToEveryStatistic() {
        FakeHouseService houses = new FakeHouseService(8L, 9L);
        BatchMapper batches = mock(BatchMapper.class);
        Batch batch = new Batch();
        batch.setId(77L);
        batch.setHouseId(9L);
        when(batches.selectById(9L, 77L)).thenReturn(batch);
        FakeDashboardReportMapper mapper = new FakeDashboardReportMapper();
        DashboardReportService service = new DashboardReportService(houses, batches, mapper);

        DashboardSummary result = service.load(3L, 9L, 77L, 2026);

        assertEquals(77L, result.getSelectedBatchId());
        assertEquals(Collections.nCopies(6, 77L), mapper.requestedBatchIds);
    }

    @Test
    void rejectsBatchOutsideSelectedHouseBeforeQueryingStatistics() {
        FakeHouseService houses = new FakeHouseService(8L, 9L);
        FakeDashboardReportMapper mapper = new FakeDashboardReportMapper();
        DashboardReportService service = new DashboardReportService(houses, mock(BatchMapper.class), mapper);

        BizException error = assertThrows(
            BizException.class,
            () -> service.load(3L, 9L, 77L, 2026)
        );

        assertEquals(400, error.getCode());
        assertEquals("批次不属于当前兔舍", error.getMessage());
        assertEquals(Collections.emptyList(), mapper.requestedBatchIds);
    }

    private static final class FakeHouseService extends HouseService {
        private final List<RabbitHouse> houses;
        private Long assertedHouseId;

        FakeHouseService(Long... houseIds) {
            super(null, null, null, null, null, null);
            this.houses = Arrays.stream(houseIds).map(FakeHouseService::house).toList();
        }

        @Override
        public List<RabbitHouse> listMyHouses(Long userId) {
            return houses;
        }

        @Override
        public void assertHousePermission(Long userId, Long houseId, String requiredPerm) {
            assertedHouseId = houseId;
        }

        private static RabbitHouse house(Long id) {
            RabbitHouse house = new RabbitHouse();
            house.setId(id);
            return house;
        }
    }

    private static final class FakeDashboardReportMapper implements DashboardReportMapper {
        private List<Long> requestedHouseIds;
        private final List<Long> requestedBatchIds = new java.util.ArrayList<Long>();

        @Override
        public RabbitDashboardStats selectRabbitStats(List<Long> houseIds, Long batchId) {
            requestedHouseIds = houseIds;
            requestedBatchIds.add(batchId);
            RabbitDashboardStats stats = new RabbitDashboardStats();
            stats.setTotalRabbits(12);
            stats.setSeedRabbits(4);
            stats.setMaleRabbits(5);
            stats.setFemaleRabbits(7);
            stats.setFemaleSeedRabbits(3);
            stats.setCommodityRabbits(6);
            stats.setReplacementRabbits(2);
            return stats;
        }

        @Override
        public Integer countActiveBreedingMothers(List<Long> houseIds, Long batchId) {
            requestedBatchIds.add(batchId);
            return 3;
        }

        @Override
        public BreedingSummary selectBreedingSummary(List<Long> houseIds, Long batchId) {
            requestedBatchIds.add(batchId);
            BreedingSummary summary = new BreedingSummary();
            summary.setTotalLitters(2);
            summary.setTotalKits(10);
            summary.setTotalLiveKits(8);
            summary.setTotalWeaned(5);
            summary.setSuccessBreedingCount(2);
            summary.setFailedBreedingCount(0);
            return summary;
        }

        @Override
        public Integer sumCurrentNursingKits(List<Long> houseIds, Long batchId) {
            requestedBatchIds.add(batchId);
            return 6;
        }

        @Override
        public List<MonthlyCount> selectMonthlyBirths(List<Long> houseIds, Long batchId, Date from, Date to) {
            requestedBatchIds.add(batchId);
            return Collections.singletonList(month(2, 7));
        }

        @Override
        public List<MonthlyCount> selectMonthlyWeaned(List<Long> houseIds, Long batchId, Date from, Date to) {
            requestedBatchIds.add(batchId);
            return Collections.singletonList(month(3, 5));
        }

        private static MonthlyCount month(int month, int count) {
            MonthlyCount row = new MonthlyCount();
            row.setMonth(month);
            row.setCount(count);
            return row;
        }
    }
}
