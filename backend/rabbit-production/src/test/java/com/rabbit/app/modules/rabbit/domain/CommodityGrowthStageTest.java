package com.rabbit.app.modules.rabbit.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rabbit.app.modules.setting.entity.GlobalSetting;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;

class CommodityGrowthStageTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void followsTheAugustThirtyFirstThreeTenEightExample() {
        GlobalSetting setting = setting(3, 10, 8);
        Date weaningDate = date(2026, 7, 1);

        assertAll(
            () -> assertEquals(CommodityGrowthStage.ADAPTATION,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 1), setting)),
            () -> assertEquals(CommodityGrowthStage.ADAPTATION,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 4), setting)),
            () -> assertEquals(CommodityGrowthStage.GROWING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 5), setting)),
            () -> assertEquals(CommodityGrowthStage.GROWING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 14), setting)),
            () -> assertEquals(CommodityGrowthStage.FATTENING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 15), setting)),
            () -> assertEquals(CommodityGrowthStage.FATTENING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 22), setting)),
            () -> assertEquals(CommodityGrowthStage.MATURE,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 23), setting))
        );
    }

    @Test
    void followsTheAugustThirtyFirstFourFifteenTwelveExample() {
        GlobalSetting setting = setting(4, 15, 12);
        Date weaningDate = date(2026, 7, 1);

        assertAll(
            () -> assertEquals(CommodityGrowthStage.ADAPTATION,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 5), setting)),
            () -> assertEquals(CommodityGrowthStage.GROWING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 6), setting)),
            () -> assertEquals(CommodityGrowthStage.GROWING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 20), setting)),
            () -> assertEquals(CommodityGrowthStage.FATTENING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 7, 21), setting)),
            () -> assertEquals(CommodityGrowthStage.FATTENING,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 8, 1), setting)),
            () -> assertEquals(CommodityGrowthStage.MATURE,
                CommodityGrowthStage.onDate(weaningDate, date(2026, 8, 2), setting))
        );
    }

    @Test
    void derivesEachStageStartFromTheWeaningDate() {
        GlobalSetting setting = setting(3, 10, 8);
        Date weaningDate = date(2026, 7, 1);

        assertAll(
            () -> assertEquals(date(2026, 7, 1),
                CommodityGrowthStage.ADAPTATION.enteredAt(weaningDate, setting)),
            () -> assertEquals(date(2026, 7, 5),
                CommodityGrowthStage.GROWING.enteredAt(weaningDate, setting)),
            () -> assertEquals(date(2026, 7, 15),
                CommodityGrowthStage.FATTENING.enteredAt(weaningDate, setting)),
            () -> assertEquals(date(2026, 7, 23),
                CommodityGrowthStage.MATURE.enteredAt(weaningDate, setting)),
            () -> assertEquals(22,
                CommodityGrowthStage.ADAPTATION.daysUntilMature(setting))
        );
    }

    private static GlobalSetting setting(int adaptation, int growing, int fattening) {
        GlobalSetting setting = new GlobalSetting();
        setting.setAdaptationDays(adaptation);
        setting.setGrowingDays(growing);
        setting.setFatteningDays(fattening);
        return setting;
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
            .atStartOfDay(ZONE)
            .toInstant());
    }
}
