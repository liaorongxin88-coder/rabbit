package com.rabbit.app.modules.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Metric;
import com.rabbit.app.modules.batch.dto.BatchStatisticsMatingDateRow;
import com.rabbit.app.modules.batch.dto.BatchStatisticsRawSnapshot;
import com.rabbit.app.modules.batch.mapper.BatchStatisticsMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchStatisticsServiceTest {
    private static final Long HOUSE_ID = 4L;
    private static final Long BATCH_ID = 9L;
    private static final String HOUSE_NAME = "附件验收兔舍";
    private static final String BATCH_CODE = "ATTACHMENT-7";

    @Test
    void calculatesTheOrderedMetricContractFromRawAggregates() {
        Fixture fixture = completeFixture();

        BatchStatistics result = new BatchStatisticsService(fixture.mapper()).getStatistics(
            HOUSE_ID,
            BATCH_ID
        );

        assertEquals(1, result.schemaVersion());
        assertEquals(BATCH_ID, result.batchId());
        assertEquals(HOUSE_NAME, result.houseName());
        assertEquals(BATCH_CODE, result.batchCode());
        assertNotNull(result.calculatedAt());
        assertEquals(1004, result.totalLitters());
        assertEquals(10040, result.totalKits());
        assertEquals(9870, result.totalLiveKits());
        assertEquals(8604, result.totalWeaned());
        assertEquals(28, result.metrics().size());
        assertEquals(
            List.of(
                "MATING_DATE",
                "MATED_DOE_COUNT",
                "CONCEPTION_RATE",
                "DOE_BUCK_RATIO",
                "PREGNANT_DOE_COUNT",
                "ABORTION_RATE",
                "DELIVERED_LITTER_COUNT",
                "TOTAL_KIT_COUNT",
                "AVERAGE_KITS_PER_LITTER",
                "LIVE_KIT_COUNT",
                "LIVE_BIRTH_RATE",
                "KEPT_LITTER_COUNT",
                "KEPT_KIT_COUNT",
                "KEPT_LIVE_RATE",
                "AVERAGE_KEPT_PER_LITTER",
                "WEANED_KIT_COUNT",
                "AVERAGE_WEANING_WEIGHT",
                "WEANING_SURVIVAL_RATE",
                "SOLD_RABBIT_COUNT",
                "OUTBOUND_SURVIVAL_RATE",
                "SOLD_WEIGHT",
                "AVERAGE_SOLD_WEIGHT",
                "TOTAL_SALES_AMOUNT",
                "SALES_PRICE_PER_KG",
                "SALES_PRICE_PER_RABBIT",
                "FULL_FEED_CONVERSION_RATIO",
                "FATTENING_FEED_CONVERSION_RATIO",
                "CARCASS_YIELD_RATE"
            ),
            result.metrics().stream().map(Metric::code).toList()
        );
        assertEquals(
            List.of(
                10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140,
                150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280
            ),
            result.metrics().stream().map(Metric::order).toList()
        );

        assertEquals(
            List.of(
                "MATING_DATE|配种日期|MATING|配种|配种日期|DATE_RANGE|DATE|DATE_RANGE|配种日期按业务自然日去重",
                "MATED_DOE_COUNT|配种母兔数|MATING|配种|配种母兔数|NUMBER|COUNT|INTEGER|已配种周期中的去重母兔数",
                "CONCEPTION_RATE|受胎率|MATING|配种|受胎率|NUMBER|PERCENT|PERCENT_2|确认怀孕周期数 / 已配种周期数",
                "DOE_BUCK_RATIO|配种母兔/公兔比例|MATING|配种|配种母兔/公兔比例|NUMBER|RATIO|RATIO_TO_ONE|去重配种母兔数 / 去重参与配种公兔数",
                "PREGNANT_DOE_COUNT|怀孕数量|PREGNANCY|怀孕|怀孕数量|NUMBER|COUNT|INTEGER|确认怀孕周期中的去重母兔数",
                "ABORTION_RATE|流产率|PREGNANCY|怀孕|流产率|NUMBER|PERCENT|PERCENT_2|已怀孕流产周期数 / 确认怀孕周期数",
                "DELIVERED_LITTER_COUNT|产崽窝数|BIRTH|产崽|产崽窝数|NUMBER|LITTER|INTEGER|批次内产崽窝数",
                "TOTAL_KIT_COUNT|产崽总数|BIRTH|产崽|产崽总数|NUMBER|COUNT|INTEGER|批次内产崽数之和",
                "AVERAGE_KITS_PER_LITTER|平均窝产数|BIRTH|产崽|平均窝产数|NUMBER|COUNT_PER_LITTER|DECIMAL_2|产崽总数 / 产崽窝数",
                "LIVE_KIT_COUNT|活崽总数|BIRTH|产崽|活崽总数|NUMBER|COUNT|INTEGER|批次内活崽数之和",
                "LIVE_BIRTH_RATE|平均活崽率|BIRTH|产崽|平均活崽率|NUMBER|PERCENT|PERCENT_2|活崽总数 / 产崽总数",
                "KEPT_LITTER_COUNT|选留窝数|SELECTION|选留|选留窝数|NUMBER|LITTER|INTEGER|留崽数大于零的窝数",
                "KEPT_KIT_COUNT|选留总数|SELECTION|选留|选留总数|NUMBER|COUNT|INTEGER|批次内选留数之和",
                "KEPT_LIVE_RATE|选留活崽率|SELECTION|选留|选留活崽率|NUMBER|PERCENT|PERCENT_2|选留总数 / 活崽总数",
                "AVERAGE_KEPT_PER_LITTER|窝均选留|SELECTION|选留|窝均选留|NUMBER|COUNT_PER_LITTER|DECIMAL_2|选留总数 / 选留窝数",
                "WEANED_KIT_COUNT|断奶数量|WEANING|断奶|断奶数量|NUMBER|COUNT|INTEGER|批次内断奶数之和",
                "AVERAGE_WEANING_WEIGHT|断奶均重|WEANING|断奶|断奶均重|NUMBER|KG_PER_RABBIT|DECIMAL_2|断奶总重快照之和 / 断奶数量",
                "WEANING_SURVIVAL_RATE|断奶成活率|WEANING|断奶|断奶成活率|NUMBER|PERCENT|PERCENT_2|断奶数量 / 选留总数",
                "SOLD_RABBIT_COUNT|出栏数量|OUTBOUND|出栏|出栏数量|NUMBER|COUNT|INTEGER|批次快照匹配的已销售兔只数",
                "OUTBOUND_SURVIVAL_RATE|出栏成活率|OUTBOUND|出栏|出栏成活率|NUMBER|PERCENT|PERCENT_2|出栏数量 / 断奶数量",
                "SOLD_WEIGHT|出栏总重|OUTBOUND|出栏|出栏总重|NUMBER|KG|DECIMAL_2|批次销售实际重量之和",
                "AVERAGE_SOLD_WEIGHT|出栏均重|OUTBOUND|出栏|出栏均重|NUMBER|KG_PER_RABBIT|DECIMAL_2|出栏总重 / 出栏数量",
                "TOTAL_SALES_AMOUNT|总销售金额|SALES|销售|总销售金额|NUMBER|CNY|DECIMAL_2|批次销售金额快照之和",
                "SALES_PRICE_PER_KG|销售单价（重量口径）|SALES|销售|销售单价（重量口径）|NUMBER|CNY_PER_KG|DECIMAL_2|总销售金额 / 出栏总重",
                "SALES_PRICE_PER_RABBIT|销售单价（只数口径）|SALES|销售|销售单价（只数口径）|NUMBER|CNY_PER_RABBIT|DECIMAL_2|总销售金额 / 出栏数量",
                "FULL_FEED_CONVERSION_RATIO|全程料肉比|FEED_CONVERSION|料肉比|全程料肉比|NUMBER|RATIO|DECIMAL_2|批次全程饲料量 /（商品兔实际销售重量 + 转后备兔实测总重）",
                "FATTENING_FEED_CONVERSION_RATIO|育肥期料肉比|FEED_CONVERSION|料肉比|育肥期料肉比|NUMBER|RATIO|DECIMAL_2|批次育肥饲料量 /（商品兔实际销售重量 + 转后备兔实测总重 - 断奶总重）",
                "CARCASS_YIELD_RATE|出肉率|FEED_CONVERSION|料肉比|出肉率|NUMBER|PERCENT|PERCENT_2|最新出肉率版本"
            ),
            result.metrics().stream().map(metric -> String.join(
                "|",
                metric.code(),
                metric.name(),
                metric.stage(),
                metric.stageName(),
                metric.excelColumnName(),
                metric.valueType(),
                metric.unit(),
                metric.format(),
                metric.formula()
            )).toList()
        );

        assertAvailable(result, "MATED_DOE_COUNT", new BigDecimal("1230"), "1,230");
        assertAvailable(
            result,
            "CONCEPTION_RATE",
            new BigDecimal("1059").divide(new BigDecimal("1230"), MathContext.DECIMAL128),
            "86.10%"
        );
        assertAvailable(result, "DOE_BUCK_RATIO", new BigDecimal("20.5"), "20.50:1");
        assertAvailable(result, "PREGNANT_DOE_COUNT", new BigDecimal("1059"), "1,059");
        assertAvailable(
            result,
            "ABORTION_RATE",
            new BigDecimal("21").divide(new BigDecimal("1059"), MathContext.DECIMAL128),
            "1.98%"
        );
        assertAvailable(result, "DELIVERED_LITTER_COUNT", new BigDecimal("1004"), "1,004");
        assertAvailable(result, "TOTAL_KIT_COUNT", new BigDecimal("10040"), "10,040");
        assertAvailable(result, "AVERAGE_KITS_PER_LITTER", new BigDecimal("10"), "10.00");
        assertAvailable(result, "LIVE_KIT_COUNT", new BigDecimal("9870"), "9,870");
        assertAvailable(
            result,
            "LIVE_BIRTH_RATE",
            new BigDecimal("9870").divide(new BigDecimal("10040"), MathContext.DECIMAL128),
            "98.31%"
        );
        assertAvailable(result, "KEPT_LITTER_COUNT", new BigDecimal("987"), "987");
        assertAvailable(result, "KEPT_KIT_COUNT", new BigDecimal("9490"), "9,490");
        assertAvailable(
            result,
            "KEPT_LIVE_RATE",
            new BigDecimal("9490").divide(new BigDecimal("9870"), MathContext.DECIMAL128),
            "96.15%"
        );
        assertAvailable(
            result,
            "AVERAGE_KEPT_PER_LITTER",
            new BigDecimal("9490").divide(new BigDecimal("987"), MathContext.DECIMAL128),
            "9.61"
        );
        assertAvailable(result, "WEANED_KIT_COUNT", new BigDecimal("8604"), "8,604");
        assertAvailable(result, "AVERAGE_WEANING_WEIGHT", new BigDecimal("0.735"), "0.74 kg");
        assertAvailable(
            result,
            "WEANING_SURVIVAL_RATE",
            new BigDecimal("8604").divide(new BigDecimal("9490"), MathContext.DECIMAL128),
            "90.66%"
        );
        assertAvailable(result, "SOLD_RABBIT_COUNT", new BigDecimal("6834"), "6,834");
        assertAvailable(
            result,
            "OUTBOUND_SURVIVAL_RATE",
            new BigDecimal("6834").divide(new BigDecimal("8604"), MathContext.DECIMAL128),
            "79.43%"
        );
        assertAvailable(result, "SOLD_WEIGHT", new BigDecimal("13095"), "13,095.00 kg");
        assertAvailable(
            result,
            "AVERAGE_SOLD_WEIGHT",
            new BigDecimal("13095").divide(new BigDecimal("6834"), MathContext.DECIMAL128),
            "1.92 kg"
        );
        assertAvailable(
            result,
            "TOTAL_SALES_AMOUNT",
            new BigDecimal("157140"),
            "157,140.00 元"
        );
        assertAvailable(result, "SALES_PRICE_PER_KG", new BigDecimal("12"), "12.00 元/kg");
        assertAvailable(
            result,
            "SALES_PRICE_PER_RABBIT",
            new BigDecimal("157140").divide(new BigDecimal("6834"), MathContext.DECIMAL128),
            "22.99 元/只"
        );
        assertAvailable(
            result,
            "FULL_FEED_CONVERSION_RATIO",
            new BigDecimal("52120").divide(new BigDecimal("14145"), MathContext.DECIMAL128),
            "3.68"
        );
        assertAvailable(
            result,
            "FATTENING_FEED_CONVERSION_RATIO",
            new BigDecimal("30070").divide(new BigDecimal("7821.06"), MathContext.DECIMAL128),
            "3.84"
        );
        assertAvailable(result, "CARCASS_YIELD_RATE", new BigDecimal("0.56"), "56.00%");

        Metric matingDate = metric(result, "MATING_DATE");
        assertEquals("DATE_RANGE", matingDate.valueType());
        assertEquals("AVAILABLE", matingDate.status());
        assertEquals("2024-04-22", matingDate.displayValue());
        assertEquals(LocalDate.of(2024, 4, 22), matingDate.dateValue().firstDate());
        assertEquals(LocalDate.of(2024, 4, 22), matingDate.dateValue().lastDate());
        assertEquals(1, matingDate.dateValue().dateCount());
        assertEquals(1230, matingDate.dateValue().dailyCycleCounts().get(0).cycleCount());

        Metric conceptionRate = metric(result, "CONCEPTION_RATE");
        assertDecimalEquals(
            new BigDecimal("1059").divide(new BigDecimal("1230"), MathContext.DECIMAL128),
            conceptionRate.numericValue()
        );
        assertEquals("86.10%", conceptionRate.displayValue());
        assertDecimalEquals(new BigDecimal("1059"), conceptionRate.numerator().value());
        assertDecimalEquals(new BigDecimal("1230"), conceptionRate.denominator().value());

        Metric pregnantDoes = metric(result, "PREGNANT_DOE_COUNT");
        assertDecimalEquals(new BigDecimal("1059"), pregnantDoes.numericValue());
        Metric abortionRate = metric(result, "ABORTION_RATE");
        assertDecimalEquals(new BigDecimal("21"), abortionRate.numerator().value());
        assertDecimalEquals(new BigDecimal("1059"), abortionRate.denominator().value());

        Metric fullFeedConversion = metric(result, "FULL_FEED_CONVERSION_RATIO");
        assertDecimalEquals(
            new BigDecimal("52120").divide(new BigDecimal("14145"), MathContext.DECIMAL128),
            fullFeedConversion.numericValue()
        );
        assertEquals("3.68", fullFeedConversion.displayValue());
        assertEquals(4, fullFeedConversion.components().size());

        Metric fatteningFeedConversion = metric(result, "FATTENING_FEED_CONVERSION_RATIO");
        assertDecimalEquals(
            new BigDecimal("30070").divide(new BigDecimal("7821.06"), MathContext.DECIMAL128),
            fatteningFeedConversion.numericValue()
        );
        assertEquals("3.84", fatteningFeedConversion.displayValue());

        Metric carcassYield = metric(result, "CARCASS_YIELD_RATE");
        assertDecimalEquals(new BigDecimal("0.56"), carcassYield.numericValue());
        assertEquals("56.00%", carcassYield.displayValue());
        assertEquals("PERCENT_2", carcassYield.format());

        verifyEveryAggregateUsesHouseAndBatch(fixture.mapper());
    }

    @Test
    void preservesStatusPrecedenceAndOrderedMissingCauses() {
        Fixture fixture = completeFixture();
        fixture.mating().setMatedBuckCount(0);
        fixture.mating().setMissingNaturalMale(true);
        fixture.mating().setPregnantCycleCount(0);
        fixture.abortion().setMissingPregnancyEvidence(true);
        fixture.litter().setTotalWeaned(0);
        fixture.litter().setMissingWeaningWeight(true);
        fixture.salesCount().setSoldRabbitCount(0);
        fixture.salesCount().setMissingBatchAttribution(true);
        fixture.salesValue().setSoldWeightKg(BigDecimal.ZERO);
        fixture.salesValue().setMissingBatchSaleAllocation(true);
        fixture.salesValue().setMissingSaleUnitPrice(true);
        fixture.feed().setMissingFeedAllocation(true);
        fixture.replacement().setMissingReplacementWeight(true);

        BatchStatistics result = new BatchStatisticsService(fixture.mapper()).getStatistics(
            HOUSE_ID,
            BATCH_ID
        );

        assertUnavailable(
            metric(result, "DOE_BUCK_RATIO"),
            "DATA_MISSING",
            "MISSING_NATURAL_MALE"
        );
        assertUnavailable(
            metric(result, "ABORTION_RATE"),
            "DATA_MISSING",
            "MISSING_PREGNANCY_EVIDENCE"
        );
        assertUnavailable(
            metric(result, "AVERAGE_WEANING_WEIGHT"),
            "DATA_MISSING",
            "MISSING_WEANING_WEIGHT"
        );
        assertUnavailable(
            metric(result, "OUTBOUND_SURVIVAL_RATE"),
            "DATA_MISSING",
            "MISSING_BATCH_ATTRIBUTION"
        );
        assertUnavailable(
            metric(result, "SALES_PRICE_PER_KG"),
            "DATA_MISSING",
            "MISSING_BATCH_SALE_ALLOCATION",
            "MISSING_SALE_UNIT_PRICE"
        );
        assertUnavailable(
            metric(result, "FULL_FEED_CONVERSION_RATIO"),
            "DATA_MISSING",
            "MISSING_BATCH_SALE_ALLOCATION",
            "MISSING_FEED_ALLOCATION",
            "MISSING_REPLACEMENT_WEIGHT"
        );
    }

    @Test
    void returnsNotRecordedAndNotApplicableWithoutFabricatingValues() {
        Fixture fixture = emptyFixture();

        BatchStatistics result = new BatchStatisticsService(fixture.mapper()).getStatistics(
            HOUSE_ID,
            BATCH_ID
        );

        assertUnavailable(
            metric(result, "MATING_DATE"),
            "NOT_RECORDED",
            "MATING_NOT_RECORDED"
        );
        Metric count = metric(result, "MATED_DOE_COUNT");
        assertEquals("AVAILABLE", count.status());
        assertDecimalEquals(BigDecimal.ZERO, count.numericValue());
        assertEquals("0", count.displayValue());
        Metric conceptionRate = metric(result, "CONCEPTION_RATE");
        assertUnavailable(
            conceptionRate,
            "NOT_APPLICABLE",
            "ZERO_DENOMINATOR"
        );
        assertDecimalEquals(BigDecimal.ZERO, conceptionRate.numerator().value());
        assertDecimalEquals(BigDecimal.ZERO, conceptionRate.denominator().value());
        assertUnavailable(
            metric(result, "CARCASS_YIELD_RATE"),
            "NOT_RECORDED",
            "CARCASS_YIELD_NOT_RECORDED"
        );
    }

    @Test
    void returnsAvailableZeroWhenTheNumeratorIsZeroAndTheDenominatorIsValid() {
        Fixture fixture = completeFixture();
        fixture.mating().setPregnantCycleCount(0);
        fixture.mating().setPregnantDoeCount(0);
        fixture.salesCount().setSoldRabbitCount(0);

        BatchStatistics result = new BatchStatisticsService(fixture.mapper()).getStatistics(
            HOUSE_ID,
            BATCH_ID
        );

        Metric conceptionRate = metric(result, "CONCEPTION_RATE");
        assertEquals("AVAILABLE", conceptionRate.status());
        assertDecimalEquals(BigDecimal.ZERO, conceptionRate.numericValue());
        assertEquals("0.00%", conceptionRate.displayValue());
        Metric outboundSurvivalRate = metric(result, "OUTBOUND_SURVIVAL_RATE");
        assertEquals("AVAILABLE", outboundSurvivalRate.status());
        assertDecimalEquals(BigDecimal.ZERO, outboundSurvivalRate.numericValue());
    }

    @Test
    void sortsAndMergesDailyMatingCycleCounts() {
        Fixture fixture = completeFixture();
        BatchStatisticsMatingDateRow later = matingDate(LocalDate.of(2024, 4, 23), 3);
        BatchStatisticsMatingDateRow earlierFirst = matingDate(LocalDate.of(2024, 4, 22), 2);
        BatchStatisticsMatingDateRow earlierSecond = matingDate(LocalDate.of(2024, 4, 22), 1);
        when(fixture.mapper().selectMatingDates(HOUSE_ID, BATCH_ID)).thenReturn(
            List.of(later, earlierFirst, earlierSecond)
        );

        Metric result = metric(
            new BatchStatisticsService(fixture.mapper()).getStatistics(HOUSE_ID, BATCH_ID),
            "MATING_DATE"
        );

        assertEquals(LocalDate.of(2024, 4, 22), result.dateValue().firstDate());
        assertEquals(LocalDate.of(2024, 4, 23), result.dateValue().lastDate());
        assertEquals(2, result.dateValue().dateCount());
        assertEquals(3, result.dateValue().dailyCycleCounts().get(0).cycleCount());
        assertEquals(3, result.dateValue().dailyCycleCounts().get(1).cycleCount());
        assertEquals("2024-04-22 至 2024-04-23（2个配种日）", result.displayValue());
    }

    @Test
    void rejectsNegativeFatteningGainAsMissingData() {
        Fixture fixture = completeFixture();
        fixture.salesValue().setSoldWeightKg(BigDecimal.ZERO);
        fixture.replacement().setReplacementWeightKg(BigDecimal.ZERO);

        BatchStatistics result = new BatchStatisticsService(fixture.mapper()).getStatistics(
            HOUSE_ID,
            BATCH_ID
        );

        Metric metric = metric(result, "FATTENING_FEED_CONVERSION_RATIO");
        assertUnavailable(metric, "DATA_MISSING", "INVALID_FATTENING_GAIN");
        assertTrue(metric.denominator().value().signum() < 0);
        assertEquals(
            List.of("SOLD_WEIGHT", "REPLACEMENT_WEIGHT", "WEANING_TOTAL_WEIGHT"),
            metric.components().stream().map(BatchStatistics.Operand::code).toList()
        );
    }

    @Test
    void rejectsBatchOutsideTheRequestedHouse() {
        BatchStatisticsMapper mapper = mock(BatchStatisticsMapper.class);
        when(mapper.selectBatch(5L, BATCH_ID)).thenReturn(null);

        BizException error = assertThrows(
            BizException.class,
            () -> new BatchStatisticsService(mapper).getStatistics(5L, BATCH_ID)
        );

        assertEquals(404, error.getCode());
        assertEquals("批次不存在", error.getMessage());
        verify(mapper).selectBatch(5L, BATCH_ID);
        verifyNoMoreInteractions(mapper);
    }

    private static Fixture completeFixture() {
        BatchStatisticsMapper mapper = mock(BatchStatisticsMapper.class);
        BatchStatisticsRawSnapshot batch = new BatchStatisticsRawSnapshot();
        batch.setBatchId(BATCH_ID);
        batch.setHouseName(HOUSE_NAME);
        batch.setBatchCode(BATCH_CODE);

        BatchStatisticsRawSnapshot mating = new BatchStatisticsRawSnapshot();
        mating.setMatedCycleCount(1230);
        mating.setMatedDoeCount(1230);
        mating.setPregnantCycleCount(1059);
        mating.setPregnantDoeCount(1059);
        mating.setMatedBuckCount(60);

        BatchStatisticsMatingDateRow matingDate = new BatchStatisticsMatingDateRow();
        matingDate.setDate(LocalDate.of(2024, 4, 22));
        matingDate.setCycleCount(1230);

        BatchStatisticsRawSnapshot abortion = new BatchStatisticsRawSnapshot();
        abortion.setAbortedPregnantCycleCount(21);

        BatchStatisticsRawSnapshot litter = new BatchStatisticsRawSnapshot();
        litter.setTotalLitters(1004);
        litter.setTotalKits(10040);
        litter.setTotalLiveKits(9870);
        litter.setKeptLitterCount(987);
        litter.setTotalKept(9490);
        litter.setTotalWeaned(8604);
        litter.setTotalWeaningWeightKg(new BigDecimal("6323.94"));

        BatchStatisticsRawSnapshot salesCount = new BatchStatisticsRawSnapshot();
        salesCount.setSoldRabbitCount(6834);

        BatchStatisticsRawSnapshot salesValue = new BatchStatisticsRawSnapshot();
        salesValue.setSoldWeightKg(new BigDecimal("13095"));
        salesValue.setTotalSalesAmount(new BigDecimal("157140"));

        BatchStatisticsRawSnapshot feed = new BatchStatisticsRawSnapshot();
        feed.setBreedingFeedAmountKg(new BigDecimal("22050"));
        feed.setFatteningFeedAmountKg(new BigDecimal("30070"));

        BatchStatisticsRawSnapshot replacement = new BatchStatisticsRawSnapshot();
        replacement.setReplacementWeightKg(new BigDecimal("1050"));

        BatchStatisticsRawSnapshot carcass = new BatchStatisticsRawSnapshot();
        carcass.setCarcassYieldRate(new BigDecimal("0.56"));

        stubAggregates(
            mapper,
            batch,
            mating,
            List.of(matingDate),
            abortion,
            litter,
            salesCount,
            salesValue,
            feed,
            replacement,
            carcass
        );
        return new Fixture(
            mapper,
            mating,
            abortion,
            litter,
            salesCount,
            salesValue,
            feed,
            replacement
        );
    }

    private static Fixture emptyFixture() {
        BatchStatisticsMapper mapper = mock(BatchStatisticsMapper.class);
        BatchStatisticsRawSnapshot batch = new BatchStatisticsRawSnapshot();
        batch.setBatchId(BATCH_ID);
        batch.setHouseName(HOUSE_NAME);
        batch.setBatchCode(BATCH_CODE);
        BatchStatisticsRawSnapshot mating = new BatchStatisticsRawSnapshot();
        BatchStatisticsRawSnapshot abortion = new BatchStatisticsRawSnapshot();
        BatchStatisticsRawSnapshot litter = new BatchStatisticsRawSnapshot();
        BatchStatisticsRawSnapshot salesCount = new BatchStatisticsRawSnapshot();
        BatchStatisticsRawSnapshot salesValue = new BatchStatisticsRawSnapshot();
        BatchStatisticsRawSnapshot feed = new BatchStatisticsRawSnapshot();
        BatchStatisticsRawSnapshot replacement = new BatchStatisticsRawSnapshot();
        stubAggregates(
            mapper,
            batch,
            mating,
            List.of(),
            abortion,
            litter,
            salesCount,
            salesValue,
            feed,
            replacement,
            null
        );
        return new Fixture(
            mapper,
            mating,
            abortion,
            litter,
            salesCount,
            salesValue,
            feed,
            replacement
        );
    }

    private static void stubAggregates(
        BatchStatisticsMapper mapper,
        BatchStatisticsRawSnapshot batch,
        BatchStatisticsRawSnapshot mating,
        List<BatchStatisticsMatingDateRow> matingDates,
        BatchStatisticsRawSnapshot abortion,
        BatchStatisticsRawSnapshot litter,
        BatchStatisticsRawSnapshot salesCount,
        BatchStatisticsRawSnapshot salesValue,
        BatchStatisticsRawSnapshot feed,
        BatchStatisticsRawSnapshot replacement,
        BatchStatisticsRawSnapshot carcass
    ) {
        when(mapper.selectBatch(HOUSE_ID, BATCH_ID)).thenReturn(batch);
        when(mapper.selectMatingAggregate(HOUSE_ID, BATCH_ID)).thenReturn(mating);
        when(mapper.selectMatingDates(HOUSE_ID, BATCH_ID)).thenReturn(matingDates);
        when(mapper.selectAbortionAggregate(HOUSE_ID, BATCH_ID)).thenReturn(abortion);
        when(mapper.selectLitterAggregate(HOUSE_ID, BATCH_ID)).thenReturn(litter);
        when(mapper.selectSalesCountAggregate(HOUSE_ID, BATCH_ID)).thenReturn(salesCount);
        when(mapper.selectSalesValueAggregate(HOUSE_ID, BATCH_ID)).thenReturn(salesValue);
        when(mapper.selectFeedAggregate(HOUSE_ID, BATCH_ID)).thenReturn(feed);
        when(mapper.selectReplacementAggregate(HOUSE_ID, BATCH_ID)).thenReturn(replacement);
        when(mapper.selectLatestCarcassYield(HOUSE_ID, BATCH_ID)).thenReturn(carcass);
    }

    private static void verifyEveryAggregateUsesHouseAndBatch(BatchStatisticsMapper mapper) {
        verify(mapper).selectBatch(HOUSE_ID, BATCH_ID);
        verify(mapper).selectMatingAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectMatingDates(HOUSE_ID, BATCH_ID);
        verify(mapper).selectAbortionAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectLitterAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectSalesCountAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectSalesValueAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectFeedAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectReplacementAggregate(HOUSE_ID, BATCH_ID);
        verify(mapper).selectLatestCarcassYield(HOUSE_ID, BATCH_ID);
        verifyNoMoreInteractions(mapper);
    }

    private static BatchStatisticsMatingDateRow matingDate(LocalDate date, int cycleCount) {
        BatchStatisticsMatingDateRow row = new BatchStatisticsMatingDateRow();
        row.setDate(date);
        row.setCycleCount(cycleCount);
        return row;
    }

    private static Metric metric(BatchStatistics statistics, String code) {
        return statistics.metrics().stream()
            .filter(metric -> metric.code().equals(code))
            .findFirst()
            .orElseThrow();
    }

    private static void assertAvailable(
        BatchStatistics statistics,
        String code,
        BigDecimal expectedValue,
        String expectedDisplay
    ) {
        Metric metric = metric(statistics, code);
        assertEquals("AVAILABLE", metric.status());
        assertDecimalEquals(expectedValue, metric.numericValue());
        assertEquals(expectedDisplay, metric.displayValue());
        assertTrue(metric.missingCauses().isEmpty());
    }

    private static void assertUnavailable(Metric metric, String status, String... causeCodes) {
        assertEquals(status, metric.status());
        assertNull(metric.numericValue());
        assertNull(metric.displayValue());
        assertNull(metric.dateValue());
        assertEquals(
            List.of(causeCodes),
            metric.missingCauses().stream().map(BatchStatistics.MissingCause::code).toList()
        );
    }

    private static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private record Fixture(
        BatchStatisticsMapper mapper,
        BatchStatisticsRawSnapshot mating,
        BatchStatisticsRawSnapshot abortion,
        BatchStatisticsRawSnapshot litter,
        BatchStatisticsRawSnapshot salesCount,
        BatchStatisticsRawSnapshot salesValue,
        BatchStatisticsRawSnapshot feed,
        BatchStatisticsRawSnapshot replacement
    ) {
    }
}
