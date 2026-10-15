package com.ygb.abroad.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 申请日历的纯函数部分：汇总截止事件、生成手机日历文件（iCalendar，RFC 5545）
 *
 * 截止日只取院校库（最新申请季的 deadlines），不推算；项目信息超过 90 天未核实时不展示截止日。
 * 日历文件只含未过的截止事件，每个事件为全天事件，提前 7 天和 1 天提醒。
 *
 * @author ygb
 */
final class AbrPlanCalendar
{
    /** 说明 */
    static final String NOTE = "截止日来自院校库，按院校当地时间，以院校官网为准";

    /** 日历名称 */
    static final String CALENDAR_NAME = "留学申请截止日历";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /** iCalendar 每行最多 75 个字节（不含换行） */
    private static final int FOLD = 75;

    private AbrPlanCalendar()
    {
    }

    /**
     * 汇总申请日历
     *
     * @param items 选校单条目（只取正常项目）
     * @param programs 项目ID → 院校库记录
     * @param requirements 项目ID → 最新申请季的要求
     * @param countryLabel 国家 / 地区代码 → 名称
     * @param today 今天（北京时间）
     * @return 日历
     */
    static AppCalendarVo build(List<AbrPlanItem> items, Map<Long, AbrProgram> programs, Map<Long, AbrRequirement> requirements,
            Function<String, String> countryLabel, LocalDate today)
    {
        List<AppCalendarVo.Event> events = new ArrayList<>();
        List<AppCalendarVo.ProgramRef> rolling = new ArrayList<>();
        List<AppCalendarVo.ProgramRef> none = new ArrayList<>();
        List<AppCalendarVo.ProgramRef> unverified = new ArrayList<>();
        for (AbrPlanItem item : items)
        {
            if (!Boolean.TRUE.equals(item.getActive()))
            {
                continue;
            }
            AbrProgram program = programs.get(item.getProgramId());
            String label = countryLabel.apply(item.getCountry());
            String website = program == null ? null : program.getWebsite();
            AppCalendarVo.ProgramRef ref = new AppCalendarVo.ProgramRef(item.getProgramId(), item.getSchoolName(), item.getProgramName(), label,
                    website);
            if (AbrLookupTools.stale(program, today))
            {
                unverified.add(ref);
                continue;
            }
            AbrRequirement req = requirements.get(item.getProgramId());
            JsonNode d = AbrAssessmentAnswers.object(req == null ? null : req.getDeadlines());
            int added = 0;
            for (JsonNode r : d.path("rounds"))
            {
                LocalDate date;
                try
                {
                    date = LocalDate.parse(r.path("date").asString(""));
                }
                catch (DateTimeParseException e)
                {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(today, date);
                events.add(new AppCalendarVo.Event(item.getProgramId(), item.getSchoolName(), item.getProgramName(), label,
                        StringUtils.defaultIfBlank(r.path("name").asString(""), "Deadline"), date.toString(), days, days < 0, website,
                        verifiedDate(program)));
                added++;
            }
            if (d.path("rolling").asBoolean(false))
            {
                rolling.add(ref);
            }
            else if (added == 0)
            {
                none.add(ref);
            }
        }
        events.sort(Comparator.comparing(AppCalendarVo.Event::date).thenComparing(AppCalendarVo.Event::programId)
                .thenComparing(AppCalendarVo.Event::round));
        AppCalendarVo.Event next = events.stream().filter(e -> !e.passed()).findFirst().orElse(null);
        return new AppCalendarVo(events, rolling, none, unverified, next, NOTE);
    }

    /**
     * 生成手机日历文件（只含未过的截止事件）
     *
     * @param calendar 日历
     * @param now 生成时刻
     * @param uidDomain 事件 UID 的域名部分
     * @return iCalendar 文本（CRLF 换行）
     */
    static String ics(AppCalendarVo calendar, ZonedDateTime now, String uidDomain)
    {
        String stamp = STAMP.format(now.withZoneSameInstant(ZoneOffset.UTC));
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//zt-abroad//plan-calendar//ZH");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + escape(CALENDAR_NAME));
        line(sb, "X-WR-TIMEZONE:Asia/Shanghai");
        for (AppCalendarVo.Event e : calendar.events())
        {
            if (e.passed())
            {
                continue;
            }
            LocalDate date = LocalDate.parse(e.date());
            String title = "截止：" + StringUtils.nvl(e.schoolName(), "") + " " + StringUtils.nvl(e.programName(), "") + "（" + e.round() + "）";
            StringBuilder desc = new StringBuilder(NOTE);
            if (e.verifiedDate() != null)
            {
                desc.append("。院校信息核对于 ").append(e.verifiedDate());
            }
            if (StringUtils.isNotBlank(e.website()))
            {
                desc.append("\n").append(e.website());
            }
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + uid(e) + "@" + uidDomain);
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART;VALUE=DATE:" + DATE.format(date));
            line(sb, "DTEND;VALUE=DATE:" + DATE.format(date.plusDays(1)));
            line(sb, "SUMMARY:" + escape(title));
            line(sb, "DESCRIPTION:" + escape(desc.toString()));
            if (StringUtils.isNotBlank(e.website()))
            {
                line(sb, "URL:" + e.website().trim());
            }
            line(sb, "TRANSP:TRANSPARENT");
            alarm(sb, "-P7D", title);
            alarm(sb, "-P1D", title);
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    private static void alarm(StringBuilder sb, String trigger, String title)
    {
        line(sb, "BEGIN:VALARM");
        line(sb, "ACTION:DISPLAY");
        line(sb, "DESCRIPTION:" + escape(title));
        line(sb, "TRIGGER:" + trigger);
        line(sb, "END:VALARM");
    }

    /**
     * 事件 UID：项目ID + 日期 + 轮次（同一事件重复导入时日历应用可去重）
     */
    static String uid(AppCalendarVo.Event e)
    {
        String round = e.round().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return "abr-" + e.programId() + "-" + e.date().replace("-", "") + (round.isEmpty() ? "" : "-" + round);
    }

    /**
     * 文本转义：反斜杠、分号、逗号、换行
     */
    static String escape(String s)
    {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /**
     * 写一行并按 75 字节折行（续行以空格开头，不拆开多字节字符）
     */
    static void line(StringBuilder sb, String content)
    {
        int bytes = 0;
        int limit = FOLD;
        for (int i = 0; i < content.length(); )
        {
            int cp = content.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int n = ch.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + n > limit)
            {
                sb.append("\r\n ");
                bytes = 0;
                limit = FOLD - 1;
            }
            sb.append(ch);
            bytes += n;
            i += Character.charCount(cp);
        }
        sb.append("\r\n");
    }

    private static String verifiedDate(AbrProgram program)
    {
        Date verified = program == null ? null : program.getVerifiedTime();
        return verified == null ? null : verified.toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate().toString();
    }
}
