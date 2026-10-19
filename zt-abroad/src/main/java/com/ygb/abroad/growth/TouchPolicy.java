package com.ygb.abroad.growth;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * 触达与执行保障的纯计算（不访问数据库）：免打扰时段、抽检命中、熔断阈值
 *
 * 时刻一律按北京时间。免打扰结束早于开始表示跨午夜（如 22:00–08:00）；开始等于结束表示不设免打扰。
 *
 * @author ygb
 */
public final class TouchPolicy
{
    private TouchPolicy()
    {
    }

    /**
     * 解析 HH:mm
     *
     * @param text 时刻文本
     * @return 时刻，格式不对返回 null
     */
    public static LocalTime parseTime(String text)
    {
        if (text == null || !text.matches("^([01]\\d|2[0-3]):[0-5]\\d$"))
        {
            return null;
        }
        try
        {
            return LocalTime.parse(text);
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    /**
     * 某时刻是否在免打扰时段内（含开始，不含结束）
     *
     * @param t 时刻
     * @param start 开始
     * @param end 结束
     * @return 是否免打扰
     */
    public static boolean inQuietHours(LocalTime t, LocalTime start, LocalTime end)
    {
        if (t == null || start == null || end == null || start.equals(end))
        {
            return false;
        }
        if (start.isBefore(end))
        {
            return !t.isBefore(start) && t.isBefore(end);
        }
        return !t.isBefore(start) || t.isBefore(end);
    }

    /**
     * 免打扰时段内的某时刻，到免打扰结束的时刻
     *
     * @param now 当前时刻（北京时间）
     * @param start 开始
     * @param end 结束
     * @return 结束时刻；不在免打扰内返回 null
     */
    public static ZonedDateTime quietEnd(ZonedDateTime now, LocalTime start, LocalTime end)
    {
        if (!inQuietHours(now.toLocalTime(), start, end))
        {
            return null;
        }
        ZonedDateTime candidate = now.toLocalDate().atTime(end).atZone(now.getZone());
        return candidate.isAfter(now) ? candidate : candidate.plusDays(1);
    }

    /**
     * 是否抽中检查（按样本键的哈希确定，同一键结果固定，便于复核）
     *
     * @param sampleKey 样本键（如任务ID、内容编号）
     * @param rate 抽检比例 0–1
     * @return 是否抽中
     */
    public static boolean sampled(String sampleKey, BigDecimal rate)
    {
        if (rate == null || rate.signum() <= 0)
        {
            return false;
        }
        if (rate.compareTo(BigDecimal.ONE) >= 0)
        {
            return true;
        }
        try
        {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(String.valueOf(sampleKey).getBytes(StandardCharsets.UTF_8));
            int bucket = (((h[0] & 0xff) << 8) | (h[1] & 0xff)) % 10000;
            return bucket < rate.multiply(BigDecimal.valueOf(10000)).intValue();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 熔断指标是否达到阈值
     *
     * @param ratio 是否比率类指标
     * @param count 计数（比率类为分子）
     * @param sample 分母（计数类忽略）
     * @param threshold 阈值
     * @param minSample 最少样本数（比率类分母不足时不触发）
     * @return 达到阈值时返回指标值，否则返回 null
     */
    public static BigDecimal breach(boolean ratio, long count, long sample, BigDecimal threshold, int minSample)
    {
        if (threshold == null || threshold.signum() <= 0)
        {
            return null;
        }
        BigDecimal value;
        if (ratio)
        {
            if (sample <= 0 || sample < minSample)
            {
                return null;
            }
            value = BigDecimal.valueOf(count).divide(BigDecimal.valueOf(sample), 4, java.math.RoundingMode.HALF_UP);
        }
        else
        {
            value = BigDecimal.valueOf(count);
        }
        return value.compareTo(threshold) >= 0 ? value : null;
    }

    /**
     * 已用量是否达到预算
     *
     * @param used 已用
     * @param budget 预算（为空不限）
     * @return 是否达到
     */
    public static boolean overBudget(BigDecimal used, BigDecimal budget)
    {
        return budget != null && used != null && used.compareTo(budget) >= 0;
    }
}
