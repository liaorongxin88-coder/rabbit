package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.ReproStage;
import java.util.Date;

/**
 * 一次状态推进的结果。
 *
 * @param cycleId       被推进的周期
 * @param eventId       写入的事件 id
 * @param litterId      本次涉及的窝；无则 null
 * @param nextTaskId    生成的下一条待办；无后续任务（如离场）则 null
 * @param stage         周期推进后的阶段
 * @param lifecycle     OPEN / CLOSED
 * @param nextDueTime   下一条待办的到期时间
 * @param followUpCycleId 关闭并接续新周期时的新周期 id
 * @param replayed      true 表示命中幂等回放，本次未产生新的状态变更
 */
public record ReproResult(
    Long cycleId,
    Long eventId,
    Long litterId,
    Long nextTaskId,
    ReproStage stage,
    String lifecycle,
    Date nextDueTime,
    Long followUpCycleId,
    boolean replayed
) {
}
