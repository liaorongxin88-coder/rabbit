package com.rabbit.app.modules.operation.dto;

import java.util.List;

/**
 * 事件流一页。
 *
 * <p>刻意不带 total：这是只增不减的追加流，为每次翻页做一次全表 count 正是
 * keyset 分页要避开的成本；客户端据 hasMore 决定还要不要继续拉。
 */
public record OperationEventPage(
    List<OperationEventView> items,
    String nextCursor,
    boolean hasMore
) {
}
