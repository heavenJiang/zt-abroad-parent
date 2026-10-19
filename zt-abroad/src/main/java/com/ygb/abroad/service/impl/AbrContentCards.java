package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.growth.card.Card;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 内容图卡模板（content_designer，纯计算）：按模板把院校库数据排成图卡，数字一律取自院校库，不经大模型
 *
 * 模板：cover 封面、program_list 项目清单、deadline_calendar 截止日历、comparison 对比表。超过 90 天未核对的项目不显示数值，
 * 改为“以官网为准”。每张图卡同时给出引用依据（项目与申请要求记录）和图上全部文字，供合规闸门核对。
 *
 * @author ygb
 */
final class AbrContentCards
{
    /** 模板：封面 */
    static final String COVER = "cover";

    /** 模板：项目清单 */
    static final String PROGRAM_LIST = "program_list";

    /** 模板：截止日历 */
    static final String DEADLINE_CALENDAR = "deadline_calendar";

    /** 模板：对比表 */
    static final String COMPARISON = "comparison";

    /** 可选的展示字段 */
    static final List<String> FIELDS = List.of("ielts", "deadline", "tuition", "duration");

    /** 模板说明（管理端选择用） */
    static final Map<String, String> TEMPLATES = new LinkedHashMap<>();

    static
    {
        TEMPLATES.put(COVER, "封面：小标题 + 内容标题（无数字）；其他模板失败时的默认模板");
        TEMPLATES.put(PROGRAM_LIST, "项目清单：1–8 个项目，每个项目列出所选字段（默认雅思、截止日）");
        TEMPLATES.put(DEADLINE_CALENDAR, "截止日历：1–12 个项目按最近一轮截止日排序、按月分组；已截止的不列");
        TEMPLATES.put(COMPARISON, "对比表：2–4 个项目按字段逐项对比（默认雅思、截止日、学费、学制）");
    }

    static final String UNVERIFIED = "以官网为准";

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private AbrContentCards()
    {
    }

    /**
     * 模板参数
     *
     * @param kicker 小标题（可为 null）
     * @param programIds 项目ID（按填写顺序）
     * @param fields 展示字段
     */
    record Params(String kicker, List<Long> programIds, List<String> fields)
    {
    }

    /**
     * 排版结果
     *
     * @param card 图卡
     * @param citations 引用依据（type / id）
     * @param text 图上全部文字（换行分隔）
     * @param dataDate 所用数据中最早的核对日期（无数据为 null）
     */
    record Built(Card card, List<Map<String, Object>> citations, String text, LocalDate dataDate)
    {
    }

    /**
     * 解析并校验模板参数
     *
     * @param template 模板
     * @param params 参数（JSON 对象：kicker、programIds、fields）
     * @return 参数
     */
    static Params params(String template, JsonNode params)
    {
        if (!TEMPLATES.containsKey(template))
        {
            throw new ServiceException("图卡模板“" + template + "”不存在，可选 " + String.join(" / ", TEMPLATES.keySet()));
        }
        String kicker = StringUtils.trimToNull(params == null ? null : params.path("kicker").asString(null));
        if (kicker != null && kicker.length() > 30)
        {
            throw new ServiceException("小标题最多 30 个字符");
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (params != null)
        {
            for (JsonNode n : params.path("programIds"))
            {
                if (!n.isIntegralNumber())
                {
                    throw new ServiceException("programIds 须为项目ID数组");
                }
                ids.add(n.asLong());
            }
        }
        List<String> fields = new ArrayList<>();
        if (params != null)
        {
            for (JsonNode n : params.path("fields"))
            {
                String f = n.asString("");
                if (!FIELDS.contains(f))
                {
                    throw new ServiceException("展示字段只能是 " + String.join(" / ", FIELDS));
                }
                if (!fields.contains(f))
                {
                    fields.add(f);
                }
            }
        }
        int[] range = switch (template)
        {
            case PROGRAM_LIST -> new int[] { 1, 8 };
            case DEADLINE_CALENDAR -> new int[] { 1, 12 };
            case COMPARISON -> new int[] { 2, 4 };
            default -> new int[] { 0, 0 };
        };
        if (ids.size() < range[0] || ids.size() > range[1])
        {
            throw new ServiceException(range[1] == 0 ? "封面不需要选择项目" : "该模板须选择 " + range[0] + "–" + range[1] + " 个项目");
        }
        if (fields.isEmpty())
        {
            fields = COMPARISON.equals(template) ? FIELDS : List.of("ielts", "deadline");
        }
        return new Params(kicker, new ArrayList<>(ids), fields);
    }

    /**
     * 排版
     *
     * @param template 模板
     * @param p 参数
     * @param title 标题（内容标题）
     * @param programs 项目（ID → 项目）
     * @param requirements 最新申请要求（项目ID → 要求）
     * @param countryLabels 国家 / 地区代码 → 名称
     * @param today 今天（北京时间）
     * @param brand 品牌名
     * @param qrLink 二维码链接（内容的工具链接，可为 null）
     * @param qrCaption 二维码说明
     * @return 排版结果
     */
    static Built build(String template, Params p, String title, Map<Long, AbrProgram> programs, Map<Long, AbrRequirement> requirements,
            Map<String, String> countryLabels, LocalDate today, String brand, String qrLink, String qrCaption)
    {
        List<AbrProgram> list = new ArrayList<>();
        for (Long id : p.programIds())
        {
            AbrProgram program = programs.get(id);
            if (program == null)
            {
                throw new ServiceException("项目 " + id + " 不存在或已停用");
            }
            list.add(program);
        }
        List<Card.Section> sections = switch (template)
        {
            case PROGRAM_LIST -> programList(list, p.fields(), requirements, countryLabels, today);
            case DEADLINE_CALENDAR -> deadlineCalendar(list, requirements, today);
            case COMPARISON -> comparison(list, p.fields(), requirements, today);
            default -> List.of();
        };
        List<Map<String, Object>> citations = new ArrayList<>();
        LocalDate dataDate = null;
        for (AbrProgram program : list)
        {
            citations.add(citation("program", program.getProgramId()));
            AbrRequirement req = requirements.get(program.getProgramId());
            if (req != null)
            {
                citations.add(citation("requirement", req.getRequirementId()));
            }
            LocalDate verified = day(program.getVerifiedTime());
            if (verified != null && (dataDate == null || verified.isBefore(dataDate)))
            {
                dataDate = verified;
            }
        }
        String footnote = list.isEmpty() ? null
                : "数据来源：各校官网" + (dataDate == null ? "" : "，院校库核对于 " + dataDate + " 起") + "；超过 90 天未核对的显示“以官网为准”，申请前请以官网为准";
        Card card = new Card(brand, p.kicker(), title, sections, List.of(), qrLink, qrCaption, footnote);
        return new Built(card, citations, text(card), dataDate);
    }

    private static List<Card.Section> programList(List<AbrProgram> list, List<String> fields, Map<Long, AbrRequirement> reqs,
            Map<String, String> labels, LocalDate today)
    {
        List<Card.Section> sections = new ArrayList<>();
        for (AbrProgram program : list)
        {
            List<String> lines = new ArrayList<>();
            lines.add(program.getProgramName());
            boolean stale = AbrLookupTools.stale(program, today);
            for (String f : fields)
            {
                lines.add(fieldLabel(f) + "：" + (stale ? UNVERIFIED : value(f, program, reqs.get(program.getProgramId()), today)));
            }
            sections.add(new Card.Section(program.getSchoolName(), labels.getOrDefault(program.getCountry(), program.getCountry()), lines));
        }
        return sections;
    }

    private static List<Card.Section> deadlineCalendar(List<AbrProgram> list, Map<Long, AbrRequirement> reqs, LocalDate today)
    {
        record Row(LocalDate date, String line)
        {
        }
        List<Row> rows = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (AbrProgram program : list)
        {
            String name = program.getSchoolName() + " · " + program.getProgramName();
            AbrRequirement req = reqs.get(program.getProgramId());
            AbrLookupTools.Deadline d = AbrLookupTools.deadline(req == null ? null : req.getDeadlines(), today);
            if (AbrLookupTools.stale(program, today) || d == null)
            {
                other.add(name + "：截止日" + UNVERIFIED);
            }
            else if (d.nextDate() != null)
            {
                rows.add(new Row(d.nextDate(), d.nextDate().getMonthValue() + "-" + String.format("%02d", d.nextDate().getDayOfMonth()) + "  " + name
                        + (d.nextRound() == null ? "" : "（" + d.nextRound() + "）")));
            }
            else if (d.open())
            {
                other.add(name + "：滚动录取");
            }
        }
        rows.sort(Comparator.comparing(Row::date));
        // 分组标签只写月份（标签区较窄），年份放在右侧说明
        Map<LocalDate, List<String>> byMonth = new LinkedHashMap<>();
        for (Row r : rows)
        {
            byMonth.computeIfAbsent(r.date().withDayOfMonth(1), k -> new ArrayList<>()).add(r.line());
        }
        List<Card.Section> sections = new ArrayList<>();
        byMonth.forEach((month, lines) -> sections.add(
                new Card.Section(month.getMonthValue() + " 月", month.getYear() + " 年 · " + lines.size() + " 个项目", lines)));
        if (!other.isEmpty())
        {
            sections.add(new Card.Section("其他", null, other));
        }
        if (sections.isEmpty())
        {
            sections.add(new Card.Section("本季", null, List.of("所选项目本季均已截止")));
        }
        return sections;
    }

    private static List<Card.Section> comparison(List<AbrProgram> list, List<String> fields, Map<Long, AbrRequirement> reqs, LocalDate today)
    {
        List<Card.Section> sections = new ArrayList<>();
        sections.add(new Card.Section("项目", null, list.stream().map(p -> p.getSchoolName() + " · " + p.getProgramName()).toList()));
        // 同一学校有多个项目时，行首写“学校 · 项目”以免分不清
        boolean sameSchool = list.stream().map(AbrProgram::getSchoolName).distinct().count() < list.size();
        for (String f : fields)
        {
            List<String> lines = new ArrayList<>();
            for (AbrProgram program : list)
            {
                String v = AbrLookupTools.stale(program, today) ? UNVERIFIED : value(f, program, reqs.get(program.getProgramId()), today);
                lines.add((sameSchool ? program.getSchoolName() + " · " + program.getProgramName() : program.getSchoolName()) + "：" + v);
            }
            sections.add(new Card.Section(fieldLabel(f), null, lines));
        }
        return sections;
    }

    static String fieldLabel(String field)
    {
        return switch (field)
        {
            case "ielts" -> "雅思";
            case "deadline" -> "最近截止";
            case "tuition" -> "学费";
            default -> "学制";
        };
    }

    /**
     * 字段值（未录入时为“以官网为准”）
     */
    static String value(String field, AbrProgram program, AbrRequirement req, LocalDate today)
    {
        switch (field)
        {
            case "ielts":
                if (req == null || req.getIeltsOverall() == null)
                {
                    return UNVERIFIED;
                }
                return score(req.getIeltsOverall()) + (req.getIeltsMinSub() == null ? "" : "（单项 " + score(req.getIeltsMinSub()) + "）");
            case "deadline":
                AbrLookupTools.Deadline d = AbrLookupTools.deadline(req == null ? null : req.getDeadlines(), today);
                if (d == null)
                {
                    return UNVERIFIED;
                }
                if (d.nextDate() != null)
                {
                    return d.nextDate() + (d.nextRound() == null ? "" : "（" + d.nextRound() + "）");
                }
                return d.open() ? "滚动录取" : "本季已截止";
            case "tuition":
                if (program.getTuition() == null)
                {
                    return UNVERIFIED;
                }
                return StringUtils.defaultIfBlank(program.getCurrency(), "") + " " + new DecimalFormat("#,##0").format(program.getTuition()) + " / 年";
            default:
                return program.getDurationMonths() == null ? UNVERIFIED : program.getDurationMonths() + " 个月";
        }
    }

    private static String score(BigDecimal v)
    {
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static Map<String, Object> citation(String type, Long id)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("source", "card");
        return m;
    }

    private static LocalDate day(Date d)
    {
        return d == null ? null : d.toInstant().atZone(BEIJING).toLocalDate();
    }

    /**
     * 图上全部文字（合规闸门检查用）
     */
    static String text(Card card)
    {
        List<String> parts = new ArrayList<>();
        for (String s : new String[] { card.kicker(), card.title() })
        {
            if (StringUtils.isNotBlank(s))
            {
                parts.add(s);
            }
        }
        for (Card.Section s : card.sections())
        {
            parts.add(s.label() + (s.summary() == null ? "" : " " + s.summary()));
            parts.addAll(s.lines());
        }
        if (StringUtils.isNotBlank(card.qrCaption()) && StringUtils.isNotBlank(card.qrContent()))
        {
            parts.add(card.qrCaption());
        }
        if (StringUtils.isNotBlank(card.footnote()))
        {
            parts.add(card.footnote());
        }
        return String.join("\n", parts);
    }
}
