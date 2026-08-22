package com.rabbit.app.modules.repro.dto;

import java.util.List;

/**
 * 批量操作的逐项结果。
 *
 * <p>刻意做成部分成功而非全有全无：一批一百只母兔里有一只状态已被他人推进，
 * 不应该让另外九十九只白做。失败项带回可读原因，客户端据此只重试失败的部分。
 *
 * @param total     目标任务总数
 * @param succeeded 成功推进数（含幂等回放）
 * @param failed    失败数
 * @param items     逐项明细，顺序与实际执行顺序一致
 */
public record BulkActionResult(int total, int succeeded, int failed, List<Item> items) {

    /**
     * @param ok      是否成功
     * @param code    失败时的业务码（409 状态冲突 / 400 参数不合法等）
     * @param message 失败原因，可直接展示
     * @param replayed 命中幂等回放：本次没有产生新的状态变更
     */
    public record Item(
        Long taskId,
        Long cycleId,
        Long rabbitId,
        boolean ok,
        Integer code,
        String message,
        boolean replayed
    ) {
    }
}
