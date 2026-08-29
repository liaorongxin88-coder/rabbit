import { useCallback, useEffect, useState } from "react";
import { HistoryIcon } from "lucide-react";
import { listOperationEvents } from "@/api/workspace";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatRecordDate } from "@/lib/date";
import {
  appendEvents,
  operationLabel,
  operatorLabel,
  stageTransition,
} from "@/lib/operation-events";
import type { OperationEvent } from "@/types/api";

interface OperationEventStreamProps {
  houseId: number;
  targetType: string;
  targetId: number;
  title?: string;
  description?: string;
}

const PAGE_SIZE = 20;

/**
 * 某个目标身上的操作留痕。
 *
 * 这一块自己加载、自己报错、自己重试：它挂了不能连累所在页面的其他内容，
 * 上一轮就是把区块状态并进整页 gate，结果统计接口一慢就把整页拖黑。
 *
 * 分页是游标不是页码，所以只有「加载更多」，也不显示总条数 ——
 * 后端刻意不返回 total。
 */
export function OperationEventStream({
  houseId,
  targetType,
  targetId,
  title = "操作记录",
  description = "这个对象身上发生过的操作，最近的排在前面。",
}: OperationEventStreamProps) {
  const [events, setEvents] = useState<OperationEvent[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [status, setStatus] = useState<"loading" | "ready" | "error">(
    "loading",
  );
  const [loadingMore, setLoadingMore] = useState(false);

  const loadFirstPage = useCallback(async () => {
    setStatus("loading");
    try {
      const page = await listOperationEvents(houseId, {
        targetType,
        targetId,
        limit: PAGE_SIZE,
      });
      setEvents(page.items);
      setCursor(page.nextCursor);
      setHasMore(page.hasMore);
      setStatus("ready");
    } catch {
      setEvents([]);
      setCursor(null);
      setHasMore(false);
      setStatus("error");
    }
  }, [houseId, targetId, targetType]);

  useEffect(() => {
    void loadFirstPage();
  }, [loadFirstPage]);

  const loadMore = useCallback(async () => {
    if (!cursor || loadingMore) {
      return;
    }
    setLoadingMore(true);
    try {
      const page = await listOperationEvents(houseId, {
        targetType,
        targetId,
        cursor,
        limit: PAGE_SIZE,
      });
      // 去重后再追加：并发写入时游标边界可能把同一行带回来。
      setEvents((current) => appendEvents(current, page.items));
      setCursor(page.nextCursor);
      setHasMore(page.hasMore);
    } catch {
      // 加载更多失败不清空已读到的部分，用户手上的记录不该凭空消失。
      setHasMore(true);
    } finally {
      setLoadingMore(false);
    }
  }, [cursor, houseId, loadingMore, targetId, targetType]);

  return (
    <Card data-testid="operation-event-stream">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {status === "loading" ? (
          <div
            className="flex flex-col gap-2"
            data-testid="operation-event-stream-loading"
          >
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : null}

        {status === "error" ? (
          <div
            className="flex flex-col items-start gap-3"
            data-testid="operation-event-stream-error"
          >
            <p className="text-muted-foreground text-sm">
              操作记录读取失败，请重试。
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => void loadFirstPage()}
            >
              重试
            </Button>
          </div>
        ) : null}

        {status === "ready" && events.length === 0 ? (
          <Empty data-testid="operation-event-stream-empty">
            <HistoryIcon aria-hidden="true" />
            <EmptyTitle>暂无操作记录</EmptyTitle>
            <EmptyDescription>
              这个对象上还没有产生过留痕的操作。
            </EmptyDescription>
          </Empty>
        ) : null}

        {status === "ready" && events.length > 0 ? (
          <div className="flex flex-col gap-3">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>时间</TableHead>
                  <TableHead>操作</TableHead>
                  <TableHead>阶段</TableHead>
                  <TableHead>操作人</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell>{formatRecordDate(event.occurredAt)}</TableCell>
                    <TableCell>{operationLabel(event)}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {stageTransition(event) || "—"}
                    </TableCell>
                    <TableCell>{operatorLabel(event)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {hasMore ? (
              <Button
                variant="outline"
                size="sm"
                className="self-start"
                disabled={loadingMore}
                onClick={() => void loadMore()}
              >
                {loadingMore ? "加载中…" : "加载更多"}
              </Button>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
