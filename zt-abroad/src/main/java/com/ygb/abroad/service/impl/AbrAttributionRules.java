package com.ygb.abroad.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.ygb.abroad.domain.vo.AbrAttributionRow;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 归因看板规则（纯计算，不访问数据库）：日期范围、漏斗行合并与合计、每周活跃补齐、留存分组
 * <ul>
 * <li>7 日留存：首次活跃（选校单有项目且打开过）后第 7–13 天内再次活跃；30 日留存：第 30–36 天内；</li>
 * <li>窗口还没过完的访客不计入分母，整组都没过完时留存为空。</li>
 * </ul>
 *
 * @author ygb
 */
final class AbrAttributionRules
{
    /** 分组方式 */
    static final List<String> GROUPS = List.of("channel", "account", "content", "playbook");

    /** 日期范围最长天数 */
    static final int MAX_DAYS = 366;

    /** 默认最近几天 */
    static final int DEFAULT_DAYS = 30;

    /** 每周活跃最少、最多看几周 */
    static final int MIN_WEEKS = 4;

    static final int MAX_WEEKS = 26;

    /** 留存窗口：[起始天, 结束天]（相对首次活跃） */
    static final int[] D7 = { 7, 13 };

    static final int[] D30 = { 30, 36 };

    /**
     * 日期范围
     */
    record Range(LocalDate begin, LocalDate end)
    {
    }

    private AbrAttributionRules()
    {
    }

    /**
     * 解析日期范围：都不填为最近 30 天（含今天）；只填一端时另一端按 30 天补齐；最长 366 天
     *
     * @param begin 开始日期 yyyy-MM-dd
     * @param end 结束日期 yyyy-MM-dd
     * @param today 今天（北京时间）
     * @return 日期范围
     */
    static Range range(String begin, String end, LocalDate today)
    {
        LocalDate b = parse(begin);
        LocalDate e = parse(end);
        if (e == null)
        {
            e = b == null ? today : b.plusDays(DEFAULT_DAYS - 1);
        }
        if (b == null)
        {
            b = e.minusDays(DEFAULT_DAYS - 1);
        }
        if (b.isAfter(e))
        {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(b, e) + 1 > MAX_DAYS)
        {
            throw new ServiceException("日期范围最长 " + MAX_DAYS + " 天");
        }
        return new Range(b, e);
    }

    private static LocalDate parse(String s)
    {
        if (StringUtils.isBlank(s))
        {
            return null;
        }
        try
        {
            return LocalDate.parse(s.trim());
        }
        catch (DateTimeParseException ex)
        {
            throw new ServiceException("日期格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 分组方式：不填为按渠道
     *
     * @param groupBy 分组方式
     * @return 分组方式
     */
    static String group(String groupBy)
    {
        if (StringUtils.isBlank(groupBy))
        {
            return GROUPS.get(0);
        }
        if (!GROUPS.contains(groupBy))
        {
            throw new ServiceException("分组方式只能是 " + String.join(" / ", GROUPS));
        }
        return groupBy;
    }

    /**
     * 合并漏斗行与内容指标行（按分组键），空值补 0；按新访客、发布数从多到少
     *
     * @param funnel 漏斗行
     * @param content 内容指标行
     * @return 合并后的行
     */
    static List<AbrAttributionRow> merge(List<AbrAttributionRow> funnel, List<AbrAttributionRow> content)
    {
        Map<String, AbrAttributionRow> byKey = new LinkedHashMap<>();
        for (AbrAttributionRow r : funnel)
        {
            byKey.put(StringUtils.defaultString(r.getGroupKey()), r);
        }
        for (AbrAttributionRow c : content)
        {
            String key = StringUtils.defaultString(c.getGroupKey());
            AbrAttributionRow r = byKey.computeIfAbsent(key, k -> {
                AbrAttributionRow n = new AbrAttributionRow();
                n.setGroupKey(k);
                return n;
            });
            if (StringUtils.isBlank(r.getGroupLabel()))
            {
                r.setGroupLabel(c.getGroupLabel());
            }
            r.setPublished(c.getPublished());
            r.setViews(c.getViews());
            r.setInteractions(c.getInteractions());
            r.setDms(c.getDms());
            r.setFollows(c.getFollows());
        }
        List<AbrAttributionRow> rows = new ArrayList<>(byKey.values());
        rows.forEach(AbrAttributionRules::zeroFill);
        rows.sort(Comparator.comparing(AbrAttributionRow::getVisitors).reversed()
                .thenComparing(Comparator.comparing(AbrAttributionRow::getPublished).reversed())
                .thenComparing(AbrAttributionRow::getGroupKey));
        return rows;
    }

    /**
     * 合计行
     *
     * @param rows 各组
     * @return 合计（分组键为空）
     */
    static AbrAttributionRow total(List<AbrAttributionRow> rows)
    {
        AbrAttributionRow t = new AbrAttributionRow();
        zeroFill(t);
        t.setGroupKey("");
        t.setGroupLabel("合计");
        for (AbrAttributionRow r : rows)
        {
            t.setVisitors(t.getVisitors() + r.getVisitors());
            t.setToolUsers(t.getToolUsers() + r.getToolUsers());
            t.setPlanUsers(t.getPlanUsers() + r.getPlanUsers());
            t.setLoginUsers(t.getLoginUsers() + r.getLoginUsers());
            t.setMomentUsers(t.getMomentUsers() + r.getMomentUsers());
            t.setBookedUsers(t.getBookedUsers() + r.getBookedUsers());
            t.setSignedUsers(t.getSignedUsers() + r.getSignedUsers());
            t.setPublished(t.getPublished() + r.getPublished());
            t.setViews(t.getViews() + r.getViews());
            t.setInteractions(t.getInteractions() + r.getInteractions());
            t.setDms(t.getDms() + r.getDms());
            t.setFollows(t.getFollows() + r.getFollows());
        }
        return t;
    }

    private static void zeroFill(AbrAttributionRow r)
    {
        r.setVisitors(n(r.getVisitors()));
        r.setToolUsers(n(r.getToolUsers()));
        r.setPlanUsers(n(r.getPlanUsers()));
        r.setLoginUsers(n(r.getLoginUsers()));
        r.setMomentUsers(n(r.getMomentUsers()));
        r.setBookedUsers(n(r.getBookedUsers()));
        r.setSignedUsers(n(r.getSignedUsers()));
        r.setPublished(n(r.getPublished()));
        r.setViews(n(r.getViews()));
        r.setInteractions(n(r.getInteractions()));
        r.setDms(n(r.getDms()));
        r.setFollows(n(r.getFollows()));
    }

    private static Long n(Long v)
    {
        return v == null ? 0L : v;
    }

    /**
     * 所在周的周一
     */
    static LocalDate weekStart(LocalDate d)
    {
        return d.with(DayOfWeek.MONDAY);
    }

    /**
     * 每周活跃：从 since 所在周到今天所在周逐周补齐，没有数据的周为 0
     *
     * @param weekly 查询结果（week、active、opened）
     * @param since 开始周的周一
     * @param today 今天
     * @return 每周一行（week、active、opened）
     */
    static List<Map<String, Object>> weeks(List<Map<String, Object>> weekly, LocalDate since, LocalDate today)
    {
        Map<String, Map<String, Object>> byWeek = new LinkedHashMap<>();
        for (Map<String, Object> w : weekly)
        {
            byWeek.put(String.valueOf(w.get("week")), w);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate w = weekStart(since); !w.isAfter(today); w = w.plusWeeks(1))
        {
            Map<String, Object> src = byWeek.get(w.toString());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("week", w.toString());
            row.put("active", src == null ? 0L : num(src.get("active")));
            row.put("opened", src == null ? 0L : num(src.get("opened")));
            out.add(row);
        }
        return out;
    }

    /**
     * 留存：按首次活跃所在周分组
     *
     * @param activeDays 活跃天（visitor_id、first_date、active_date，按访客、日期排序）
     * @param today 今天
     * @return 每组一行（week 首次活跃周、size 人数、d7Base / d7 / d7Rate、d30Base / d30 / d30Rate；率为空表示窗口还没过完）
     */
    static List<Map<String, Object>> cohorts(List<Map<String, Object>> activeDays, LocalDate today)
    {
        Map<Long, LocalDate> first = new LinkedHashMap<>();
        Map<Long, List<LocalDate>> days = new LinkedHashMap<>();
        for (Map<String, Object> r : activeDays)
        {
            Long v = num(r.get("visitor_id"));
            first.put(v, LocalDate.parse(String.valueOf(r.get("first_date"))));
            days.computeIfAbsent(v, k -> new ArrayList<>()).add(LocalDate.parse(String.valueOf(r.get("active_date"))));
        }
        Map<LocalDate, long[]> byWeek = new TreeMap<>();
        for (Map.Entry<Long, LocalDate> e : first.entrySet())
        {
            LocalDate f = e.getValue();
            List<LocalDate> ds = days.get(e.getKey());
            long[] c = byWeek.computeIfAbsent(weekStart(f), k -> new long[5]);
            c[0]++;
            if (f.plusDays(D7[1]).isBefore(today))
            {
                c[1]++;
                c[2] += retained(f, ds, D7) ? 1 : 0;
            }
            if (f.plusDays(D30[1]).isBefore(today))
            {
                c[3]++;
                c[4] += retained(f, ds, D30) ? 1 : 0;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> e : byWeek.entrySet())
        {
            long[] c = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("week", e.getKey().toString());
            row.put("size", c[0]);
            row.put("d7Base", c[1]);
            row.put("d7", c[2]);
            row.put("d7Rate", rate(c[2], c[1]));
            row.put("d30Base", c[3]);
            row.put("d30", c[4]);
            row.put("d30Rate", rate(c[4], c[3]));
            out.add(row);
        }
        return out;
    }

    private static boolean retained(LocalDate first, List<LocalDate> days, int[] window)
    {
        LocalDate from = first.plusDays(window[0]);
        LocalDate to = first.plusDays(window[1]);
        return days.stream().anyMatch(d -> !d.isBefore(from) && !d.isAfter(to));
    }

    /**
     * 比率（保留 3 位小数）；分母为 0 时为空
     */
    static Double rate(long part, long base)
    {
        return base <= 0 ? null : Math.round(part * 1000.0 / base) / 1000.0;
    }

    static Long num(Object v)
    {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
