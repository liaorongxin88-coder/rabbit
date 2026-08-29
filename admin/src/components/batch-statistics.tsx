import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { BatchStatistics } from "@/types/api";

type BatchStatisticsStatus = "idle" | "loading" | "ready" | "error";

export function BatchStatisticsSummary({
  statistics,
  status,
  onRetry,
}: {
  statistics: BatchStatistics | null;
  status: BatchStatisticsStatus;
  onRetry?: () => void;
}) {
  if (status === "idle" || status === "loading") {
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

  if (status === "error") {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-sm text-muted-foreground" role="alert">
          批次统计读取失败，请重试。
        </p>
        {onRetry ? (
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            重试
          </Button>
        ) : null}
      </div>
    );
  }

  if (!statistics) {
    return (
      <p className="text-sm text-muted-foreground">
        当前批次暂无产崽记录。
      </p>
    );
  }

  const metrics = [
    ["产崽窝数", statistics.totalLitters],
    ["产崽总数", statistics.totalKits],
    ["活崽总数", statistics.totalLiveKits],
    ["断奶数量", statistics.totalWeaned],
  ] as const;

  return (
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
  );
}
