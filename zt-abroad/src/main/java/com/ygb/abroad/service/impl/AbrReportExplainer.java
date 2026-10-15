package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测评解读（report_explainer）的纯函数部分：模型输入、模板文字、AI 输出校验与合并（不访问数据库、不调用模型）
 *
 * 模型输入只含报告中学生可见的内容（档位、学校、逐项达标、补足动作），不含综合分、录取概率、参考线与院校层次；
 * AI 输出逐条校验：项目必须是输入中的项目，文字中的数字必须在输入中出现过，不得出现百分比或“概率”，
 * 长度超限或为空的条目一律改用模板文字。模板文字由报告结构化数据确定性生成，也是 AI 失败时的兜底。
 *
 * @author ygb
 */
final class AbrReportExplainer
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Agent 编码 */
    static final String AGENT_CODE = "report_explainer";

    /** 定位最大长度 */
    static final int MAX_HEADLINE = 80;

    /** 学校理由、短板说明最大长度 */
    static final int MAX_TEXT = 100;

    /** 文字中的数字（含小数） */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 学生侧不得出现的写法（录取概率、百分比） */
    private static final Pattern FORBIDDEN = Pattern.compile("[%％]|概率|百分之|几率|可能性为");

    /** 检查项的学生侧名称 */
    static final Map<String, String> ITEM_LABELS = Map.ofEntries(Map.entry("language_en", "英语成绩"), Map.entry("language_ja", "日语成绩"),
            Map.entry("language_ko", "韩语成绩"), Map.entry("language_de", "德语成绩"), Map.entry("tier_accepted", "本科院校背景"),
            Map.entry("gpa", "均分"), Map.entry("prerequisites", "先修课"), Map.entry("work_years", "工作年限"), Map.entry("cross_major", "专业背景"),
            Map.entry("aps", "APS 审核"), Map.entry("course_match", "课程匹配"));

    /** 动作的学生侧名称（雅思按次数单独处理） */
    static final Map<String, String> ACTION_LABELS = Map.of("ielts_plus_0_5", "雅思提高 0.5 分", "do_aps", "完成 APS 审核", "gre_plus_10",
            "GRE 提高 10 分", "add_lab_research", "增加一段科研经历", "add_related_internship_3m", "增加 3 个月相关实习");

    /** 档位的学生侧名称 */
    static final Map<String, String> TIER_LABELS = Map.of("reach", "冲刺", "match", "主攻", "safety", "保底", "needs_improvement", "补足后可申");

    private static final String IELTS_ACTION = "ielts_plus_0_5";

    private AbrReportExplainer()
    {
    }

    /**
     * 模型输入
     *
     * @param vo 报告（结构化部分）
     * @param degreeLabel 学段名称
     * @param fieldLabel 专业方向名称
     * @param templates 模板文字（定位模板句一并给模型参考）
     * @return 输入变量（report：报告 JSON；templateHeadline：模板定位）
     */
    static Map<String, Object> input(AppReportVo vo, String degreeLabel, String fieldLabel, Texts templates)
    {
        ObjectNode report = MAPPER.createObjectNode();
        report.put("degree", StringUtils.nvl(degreeLabel, ""));
        report.put("field", StringUtils.nvl(fieldLabel, ""));
        ObjectNode counts = report.putObject("counts");
        vo.tiers().forEach(t -> counts.put(TIER_LABELS.get(t.tier()), t.count()));
        counts.put("补足后可申", vo.needsImprovementCount());
        counts.put("待顾问定档", vo.unratedCount());
        ArrayNode programs = report.putArray("programs");
        for (Listed l : listed(vo))
        {
            ObjectNode p = programs.addObject();
            p.put("programId", l.program().programId());
            p.put("tier", TIER_LABELS.get(l.tier()));
            p.put("school", StringUtils.nvl(l.program().schoolName(), ""));
            p.put("program", StringUtils.nvl(l.program().programName(), ""));
            p.put("country", StringUtils.nvl(l.program().countryLabel(), ""));
            ArrayNode met = p.putArray("met");
            l.program().met().forEach(m -> met.add(ITEM_LABELS.getOrDefault(m, m)));
            ArrayNode missing = p.putArray("missing");
            for (AppReportVo.Gap g : l.program().missing())
            {
                missing.add(gapText(g));
            }
            String improve = l.program().upgrade().isEmpty() ? "" : actions(l.program().upgrade(), 1) + "后有望进入主攻档";
            p.put("howToImprove", l.program().fixes().isEmpty() ? improve : fixText(l.program().fixes()));
            p.put("infoUnverified", l.program().unverified());
        }
        ArrayNode shortcomings = report.putArray("shortcomings");
        for (int i = 0; i < vo.shortcomings().size(); i++)
        {
            ObjectNode n = shortcomings.addObject();
            n.put("index", i);
            n.put("meaning", templates.shortcomings().get(i));
        }
        ArrayNode skipped = report.putArray("countriesForConsultant");
        vo.skippedCountries().forEach(c -> skipped.add(c.label()));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("report", report);
        input.put("templateHeadline", templates.headline());
        return input;
    }

    /**
     * 模板文字（AI 失败或未返回时展示）
     *
     * @param vo 报告
     * @return 模板
     */
    static Texts templates(AppReportVo vo)
    {
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (Listed l : listed(vo))
        {
            reasons.putIfAbsent(l.program().programId(), reason(l.tier(), l.program()));
        }
        List<String> notes = new ArrayList<>();
        vo.shortcomings().forEach(s -> notes.add(shortcoming(s)));
        return new Texts(vo.headline(), reasons, notes);
    }

    /**
     * 单所学校的模板理由
     */
    static String reason(String tier, AppReportVo.Program p)
    {
        String met = p.met().isEmpty() ? "基本申请" : String.join("、", p.met().stream().map(m -> ITEM_LABELS.getOrDefault(m, m)).toList());
        String text;
        switch (tier)
        {
            case "reach":
                text = p.upgrade().isEmpty() ? "已满足" + met + "要求，竞争较激烈，适合作为冲刺"
                        : "已满足" + met + "要求；" + actions(p.upgrade(), 1) + "后有望进入主攻档";
                break;
            case "match":
                text = "已满足" + met + "要求，背景与项目匹配度较好，适合作为主攻";
                break;
            case "safety":
                text = "已满足" + met + "要求，背景高于项目一般要求，适合作为保底";
                break;
            default:
                List<String> gaps = p.missing().stream().map(AbrReportExplainer::gapText).toList();
                text = (gaps.isEmpty() ? "暂未满足申请要求" : String.join("；", gaps)) + "；" + fixText(p.fixes());
                break;
        }
        return p.unverified() ? text + "（项目信息待核实，以官网为准）" : text;
    }

    /**
     * 短板的模板说明
     */
    static String shortcoming(AppReportVo.Shortcoming s)
    {
        String months = s.months() == null || s.months() <= 0 ? "" : "（约需 " + s.months() + " 个月）";
        if (AbrAssessmentReport.KIND_UPGRADE.equals(s.kind()))
        {
            return actions(s.actions(), s.steps()) + "，可让 " + s.programCount() + " 个冲刺项目有望进入主攻档" + months
                    + (Boolean.FALSE.equals(s.inTime()) ? "，按你的入学时间可能来不及" : "");
        }
        return actions(s.actions(), s.steps()) + "，可让 " + s.programCount() + " 个项目达到申请要求" + months;
    }

    private static String gapText(AppReportVo.Gap g)
    {
        String label = ITEM_LABELS.getOrDefault(g.item(), g.item());
        if (g.required() != null && g.actual() != null)
        {
            return label + "要求 " + g.required() + "，你目前 " + g.actual();
        }
        return g.actual() != null ? label + "暂未达标（你目前 " + g.actual() + "）" : label + "暂未达标";
    }

    private static String fixText(List<AppReportVo.Fix> fixes)
    {
        if (fixes.isEmpty() || fixes.stream().anyMatch(f -> f.action() == null))
        {
            return "补足方式需顾问评估";
        }
        List<String> parts = new ArrayList<>();
        for (AppReportVo.Fix f : fixes)
        {
            parts.add(actions(List.of(f.action()), f.steps()));
        }
        return String.join("、", parts) + "后可申";
    }

    /**
     * 动作文字；雅思按次数合并为“雅思提高 1 分”
     */
    static String actions(List<String> codes, int steps)
    {
        List<String> parts = new ArrayList<>();
        for (String code : codes)
        {
            if (IELTS_ACTION.equals(code))
            {
                BigDecimal points = new BigDecimal("0.5").multiply(BigDecimal.valueOf(Math.max(steps, 1)));
                parts.add("雅思提高 " + points.stripTrailingZeros().toPlainString() + " 分");
            }
            else
            {
                parts.add(ACTION_LABELS.getOrDefault(code, code));
            }
        }
        return String.join("、", parts);
    }

    /**
     * 合并 AI 输出与模板：每一条单独校验，不合格的用模板
     *
     * @param templates 模板
     * @param ai 已通过网关 Schema 校验的 AI 输出（可为 null）
     * @param input 模型输入（校验数字是否出现过）
     * @return 校验后保留的 AI 文字（只含合格条目，用于保存）
     */
    static ObjectNode validate(Texts templates, JsonNode ai, Map<String, Object> input)
    {
        ObjectNode kept = MAPPER.createObjectNode();
        if (ai == null || !ai.isObject())
        {
            return kept;
        }
        Set<String> numbers = numbers(MAPPER.writeValueAsString(input));
        String headline = ai.path("headline").asString("");
        if (ok(headline, MAX_HEADLINE, numbers))
        {
            kept.put("headline", headline.trim());
        }
        ObjectNode programs = kept.putObject("programs");
        for (JsonNode p : ai.path("programs"))
        {
            long id = p.path("programId").asLong(-1);
            String reason = p.path("reason").asString("");
            if (templates.reasons().containsKey(id) && !programs.has(String.valueOf(id)) && ok(reason, MAX_TEXT, numbers))
            {
                programs.put(String.valueOf(id), reason.trim());
            }
        }
        ObjectNode notes = kept.putObject("shortcomings");
        for (JsonNode n : ai.path("shortcomings"))
        {
            int i = n.path("index").asInt(-1);
            String text = n.path("text").asString("");
            if (i >= 0 && i < templates.shortcomings().size() && !notes.has(String.valueOf(i)) && ok(text, MAX_TEXT, numbers))
            {
                notes.put(String.valueOf(i), text.trim());
            }
        }
        return kept;
    }

    /**
     * 组装解读：保存的 AI 文字优先，缺的条目用模板
     *
     * @param templates 模板
     * @param saved 保存的 AI 文字（validate 的结果；没有时为 null）
     * @param status 解读状态
     * @return 解读
     */
    static AppReportVo.Explanation explanation(Texts templates, JsonNode saved, String status)
    {
        JsonNode s = saved == null || AppReportVo.EXPLAIN_PENDING.equals(status) ? MAPPER.createObjectNode() : saved;
        boolean headlineAi = s.path("headline").isString();
        boolean any = headlineAi;
        List<AppReportVo.Reason> reasons = new ArrayList<>();
        for (Map.Entry<Long, String> e : templates.reasons().entrySet())
        {
            JsonNode t = s.path("programs").get(String.valueOf(e.getKey()));
            boolean ai = t != null && t.isString();
            any |= ai;
            reasons.add(new AppReportVo.Reason(e.getKey(), ai ? t.asString() : e.getValue(), ai));
        }
        List<AppReportVo.Note> notes = new ArrayList<>();
        for (int i = 0; i < templates.shortcomings().size(); i++)
        {
            JsonNode t = s.path("shortcomings").get(String.valueOf(i));
            boolean ai = t != null && t.isString();
            any |= ai;
            notes.add(new AppReportVo.Note(ai ? t.asString() : templates.shortcomings().get(i), ai));
        }
        return new AppReportVo.Explanation(status, headlineAi ? s.path("headline").asString() : templates.headline(), headlineAi, reasons, notes,
                any);
    }

    /**
     * 单条 AI 文字是否可用：非空、不超长、无百分比与概率、数字都在输入中出现过、不含类似 HTML 标签的写法
     */
    static boolean ok(String text, int max, Set<String> numbers)
    {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.length() > max || FORBIDDEN.matcher(t).find() || AbrAssessmentAnswers.containsTag(MAPPER.getNodeFactory().stringNode(t)))
        {
            return false;
        }
        Matcher m = NUMBER.matcher(t);
        while (m.find())
        {
            if (!numbers.contains(normalize(m.group())))
            {
                return false;
            }
        }
        return true;
    }

    private static Set<String> numbers(String text)
    {
        Set<String> set = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find())
        {
            set.add(normalize(m.group()));
        }
        return set;
    }

    private static String normalize(String n)
    {
        return new BigDecimal(n).stripTrailingZeros().toPlainString();
    }

    /**
     * 报告中出现的代表校（冲刺 / 主攻 / 保底 / 补足后可申，按页面顺序）
     */
    private static List<Listed> listed(AppReportVo vo)
    {
        List<Listed> list = new ArrayList<>();
        vo.tiers().forEach(t -> t.programs().forEach(p -> list.add(new Listed(t.tier(), p))));
        vo.needsImprovement().forEach(p -> list.add(new Listed("needs_improvement", p)));
        return list;
    }

    /**
     * 带档位的代表校
     */
    private record Listed(String tier, AppReportVo.Program program)
    {
    }

    /**
     * 模板文字
     *
     * @param headline 定位
     * @param reasons 项目ID → 理由（按页面顺序）
     * @param shortcomings 短板说明（与报告短板一一对应）
     */
    record Texts(String headline, Map<Long, String> reasons, List<String> shortcomings)
    {
    }
}
