package com.rabbit.app.modules.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbit.app.modules.batch.dto.BreedingSummary;
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
        DashboardReportService service = new DashboardReportService(houses, mapper);

        DashboardSummary result = service.load(3L, null, 2026);

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
        DashboardReportService service = new DashboardReportService(houses, mapper);

        DashboardSummary result = service.load(3L, 9L, 2026);

        assertEquals(9L, houses.assertedHouseId);
        assertEquals(Collections.singletonList(9L), mapper.requestedHouseIds);
        assertEquals(1, result.getHouseCount());
        assertEquals(9L, result.getSelectedHouseId());
    }

    private static final class FakeHouseService extends HouseService {
        private final List<RabbitHouse> houses;
        private Long assertedHouseId;

        FakeHouseService(Long... houseIds) {
            super(null, null, null, null, null, null, null);
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

        @Override
        public RabbitDashboardStats selectRabbitStats(List<Long> houseIds) {
            requestedHouseIds = houseIds;
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
        public Integer countActiveBreedingMothers(List<Long> houseIds) {
            return 3;
        }

        @Override
        public BreedingSummary selectBreedingSummary(List<Long> houseIds) {
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
        public Integer sumCurrentNursingKits(List<Long> houseIds) {
            return 6;
        }

        @Override
        public List<MonthlyCount> selectMonthlyBirths(List<Long> houseIds, Date from, Date to) {
            return Collections.singletonList(month(2, 7));
        }

        @Override
        public List<MonthlyCount> selectMonthlyWeaned(List<Long> houseIds, Date from, Date to) {
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
