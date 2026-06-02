package com.rabbit.app.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class DateUtil {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    public static Date now() {
        return new Date();
    }

    public static Date plusDays(Date date, int days) {
        if (date == null) {
            return null;
        }
        ZonedDateTime zdt = Instant.ofEpochMilli(date.getTime()).atZone(ZONE_ID).plusDays(days);
        return Date.from(zdt.toInstant());
    }

    public static Date minusDays(Date date, int days) {
        return plusDays(date, -days);
    }
}
