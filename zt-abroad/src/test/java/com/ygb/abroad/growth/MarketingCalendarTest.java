package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.domain.AbrPlaybook;

/**
 * 营销日历测试：每年重复与跨年、全年、单次区间、停用、优先级排序、节点、切换预览
 *
 * @author ygb
 */
class MarketingCalendarTest
{
    private static LocalDate d(String s)
    {
        return LocalDate.parse(s);
    }

    private static AbrPlaybook playbook(long id, String code, int priority, String status)
    {
        AbrPlaybook p = new AbrPlaybook();
        p.setPlaybookId(id);
        p.setPlaybookCode(code);
        p.setPlaybookName(code);
        p.setPriority(priority);
        p.setStatus(status);
        return p;
    }

    private static AbrCalendarEntry window(long playbookId, String start, String end, boolean repeat)
    {
        AbrCalendarEntry e = new AbrCalendarEntry();
        e.setEntryType(AbrCalendarEntry.TYPE_WINDOW);
        e.setPlaybookId(playbookId);
        e.setStartDate(java.sql.Date.valueOf(start));
        e.setEndDate(java.sql.Date.valueOf(end));
        e.setRepeatYearly(repeat ? "Y" : "N");
        e.setStatus("0");
        return e;
    }

    private static AbrCalendarEntry node(String code, String date, boolean repeat)
    {
        AbrCalendarEntry e = new AbrCalendarEntry();
        e.setEntryType(AbrCalendarEntry.TYPE_NODE);
        e.setNodeCode(code);
        e.setStartDate(java.sql.Date.valueOf(date));
        e.setEndDate(java.sql.Date.valueOf(date));
        e.setRepeatYearly(repeat ? "Y" : "N");
        e.setStatus("0");
        return e;
    }

    @Test
    void repeatYearlyWindowCrossesYear()
    {
        LocalDate start = d("2026-10-01");
        LocalDate end = d("2027-03-31");
        assertTrue(MarketingCalendar.covers(start, end, true, d("2026-10-01")));
        assertTrue(MarketingCalendar.covers(start, end, true, d("2028-01-15")));
        assertTrue(MarketingCalendar.covers(start, end, true, d("2030-03-31")));
        assertFalse(MarketingCalendar.covers(start, end, true, d("2027-04-01")));
        assertFalse(MarketingCalendar.covers(start, end, true, d("2027-09-30")));
    }

    @Test
    void repeatYearlyWindowWithinYear()
    {
        LocalDate start = d("2026-02-01");
        LocalDate end = d("2026-03-31");
        assertTrue(MarketingCalendar.covers(start, end, true, d("2031-02-01")));
        assertTrue(MarketingCalendar.covers(start, end, true, d("2028-02-29")));
        assertFalse(MarketingCalendar.covers(start, end, true, d("2031-01-31")));
        assertFalse(MarketingCalendar.covers(start, end, true, d("2031-04-01")));
    }

    @Test
    void fullYearWindowAlwaysCovers()
    {
        assertTrue(MarketingCalendar.covers(d("2026-01-01"), d("2026-12-31"), true, d("2029-07-07")));
        assertTrue(MarketingCalendar.covers(d("2026-06-15"), d("2027-06-14"), true, d("2029-06-14")));
        assertTrue(MarketingCalendar.covers(d("2026-06-15"), d("2027-06-14"), true, d("2029-06-15")));
    }

    @Test
    void oneOffWindowUsesFullDates()
    {
        LocalDate start = d("2026-11-01");
        LocalDate end = d("2026-11-30");
        assertTrue(MarketingCalendar.covers(start, end, false, d("2026-11-30")));
        assertFalse(MarketingCalendar.covers(start, end, false, d("2027-11-15")));
        assertFalse(MarketingCalendar.covers(start, end, false, d("2026-10-31")));
        assertFalse(MarketingCalendar.covers(null, end, false, d("2026-11-15")));
    }

    @Test
    void activePlaybooksSortedByPriorityAndSkipDisabled()
    {
        List<AbrPlaybook> playbooks = List.of(playbook(1, "season_open", 60, "0"), playbook(2, "round_deadline", 70, "0"),
                playbook(3, "japan", 10, "0"), playbook(4, "disabled", 99, "1"), playbook(5, "b_same", 60, "0"));
        AbrCalendarEntry stopped = window(3, "2026-01-01", "2026-12-31", true);
        stopped.setStatus("1");
        List<AbrCalendarEntry> entries = List.of(window(1, "2026-09-01", "2026-10-31", true), window(2, "2026-10-01", "2027-03-31", true),
                window(3, "2026-01-01", "2026-06-30", true), stopped, window(4, "2026-01-01", "2026-12-31", true),
                window(5, "2026-10-01", "2026-10-31", true), node("kaoyan", "2026-10-18", true));

        List<String> codes = MarketingCalendar.codes(MarketingCalendar.activePlaybooks(playbooks, entries, d("2026-10-18")));
        assertEquals(List.of("round_deadline", "b_same", "season_open"), codes);
        assertEquals(List.of("japan"), MarketingCalendar.codes(MarketingCalendar.activePlaybooks(playbooks, entries, d("2027-05-01"))));
        assertEquals(List.of("round_deadline", "japan"), MarketingCalendar.codes(MarketingCalendar.activePlaybooks(playbooks, entries, d("2027-02-01"))));
    }

    @Test
    void nodesOnlyOnTheirDay()
    {
        AbrCalendarEntry disabled = node("off", "2026-10-18", true);
        disabled.setStatus("1");
        List<AbrCalendarEntry> entries = List.of(node("yearly", "2026-02-20", true), node("once", "2026-06-10", false), disabled,
                window(1, "2026-02-20", "2026-02-20", true));
        assertEquals(1, MarketingCalendar.nodesOn(entries, d("2028-02-20")).size());
        assertEquals("yearly", MarketingCalendar.nodesOn(entries, d("2028-02-20")).get(0).getNodeCode());
        assertEquals(1, MarketingCalendar.nodesOn(entries, d("2026-06-10")).size());
        assertTrue(MarketingCalendar.nodesOn(entries, d("2027-06-10")).isEmpty());
        assertTrue(MarketingCalendar.nodesOn(entries, d("2026-10-18")).isEmpty());

        List<MarketingCalendar.NodeHit> hits = MarketingCalendar.nodes(entries, d("2026-01-01"), 365);
        assertEquals(2, hits.size());
        assertEquals(d("2026-02-20"), hits.get(0).date());
        assertEquals(d("2026-06-10"), hits.get(1).date());
    }

    @Test
    void segmentsMergeSameCombination()
    {
        List<AbrPlaybook> playbooks = List.of(playbook(1, "a", 10, "0"), playbook(2, "b", 20, "0"));
        List<AbrCalendarEntry> entries = List.of(window(1, "2026-01-01", "2026-01-10", false), window(2, "2026-01-05", "2026-01-20", false));
        List<MarketingCalendar.Segment> segs = MarketingCalendar.segments(playbooks, entries, d("2026-01-01"), 25);
        assertEquals(4, segs.size());
        assertEquals(new MarketingCalendar.Segment(d("2026-01-01"), d("2026-01-04"), List.of("a")), segs.get(0));
        assertEquals(new MarketingCalendar.Segment(d("2026-01-05"), d("2026-01-10"), List.of("b", "a")), segs.get(1));
        assertEquals(new MarketingCalendar.Segment(d("2026-01-11"), d("2026-01-20"), List.of("b")), segs.get(2));
        assertEquals(new MarketingCalendar.Segment(d("2026-01-21"), d("2026-01-25"), List.of()), segs.get(3));
        assertTrue(MarketingCalendar.segments(playbooks, entries, d("2026-01-01"), 0).isEmpty());
    }

    @Test
    void diffAndSameSet()
    {
        assertEquals(List.of("c"), MarketingCalendar.diff(List.of("a", "b"), List.of("b", "c")).get("added"));
        assertEquals(List.of("a"), MarketingCalendar.diff(List.of("a", "b"), List.of("b", "c")).get("removed"));
        assertTrue(MarketingCalendar.sameSet(List.of("a", "b"), List.of("b", "a")));
        assertFalse(MarketingCalendar.sameSet(List.of("a"), List.of("a", "b")));
        assertTrue(MarketingCalendar.sameSet(List.of(), List.of()));
    }

    @Test
    void toLocalDateUsesBeijingTime()
    {
        assertNull(MarketingCalendar.toLocalDate(null));
        assertEquals(d("2026-10-18"), MarketingCalendar.toLocalDate(java.sql.Date.valueOf("2026-10-18")));
        // 2026-10-17 16:30 UTC = 2026-10-18 00:30 北京时间
        assertEquals(d("2026-10-18"), MarketingCalendar.toLocalDate(Date.from(java.time.Instant.parse("2026-10-17T16:30:00Z"))));
    }
}
