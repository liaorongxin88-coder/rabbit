import type {
  BatchMetricOperand,
  BatchMetricStatus,
  BatchStatisticMetric,
  BatchStatistics,
} from "@/types/api";

export interface BatchMetricContract {
  code: string;
  name: string;
  stage: string;
  stageName: string;
  order: number;
  excelColumnName: string;
  valueType: "NUMBER" | "DATE_RANGE";
  unit: string;
  format: string;
  formula: string;
}

export const BATCH_METRIC_CONTRACTS: BatchMetricContract[] = [
  {
    code: "MATING_DATE",
    name: "配种日期",
    stage: "MATING",
    stageName: "配种",
    order: 10,
    excelColumnName: "配种日期",
    valueType: "DATE_RANGE",
    unit: "DATE",
    format: "DATE_RANGE",
    formula: "配种日期按业务自然日去重",
  },
  {
    code: "MATED_DOE_COUNT",
    name: "配种母兔数",
    stage: "MATING",
    stageName: "配种",
    order: 20,
    excelColumnName: "配种母兔数",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "已配种周期中的去重母兔数",
  },
  {
    code: "CONCEPTION_RATE",
    name: "受胎率",
    stage: "MATING",
    stageName: "配种",
    order: 30,
    excelColumnName: "受胎率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "确认怀孕周期数 / 已配种周期数",
  },
  {
    code: "DOE_BUCK_RATIO",
    name: "配种母兔/公兔比例",
    stage: "MATING",
    stageName: "配种",
    order: 40,
    excelColumnName: "配种母兔/公兔比例",
    valueType: "NUMBER",
    unit: "RATIO",
    format: "RATIO_TO_ONE",
    formula: "去重配种母兔数 / 去重参与配种公兔数",
  },
  {
    code: "PREGNANT_DOE_COUNT",
    name: "怀孕数量",
    stage: "PREGNANCY",
    stageName: "怀孕",
    order: 50,
    excelColumnName: "怀孕数量",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "确认怀孕周期中的去重母兔数",
  },
  {
    code: "ABORTION_RATE",
    name: "流产率",
    stage: "PREGNANCY",
    stageName: "怀孕",
    order: 60,
    excelColumnName: "流产率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "已怀孕流产周期数 / 确认怀孕周期数",
  },
  {
    code: "DELIVERED_LITTER_COUNT",
    name: "产崽窝数",
    stage: "BIRTH",
    stageName: "产崽",
    order: 70,
    excelColumnName: "产崽窝数",
    valueType: "NUMBER",
    unit: "LITTER",
    format: "INTEGER",
    formula: "批次内产崽窝数",
  },
  {
    code: "TOTAL_KIT_COUNT",
    name: "产崽总数",
    stage: "BIRTH",
    stageName: "产崽",
    order: 80,
    excelColumnName: "产崽总数",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "批次内产崽数之和",
  },
  {
    code: "AVERAGE_KITS_PER_LITTER",
    name: "平均窝产数",
    stage: "BIRTH",
    stageName: "产崽",
    order: 90,
    excelColumnName: "平均窝产数",
    valueType: "NUMBER",
    unit: "COUNT_PER_LITTER",
    format: "DECIMAL_2",
    formula: "产崽总数 / 产崽窝数",
  },
  {
    code: "LIVE_KIT_COUNT",
    name: "活崽总数",
    stage: "BIRTH",
    stageName: "产崽",
    order: 100,
    excelColumnName: "活崽总数",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "批次内活崽数之和",
  },
  {
    code: "LIVE_BIRTH_RATE",
    name: "平均活崽率",
    stage: "BIRTH",
    stageName: "产崽",
    order: 110,
    excelColumnName: "平均活崽率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "活崽总数 / 产崽总数",
  },
  {
    code: "KEPT_LITTER_COUNT",
    name: "选留窝数",
    stage: "SELECTION",
    stageName: "选留",
    order: 120,
    excelColumnName: "选留窝数",
    valueType: "NUMBER",
    unit: "LITTER",
    format: "INTEGER",
    formula: "留崽数大于零的窝数",
  },
  {
    code: "KEPT_KIT_COUNT",
    name: "选留总数",
    stage: "SELECTION",
    stageName: "选留",
    order: 130,
    excelColumnName: "选留总数",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "批次内选留数之和",
  },
  {
    code: "KEPT_LIVE_RATE",
    name: "选留活崽率",
    stage: "SELECTION",
    stageName: "选留",
    order: 140,
    excelColumnName: "选留活崽率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "选留总数 / 活崽总数",
  },
  {
    code: "AVERAGE_KEPT_PER_LITTER",
    name: "窝均选留",
    stage: "SELECTION",
    stageName: "选留",
    order: 150,
    excelColumnName: "窝均选留",
    valueType: "NUMBER",
    unit: "COUNT_PER_LITTER",
    format: "DECIMAL_2",
    formula: "选留总数 / 选留窝数",
  },
  {
    code: "WEANED_KIT_COUNT",
    name: "断奶数量",
    stage: "WEANING",
    stageName: "断奶",
    order: 160,
    excelColumnName: "断奶数量",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "批次内断奶数之和",
  },
  {
    code: "AVERAGE_WEANING_WEIGHT",
    name: "断奶均重",
    stage: "WEANING",
    stageName: "断奶",
    order: 170,
    excelColumnName: "断奶均重",
    valueType: "NUMBER",
    unit: "KG_PER_RABBIT",
    format: "DECIMAL_2",
    formula: "断奶总重快照之和 / 断奶数量",
  },
  {
    code: "WEANING_SURVIVAL_RATE",
    name: "断奶成活率",
    stage: "WEANING",
    stageName: "断奶",
    order: 180,
    excelColumnName: "断奶成活率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "断奶数量 / 选留总数",
  },
  {
    code: "SOLD_RABBIT_COUNT",
    name: "出栏数量",
    stage: "OUTBOUND",
    stageName: "出栏",
    order: 190,
    excelColumnName: "出栏数量",
    valueType: "NUMBER",
    unit: "COUNT",
    format: "INTEGER",
    formula: "批次快照匹配的已销售兔只数",
  },
  {
    code: "OUTBOUND_SURVIVAL_RATE",
    name: "出栏成活率",
    stage: "OUTBOUND",
    stageName: "出栏",
    order: 200,
    excelColumnName: "出栏成活率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "出栏数量 / 断奶数量",
  },
  {
    code: "SOLD_WEIGHT",
    name: "出栏总重",
    stage: "OUTBOUND",
    stageName: "出栏",
    order: 210,
    excelColumnName: "出栏总重",
    valueType: "NUMBER",
    unit: "KG",
    format: "DECIMAL_2",
    formula: "批次销售实际重量之和",
  },
  {
    code: "AVERAGE_SOLD_WEIGHT",
    name: "出栏均重",
    stage: "OUTBOUND",
    stageName: "出栏",
    order: 220,
    excelColumnName: "出栏均重",
    valueType: "NUMBER",
    unit: "KG_PER_RABBIT",
    format: "DECIMAL_2",
    formula: "出栏总重 / 出栏数量",
  },
  {
    code: "TOTAL_SALES_AMOUNT",
    name: "总销售金额",
    stage: "SALES",
    stageName: "销售",
    order: 230,
    excelColumnName: "总销售金额",
    valueType: "NUMBER",
    unit: "CNY",
    format: "DECIMAL_2",
    formula: "批次销售金额快照之和",
  },
  {
    code: "SALES_PRICE_PER_KG",
    name: "销售单价（重量口径）",
    stage: "SALES",
    stageName: "销售",
    order: 240,
    excelColumnName: "销售单价（重量口径）",
    valueType: "NUMBER",
    unit: "CNY_PER_KG",
    format: "DECIMAL_2",
    formula: "总销售金额 / 出栏总重",
  },
  {
    code: "SALES_PRICE_PER_RABBIT",
    name: "销售单价（只数口径）",
    stage: "SALES",
    stageName: "销售",
    order: 250,
    excelColumnName: "销售单价（只数口径）",
    valueType: "NUMBER",
    unit: "CNY_PER_RABBIT",
    format: "DECIMAL_2",
    formula: "总销售金额 / 出栏数量",
  },
  {
    code: "FULL_FEED_CONVERSION_RATIO",
    name: "全程料肉比",
    stage: "FEED_CONVERSION",
    stageName: "料肉比",
    order: 260,
    excelColumnName: "全程料肉比",
    valueType: "NUMBER",
    unit: "RATIO",
    format: "DECIMAL_2",
    formula: "批次全程饲料量 /（商品兔实际销售重量 + 转后备兔实测总重）",
  },
  {
    code: "FATTENING_FEED_CONVERSION_RATIO",
    name: "育肥期料肉比",
    stage: "FEED_CONVERSION",
    stageName: "料肉比",
    order: 270,
    excelColumnName: "育肥期料肉比",
    valueType: "NUMBER",
    unit: "RATIO",
    format: "DECIMAL_2",
    formula:
      "批次育肥饲料量 /（商品兔实际销售重量 + 转后备兔实测总重 - 断奶总重）",
  },
  {
    code: "CARCASS_YIELD_RATE",
    name: "出肉率",
    stage: "FEED_CONVERSION",
    stageName: "料肉比",
    order: 280,
    excelColumnName: "出肉率",
    valueType: "NUMBER",
    unit: "PERCENT",
    format: "PERCENT_2",
    formula: "最新出肉率版本",
  },
];

export const BATCH_METRIC_CODES = BATCH_METRIC_CONTRACTS.map(
  (contract) => contract.code,
);

const metricContractByCode = new Map(
  BATCH_METRIC_CONTRACTS.map((contract) => [contract.code, contract]),
);

const missingCauseOrder = new Map(
  [
    "MISSING_BATCH_ATTRIBUTION",
    "MISSING_NATURAL_MALE",
    "MISSING_PREGNANCY_EVIDENCE",
    "MISSING_WEANING_WEIGHT",
    "MISSING_BATCH_SALE_ALLOCATION",
    "MISSING_SALE_UNIT_PRICE",
    "MISSING_FEED_ALLOCATION",
    "MISSING_FEED_UNIT",
    "MISSING_REPLACEMENT_WEIGHT",
    "INVALID_FATTENING_GAIN",
    "MATING_NOT_RECORDED",
    "CARCASS_YIELD_NOT_RECORDED",
    "ZERO_DENOMINATOR",
  ].map((code, index) => [code, index]),
);

export interface BatchMetricLayoutGroup {
  stage: string;
  name: string;
  rows: string[][];
}

/**
 * Each row mirrors the metric relationship in the approved source.
 * Every code is an independent metric item within that row.
 */
export const BATCH_METRIC_LAYOUT: BatchMetricLayoutGroup[] = [
  {
    stage: "MATING",
    name: "配种",
    rows: [
      ["MATING_DATE"],
      ["MATED_DOE_COUNT", "CONCEPTION_RATE"],
      ["DOE_BUCK_RATIO"],
    ],
  },
  {
    stage: "PREGNANCY",
    name: "怀孕",
    rows: [["PREGNANT_DOE_COUNT", "ABORTION_RATE"]],
  },
  {
    stage: "BIRTH",
    name: "产崽",
    rows: [
      ["DELIVERED_LITTER_COUNT", "TOTAL_KIT_COUNT"],
      ["AVERAGE_KITS_PER_LITTER"],
      ["LIVE_KIT_COUNT", "LIVE_BIRTH_RATE"],
    ],
  },
  {
    stage: "SELECTION",
    name: "选留",
    rows: [
      ["KEPT_LITTER_COUNT", "KEPT_KIT_COUNT"],
      ["KEPT_LIVE_RATE", "AVERAGE_KEPT_PER_LITTER"],
    ],
  },
  {
    stage: "WEANING",
    name: "断奶",
    rows: [
      ["WEANED_KIT_COUNT"],
      ["AVERAGE_WEANING_WEIGHT", "WEANING_SURVIVAL_RATE"],
    ],
  },
  {
    stage: "OUTBOUND",
    name: "出栏",
    rows: [
      ["SOLD_RABBIT_COUNT", "OUTBOUND_SURVIVAL_RATE"],
      ["SOLD_WEIGHT", "AVERAGE_SOLD_WEIGHT"],
    ],
  },
  {
    stage: "SALES",
    name: "销售",
    rows: [
      ["TOTAL_SALES_AMOUNT", "SALES_PRICE_PER_KG", "SALES_PRICE_PER_RABBIT"],
    ],
  },
  {
    stage: "FEED_CONVERSION",
    name: "料肉比",
    rows: [
      ["FULL_FEED_CONVERSION_RATIO", "FATTENING_FEED_CONVERSION_RATIO"],
      ["CARCASS_YIELD_RATE"],
    ],
  },
];

const metricStatuses = new Set([
  "AVAILABLE",
  "NOT_APPLICABLE",
  "NOT_RECORDED",
  "DATA_MISSING",
]);
const metricValueTypes = new Set(["NUMBER", "DATE_RANGE"]);

export function batchStatisticsContractError(
  statistics: BatchStatistics,
): string | null {
  if (
    !statistics ||
    typeof statistics !== "object" ||
    statistics.schemaVersion !== 1 ||
    !Array.isArray(statistics.metrics)
  ) {
    return "当前服务尚未提供完整批次统计，请升级服务后重试。";
  }
  if (
    !Number.isSafeInteger(statistics.batchId) ||
    statistics.batchId <= 0 ||
    typeof statistics.houseName !== "string" ||
    !statistics.houseName.trim() ||
    typeof statistics.batchCode !== "string" ||
    !statistics.batchCode.trim() ||
    typeof statistics.calculatedAt !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]00:00)$/.test(
      statistics.calculatedAt,
    ) ||
    Number.isNaN(Date.parse(statistics.calculatedAt)) ||
    !["totalLitters", "totalKits", "totalLiveKits", "totalWeaned"].every(
      (field) => {
        const value = statistics[field as keyof BatchStatistics];
        return Number.isSafeInteger(value) && Number(value) >= 0;
      },
    )
  ) {
    return "批次统计缺少批次或取数信息，请联系管理员核对服务版本。";
  }

  const seen = new Set<string>();
  const fixedCodes: string[] = [];
  for (const metric of statistics.metrics) {
    if (!metric?.code || seen.has(metric.code)) {
      return "批次统计包含重复或无效指标，请联系管理员核对服务版本。";
    }
    seen.add(metric.code);
    if (
      !metricStatuses.has(metric.status) ||
      !metricValueTypes.has(metric.valueType)
    ) {
      return "批次统计包含无法识别的数据状态，请升级管理端后重试。";
    }

    const contract = metricContractByCode.get(metric.code);
    if (!contract) continue;
    fixedCodes.push(metric.code);
    if (
      metric.name !== contract.name ||
      metric.stage !== contract.stage ||
      metric.stageName !== contract.stageName ||
      metric.order !== contract.order ||
      metric.excelColumnName !== contract.excelColumnName ||
      metric.valueType !== contract.valueType ||
      metric.unit !== contract.unit ||
      metric.format !== contract.format ||
      metric.formula !== contract.formula
    ) {
      return `批次统计指标 ${metric.code} 的版本 1 元数据不一致，请升级服务后重试。`;
    }
    if (
      !hasMetricDetailShape(metric) ||
      metricShapeError(metric) ||
      missingCauseShapeError(metric)
    ) {
      return `批次统计指标 ${metric.code} 的值或状态不符合版本 1 契约。`;
    }
  }

  const missing = BATCH_METRIC_CODES.filter((code) => !seen.has(code));
  if (missing.length > 0) {
    return `批次统计缺少 ${missing.length} 项固定指标，请联系管理员核对服务版本。`;
  }
  return fixedCodes.every((code, index) => code === BATCH_METRIC_CODES[index])
    ? null
    : "批次统计固定指标顺序不正确，请联系管理员核对服务版本。";
}

function hasMetricDetailShape(metric: BatchStatisticMetric) {
  const fields = [
    "numericValue",
    "dateValue",
    "displayValue",
    "numerator",
    "denominator",
    "components",
    "missingCauses",
  ];
  if (
    !fields.every((field) => Object.hasOwn(metric, field)) ||
    !Array.isArray(metric.components) ||
    !Array.isArray(metric.missingCauses)
  ) {
    return false;
  }
  return [metric.numerator, metric.denominator, ...metric.components].every(
    (operand) =>
      operand === null ||
      (typeof operand === "object" &&
        typeof operand.code === "string" &&
        operand.code.trim().length > 0 &&
        typeof operand.label === "string" &&
        operand.label.trim().length > 0 &&
        typeof operand.unit === "string" &&
        (operand.value === null ||
          (typeof operand.value === "number" &&
            Number.isFinite(operand.value)))),
  );
}

function metricShapeError(metric: BatchStatisticMetric) {
  const hasNumericValue =
    typeof metric.numericValue === "number" &&
    Number.isFinite(metric.numericValue) &&
    metric.numericValue >= 0;
  const hasDisplayValue =
    typeof metric.displayValue === "string" &&
    metric.displayValue.trim() !== "";
  if (metric.status !== "AVAILABLE") {
    return (
      metric.numericValue !== null ||
      metric.dateValue !== null ||
      metric.displayValue !== null
    );
  }
  if (!hasDisplayValue) return true;
  if (metric.valueType === "NUMBER") {
    if (!hasNumericValue || metric.dateValue !== null) return true;
    if (
      metric.format === "INTEGER" &&
      !Number.isSafeInteger(metric.numericValue)
    ) {
      return true;
    }
    if (
      metric.format === "PERCENT_2" &&
      (metric.numericValue! < 0 || metric.numericValue! > 1)
    ) {
      return true;
    }
    return false;
  }
  return metric.numericValue !== null || !validDateValue(metric.dateValue);
}

function validDateValue(value: BatchStatisticMetric["dateValue"]) {
  if (!value || !Array.isArray(value.dailyCycleCounts)) return false;
  const validDate = (date: unknown) => {
    if (typeof date !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return false;
    }
    const timestamp = Date.parse(`${date}T00:00:00Z`);
    return (
      Number.isFinite(timestamp) &&
      new Date(timestamp).toISOString().slice(0, 10) === date
    );
  };
  return (
    validDate(value.firstDate) &&
    validDate(value.lastDate) &&
    value.firstDate <= value.lastDate &&
    Number.isSafeInteger(value.dateCount) &&
    value.dateCount > 0 &&
    value.dailyCycleCounts.length === value.dateCount &&
    value.dailyCycleCounts.every(
      (item, index, items) =>
        item !== null &&
        typeof item === "object" &&
        validDate(item.date) &&
        Number.isSafeInteger(item.cycleCount) &&
        item.cycleCount > 0 &&
        (index === 0 || items[index - 1].date < item.date),
    ) &&
    value.dailyCycleCounts[0]?.date === value.firstDate &&
    value.dailyCycleCounts.at(-1)?.date === value.lastDate
  );
}

function missingCauseShapeError(metric: BatchStatisticMetric) {
  let previousOrder = -1;
  const seen = new Set<string>();
  for (const cause of metric.missingCauses) {
    if (
      cause === null ||
      typeof cause !== "object" ||
      typeof cause.code !== "string" ||
      typeof cause.message !== "string"
    ) {
      return true;
    }
    const order = missingCauseOrder.get(cause.code);
    if (
      order === undefined ||
      order <= previousOrder ||
      seen.has(cause.code) ||
      cause.message.trim() === ""
    ) {
      return true;
    }
    previousOrder = order;
    seen.add(cause.code);
  }
  if (metric.status === "AVAILABLE") return metric.missingCauses.length !== 0;
  if (metric.status === "NOT_APPLICABLE") {
    return metric.missingCauses.length !== 1 || !seen.has("ZERO_DENOMINATOR");
  }
  if (metric.status === "NOT_RECORDED") {
    const expected =
      metric.code === "MATING_DATE"
        ? "MATING_NOT_RECORDED"
        : metric.code === "CARCASS_YIELD_RATE"
          ? "CARCASS_YIELD_NOT_RECORDED"
          : null;
    return (
      expected === null ||
      metric.missingCauses.length !== 1 ||
      !seen.has(expected)
    );
  }
  return (
    metric.missingCauses.length === 0 ||
    metric.missingCauses.some(
      (cause) =>
        (missingCauseOrder.get(cause.code) ?? Number.MAX_SAFE_INTEGER) > 9,
    )
  );
}

export function metricStatusLabel(status: BatchMetricStatus) {
  switch (status) {
    case "AVAILABLE":
      return "数据可用";
    case "NOT_APPLICABLE":
      return "暂无可计算数据";
    case "NOT_RECORDED":
      return "未录入";
    case "DATA_MISSING":
      return "历史数据缺失";
    default:
      return "未知状态";
  }
}

export function metricDisplayValue(metric: BatchStatisticMetric) {
  return metric.status === "AVAILABLE"
    ? (metric.displayValue ?? "-")
    : metricStatusLabel(metric.status);
}

const operandUnitLabels: Record<string, string> = {
  COUNT: "个",
  RABBIT: "只",
  LITTER: "窝",
  KG: "kg",
  CURRENCY: "元",
  CURRENCY_PER_KG: "元/kg",
};

export function formatMetricOperand(operand: BatchMetricOperand | null) {
  if (!operand) return "-";
  const value = operand.value ?? "-";
  const unit = operandUnitLabels[operand.unit] ?? operand.unit;
  return `${operand.label}：${value}${unit ? ` ${unit}` : ""}`;
}

export function formatStatisticsTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(date);
}

export function metricMap(statistics: BatchStatistics) {
  return new Map(statistics.metrics.map((metric) => [metric.code, metric]));
}
