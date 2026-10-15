package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.vo.AppCalendarVo;

/**
 * 申请日历纯函数测试：汇总轮次、滚动录取与无截止信息分组、90 天未核实不展示、日历文件格式（折行、转义、只含未过事件）
 *
 * @author ygb
 */
class AbrPlanCalendarTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private static AbrPlanItem item(long id, boolean active)
    {
        AbrPlanItem i = new AbrPlanItem();
        i.setProgramId(id);
        i.setSchoolName("University of Eastvale, London");
        i.setProgramName("MSc Management; Strategy");
        i.setCountry("UK");
        i.setActive(active);
        return i;
    }

    private static AbrProgram program(long id, int verifiedDaysAgo)
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(id);
        p.setWebsite("https://www.eastvale.example/pg/" + id);
        p.setVerifiedTime(Date.from(TODAY.minusDays(verifiedDaysAgo).atStartOfDay(AbrLookupTools.BEIJING).toInstant()));
        return p;
    }

    private static AbrRequirement req(long id, String deadlines)
    {
        AbrRequirement r = new AbrRequirement();
        r.setProgramId(id);
        r.setDeadlines(deadlines);
        return r;
    }

    private static AppCalendarVo calendar()
    {
        List<AbrPlanItem> items = List.of(item(1, true), item(2, true), item(3, true), item(4, true), item(5, false));
        Map<Long, AbrProgram> programs = new HashMap<>();
        Map<Long, AbrRequirement> reqs = new HashMap<>();
        programs.put(1L, program(1, 5));
        reqs.put(1L, req(1, "{\"rolling\": false, \"rounds\": [{\"name\": \"Round 2\", \"date\": \"2027-01-15\"},"
                + " {\"name\": \"Round 1\", \"date\": \"2026-10-01\"}, {\"name\": \"bad\", \"date\": \"2027-13-01\"}]}"));
        programs.put(2L, program(2, 5));
        reqs.put(2L, req(2, "{\"rolling\": true, \"rounds\": [{\"name\": \"\", \"date\": \"2026-11-30\"}]}"));
        programs.put(3L, program(3, 5));
        programs.put(4L, program(4, 120));
        reqs.put(4L, req(4, "{\"rounds\": [{\"name\": \"Round 1\", \"date\": \"2026-12-01\"}]}"));
        return AbrPlanCalendar.build(items, programs, reqs, c -> "英国", TODAY);
    }

    @Test
    void buildGroupsEventsAndPrograms()
    {
        AppCalendarVo vo = calendar();
        assertEquals(List.of("2026-10-01", "2026-11-30", "2027-01-15"), vo.events().stream().map(AppCalendarVo.Event::date).toList());
        AppCalendarVo.Event r1 = vo.events().get(0);
        assertTrue(r1.passed());
        assertEquals(-15, r1.daysLeft());
        assertEquals("Deadline", vo.events().get(1).round());
        assertEquals(45, vo.events().get(1).daysLeft());
        assertEquals(vo.events().get(1), vo.nextDeadline());
        assertEquals("英国", r1.countryLabel());
        assertEquals(TODAY.minusDays(5).toString(), r1.verifiedDate());
        // 2 有固定截止日也写明滚动录取；3 没有截止信息；4 超过 90 天未核实不展示日期；5 已下线不出现
        assertEquals(List.of(2L), vo.rolling().stream().map(AppCalendarVo.ProgramRef::programId).toList());
        assertEquals(List.of(3L), vo.noDeadline().stream().map(AppCalendarVo.ProgramRef::programId).toList());
        assertEquals(List.of(4L), vo.unverified().stream().map(AppCalendarVo.ProgramRef::programId).toList());
        assertTrue(vo.events().stream().noneMatch(e -> e.programId() == 4L || e.programId() == 5L));
    }

    @Test
    void emptyCalendar()
    {
        AppCalendarVo vo = AbrPlanCalendar.build(List.of(), Map.of(), Map.of(), c -> c, TODAY);
        assertTrue(vo.events().isEmpty());
        assertNull(vo.nextDeadline());
        String ics = AbrPlanCalendar.ics(vo, ZonedDateTime.now(AbrLookupTools.BEIJING), "m.example.com");
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n") && ics.endsWith("END:VCALENDAR\r\n"));
        assertFalse(ics.contains("VEVENT"));
    }

    @Test
    void icsHasOnlyUpcomingEventsWithAlarmsAndValidLines()
    {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 16, 9, 30, 0, 0, AbrLookupTools.BEIJING);
        String ics = AbrPlanCalendar.ics(calendar(), now, "m.example.com");
        assertEquals(2, ics.split("BEGIN:VEVENT", -1).length - 1);
        assertEquals(4, ics.split("BEGIN:VALARM", -1).length - 1);
        assertTrue(ics.contains("UID:abr-1-20270115-round2@m.example.com\r\n"));
        assertTrue(ics.contains("UID:abr-2-20261130-deadline@m.example.com\r\n"));
        assertTrue(ics.contains("DTSTAMP:20261016T013000Z\r\n"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20270115\r\nDTEND;VALUE=DATE:20270116\r\n"));
        assertTrue(ics.contains("TRIGGER:-P7D\r\n") && ics.contains("TRIGGER:-P1D\r\n"));
        assertFalse(ics.contains("20261001"));
        String unfolded = ics.replace("\r\n ", "");
        assertTrue(unfolded.contains("SUMMARY:截止：University of Eastvale\\, London MSc Management\\; Strategy（Round 2）"), unfolded);
        for (String line : ics.split("\r\n"))
        {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        }
        assertFalse(ics.replace("\r\n", "").contains("\n"));
    }

    @Test
    void lineFoldingKeepsMultibyteCharacters()
    {
        StringBuilder sb = new StringBuilder();
        String content = "DESCRIPTION:" + "截止日来自院校库".repeat(10);
        AbrPlanCalendar.line(sb, content);
        String[] lines = sb.toString().split("\r\n");
        assertTrue(lines.length > 1);
        for (int i = 1; i < lines.length; i++)
        {
            assertTrue(lines[i].startsWith(" "));
        }
        assertEquals(content, sb.toString().replace("\r\n ", "").replace("\r\n", ""));
        assertEquals("a\\\\b\\;c\\,d\\ne", AbrPlanCalendar.escape("a\\b;c,d\ne"));
    }
}
