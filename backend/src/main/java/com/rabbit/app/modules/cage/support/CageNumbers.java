package com.rabbit.app.modules.cage.support;

/**
 * 笼位编号的唯一出处：<b>排-位-层</b>，例如 {@code 2-3-1} 是 2 排第 3 位最下层。
 *
 * <p>顺序按人在舍里走的路线定：先找到那一排，再沿着排数到第几位，最后抬头看第几层。
 * 层号从下往上递增，1 是最底层。
 *
 * <p>以前建兔舍、平台后台建场、App 批量建笼各拼各的串，同一个兔舍里能同时出现
 * {@code 1-3-2} 和 {@code 2(下)1} 两种写法，工人拿着签对不上实物。所有生成编号的地方
 * 都必须走这里，不要再在别处拼字符串。
 */
public final class CageNumbers {
    private CageNumbers() {
    }

    /** 建兔舍这类已经拿着纯数字坐标的场景。 */
    public static String canonical(int row, int position, int layer) {
        return row + "-" + position + "-" + layer;
    }

    /**
     * 从排号加坐标推编号；推不出来时返回 null，由调用方决定是报错还是用客户端传来的编号。
     *
     * <p>排号存的是 {@code R2} 这种带前缀的形式，编号里只留数字部分。排号如果不是
     * {@code R+数字}（历史数据里有 {@code LEGACY}、也可能有人手填 {@code A}），
     * 就原样保留，至少还能看出是哪一排。
     */
    public static String canonical(String rowCode, Integer positionIndex, Integer layerIndex) {
        if (positionIndex == null || positionIndex <= 0 || layerIndex == null || layerIndex <= 0) {
            return null;
        }
        String row = rowLabel(rowCode);
        if (row == null) {
            return null;
        }
        return row + "-" + positionIndex + "-" + layerIndex;
    }

    private static String rowLabel(String rowCode) {
        if (rowCode == null) {
            return null;
        }
        String trimmed = rowCode.trim();
        if (trimmed.isEmpty() || "LEGACY".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if ((trimmed.charAt(0) == 'R' || trimmed.charAt(0) == 'r') && trimmed.length() > 1) {
            String rest = trimmed.substring(1);
            if (isDigits(rest)) {
                return stripLeadingZeros(rest);
            }
        }
        return trimmed;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String stripLeadingZeros(String digits) {
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') {
            i++;
        }
        return digits.substring(i);
    }
}
