package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 产品与预约规则（纯计算）：价格展示、产品校验、批量生成顾问时段
 *
 * @author ygb
 */
final class AbrBookingRules
{
    /** 产品类别 */
    static final Set<String> CATEGORIES = Set.of("consult", "service", "other");

    /** 预约方式 */
    static final Set<String> MODES = Set.of("online", "offline");

    /** 一次最多生成的时段数、最多跨几天 */
    static final int MAX_SLOTS = 500;

    static final int MAX_DAYS = 62;

    /** 时间段写法 09:00-12:00 */
    static final Pattern RANGE = Pattern.compile("^(\\d{1,2}:\\d{2})\\s*[-–~]\\s*(\\d{1,2}:\\d{2})$");

    /** 币种符号 */
    private static final String CNY = "¥";

    private AbrBookingRules()
    {
    }

    /**
     * 价格展示：未定价为“价格面议”，0 为“免费”，固定价、区间、起价
     *
     * @param min 下限
     * @param max 上限
     * @param currency 币种
     * @param note 价格说明
     * @return 文字
     */
    static String priceText(BigDecimal min, BigDecimal max, String currency, String note)
    {
        String text;
        if (min == null && max == null)
        {
            text = "价格面议";
        }
        else if (min != null && min.signum() == 0 && (max == null || max.signum() == 0))
        {
            text = "免费";
        }
        else
        {
            String sym = StringUtils.isBlank(currency) || "CNY".equalsIgnoreCase(currency) ? CNY : currency.toUpperCase() + " ";
            DecimalFormat f = new DecimalFormat("#,##0.##");
            if (min != null && max != null && min.compareTo(max) == 0)
            {
                text = sym + f.format(min);
            }
            else if (min != null && max != null)
            {
                text = sym + f.format(min) + "–" + f.format(max);
            }
            else if (min != null)
            {
                text = sym + f.format(min) + " 起";
            }
            else
            {
                text = sym + f.format(max) + " 以内";
            }
        }
        return StringUtils.isBlank(note) ? text : text + "（" + note.trim() + "）";
    }

    /**
     * 校验产品：类别、价格不为负且下限不高于上限、可预约的须有 5–240 分钟的时长
     *
     * @param p 产品
     */
    static void validate(AbrProduct p)
    {
        if (!CATEGORIES.contains(p.getCategory()))
        {
            throw new ServiceException("类别只能是 consult / service / other");
        }
        if ((p.getPriceMin() != null && p.getPriceMin().signum() < 0) || (p.getPriceMax() != null && p.getPriceMax().signum() < 0))
        {
            throw new ServiceException("价格不能为负");
        }
        if (p.getPriceMin() != null && p.getPriceMax() != null && p.getPriceMin().compareTo(p.getPriceMax()) > 0)
        {
            throw new ServiceException("价格下限不能高于上限");
        }
        boolean bookable = "Y".equals(p.getBookable());
        if (bookable && (p.getDurationMinutes() == null || p.getDurationMinutes() < 5 || p.getDurationMinutes() > 240))
        {
            throw new ServiceException("可预约的产品须填写 5–240 分钟的时长");
        }
        p.setBookable(bookable ? "Y" : "N");
        p.setStatus("1".equals(p.getStatus()) ? "1" : "0");
        p.setCurrency(StringUtils.defaultIfBlank(p.getCurrency(), "CNY").toUpperCase());
        if (!p.getCurrency().matches("^[A-Z]{3}$"))
        {
            throw new ServiceException("币种须为 3 位字母（如 CNY）");
        }
    }

    /**
     * 批量生成时段：日期区间内、选中的星期几、每个时间段按时长切分；早于 notBefore 的跳过
     *
     * @param from 开始日期
     * @param to 结束日期（含）
     * @param weekdays 星期几（1 周一 … 7 周日；空为每天）
     * @param ranges 时间段（如 09:00-12:00）
     * @param minutes 每个时段的分钟数
     * @param notBefore 不早于（北京时间的现在）
     * @return [开始, 结束]
     */
    static List<LocalDateTime[]> slots(LocalDate from, LocalDate to, Set<Integer> weekdays, List<String> ranges, int minutes, LocalDateTime notBefore)
    {
        if (from == null || to == null || to.isBefore(from))
        {
            throw new ServiceException("请选择正确的日期区间");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to))
        {
            throw new ServiceException("一次最多开放 " + MAX_DAYS + " 天");
        }
        if (minutes < 10 || minutes > 240)
        {
            throw new ServiceException("每个时段须为 10–240 分钟");
        }
        if (ranges == null || ranges.isEmpty())
        {
            throw new ServiceException("请填写时间段（如 09:00-12:00）");
        }
        List<LocalTime[]> windows = new ArrayList<>();
        for (String r : ranges)
        {
            Matcher m = RANGE.matcher(StringUtils.trimToEmpty(r));
            if (!m.matches())
            {
                throw new ServiceException("时间段写法不对：" + r + "（如 09:00-12:00）");
            }
            try
            {
                LocalTime a = LocalTime.parse(pad(m.group(1)));
                LocalTime b = LocalTime.parse(pad(m.group(2)));
                if (!b.isAfter(a))
                {
                    throw new ServiceException("时间段结束须晚于开始：" + r);
                }
                windows.add(new LocalTime[] { a, b });
            }
            catch (DateTimeParseException e)
            {
                throw new ServiceException("时间段写法不对：" + r);
            }
        }
        List<LocalDateTime[]> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1))
        {
            if (weekdays != null && !weekdays.isEmpty() && !weekdays.contains(d.getDayOfWeek().getValue()))
            {
                continue;
            }
            for (LocalTime[] w : windows)
            {
                for (LocalTime t = w[0]; !t.plusMinutes(minutes).isAfter(w[1]) && !t.plusMinutes(minutes).isBefore(t); t = t.plusMinutes(minutes))
                {
                    LocalDateTime start = d.atTime(t);
                    if (start.isBefore(notBefore))
                    {
                        continue;
                    }
                    out.add(new LocalDateTime[] { start, start.plusMinutes(minutes) });
                    if (out.size() > MAX_SLOTS)
                    {
                        throw new ServiceException("一次最多生成 " + MAX_SLOTS + " 个时段，请缩短日期区间");
                    }
                }
            }
        }
        return out;
    }

    private static String pad(String hhmm)
    {
        return hhmm.length() == 4 ? "0" + hhmm : hhmm;
    }
}
