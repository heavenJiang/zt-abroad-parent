package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * 触达与执行保障纯计算测试：免打扰（跨午夜、同日、不设）、免打扰结束时刻、抽检、熔断阈值、预算
 *
 * @author ygb
 */
class TouchPolicyTest
{
    private static final LocalTime T22 = LocalTime.of(22, 0);

    private static final LocalTime T08 = LocalTime.of(8, 0);

    private static ZonedDateTime at(String s)
    {
        return LocalDateTime.parse(s).atZone(MarketingCalendar.ZONE);
    }

    @Test
    void parseTime()
    {
        assertEquals(T22, TouchPolicy.parseTime("22:00"));
        assertNull(TouchPolicy.parseTime("24:00"));
        assertNull(TouchPolicy.parseTime("8:00"));
        assertNull(TouchPolicy.parseTime(null));
    }

    @Test
    void quietHoursAcrossMidnight()
    {
        assertTrue(TouchPolicy.inQuietHours(LocalTime.of(22, 0), T22, T08));
        assertTrue(TouchPolicy.inQuietHours(LocalTime.of(23, 59), T22, T08));
        assertTrue(TouchPolicy.inQuietHours(LocalTime.of(7, 59), T22, T08));
        assertFalse(TouchPolicy.inQuietHours(LocalTime.of(8, 0), T22, T08));
        assertFalse(TouchPolicy.inQuietHours(LocalTime.of(21, 59), T22, T08));
    }

    @Test
    void quietHoursSameDayAndDisabled()
    {
        LocalTime s = LocalTime.of(12, 0);
        LocalTime e = LocalTime.of(14, 0);
        assertTrue(TouchPolicy.inQuietHours(LocalTime.of(13, 0), s, e));
        assertFalse(TouchPolicy.inQuietHours(LocalTime.of(14, 0), s, e));
        assertFalse(TouchPolicy.inQuietHours(LocalTime.of(11, 0), s, e));
        assertFalse(TouchPolicy.inQuietHours(LocalTime.of(13, 0), s, s));
        assertFalse(TouchPolicy.inQuietHours(LocalTime.of(13, 0), null, e));
    }

    @Test
    void quietEnd()
    {
        assertEquals(at("2026-10-20T08:00"), TouchPolicy.quietEnd(at("2026-10-19T23:30"), T22, T08));
        assertEquals(at("2026-10-20T08:00"), TouchPolicy.quietEnd(at("2026-10-20T06:10"), T22, T08));
        assertNull(TouchPolicy.quietEnd(at("2026-10-20T09:00"), T22, T08));
    }

    @Test
    void sampled()
    {
        assertFalse(TouchPolicy.sampled("a", null));
        assertFalse(TouchPolicy.sampled("a", BigDecimal.ZERO));
        assertTrue(TouchPolicy.sampled("a", BigDecimal.ONE));
        assertEquals(TouchPolicy.sampled("task:42", new BigDecimal("0.3")), TouchPolicy.sampled("task:42", new BigDecimal("0.3")));
        int hits = 0;
        for (int i = 0; i < 2000; i++)
        {
            hits += TouchPolicy.sampled("k" + i, new BigDecimal("0.2")) ? 1 : 0;
        }
        assertTrue(hits > 300 && hits < 500, "抽中 " + hits);
    }

    @Test
    void breach()
    {
        assertEquals(new BigDecimal("2"), TouchPolicy.breach(false, 2, 0, new BigDecimal("2"), 0));
        assertNull(TouchPolicy.breach(false, 1, 0, new BigDecimal("2"), 0));
        assertEquals(new BigDecimal("0.0500"), TouchPolicy.breach(true, 2, 40, new BigDecimal("0.05"), 40));
        assertNull(TouchPolicy.breach(true, 2, 39, new BigDecimal("0.05"), 40));
        assertNull(TouchPolicy.breach(true, 1, 40, new BigDecimal("0.05"), 40));
        assertNull(TouchPolicy.breach(true, 1, 0, new BigDecimal("0.05"), 0));
        assertNull(TouchPolicy.breach(false, 5, 0, null, 0));
    }

    @Test
    void overBudget()
    {
        assertTrue(TouchPolicy.overBudget(new BigDecimal("50"), new BigDecimal("50")));
        assertFalse(TouchPolicy.overBudget(new BigDecimal("49.99"), new BigDecimal("50")));
        assertFalse(TouchPolicy.overBudget(new BigDecimal("1000"), null));
    }
}
