package com.ygb.abroad.growth;

import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;
import com.ygb.common.utils.StringUtils;

/**
 * 定时触发规则的时间计算（纯 Java）：6 位表达式（秒 分 时 日 月 周），按北京时间
 *
 * 编排器每分钟一轮，所以秒位只能是一个固定数字（如 0），不能每秒触发；停机期间错过的时刻不补触发，
 * 恢复后只补最近一次（在补触发窗口内）。
 *
 * @author ygb
 */
public final class CronSchedule
{
    /** 单次计算最多向后推算的次数（防止异常表达式死循环） */
    private static final int MAX_STEPS = 10000;

    private CronSchedule()
    {
    }

    /**
     * 校验表达式
     *
     * @param expression 表达式
     * @return 错误说明，合法返回 null
     */
    public static String validate(String expression)
    {
        if (StringUtils.isBlank(expression))
        {
            return "定时规则必须填写定时表达式";
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 6)
        {
            return "定时表达式须为 6 位：秒 分 时 日 月 周（如 0 0 7 * * * 表示每天 07:00）";
        }
        if (!fields[0].matches("\\d{1,2}") || Integer.parseInt(fields[0]) > 59)
        {
            return "定时表达式的秒位只能是 0–59 中的一个数字（编排器每分钟执行一轮）";
        }
        try
        {
            CronExpression.parse(expression.trim());
        }
        catch (IllegalArgumentException e)
        {
            return "定时表达式不合法：" + e.getMessage();
        }
        return null;
    }

    /**
     * (since, now] 区间内最近的一次计划时刻
     *
     * @param expression 表达式（须已通过校验）
     * @param since 起点（不含）
     * @param now 当前时刻（含）
     * @return 计划时刻，区间内没有返回 null
     */
    public static ZonedDateTime latestDue(String expression, ZonedDateTime since, ZonedDateTime now)
    {
        CronExpression cron = CronExpression.parse(expression.trim());
        ZonedDateTime latest = null;
        ZonedDateTime next = cron.next(since);
        for (int i = 0; next != null && !next.isAfter(now) && i < MAX_STEPS; i++)
        {
            latest = next;
            next = cron.next(next);
        }
        return latest;
    }

    /**
     * 某时刻之后的下一次计划时刻（管理端展示）
     *
     * @param expression 表达式（须已通过校验）
     * @param after 起点（不含）
     * @return 计划时刻，没有返回 null
     */
    public static ZonedDateTime next(String expression, ZonedDateTime after)
    {
        return CronExpression.parse(expression.trim()).next(after);
    }
}
