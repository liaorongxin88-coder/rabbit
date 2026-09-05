package com.rabbit.app.modules.batch.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.dto.BatchStatistics;
import com.rabbit.app.modules.batch.dto.BatchStatistics.DailyCycleCount;
import com.rabbit.app.modules.batch.dto.BatchStatistics.DateRangeValue;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Metric;
import com.rabbit.app.modules.batch.dto.BatchStatistics.MissingCause;
import com.rabbit.app.modules.batch.dto.BatchStatistics.Operand;
import com.rabbit.app.modules.batch.dto.BatchStatisticsMatingDateRow;
import com.rabbit.app.modules.batch.dto.BatchStatisticsRawSnapshot;
import com.rabbit.app.modules.batch.mapper.BatchStatisticsMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchStatisticsService {
    private static final int SCHEMA_VERSION = 1;
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    private static final List<MetricSpec> CATALOG = List.of(
        spec("MATING_DATE", "配种日期", "MATING", "配种", 10, "配种日期",
            "DATE_RANGE", "DATE", "DATE_RANGE", "配种日期按业务自然日去重"),
        spec("MATED_DOE_COUNT", "配种母兔数", "MATING", "配种", 20, "配种母兔数",
            "NUMBER", "COUNT", "INTEGER", "已配种周期中的去重母兔数"),
        spec("CONCEPTION_RATE", "受胎率", "MATING", "配种", 30, "受胎率",
            "NUMBER", "PERCENT", "PERCENT_2", "确认怀孕周期数 / 已配种周期数"),
        spec("DOE_BUCK_RATIO", "配种母兔/公兔比例", "MATING", "配种", 40,
            "配种母兔/公兔比例", "NUMBER", "RATIO", "RATIO_TO_ONE",
            "去重配种母兔数 / 去重参与配种公兔数"),
        spec("PREGNANT_DOE_COUNT", "怀孕数量", "PREGNANCY", "怀孕", 50, "怀孕数量",
            "NUMBER", "COUNT", "INTEGER", "确认怀孕周期中的去重母兔数"),
        spec("ABORTION_RATE", "流产率", "PREGNANCY", "怀孕", 60, "流产率",
            "NUMBER", "PERCENT", "PERCENT_2", "已怀孕流产周期数 / 确认怀孕周期数"),
        spec("DELIVERED_LITTER_COUNT", "产崽窝数", "BIRTH", "产崽", 70, "产崽窝数",
            "NUMBER", "LITTER", "INTEGER", "批次内产崽窝数"),
        spec("TOTAL_KIT_COUNT", "产崽总数", "BIRTH", "产崽", 80, "产崽总数",
            "NUMBER", "COUNT", "INTEGER", "批次内产崽数之和"),
        spec("AVERAGE_KITS_PER_LITTER", "平均窝产数", "BIRTH", "产崽", 90,
            "平均窝产数", "NUMBER", "COUNT_PER_LITTER", "DECIMAL_2",
            "产崽总数 / 产崽窝数"),
        spec("LIVE_KIT_COUNT", "活崽总数", "BIRTH", "产崽", 100, "活崽总数",
            "NUMBER", "COUNT", "INTEGER", "批次内活崽数之和"),
        spec("LIVE_BIRTH_RATE", "平均活崽率", "BIRTH", "产崽", 110, "平均活崽率",
            "NUMBER", "PERCENT", "PERCENT_2", "活崽总数 / 产崽总数"),
        spec("KEPT_LITTER_COUNT", "选留窝数", "SELECTION", "选留", 120, "选留窝数",
            "NUMBER", "LITTER", "INTEGER", "留崽数大于零的窝数"),
        spec("KEPT_KIT_COUNT", "选留总数", "SELECTION", "选留", 130, "选留总数",
            "NUMBER", "COUNT", "INTEGER", "批次内选留数之和"),
        spec("KEPT_LIVE_RATE", "选留活崽率", "SELECTION", "选留", 140, "选留活崽率",
            "NUMBER", "PERCENT", "PERCENT_2", "选留总数 / 活崽总数"),
        spec("AVERAGE_KEPT_PER_LITTER", "窝均选留", "SELECTION", "选留", 150,
            "窝均选留", "NUMBER", "COUNT_PER_LITTER", "DECIMAL_2",
            "选留总数 / 选留窝数"),
        spec("WEANED_KIT_COUNT", "断奶数量", "WEANING", "断奶", 160, "断奶数量",
            "NUMBER", "COUNT", "INTEGER", "批次内断奶数之和"),
        spec("AVERAGE_WEANING_WEIGHT", "断奶均重", "WEANING", "断奶", 170,
            "断奶均重", "NUMBER", "KG_PER_RABBIT", "DECIMAL_2",
            "断奶总重快照之和 / 断奶数量"),
        spec("WEANING_SURVIVAL_RATE", "断奶成活率", "WEANING", "断奶", 180,
            "断奶成活率", "NUMBER", "PERCENT", "PERCENT_2", "断奶数量 / 选留总数"),
        spec("SOLD_RABBIT_COUNT", "出栏数量", "OUTBOUND", "出栏", 190, "出栏数量",
            "NUMBER", "COUNT", "INTEGER", "批次快照匹配的已销售兔只数"),
        spec("OUTBOUND_SURVIVAL_RATE", "出栏成活率", "OUTBOUND", "出栏", 200,
            "出栏成活率", "NUMBER", "PERCENT", "PERCENT_2", "出栏数量 / 断奶数量"),
        spec("SOLD_WEIGHT", "出栏总重", "OUTBOUND", "出栏", 210, "出栏总重",
            "NUMBER", "KG", "DECIMAL_2", "批次销售实际重量之和"),
        spec("AVERAGE_SOLD_WEIGHT", "出栏均重", "OUTBOUND", "出栏", 220,
            "出栏均重", "NUMBER", "KG_PER_RABBIT", "DECIMAL_2", "出栏总重 / 出栏数量"),
        spec("TOTAL_SALES_AMOUNT", "总销售金额", "SALES", "销售", 230, "总销售金额",
            "NUMBER", "CNY", "DECIMAL_2", "批次销售金额快照之和"),
        spec("SALES_PRICE_PER_KG", "销售单价（重量口径）", "SALES", "销售", 240,
            "销售单价（重量口径）", "NUMBER", "CNY_PER_KG", "DECIMAL_2",
            "总销售金额 / 出栏总重"),
        spec("SALES_PRICE_PER_RABBIT", "销售单价（只数口径）", "SALES", "销售", 250,
            "销售单价（只数口径）", "NUMBER", "CNY_PER_RABBIT", "DECIMAL_2",
            "总销售金额 / 出栏数量"),
        spec("FULL_FEED_CONVERSION_RATIO", "全程料肉比", "FEED_CONVERSION", "料肉比", 260,
            "全程料肉比", "NUMBER", "RATIO", "DECIMAL_2",
            "批次全程饲料量 /（商品兔实际销售重量 + 转后备兔实测总重）"),
        spec("FATTENING_FEED_CONVERSION_RATIO", "育肥期料肉比", "FEED_CONVERSION",
            "料肉比", 270, "育肥期料肉比", "NUMBER", "RATIO", "DECIMAL_2",
            "批次育肥饲料量 /（商品兔实际销售重量 + 转后备兔实测总重 - 断奶总重）"),
        spec("CARCASS_YIELD_RATE", "出肉率", "FEED_CONVERSION", "料肉比", 280, "出肉率",
            "NUMBER", "PERCENT", "PERCENT_2", "最新出肉率版本")
    );

    private static final Map<String, MetricSpec> SPEC_BY_CODE = catalogByCode();

    private final BatchStatisticsMapper batchStatisticsMapper;

    public BatchStatisticsService(BatchStatisticsMapper batchStatisticsMapper) {
        this.batchStatisticsMapper = batchStatisticsMapper;
    }

    @Transactional(readOnly = true)
    public BatchStatistics getStatistics(Long houseId, Long batchId) {
        BatchStatisticsRawSnapshot batch = batchStatisticsMapper.selectBatch(houseId, batchId);
        if (batch == null) {
            throw new BizException(404, "批次不存在");
        }

        BatchStatisticsRawSnapshot mating = valueOrEmpty(
            batchStatisticsMapper.selectMatingAggregate(houseId, batchId));
        List<BatchStatisticsMatingDateRow> matingDates = valueOrEmpty(
            batchStatisticsMapper.selectMatingDates(houseId, batchId));
        BatchStatisticsRawSnapshot abortion = valueOrEmpty(
            batchStatisticsMapper.selectAbortionAggregate(houseId, batchId));
        BatchStatisticsRawSnapshot litter = valueOrEmpty(
            batchStatisticsMapper.selectLitterAggregate(houseId, batchId));
        BatchStatisticsRawSnapshot salesCount = valueOrEmpty(
            batchStatisticsMapper.selectSalesCountAggregate(houseId, batchId));
        BatchStatisticsRawSnapshot salesValue = valueOrEmpty(
            batchStatisticsMapper.selectSalesValueAggregate(houseId, batchId));
        BatchStatisticsRawSnapshot feed = valueOrEmpty(
            batchStatisticsMapper.selectFeedAggregate(houseId, batchId));
        BatchStatisticsRawSnapshot replacement = valueOrEmpty(
            batchStatisticsMapper.selectReplacementAggregate(houseId, batchId));
        BatchStatisticsRawSnapshot carcass = batchStatisticsMapper.selectLatestCarcassYield(
            houseId, batchId);

        int totalLitters = integer(litter.getTotalLitters());
        int totalKits = integer(litter.getTotalKits());
        int totalLiveKits = integer(litter.getTotalLiveKits());
        int totalWeaned = integer(litter.getTotalWeaned());

        List<Metric> metrics = calculateMetrics(
            mating,
            matingDates,
            abortion,
            litter,
            salesCount,
            salesValue,
            feed,
            replacement,
            carcass
        );
        return new BatchStatistics(
            SCHEMA_VERSION,
            batch.getBatchId(),
            batch.getHouseName(),
            batch.getBatchCode(),
            Instant.now(),
            totalLitters,
            totalKits,
            totalLiveKits,
            totalWeaned,
            metrics
        );
    }

    private List<Metric> calculateMetrics(
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
        BigDecimal matedCycles = decimal(mating.getMatedCycleCount());
        BigDecimal matedDoes = decimal(mating.getMatedDoeCount());
        BigDecimal pregnantCycles = decimal(mating.getPregnantCycleCount());
        BigDecimal pregnantDoes = decimal(mating.getPregnantDoeCount());
        BigDecimal matedBucks = decimal(mating.getMatedBuckCount());
        BigDecimal abortedCycles = decimal(abortion.getAbortedPregnantCycleCount());

        BigDecimal litterCount = decimal(litter.getTotalLitters());
        BigDecimal totalKits = decimal(litter.getTotalKits());
        BigDecimal liveKits = decimal(litter.getTotalLiveKits());
        BigDecimal keptLitterCount = decimal(litter.getKeptLitterCount());
        BigDecimal keptKits = decimal(litter.getTotalKept());
        BigDecimal weanedKits = decimal(litter.getTotalWeaned());
        BigDecimal weaningWeight = decimal(litter.getTotalWeaningWeightKg());

        BigDecimal soldRabbits = decimal(salesCount.getSoldRabbitCount());
        BigDecimal soldWeight = decimal(salesValue.getSoldWeightKg());
        BigDecimal salesAmount = decimal(salesValue.getTotalSalesAmount());
        BigDecimal breedingFeed = decimal(feed.getBreedingFeedAmountKg());
        BigDecimal fatteningFeed = decimal(feed.getFatteningFeedAmountKg());
        BigDecimal fullFeed = breedingFeed.add(fatteningFeed);
        BigDecimal replacementWeight = decimal(replacement.getReplacementWeightKg());
        BigDecimal totalOutputWeight = soldWeight.add(replacementWeight);
        BigDecimal fatteningGain = totalOutputWeight.subtract(weaningWeight);

        List<Metric> metrics = new ArrayList<>(CATALOG.size());
        metrics.add(dateMetric(matingDates));
        metrics.add(numberMetric("MATED_DOE_COUNT", matedDoes));
        metrics.add(ratioMetric(
            "CONCEPTION_RATE",
            pregnantCycles,
            matedCycles,
            operand("PREGNANT_CYCLES", "确认怀孕周期数", pregnantCycles, "COUNT"),
            operand("MATED_CYCLES", "已配种周期数", matedCycles, "COUNT")
        ));
        metrics.add(ratioMetric(
            "DOE_BUCK_RATIO",
            matedDoes,
            matedBucks,
            operand("MATED_DOES", "去重配种母兔数", matedDoes, "COUNT"),
            operand("MATED_BUCKS", "去重参与配种公兔数", matedBucks, "COUNT"),
            cause(mating.getMissingNaturalMale(), Cause.MISSING_NATURAL_MALE)
        ));
        metrics.add(numberMetric("PREGNANT_DOE_COUNT", pregnantDoes));
        metrics.add(ratioMetric(
            "ABORTION_RATE",
            abortedCycles,
            pregnantCycles,
            operand("ABORTED_PREGNANT_CYCLES", "已怀孕流产周期数", abortedCycles, "COUNT"),
            operand("PREGNANT_CYCLES", "确认怀孕周期数", pregnantCycles, "COUNT"),
            cause(abortion.getMissingPregnancyEvidence(), Cause.MISSING_PREGNANCY_EVIDENCE)
        ));
        metrics.add(numberMetric("DELIVERED_LITTER_COUNT", litterCount));
        metrics.add(numberMetric("TOTAL_KIT_COUNT", totalKits));
        metrics.add(ratioMetric(
            "AVERAGE_KITS_PER_LITTER",
            totalKits,
            litterCount,
            operand("TOTAL_KITS", "产崽总数", totalKits, "COUNT"),
            operand("DELIVERED_LITTERS", "产崽窝数", litterCount, "LITTER")
        ));
        metrics.add(numberMetric("LIVE_KIT_COUNT", liveKits));
        metrics.add(ratioMetric(
            "LIVE_BIRTH_RATE",
            liveKits,
            totalKits,
            operand("LIVE_KITS", "活崽总数", liveKits, "COUNT"),
            operand("TOTAL_KITS", "产崽总数", totalKits, "COUNT")
        ));
        metrics.add(numberMetric("KEPT_LITTER_COUNT", keptLitterCount));
        metrics.add(numberMetric("KEPT_KIT_COUNT", keptKits));
        metrics.add(ratioMetric(
            "KEPT_LIVE_RATE",
            keptKits,
            liveKits,
            operand("KEPT_KITS", "选留总数", keptKits, "COUNT"),
            operand("LIVE_KITS", "活崽总数", liveKits, "COUNT")
        ));
        metrics.add(ratioMetric(
            "AVERAGE_KEPT_PER_LITTER",
            keptKits,
            keptLitterCount,
            operand("KEPT_KITS", "选留总数", keptKits, "COUNT"),
            operand("KEPT_LITTERS", "选留窝数", keptLitterCount, "LITTER")
        ));
        metrics.add(numberMetric("WEANED_KIT_COUNT", weanedKits));
        metrics.add(ratioMetric(
            "AVERAGE_WEANING_WEIGHT",
            weaningWeight,
            weanedKits,
            operand("WEANING_TOTAL_WEIGHT", "断奶总重", weaningWeight, "KG"),
            operand("WEANED_KITS", "断奶数量", weanedKits, "COUNT"),
            cause(litter.getMissingWeaningWeight(), Cause.MISSING_WEANING_WEIGHT)
        ));
        metrics.add(ratioMetric(
            "WEANING_SURVIVAL_RATE",
            weanedKits,
            keptKits,
            operand("WEANED_KITS", "断奶数量", weanedKits, "COUNT"),
            operand("KEPT_KITS", "选留总数", keptKits, "COUNT")
        ));
        metrics.add(numberMetric(
            "SOLD_RABBIT_COUNT",
            soldRabbits,
            cause(salesCount.getMissingBatchAttribution(), Cause.MISSING_BATCH_ATTRIBUTION)
        ));
        metrics.add(ratioMetric(
            "OUTBOUND_SURVIVAL_RATE",
            soldRabbits,
            weanedKits,
            operand("SOLD_RABBITS", "出栏数量", soldRabbits, "COUNT"),
            operand("WEANED_KITS", "断奶数量", weanedKits, "COUNT"),
            cause(salesCount.getMissingBatchAttribution(), Cause.MISSING_BATCH_ATTRIBUTION)
        ));
        metrics.add(numberMetric(
            "SOLD_WEIGHT",
            soldWeight,
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION)
        ));
        metrics.add(ratioMetric(
            "AVERAGE_SOLD_WEIGHT",
            soldWeight,
            soldRabbits,
            operand("SOLD_WEIGHT", "出栏总重", soldWeight, "KG"),
            operand("SOLD_RABBITS", "出栏数量", soldRabbits, "COUNT"),
            cause(salesCount.getMissingBatchAttribution(), Cause.MISSING_BATCH_ATTRIBUTION),
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION)
        ));
        metrics.add(numberMetric(
            "TOTAL_SALES_AMOUNT",
            salesAmount,
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION),
            cause(salesValue.getMissingSaleUnitPrice(), Cause.MISSING_SALE_UNIT_PRICE)
        ));
        metrics.add(ratioMetric(
            "SALES_PRICE_PER_KG",
            salesAmount,
            soldWeight,
            operand("TOTAL_SALES_AMOUNT", "总销售金额", salesAmount, "CNY"),
            operand("SOLD_WEIGHT", "出栏总重", soldWeight, "KG"),
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION),
            cause(salesValue.getMissingSaleUnitPrice(), Cause.MISSING_SALE_UNIT_PRICE)
        ));
        metrics.add(ratioMetric(
            "SALES_PRICE_PER_RABBIT",
            salesAmount,
            soldRabbits,
            operand("TOTAL_SALES_AMOUNT", "总销售金额", salesAmount, "CNY"),
            operand("SOLD_RABBITS", "出栏数量", soldRabbits, "COUNT"),
            cause(salesCount.getMissingBatchAttribution(), Cause.MISSING_BATCH_ATTRIBUTION),
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION),
            cause(salesValue.getMissingSaleUnitPrice(), Cause.MISSING_SALE_UNIT_PRICE)
        ));
        metrics.add(ratioMetric(
            "FULL_FEED_CONVERSION_RATIO",
            fullFeed,
            totalOutputWeight,
            operand("FULL_FEED", "批次全程饲料量", fullFeed, "KG"),
            operand("TOTAL_OUTPUT_WEIGHT", "商品兔销售重量与转后备重量", totalOutputWeight, "KG"),
            List.of(
                operand("BREEDING_FEED", "繁殖阶段饲料量", breedingFeed, "KG"),
                operand("FATTENING_FEED", "育肥阶段饲料量", fatteningFeed, "KG"),
                operand("SOLD_WEIGHT", "商品兔实际销售重量", soldWeight, "KG"),
                operand("REPLACEMENT_WEIGHT", "转后备兔实测总重", replacementWeight, "KG")
            ),
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION),
            cause(feed.getMissingFeedAllocation(), Cause.MISSING_FEED_ALLOCATION),
            cause(feed.getMissingFeedUnit(), Cause.MISSING_FEED_UNIT),
            cause(replacement.getMissingReplacementWeight(), Cause.MISSING_REPLACEMENT_WEIGHT)
        ));
        metrics.add(fatteningFeedConversionMetric(
            fatteningFeed,
            fatteningGain,
            soldWeight,
            replacementWeight,
            weaningWeight,
            litter,
            salesValue,
            feed,
            replacement
        ));
        metrics.add(carcassYieldMetric(carcass));
        return List.copyOf(metrics);
    }

    private Metric dateMetric(List<BatchStatisticsMatingDateRow> rows) {
        Map<LocalDate, Integer> countsByDate = new LinkedHashMap<>();
        rows.stream()
            .filter(row -> row != null && row.getDate() != null)
            .sorted((left, right) -> left.getDate().compareTo(right.getDate()))
            .forEach(row -> countsByDate.merge(row.getDate(), integer(row.getCycleCount()), Integer::sum));
        if (countsByDate.isEmpty()) {
            return unavailable("MATING_DATE", Status.NOT_RECORDED, Cause.MATING_NOT_RECORDED);
        }

        List<DailyCycleCount> dailyCounts = countsByDate.entrySet().stream()
            .map(entry -> new DailyCycleCount(entry.getKey(), entry.getValue()))
            .toList();
        LocalDate firstDate = dailyCounts.get(0).date();
        LocalDate lastDate = dailyCounts.get(dailyCounts.size() - 1).date();
        DateRangeValue value = new DateRangeValue(
            firstDate,
            lastDate,
            dailyCounts.size(),
            dailyCounts
        );
        String display = dailyCounts.size() == 1
            ? firstDate.toString()
            : firstDate + " 至 " + lastDate + "（" + dailyCounts.size() + "个配种日）";
        return metric("MATING_DATE", Status.AVAILABLE, null, display, value, null, null, List.of(), List.of());
    }

    private Metric fatteningFeedConversionMetric(
        BigDecimal fatteningFeed,
        BigDecimal fatteningGain,
        BigDecimal soldWeight,
        BigDecimal replacementWeight,
        BigDecimal weaningWeight,
        BatchStatisticsRawSnapshot litter,
        BatchStatisticsRawSnapshot salesValue,
        BatchStatisticsRawSnapshot feed,
        BatchStatisticsRawSnapshot replacement
    ) {
        List<Cause> missing = orderedCauses(
            cause(litter.getMissingWeaningWeight(), Cause.MISSING_WEANING_WEIGHT),
            cause(salesValue.getMissingBatchSaleAllocation(), Cause.MISSING_BATCH_SALE_ALLOCATION),
            cause(feed.getMissingFeedAllocation(), Cause.MISSING_FEED_ALLOCATION),
            cause(feed.getMissingFeedUnit(), Cause.MISSING_FEED_UNIT),
            cause(replacement.getMissingReplacementWeight(), Cause.MISSING_REPLACEMENT_WEIGHT)
        );
        if (!missing.isEmpty()) {
            return unavailable("FATTENING_FEED_CONVERSION_RATIO", Status.DATA_MISSING, missing);
        }
        Operand numerator = operand(
            "FATTENING_FEED", "批次育肥饲料量", fatteningFeed, "KG"
        );
        Operand denominator = operand("FATTENING_GAIN", "育肥增重", fatteningGain, "KG");
        List<Operand> components = List.of(
            operand("SOLD_WEIGHT", "商品兔实际销售重量", soldWeight, "KG"),
            operand("REPLACEMENT_WEIGHT", "转后备兔实测总重", replacementWeight, "KG"),
            operand("WEANING_TOTAL_WEIGHT", "断奶总重", weaningWeight, "KG")
        );
        if (fatteningGain.signum() < 0) {
            return unavailableWithOperands(
                "FATTENING_FEED_CONVERSION_RATIO",
                Status.DATA_MISSING,
                numerator,
                denominator,
                components,
                Cause.INVALID_FATTENING_GAIN
            );
        }
        return ratioMetric(
            "FATTENING_FEED_CONVERSION_RATIO",
            fatteningFeed,
            fatteningGain,
            numerator,
            denominator,
            components
        );
    }

    private Metric carcassYieldMetric(BatchStatisticsRawSnapshot carcass) {
        if (carcass == null || carcass.getCarcassYieldRate() == null) {
            return unavailable(
                "CARCASS_YIELD_RATE",
                Status.NOT_RECORDED,
                Cause.CARCASS_YIELD_NOT_RECORDED
            );
        }
        return numberMetric("CARCASS_YIELD_RATE", carcass.getCarcassYieldRate());
    }

    private Metric numberMetric(String code, BigDecimal value, Cause... causes) {
        List<Cause> missing = orderedCauses(causes);
        if (!missing.isEmpty()) {
            return unavailable(code, Status.DATA_MISSING, missing);
        }
        return metric(
            code,
            Status.AVAILABLE,
            value,
            format(code, value),
            null,
            null,
            null,
            List.of(),
            List.of()
        );
    }

    private Metric ratioMetric(
        String code,
        BigDecimal numeratorValue,
        BigDecimal denominatorValue,
        Operand numerator,
        Operand denominator,
        Cause... causes
    ) {
        return ratioMetric(code, numeratorValue, denominatorValue, numerator, denominator, List.of(), causes);
    }

    private Metric ratioMetric(
        String code,
        BigDecimal numeratorValue,
        BigDecimal denominatorValue,
        Operand numerator,
        Operand denominator,
        List<Operand> components,
        Cause... causes
    ) {
        List<Cause> missing = orderedCauses(causes);
        if (!missing.isEmpty()) {
            return unavailable(code, Status.DATA_MISSING, missing);
        }
        if (denominatorValue.signum() == 0) {
            return unavailableWithOperands(
                code,
                Status.NOT_APPLICABLE,
                numerator,
                denominator,
                components,
                Cause.ZERO_DENOMINATOR
            );
        }
        BigDecimal value = numeratorValue.divide(denominatorValue, CALCULATION_CONTEXT);
        return metric(
            code,
            Status.AVAILABLE,
            value,
            format(code, value),
            null,
            numerator,
            denominator,
            components,
            List.of()
        );
    }

    private Metric unavailable(String code, Status status, Cause... causes) {
        return unavailable(code, status, orderedCauses(causes));
    }

    private Metric unavailable(String code, Status status, List<Cause> causes) {
        return unavailableWithOperands(
            code, status, null, null, List.of(), causes.toArray(Cause[]::new)
        );
    }

    private Metric unavailableWithOperands(
        String code,
        Status status,
        Operand numerator,
        Operand denominator,
        List<Operand> components,
        Cause... causes
    ) {
        List<MissingCause> missingCauses = orderedCauses(causes).stream()
            .map(cause -> new MissingCause(cause.name(), cause.message))
            .toList();
        return metric(
            code,
            status,
            null,
            null,
            null,
            numerator,
            denominator,
            components,
            missingCauses
        );
    }

    private Metric metric(
        String code,
        Status status,
        BigDecimal numericValue,
        String displayValue,
        DateRangeValue dateValue,
        Operand numerator,
        Operand denominator,
        List<Operand> components,
        List<MissingCause> missingCauses
    ) {
        MetricSpec spec = SPEC_BY_CODE.get(code);
        return new Metric(
            spec.code,
            spec.name,
            spec.stage,
            spec.stageName,
            spec.order,
            spec.excelColumnName,
            spec.valueType,
            spec.unit,
            spec.format,
            spec.formula,
            status.name(),
            numericValue,
            displayValue,
            dateValue,
            numerator,
            denominator,
            components,
            missingCauses
        );
    }

    private static Operand operand(String code, String label, BigDecimal value, String unit) {
        return new Operand(code, label, value, unit);
    }

    private static String format(String code, BigDecimal value) {
        MetricSpec spec = SPEC_BY_CODE.get(code);
        return switch (spec.format) {
            case "INTEGER" -> decimalFormat("#,##0").format(value);
            case "PERCENT_2" -> decimalFormat("#,##0.00").format(value.multiply(BigDecimal.valueOf(100))) + "%";
            case "RATIO_TO_ONE" -> decimalFormat("#,##0.00").format(value) + ":1";
            case "DECIMAL_2" -> decimalFormat("#,##0.00").format(value) + displayUnitSuffix(spec.unit);
            default -> throw new IllegalStateException("未知统计格式: " + spec.format);
        };
    }

    private static DecimalFormat decimalFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(
            pattern,
            DecimalFormatSymbols.getInstance(Locale.US)
        );
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private static String displayUnitSuffix(String unit) {
        return switch (unit) {
            case "KG", "KG_PER_RABBIT" -> " kg";
            case "CNY" -> " 元";
            case "CNY_PER_KG" -> " 元/kg";
            case "CNY_PER_RABBIT" -> " 元/只";
            default -> "";
        };
    }

    private static Cause cause(Boolean present, Cause cause) {
        return Boolean.TRUE.equals(present) ? cause : null;
    }

    private static List<Cause> orderedCauses(Cause... causes) {
        EnumSet<Cause> unique = EnumSet.noneOf(Cause.class);
        Arrays.stream(causes).filter(java.util.Objects::nonNull).forEach(unique::add);
        return List.copyOf(unique);
    }

    private static BatchStatisticsRawSnapshot valueOrEmpty(BatchStatisticsRawSnapshot value) {
        return value == null ? new BatchStatisticsRawSnapshot() : value;
    }

    private static List<BatchStatisticsMatingDateRow> valueOrEmpty(
        List<BatchStatisticsMatingDateRow> value
    ) {
        return value == null ? List.of() : value;
    }

    private static int integer(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal decimal(Integer value) {
        return BigDecimal.valueOf(integer(value));
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static MetricSpec spec(
        String code,
        String name,
        String stage,
        String stageName,
        int order,
        String excelColumnName,
        String valueType,
        String unit,
        String format,
        String formula
    ) {
        return new MetricSpec(
            code,
            name,
            stage,
            stageName,
            order,
            excelColumnName,
            valueType,
            unit,
            format,
            formula
        );
    }

    private static Map<String, MetricSpec> catalogByCode() {
        Map<String, MetricSpec> result = new LinkedHashMap<>();
        for (MetricSpec spec : CATALOG) {
            if (result.put(spec.code, spec) != null) {
                throw new IllegalStateException("重复统计编码: " + spec.code);
            }
        }
        return Map.copyOf(result);
    }

    private enum Status {
        AVAILABLE,
        NOT_APPLICABLE,
        NOT_RECORDED,
        DATA_MISSING
    }

    private enum Cause {
        MISSING_BATCH_ATTRIBUTION("部分销售记录缺少批次归属快照"),
        MISSING_NATURAL_MALE("自然配种周期缺少公兔"),
        MISSING_PREGNANCY_EVIDENCE("流产记录缺少已确认怀孕周期依据"),
        MISSING_WEANING_WEIGHT("断奶记录缺少总重快照"),
        MISSING_BATCH_SALE_ALLOCATION("销售记录缺少批次重量分配"),
        MISSING_SALE_UNIT_PRICE("销售记录缺少重量单价或金额快照"),
        MISSING_FEED_ALLOCATION("投喂记录缺少批次阶段分配"),
        MISSING_FEED_UNIT("投喂记录单位不是kg"),
        MISSING_REPLACEMENT_WEIGHT("转后备记录缺少实测重量快照"),
        INVALID_FATTENING_GAIN("育肥增重小于零，请核对重量账"),
        MATING_NOT_RECORDED("未记录配种日期"),
        CARCASS_YIELD_NOT_RECORDED("未录入出肉率"),
        ZERO_DENOMINATOR("计算分母为零");

        private final String message;

        Cause(String message) {
            this.message = message;
        }
    }

    private record MetricSpec(
        String code,
        String name,
        String stage,
        String stageName,
        int order,
        String excelColumnName,
        String valueType,
        String unit,
        String format,
        String formula
    ) {
    }
}
