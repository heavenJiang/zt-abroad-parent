package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * 定时表达式测试：格式校验（6 位、秒位固定）、区间内最近一次计划时刻、北京时间、下一次
 *
 * @author ygb
 */
class CronScheduleTest
{
    private static ZonedDateTime t(String s)
    {
        return LocalDateTime.parse(s).atZone(MarketingCalendar.ZONE);
    }

    @Test
    void validate()
    {
        assertNull(CronSchedule.validate("0 0 7 * * *"));
        assertNull(CronSchedule.validate(" 0 0 8 * * MON "));
        assertNull(CronSchedule.validate("0 */15 9-18 * * ?"));
        assertNotNull(CronSchedule.validate(null));
        assertNotNull(CronSchedule.validate("0 7 * * *"));
        assertNotNull(CronSchedule.validate("* 0 7 * * *"));
        assertNotNull(CronSchedule.validate("*/5 * * * * *"));
        assertNotNull(CronSchedule.validate("60 0 7 * * *"));
        assertNotNull(CronSchedule.validate("0 0 25 * * *"));
    }

    @Test
    void latestDueInRange()
    {
        String daily = "0 0 7 * * *";
        assertEquals(t("2026-10-18T07:00"), CronSchedule.latestDue(daily, t("2026-10-18T06:55"), t("2026-10-18T07:00:30")));
        assertEquals(t("2026-10-18T07:00"), CronSchedule.latestDue(daily, t("2026-10-18T06:59:59"), t("2026-10-18T07:00")));
        assertNull(CronSchedule.latestDue(daily, t("2026-10-18T07:00"), t("2026-10-18T07:05")));
        assertNull(CronSchedule.latestDue(daily, t("2026-10-18T06:50"), t("2026-10-18T06:59:59")));
        // 每 5 分钟，停机 20 分钟后只补最近一次
        assertEquals(t("2026-10-18T10:20"), CronSchedule.latestDue("0 */5 * * * *", t("2026-10-18T10:00"), t("2026-10-18T10:22")));
    }

    @Test
    void next()
    {
        assertEquals(t("2026-10-19T08:00"), CronSchedule.next("0 0 8 * * MON", t("2026-10-18T09:00")));
    }
}
