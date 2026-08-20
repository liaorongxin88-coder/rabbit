package com.rabbit.app.modules.repro.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;

/** 到期日计算：业务时长锚点、同日推进、补录后拉平当天。 */
class DueDateCalculatorTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 刻意选与旧硬编码 30 不同的妊娠期，才能验出「预产期不再写死」。 */
    private static final ReproSettings SETTINGS = new ReproSettings(2, 12, 31, 3, 25, 10, 30, 45);

    @Test
    void expectedBirthDateUsesConfiguredGestationDaysInsteadOfHardcoded30() {
        Date mating = date(2026, 3, 1);

        assertEquals(
            date(2026, 4, 1),
            DueDateCalculator.expectedBirthDate(mating, SETTINGS),
            "预产期应为配种日 + gestation_days(31)，不是硬编码的 30"
        );
    }

    @Test
    void prepartumWaitStartsAtPalpationAndDeliveryStartsTheSameDay() {
        Date palpationDate = date(2026, 3, 13);
        DueContext context = DueContext.builder(palpationDate, palpationDate).build();

        Date prepartumDue = DueDateCalculator.compute(
            DueAnchor.PREPARTUM_DURATION, context, SETTINGS
        );
        Date deliveryDue = DueDateCalculator.compute(DueAnchor.SAME_DAY, context, SETTINGS);

        assertAll(
            () -> assertEquals(date(2026, 3, 16), prepartumDue, "待备产 = 摸胎确认日 + 3 天"),
            () -> assertEquals(palpationDate, deliveryDue, "备产完成后当天进入待分娩")
        );
    }

    @Test
    void everyAnchorResolvesFromItsDocumentedFact() {
        Date today = date(2026, 3, 1);
        DueContext context = DueContext.builder(date(2026, 3, 1), today)
            .stageEnteredAt(date(2026, 3, 1))
            .matingDate(date(2026, 3, 1))
            .birthDate(date(2026, 3, 1))
            .userSpecified(date(2026, 3, 20))
            .build();

        assertAll(
            () -> assertEquals(
                date(2026, 3, 3),
                DueDateCalculator.compute(DueAnchor.ESTRUS_DURATION, context, SETTINGS),
                "催情 → 配种 = 进入阶段 + 2"
            ),
            () -> assertEquals(
                date(2026, 3, 13),
                DueDateCalculator.compute(DueAnchor.PALPATION_WAIT, context, SETTINGS),
                "配种 → 摸胎 = 配种日 + 12"
            ),
            () -> assertEquals(
                date(2026, 3, 4),
                DueDateCalculator.compute(DueAnchor.PREPARTUM_DURATION, context, SETTINGS),
                "摸胎 → 备产 = 操作日 + 3"
            ),
            () -> assertEquals(
                today,
                DueDateCalculator.compute(DueAnchor.SAME_DAY, context, SETTINGS),
                "备产 → 分娩 = 操作当天"
            ),
            () -> assertEquals(
                date(2026, 3, 26),
                DueDateCalculator.compute(DueAnchor.WEANING_DUE, context, SETTINGS),
                "分娩 → 分笼 = 分娩日 + 25"
            ),
            () -> assertEquals(
                date(2026, 3, 11),
                DueDateCalculator.compute(DueAnchor.POSTPARTUM_RECOVERY, context, SETTINGS),
                "复旧 = 操作日 + 10"
            ),
            () -> assertEquals(
                today,
                DueDateCalculator.compute(DueAnchor.IMMEDIATE, context, SETTINGS),
                "空怀后立即催情"
            ),
            () -> assertEquals(
                date(2026, 3, 20),
                DueDateCalculator.compute(DueAnchor.USER_SPECIFIED, context, SETTINGS),
                "推迟用用户选的时间"
            ),
            () -> assertNull(
                DueDateCalculator.compute(DueAnchor.NONE, context, SETTINGS),
                "离场不产生后续任务"
            )
        );
    }

    @Test
    void backdatedEntryPullsAnOverdueTaskForwardToToday() {
        // 补录 20 天前的配种：摸胎日（+12）早已过去。到期日必须拉到当天，
        // 否则这条待办会带着过去的日期落库，永远不出现在「今日待办」的视野里。
        Date today = date(2026, 3, 21);
        DueContext context = DueContext.builder(date(2026, 3, 1), today)
            .matingDate(date(2026, 3, 1))
            .build();

        assertEquals(today, DueDateCalculator.compute(DueAnchor.PALPATION_WAIT, context, SETTINGS));
    }

    @Test
    void futureDueDatesAreLeftUntouched() {
        Date today = date(2026, 3, 1);
        DueContext context = DueContext.builder(today, today).matingDate(today).build();

        assertEquals(date(2026, 3, 13), DueDateCalculator.compute(DueAnchor.PALPATION_WAIT, context, SETTINGS));
    }

    @Test
    void userSpecifiedFallsBackToTodayWhenAbsent() {
        Date today = date(2026, 3, 1);
        DueContext context = DueContext.builder(today, today).build();

        assertEquals(today, DueDateCalculator.compute(DueAnchor.USER_SPECIFIED, context, SETTINGS));
    }

    @Test
    void missingAnchorFactIsRejectedRatherThanGuessed() {
        Date today = date(2026, 3, 1);
        DueContext noMating = DueContext.builder(today, today).build();

        BizException error = assertThrows(
            BizException.class,
            () -> DueDateCalculator.compute(DueAnchor.PALPATION_WAIT, noMating, SETTINGS)
        );

        assertAll(
            () -> assertEquals(400, error.getCode()),
            () -> assertTrue(error.getMessage().contains("配种日期"), error.getMessage())
        );
    }

    @Test
    void settingsFallBackWhenLegacyRowsHaveNoGestationDays() {
        // V26 只给了列默认值，存量行可能从未显式配置过；不能因此算出早于配种日的预产期。
        GlobalSetting legacy = new GlobalSetting();
        legacy.setAphrodisiacDays(2);
        legacy.setPalpationDays(12);
        legacy.setGestationDays(null);
        legacy.setPrepartumDays(3);
        legacy.setWeaningDays(25);
        legacy.setPostpartumDays(10);
        legacy.setSaleDays(30);
        legacy.setReplacementDays(45);

        assertEquals(30, ReproSettings.from(legacy).gestationDays());
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDateTime.of(LocalDate.of(year, month, day), java.time.LocalTime.of(9, 0))
            .atZone(ZONE)
            .toInstant());
    }
}
