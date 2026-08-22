package com.rabbit.app.modules.repro.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 批量操作的逐项幂等键派生（设计 §5.1 / 计划 A11）。
 *
 * <p>批量提交只带一个 requestId，但幂等是按「单只母兔单操作」判定的，因此必须
 * 逐项派生出稳定且唯一的键：{@code requestId + '-' + taskId}。稳定是关键——
 * 客户端断网重试整批时，成功过的那些项要能命中回放而不是二次推进。
 *
 * <p>超长时回退为 UUIDv3 摘要，沿用 {@code BatchService.deriveBoundedRequestId}
 * 的既有规则，保证与旧路径产生的键在同一值域内（repro_events.request_id 为
 * VARCHAR(64)，超长会被静默截断成互相冲突的键）。
 */
public final class ReproRequestIds {
    private static final int MAX_LENGTH = 64;

    private ReproRequestIds() {
    }

    public static String derive(String requestId, String suffix) {
        String candidate = requestId + "-" + suffix;
        if (candidate.length() <= MAX_LENGTH) {
            return candidate;
        }
        return UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
