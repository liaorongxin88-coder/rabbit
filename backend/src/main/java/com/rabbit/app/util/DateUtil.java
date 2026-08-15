package com.rabbit.app.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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

    public static int daysBetween(Date from, Date to) {
        if (from == null || to == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(from.getTime()).atZone(ZONE_ID).toLocalDate(),
            Instant.ofEpochMilli(to.getTime()).atZone(ZONE_ID).toLocalDate()
        );
    }
}
