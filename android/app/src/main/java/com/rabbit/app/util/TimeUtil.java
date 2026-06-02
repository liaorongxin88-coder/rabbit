package com.rabbit.app.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {
    private static final ThreadLocal<SimpleDateFormat> FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        }
    };

    private static final ThreadLocal<SimpleDateFormat> FMT_DATE = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }
    };

    public static String fmt(Long millis) {
        if (millis == null || millis <= 0) {
            return "";
        }
        return FMT.get().format(new Date(millis));
    }

    public static String fmtMs(long millis) {
        return fmt(Long.valueOf(millis));
    }

    public static String today() {
        return FMT_DATE.get().format(new Date());
    }

    public static String fmtAny(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return "";
        }
        try {
            long ms = Long.parseLong(t);
            return fmt(ms);
        } catch (Exception e) {
            return t;
        }
    }
}
