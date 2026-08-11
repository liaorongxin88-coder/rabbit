package com.rabbit.app.modules.report.service;

import com.rabbit.app.modules.batch.dto.BreedingSummary;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.report.dto.DashboardSummary;
import com.rabbit.app.modules.report.dto.MonthlyCount;
import com.rabbit.app.modules.report.dto.RabbitDashboardStats;
import com.rabbit.app.modules.report.mapper.DashboardReportMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardReportService {
    private final HouseService houseService;
    private final DashboardReportMapper mapper;

    public DashboardReportService(HouseService houseService, DashboardReportMapper mapper) {
        this.houseService = houseService;
        this.mapper = mapper;
    }

    public DashboardSummary load(Long userId, Long selectedHouseId, Integer requestedYear) {
        int year = normalizeYear(requestedYear);
        List<Long> houseIds = resolveHouseIds(userId, selectedHouseId);
        DashboardSummary result = emptySummary(selectedHouseId, houseIds.size(), year);
        if (houseIds.isEmpty()) {
            return result;
        }

        RabbitDashboardStats rabbits = mapper.selectRabbitStats(houseIds);
        BreedingSummary breeding = mapper.selectBreedingSummary(houseIds);
        int batchCount = value(mapper.countBatches(houseIds));
        int successfulBreedings = breeding == null ? 0 : value(breeding.getSuccessBreedingCount());
        int bred = Math.max(successfulBreedings, batchCount);
        int femaleSeedRabbits = rabbits == null ? 0 : value(rabbits.getFemaleSeedRabbits());
        int totalKits = breeding == null ? 0 : value(breeding.getTotalKits());
        int liveKits = breeding == null ? 0 : value(breeding.getTotalLiveKits());
        int totalWeaned = breeding == null ? 0 : value(breeding.getTotalWeaned());

        result.setTotalRabbits(rabbits == null ? 0 : value(rabbits.getTotalRabbits()));
        result.setSeedRabbits(rabbits == null ? 0 : value(rabbits.getSeedRabbits()));
        result.setMaleRabbits(rabbits == null ? 0 : value(rabbits.getMaleRabbits()));
        result.setFemaleRabbits(rabbits == null ? 0 : value(rabbits.getFemaleRabbits()));
        result.setBredRabbits(bred);
        result.setReadyForBreeding(Math.max(femaleSeedRabbits - bred, 0));
        result.setLitters(breeding == null ? 0 : value(breeding.getTotalLitters()));
        result.setNursingKits(Math.max(liveKits - totalWeaned, 0));
        result.setCommodityRabbits(rabbits == null ? 0 : value(rabbits.getCommodityRabbits()));
        result.setReplacementRabbits(rabbits == null ? 0 : value(rabbits.getReplacementRabbits()));
        result.setLiveRate(totalKits <= 0 ? 0D : (double) liveKits / totalKits);

        ZoneId zone = ZoneId.systemDefault();
        Date from = Date.from(LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant());
        Date to = Date.from(LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant());
        result.setMonthlyBirths(toMonths(mapper.selectMonthlyBirths(houseIds, from, to)));
        result.setMonthlyWeaned(toMonths(mapper.selectMonthlyWeaned(houseIds, from, to)));
        return result;
    }

    private List<Long> resolveHouseIds(Long userId, Long selectedHouseId) {
        if (selectedHouseId != null) {
            houseService.assertHousePermission(userId, selectedHouseId, "view");
            return Collections.singletonList(selectedHouseId);
        }
        List<Long> ids = new ArrayList<Long>();
        for (RabbitHouse house : houseService.listMyHouses(userId)) {
            if (house.getId() != null && house.getId() > 0) {
                ids.add(house.getId());
            }
        }
        return ids;
    }

    private static DashboardSummary emptySummary(Long selectedHouseId, int houseCount, int year) {
        DashboardSummary result = new DashboardSummary();
        result.setSelectedHouseId(selectedHouseId);
        result.setHouseCount(houseCount);
        result.setYear(year);
        result.setTotalRabbits(0);
        result.setSeedRabbits(0);
        result.setMaleRabbits(0);
        result.setFemaleRabbits(0);
        result.setBredRabbits(0);
        result.setReadyForBreeding(0);
        result.setLitters(0);
        result.setNursingKits(0);
        result.setCommodityRabbits(0);
        result.setReplacementRabbits(0);
        result.setLiveRate(0D);
        result.setMonthlyBirths(new ArrayList<Integer>(Collections.nCopies(12, 0)));
        result.setMonthlyWeaned(new ArrayList<Integer>(Collections.nCopies(12, 0)));
        return result;
    }

    private static List<Integer> toMonths(List<MonthlyCount> rows) {
        List<Integer> values = new ArrayList<Integer>(Collections.nCopies(12, 0));
        if (rows == null) {
            return values;
        }
        for (MonthlyCount row : rows) {
            if (row == null || row.getMonth() == null || row.getMonth() < 1 || row.getMonth() > 12) {
                continue;
            }
            values.set(row.getMonth() - 1, value(row.getCount()));
        }
        return values;
    }

    private static int normalizeYear(Integer year) {
        int currentYear = LocalDate.now().getYear();
        return year == null || year < 2000 || year > 2100 ? currentYear : year;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
