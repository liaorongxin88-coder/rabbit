package com.rabbit.app.modules.auth.support;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 兔号：用户自己看得见、可以报给别人用来加兔场的唯一标识。
 *
 * <p>形如 {@code R3F9A0C21B7}——R 前缀加 10 位十六进制。选十六进制是因为它的字母表
 * 里没有 O、I、L，于是「零还是欧」「一还是艾」这类口头传达的老问题可以在
 * {@link #normalize(String)} 里一次性归一化掉，用户念错也能对上。
 *
 * <p>16^10 约一万亿，随机取值既不可枚举，也不会暴露平台上有多少账号。
 */
public final class UserCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern PATTERN = Pattern.compile("^R[0-9A-F]{10}$");
    private static final int RANDOM_BYTES = 5;

    private UserCodes() {
    }

    /** 生成一个新兔号。唯一性由数据库唯一键兜底，调用方生成前应先查重。 */
    public static String random() {
        byte[] buffer = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(buffer);
        StringBuilder builder = new StringBuilder(1 + RANDOM_BYTES * 2);
        builder.append('R');
        for (byte value : buffer) {
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }

    /**
     * 把用户手抄、口述、复制过来的字符串收拾干净：去掉空格和连字符，转大写，
     * 再把十六进制里根本不存在的 O/I/L 当成 0/1/1。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_]", "");
        return cleaned
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1');
    }

    /** 归一化之后是否是一个兔号。手机号是纯数字，不会命中。 */
    public static boolean looksLikeUserCode(String normalized) {
        return normalized != null && PATTERN.matcher(normalized).matches();
    }
}
