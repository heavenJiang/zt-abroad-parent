package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AbrAttributionRow;
import com.ygb.common.exception.ServiceException;

/**
 * 归因看板规则
 *
 * @author ygb
 */
class AbrAttributionRulesTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void range()
    {
        assertEquals(new AbrAttributionRules.Range(LocalDate.of(2026, 9, 21), TODAY), AbrAttributionRules.range(null, "", TODAY));
        assertEquals(new AbrAttributionRules.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30)),
                AbrAttributionRules.range("2026-10-01", null, TODAY));
        assertEquals(new AbrAttributionRules.Range(TODAY, TODAY), AbrAttributionRules.range("2026-10-20", "2026-10-20", TODAY));
        assertThrows(ServiceException.class, () -> AbrAttributionRules.range("2026-10-21", "2026-10-20", TODAY));
        assertThrows(ServiceException.class, () -> AbrAttributionRules.range("2025-01-01", "2026-10-20", TODAY));
        assertThrows(ServiceException.class, () -> AbrAttributionRules.range("2026/10/01", null, TODAY));
        assertEquals("channel", AbrAttributionRules.group(null));
        assertEquals("playbook", AbrAttributionRules.group("playbook"));
        assertThrows(ServiceException.class, () -> AbrAttributionRules.group("visitor"));
    }

    @Test
    void mergeAndTotal()
    {
        AbrAttributionRow a = row("xhs1", 5L);
        a.setSignedUsers(1L);
        AbrAttributionRow b = row("share", 2L);
        AbrAttributionRow ca = new AbrAttributionRow();
        ca.setGroupKey("xhs1");
        ca.setGroupLabel("小红书 1 号");
        ca.setPublished(3L);
        ca.setViews(900L);
        AbrAttributionRow cz = new AbrAttributionRow();
        cz.setGroupKey("gzh");
        cz.setPublished(1L);
        List<AbrAttributionRow> rows = AbrAttributionRules.merge(new ArrayList<>(List.of(b, a)), List.of(ca, cz));
        assertEquals(List.of("xhs1", "share", "gzh"), rows.stream().map(AbrAttributionRow::getGroupKey).toList());
        assertEquals("小红书 1 号", rows.get(0).getGroupLabel());
        assertEquals(0L, rows.get(1).getViews());
        assertEquals(0L, rows.get(2).getVisitors());
        AbrAttributionRow t = AbrAttributionRules.total(rows);
        assertEquals(7L, t.getVisitors());
        assertEquals(1L, t.getSignedUsers());
        assertEquals(4L, t.getPublished());
        assertEquals(900L, t.getViews());
    }

    private static AbrAttributionRow row(String key, Long visitors)
    {
        AbrAttributionRow r = new AbrAttributionRow();
        r.setGroupKey(key);
        r.setVisitors(visitors);
        r.setToolUsers(visitors);
        return r;
    }

    @Test
    void weeks()
    {
        LocalDate since = LocalDate.of(2026, 9, 30);
        List<Map<String, Object>> w = AbrAttributionRules.weeks(List.of(Map.of("week", "2026-10-05", "active", 3L, "opened", 4L)), since, TODAY);
        assertEquals(List.of("2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19"), w.stream().map(m -> m.get("week")).toList());
        assertEquals(3L, w.get(1).get("active"));
        assertEquals(0L, w.get(3).get("opened"));
    }

    @Test
    void cohorts()
    {
        List<Map<String, Object>> days = List.of(
                // 9 月 7 日首次：第 8 天、第 31 天再来 → 7 日、30 日都留存
                day(1, "2026-09-07", "2026-09-07"), day(1, "2026-09-07", "2026-09-15"), day(1, "2026-09-07", "2026-10-08"),
                // 同一周：第 6 天和第 14 天来（都不在 7–13 天窗口内）
                day(2, "2026-09-08", "2026-09-08"), day(2, "2026-09-08", "2026-09-14"), day(2, "2026-09-08", "2026-09-22"),
                // 10 月 12 日首次：7 日窗口没过完
                day(3, "2026-10-12", "2026-10-12"));
        List<Map<String, Object>> c = AbrAttributionRules.cohorts(days, TODAY);
        assertEquals(2, c.size());
        Map<String, Object> sep = c.get(0);
        assertEquals("2026-09-07", sep.get("week"));
        assertEquals(2L, sep.get("size"));
        assertEquals(2L, sep.get("d7Base"));
        assertEquals(1L, sep.get("d7"));
        assertEquals(0.5, sep.get("d7Rate"));
        // 9 月 7 日 + 36 = 10 月 13 日已过；9 月 8 日 + 36 = 10 月 14 日已过
        assertEquals(2L, sep.get("d30Base"));
        assertEquals(1L, sep.get("d30"));
        Map<String, Object> oct = c.get(1);
        assertEquals(0L, oct.get("d7Base"));
        assertNull(oct.get("d7Rate"));
        assertNull(oct.get("d30Rate"));
    }

    private static Map<String, Object> day(long visitor, String first, String active)
    {
        return Map.of("visitor_id", visitor, "first_date", first, "active_date", active);
    }
}
