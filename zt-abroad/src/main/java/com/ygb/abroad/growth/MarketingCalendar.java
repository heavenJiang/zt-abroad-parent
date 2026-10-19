package com.ygb.abroad.growth;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.domain.AbrPlaybook;

/**
 * 营销日历计算（纯 Java，不访问数据库）：某天生效的剧本、当天的节点、一段时间内的剧本切换预览
 *
 * 日期一律按北京时间。每年重复的条目只比较月日，结束月日小于开始月日时视为跨年（如 10-01 至 03-31）；
 * 起止相差满一年的视为全年。多个剧本同时生效时按优先级从大到小、再按编码排列。
 *
 * @author ygb
 */
public final class MarketingCalendar
{
    /** 营销日历时区（北京时间） */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /**
     * 一段剧本组合不变的日期区间
     *
     * @param from 开始日期
     * @param to 结束日期（含当天）
     * @param playbookCodes 生效的剧本编码（按优先级）
     */
    public record Segment(LocalDate from, LocalDate to, List<String> playbookCodes)
    {
    }

    /**
     * 某天到达的节点
     *
     * @param date 日期
     * @param entry 节点
     */
    public record NodeHit(LocalDate date, AbrCalendarEntry entry)
    {
    }

    private MarketingCalendar()
    {
    }

    /**
     * java.util.Date（含数据库 date 列读出的 java.sql.Date）转为北京时间的日期
     *
     * @param date 日期
     * @return 日期，入参为 null 时返回 null
     */
    public static LocalDate toLocalDate(Date date)
    {
        if (date == null)
        {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate)
        {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZONE).toLocalDate();
    }

    /**
     * 北京时间的今天
     *
     * @return 日期
     */
    public static LocalDate today()
    {
        return LocalDate.now(ZONE);
    }

    /**
     * 区间是否覆盖某天
     *
     * @param start 开始日期
     * @param end 结束日期（含当天）
     * @param repeatYearly 是否每年重复（只比较月日）
     * @param date 日期
     * @return 是否覆盖
     */
    public static boolean covers(LocalDate start, LocalDate end, boolean repeatYearly, LocalDate date)
    {
        if (start == null || end == null || date == null)
        {
            return false;
        }
        if (!repeatYearly)
        {
            return !date.isBefore(start) && !date.isAfter(end);
        }
        if (!start.plusYears(1).minusDays(1).isAfter(end))
        {
            return true;
        }
        int s = monthDay(start);
        int e = monthDay(end);
        int d = monthDay(date);
        return s <= e ? d >= s && d <= e : d >= s || d <= e;
    }

    /**
     * 日历条目是否覆盖某天（停用的不覆盖）
     *
     * @param entry 日历条目
     * @param date 日期
     * @return 是否覆盖
     */
    public static boolean covers(AbrCalendarEntry entry, LocalDate date)
    {
        if (!STATUS_NORMAL.equals(entry.getStatus()))
        {
            return false;
        }
        LocalDate start = toLocalDate(entry.getStartDate());
        LocalDate end = AbrCalendarEntry.TYPE_NODE.equals(entry.getEntryType()) ? start : toLocalDate(entry.getEndDate());
        return covers(start, end, !"N".equals(entry.getRepeatYearly()), date);
    }

    /**
     * 某天生效的剧本（剧本启用，且至少有一个启用的时间窗覆盖当天）
     *
     * @param playbooks 剧本
     * @param entries 日历条目
     * @param date 日期
     * @return 剧本（按优先级从大到小、编码升序）
     */
    public static List<AbrPlaybook> activePlaybooks(List<AbrPlaybook> playbooks, List<AbrCalendarEntry> entries, LocalDate date)
    {
        Set<Long> covered = new LinkedHashSet<>();
        for (AbrCalendarEntry e : entries)
        {
            if (AbrCalendarEntry.TYPE_WINDOW.equals(e.getEntryType()) && e.getPlaybookId() != null && covers(e, date))
            {
                covered.add(e.getPlaybookId());
            }
        }
        List<AbrPlaybook> list = new ArrayList<>();
        for (AbrPlaybook p : playbooks)
        {
            if (STATUS_NORMAL.equals(p.getStatus()) && covered.contains(p.getPlaybookId()))
            {
                list.add(p);
            }
        }
        list.sort(Comparator.comparing((AbrPlaybook p) -> p.getPriority() == null ? 0 : p.getPriority()).reversed()
                .thenComparing(AbrPlaybook::getPlaybookCode));
        return list;
    }

    /**
     * 某天到达的节点（启用的节点，发生日为当天；每年重复的只比较月日）
     *
     * @param entries 日历条目
     * @param date 日期
     * @return 节点
     */
    public static List<AbrCalendarEntry> nodesOn(List<AbrCalendarEntry> entries, LocalDate date)
    {
        List<AbrCalendarEntry> list = new ArrayList<>();
        for (AbrCalendarEntry e : entries)
        {
            if (AbrCalendarEntry.TYPE_NODE.equals(e.getEntryType()) && covers(e, date))
            {
                list.add(e);
            }
        }
        return list;
    }

    /**
     * 剧本编码列表
     *
     * @param playbooks 剧本
     * @return 编码（顺序不变）
     */
    public static List<String> codes(List<AbrPlaybook> playbooks)
    {
        return playbooks.stream().map(AbrPlaybook::getPlaybookCode).toList();
    }

    /**
     * 预览一段时间内的剧本切换：相邻且剧本组合相同的日期合并为一段
     *
     * @param playbooks 剧本
     * @param entries 日历条目
     * @param from 开始日期
     * @param days 天数
     * @return 区间（按日期）
     */
    public static List<Segment> segments(List<AbrPlaybook> playbooks, List<AbrCalendarEntry> entries, LocalDate from, int days)
    {
        List<Segment> list = new ArrayList<>();
        LocalDate segStart = null;
        List<String> current = null;
        for (int i = 0; i < days; i++)
        {
            LocalDate d = from.plusDays(i);
            List<String> codes = codes(activePlaybooks(playbooks, entries, d));
            if (current == null || !current.equals(codes))
            {
                if (current != null)
                {
                    list.add(new Segment(segStart, d.minusDays(1), current));
                }
                segStart = d;
                current = codes;
            }
        }
        if (current != null)
        {
            list.add(new Segment(segStart, from.plusDays(days - 1), current));
        }
        return list;
    }

    /**
     * 预览一段时间内到达的节点
     *
     * @param entries 日历条目
     * @param from 开始日期
     * @param days 天数
     * @return 节点（按日期）
     */
    public static List<NodeHit> nodes(List<AbrCalendarEntry> entries, LocalDate from, int days)
    {
        List<NodeHit> list = new ArrayList<>();
        for (int i = 0; i < days; i++)
        {
            LocalDate d = from.plusDays(i);
            nodesOn(entries, d).forEach(e -> list.add(new NodeHit(d, e)));
        }
        return list;
    }

    /**
     * 两组剧本编码的差异
     *
     * @param before 之前
     * @param after 之后
     * @return added 新生效、removed 不再生效
     */
    public static Map<String, List<String>> diff(List<String> before, List<String> after)
    {
        Map<String, List<String>> map = new HashMap<>();
        map.put("added", after.stream().filter(c -> !before.contains(c)).toList());
        map.put("removed", before.stream().filter(c -> !after.contains(c)).toList());
        return map;
    }

    /**
     * 两组剧本编码是否相同（顺序不计）
     *
     * @param a 一组
     * @param b 另一组
     * @return 是否相同
     */
    public static boolean sameSet(List<String> a, List<String> b)
    {
        return new HashSet<>(a).equals(new HashSet<>(b));
    }

    private static int monthDay(LocalDate d)
    {
        return d.getMonthValue() * 100 + d.getDayOfMonth();
    }
}
