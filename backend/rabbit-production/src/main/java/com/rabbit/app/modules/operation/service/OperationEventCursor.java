package com.rabbit.app.modules.operation.service;

import com.rabbit.app.common.BizException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 事件流翻页游标。
 *
 * <p>编码 (occurredAt 毫秒, id) 这对坐标，正好对上 keyset 的排序键。
 * 之所以 base64 而不是直接把两个数字甩给客户端：游标是服务端的实现细节，
 * 一旦客户端开始解析或自己拼装，排序键就再也改不动了。
 *
 * <p>解码失败一律 400。伪造或过期的游标是客户端错误，不该变成 500 —— 那会
 * 让一次手抖的翻页看起来像服务故障。
 */
public final class OperationEventCursor {

    private static final String SEPARATOR = ":";

    private final long occurredAtMillis;
    private final long id;

    private OperationEventCursor(long occurredAtMillis, long id) {
        this.occurredAtMillis = occurredAtMillis;
        this.id = id;
    }

    public static OperationEventCursor of(long occurredAtMillis, long id) {
        return new OperationEventCursor(occurredAtMillis, id);
    }

    public String encode() {
        String raw = occurredAtMillis + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 解不开就是 400；调用方不需要再包一层 try。 */
    public static OperationEventCursor decode(String cursor) {
        try {
            String raw = new String(
                Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8
            );
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0 || separator == raw.length() - 1) {
                throw new IllegalArgumentException("cursor 缺少分隔符");
            }
            return new OperationEventCursor(
                Long.parseLong(raw.substring(0, separator)),
                Long.parseLong(raw.substring(separator + 1))
            );
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "翻页游标无效，请重新查询");
        }
    }

    public long getOccurredAtMillis() {
        return occurredAtMillis;
    }

    public long getId() {
        return id;
    }
}
