package com.rabbit.app.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InputUtil {
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    public static List<Long> parseIds(String s) {
        List<Long> ids = new ArrayList<Long>();
        if (s == null) {
            return ids;
        }
        String[] parts = s.split(",");
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(t));
            } catch (Exception ignored) {
            }
        }
        return ids;
    }

    public static Date parseDate(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return DF.parse(t);
        } catch (Exception e) {
            return null;
        }
    }
}
