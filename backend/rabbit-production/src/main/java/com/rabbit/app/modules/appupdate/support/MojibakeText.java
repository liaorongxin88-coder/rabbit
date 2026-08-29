package com.rabbit.app.modules.appupdate.support;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 识别「UTF-8 字节被当成 latin1 读了一遍」留下的乱码。
 *
 * <p>1.0.8 的更新说明就是这么进库的：客户端在 App 里看到的是
 * {@code 1.0.8ï¼šæ–°å¢žå›¾ç‰‡...}，原文是「1.0.8：新增图片验证码…」。
 * 表是 utf8mb4、JDBC 也带了 characterEncoding=utf8，坏的是写进来之前那一段，
 * 所以只能在入口拦。
 *
 * <p>发布是手工调接口的，没有界面兜着。一旦发错，全场区的设备都会看到乱码，
 * 而且只能改库补救。宁可让发布这一步失败。
 */
public final class MojibakeText {
    /**
     * MySQL 的 latin1 字节到字符的映射。
     *
     * <p>不能直接用 Java 的 {@code windows-1252}：0x81/0x8D/0x8F/0x90/0x9D 在
     * CP1252 里没有定义，Java 解码成 U+FFFD，MySQL 则按恒等映射保留原字节值。
     * 线上那条数据里就带着 0x81，用 Java 的实现会还原失败。
     */
    private static final char[] LATIN1_TO_CHAR = new char[256];

    static {
        for (int i = 0; i < 256; i++) {
            LATIN1_TO_CHAR[i] = (char) i;
        }
        int[][] cp1252Overrides = {
                {0x80, 0x20AC}, {0x82, 0x201A}, {0x83, 0x0192}, {0x84, 0x201E},
                {0x85, 0x2026}, {0x86, 0x2020}, {0x87, 0x2021}, {0x88, 0x02C6},
                {0x89, 0x2030}, {0x8A, 0x0160}, {0x8B, 0x2039}, {0x8C, 0x0152},
                {0x8E, 0x017D}, {0x91, 0x2018}, {0x92, 0x2019}, {0x93, 0x201C},
                {0x94, 0x201D}, {0x95, 0x2022}, {0x96, 0x2013}, {0x97, 0x2014},
                {0x98, 0x02DC}, {0x99, 0x2122}, {0x9A, 0x0161}, {0x9B, 0x203A},
                {0x9C, 0x0153}, {0x9E, 0x017E}, {0x9F, 0x0178},
        };
        for (int[] override : cp1252Overrides) {
            LATIN1_TO_CHAR[override[0]] = (char) override[1];
        }
    }

    private MojibakeText() {
    }

    /**
     * 能无损还原成含中日韩文字的文本，就判定为乱码。
     */
    public static boolean looksLikeMojibake(String text) {
        return repair(text) != null;
    }

    /**
     * 还原乱码原文；不是乱码则返回 {@code null}。
     *
     * <p>三个条件缺一不可，避免把正常的西欧文字误伤：每个字符都能映射回单字节、
     * 这些字节是合法 UTF-8、且还原结果确实含中日韩字符。
     */
    public static String repair(String text) {
        if (text == null || text.isEmpty() || containsCjk(text)) {
            // 已经有中文，说明这段没被 latin1 过一遍。
            return null;
        }
        byte[] bytes = toLatin1Bytes(text);
        if (bytes == null) {
            return null;
        }
        String decoded = decodeUtf8Strictly(bytes);
        if (decoded == null || decoded.equals(text) || !containsCjk(decoded)) {
            return null;
        }
        return decoded;
    }

    /** 把字符按 MySQL latin1 映射回单字节；有一个字符映射不回去就返回 null。 */
    private static byte[] toLatin1Bytes(String text) {
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int index = indexOf(ch);
            if (index < 0) {
                return null;
            }
            bytes[i] = (byte) index;
        }
        return bytes;
    }

    private static int indexOf(char ch) {
        for (int i = 0; i < LATIN1_TO_CHAR.length; i++) {
            if (LATIN1_TO_CHAR[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    private static String decodeUtf8Strictly(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            return null;
        }
    }

    private static boolean containsCjk(String text) {
        return text.codePoints().anyMatch(codePoint -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
        });
    }
}
