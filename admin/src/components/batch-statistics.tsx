import {
  AlertTriangleIcon,
  Clock3Icon,
  InfoIcon,
  RefreshCwIcon,
} from "lucide-react";
import {
  BATCH_METRIC_LAYOUT,
  batchStatisticsContractError,
  formatMetricOperand,
  formatStatisticsTime,
  metricDisplayValue,
  metricMap,
  metricStatusLabel,
} from "@/lib/batch-statistics";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type {
  BatchMetricStatus,
  BatchStatisticMetric,
  BatchStatistics,
} from "@/types/api";

export type BatchStatisticsStatus = "idle" | "loading" | "ready" | "error";

export function BatchStatisticsSummary({
  statistics,
  status,
  onRetry,
}: {
  statistics: BatchStatistics | null;
  status: BatchStatisticsStatus;
  onRetry?: () => void;
}) {
  if ((status === "idle" || status === "loading") && !statistics) {
    return (
      <div
        className="grid grid-cols-2 gap-x-5 gap-y-4 sm:grid-cols-4"
        aria-live="polite"
        aria-label="正在读取批次统计"
      >
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="flex flex-col gap-2">
            <Skeleton className="h-3 w-16" />
            <Skeleton className="h-7 w-12" />
          </div>
        ))}
      </div>
    );
  }

  if (status === "error" && !statistics) {
    return <StatisticsError onRetry={onRetry} />;
  }

  if (!statistics) {
    return (
      <p className="text-sm text-muted-foreground">当前批次暂无产崽记录。</p>
    );
  }

  const metrics = [
    ["产崽窝数", statistics.totalLitters],
    ["产崽总数", statistics.totalKits],
    ["活崽总数", statistics.totalLiveKits],
    ["断奶数量", statistics.totalWeaned],
  ] as const;

  return (
    <div className="flex flex-col gap-4">
      {status === "error" ? <StaleStatisticsNotice onRetry={onRetry} /> : null}
      <dl
        className="grid grid-cols-2 gap-x-5 gap-y-4 sm:grid-cols-4"
        data-testid="batch-statistics"
      >
        {metrics.map(([label, value]) => (
          <div key={label} className="min-w-0">
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="mt-1 text-xl font-semibold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

export function BatchStatisticsPanel({
  statistics,
  status,
  onRetry,
}: {
  statistics: BatchStatistics | null;
  status: BatchStatisticsStatus;
  onRetry: () => void;
}) {
  if ((status === "idle" || status === "loading") && !statistics) {
    return <BatchStatisticsSkeleton />;
  }
  if (status === "error" && !statistics) {
    return <StatisticsError onRetry={onRetry} />;
  }
  if (!statistics) return null;

  const contractError = batchStatisticsContractError(statistics);
  if (contractError) {
    return (
      <div className="flex min-h-40 flex-col items-start justify-center gap-3 rounded-lg border border-destructive/30 p-5">
        <p className="text-sm text-destructive" role="alert">
          {contractError}
        </p>
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RefreshCwIcon data-icon="inline-start" />
          重新读取
        </Button>
      </div>
    );
  }

  const byCode = metricMap(statistics);

  return (
    <div className="flex flex-col gap-6" data-testid="batch-statistics-panel">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-3">
        <div className="flex min-w-0 items-center gap-2 text-sm text-muted-foreground">
          <Clock3Icon className="size-4 shrink-0" aria-hidden="true" />
          <span>取数时间：{formatStatisticsTime(statistics.calculatedAt)}</span>
        </div>
        {status === "loading" ? (
          <span className="text-xs text-muted-foreground" aria-live="polite">
            正在更新
          </span>
        ) : null}
      </div>

      {status === "error" ? <StaleStatisticsNotice onRetry={onRetry} /> : null}

      {BATCH_METRIC_LAYOUT.map((group) => (
        <section
          key={group.stage}
          className="motion-section flex flex-col gap-3"
        >
          <div className="flex items-center gap-3">
            <h2 className="text-base font-semibold">{group.name}</h2>
            <span
              className="h-px min-w-8 flex-1 bg-border"
              aria-hidden="true"
            />
          </div>
          <div className="flex flex-col gap-3">
            {group.rows.map((row, rowIndex) => (
              <div
                key={`${group.stage}-${rowIndex}`}
                className={`grid gap-3 ${
                  row.length === 3
                    ? "[grid-template-columns:repeat(auto-fit,minmax(min(100%,20rem),1fr))]"
                    : "[grid-template-columns:repeat(auto-fit,minmax(min(100%,30rem),1fr))]"
                }`}
                data-metric-row={row.join(" ")}
              >
                {row.map((code) => {
                  const metric = byCode.get(code);
                  return metric ? (
                    <div
                      key={code}
                      className="overflow-hidden rounded-lg border"
                      data-metric-item={code}
                    >
                      <MetricItem metric={metric} />
                    </div>
                  ) : null;
                })}
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

function MetricItem({ metric }: { metric: BatchStatisticMetric }) {
  return (
    <div className="min-w-0 p-4">
      <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs text-muted-foreground">{metric.name}</p>
          <p
            className="mt-1 break-words text-xl font-semibold tabular-nums"
            data-metric-code={metric.code}
          >
            {metricDisplayValue(metric)}
          </p>
        </div>
        <MetricStatusBadge status={metric.status} />
      </div>
      <details className="mt-3 text-sm">
        <summary className="flex min-h-8 cursor-pointer list-none items-center gap-2 text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <InfoIcon className="size-4" aria-hidden="true" />
          查看口径
        </summary>
        <div className="mt-2 flex flex-col gap-2 border-t pt-3 text-sm text-muted-foreground">
          <p className="break-words">公式：{metric.formula || "-"}</p>
          {metric.numerator ? (
            <p>{formatMetricOperand(metric.numerator)}</p>
          ) : null}
          {metric.denominator ? (
            <p>{formatMetricOperand(metric.denominator)}</p>
          ) : null}
          {metric.components.map((component) => (
            <p key={component.code}>{formatMetricOperand(component)}</p>
          ))}
          {metric.dateValue?.dailyCycleCounts?.length ? (
            <ul className="space-y-1">
              {metric.dateValue.dailyCycleCounts.map((item) => (
                <li key={item.date}>
                  {item.date}：{item.cycleCount} 个周期
                </li>
              ))}
            </ul>
          ) : null}
          {metric.missingCauses.length > 0 ? (
            <ul
              className={
                metric.status === "DATA_MISSING"
                  ? "space-y-1 text-destructive"
                  : "space-y-1 text-muted-foreground"
              }
            >
              {metric.missingCauses.map((cause) => (
                <li key={cause.code} className="break-words">
                  {cause.message}（{cause.code}）
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </details>
    </div>
  );
}

function MetricStatusBadge({ status }: { status: BatchMetricStatus }) {
  const variant = status === "DATA_MISSING" ? "destructive" : "outline";
  const className =
    status === "NOT_RECORDED" || status === "NOT_APPLICABLE"
      ? "border-warning/40 text-warning"
      : undefined;
  return (
    <Badge variant={variant} className={className}>
      {metricStatusLabel(status)}
    </Badge>
  );
}

function BatchStatisticsSkeleton() {
  return (
    <div
      className="flex flex-col gap-6"
      aria-live="polite"
      aria-label="正在读取完整批次统计"
    >
      {Array.from({ length: 8 }, (_, groupIndex) => (
        <div key={groupIndex} className="flex flex-col gap-3">
          <Skeleton className="h-5 w-20" />
          <div className="grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(min(100%,30rem),1fr))]">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
        </div>
      ))}
    </div>
  );
}

function StatisticsError({ onRetry }: { onRetry?: () => void }) {
  return (
    <div className="flex min-h-36 flex-col items-start justify-center gap-3 rounded-lg border border-destructive/30 p-5">
      <p className="text-sm text-destructive" role="alert">
        批次统计读取失败，请重试。
      </p>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RefreshCwIcon data-icon="inline-start" />
          重试
        </Button>
      ) : null}
    </div>
  );
}

function StaleStatisticsNotice({ onRetry }: { onRetry?: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-warning/40 p-3">
      <p
        className="flex min-w-0 items-center gap-2 text-sm text-warning"
        role="alert"
      >
        <AlertTriangleIcon className="size-4 shrink-0" aria-hidden="true" />
        更新失败，当前保留上次成功统计。
      </p>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RefreshCwIcon data-icon="inline-start" />
          重试更新
        </Button>
      ) : null}
    </div>
  );
}
