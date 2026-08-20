package com.rabbit.app.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

class DateUtilTest {

    @Test
    void reminderDatesAllowTodayAndRejectPastBusinessDates() {
        Date now = DateUtil.now();

        assertTrue(DateUtil.isTodayOrFuture(now));
        assertTrue(DateUtil.isTodayOrFuture(DateUtil.plusDays(now, 1)));
        assertFalse(DateUtil.isTodayOrFuture(DateUtil.plusDays(now, -1)));
    }
}
