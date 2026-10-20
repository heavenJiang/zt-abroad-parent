package com.ygb.abroad.growth.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.common.utils.StringUtils;

/**
 * 合规审查 compliance_checker（纯计算、确定性，不访问数据库、不调用大模型）
 * <ol>
 * <li>合规闸：词表与正则（广告违禁词、录取承诺、教育广告禁止情形、需核实的说法、平台导流、诱导分享、个人信息），按目标平台生效；</li>
 * <li>事实闸：正文中的截止日、雅思总分与小分、学费、学制、均分必须与引用的院校库记录一致，且该项目 90 天内核对过；
 * 提到的院校必须在引用依据中；引用的记录必须存在；引用的案例或 offer 须可公开（录取、核验 A / B、宣传授权未撤回）；</li>
 * <li>AI 生成标识：AI 生成的内容正文须带标识文字；需要在平台上声明的给出提示。</li>
 * </ol>
 * 任一项不合格即拦截；结果写明命中的文字、上下文与修改建议。
 *
 * @author ygb
 */
public final class ComplianceChecker
{
    /** 结果版本（规则逻辑变化时加一） */
    public static final int VERSION = 1;

    /** Agent 编码 */
    public static final String AGENT = "compliance_checker";

    /** 系统检查的类别（不在词表中配置） */
    public static final String CATEGORY_FACT = "fact";

    public static final String CATEGORY_CITATION = "citation";

    public static final String CATEGORY_AI_LABEL = "ai_label";

    /** 系统类别，词表规则不能使用 */
    public static final Set<String> SYSTEM_CATEGORIES = Set.of(CATEGORY_FACT, CATEGORY_CITATION, CATEGORY_AI_LABEL);

    /** 事实核对结果 */
    public static final String FACT_OK = "ok";

    public static final String FACT_MISMATCH = "mismatch";

    public static final String FACT_STALE = "stale";

    public static final String FACT_UNCITED = "uncited";

    /** 满足“需核实的说法”的引用类型（案例、offer、外部网页） */
    static final Set<String> STAT_CITATIONS = Set.of("case", "offer", "url");

    /** 需要在平台上另行声明 AI 生成的平台 */
    static final Set<String> AI_DECLARE_PLATFORMS = Set.of("xiaohongshu", "douyin", "channels", "zhihu");

    /** 各类别的修改建议 */
    static final Map<String, String> SUGGESTIONS = Map.ofEntries(
            Map.entry("ad_banned", "删去绝对化用语，改为可以证实的具体描述"),
            Map.entry("admission_promise", "删去录取承诺，改为不保证结果的表述（如“按你的背景可以考虑”）"),
            Map.entry("education_ad", "删去暗示内部渠道、或以招生官 / 专业人士 / 学员名义作推荐的说法"),
            Map.entry("fact_claim", "在引用依据中补充来源（案例、offer 或官方网页），或删去这个说法"),
            Map.entry("diversion", "这个平台不能引导到站外，改用私信口令或平台自带的留资组件"),
            Map.entry("inducement", "删去以奖励诱导转发、关注、集赞的说法"),
            Map.entry("privacy", "删去手机号、证件号、邮箱等个人信息"),
            Map.entry(CATEGORY_FACT, "数字只能取自引用的院校库记录；项目超过 90 天未核对时写“以官网为准”"),
            Map.entry(CATEGORY_CITATION, "补充或修正引用依据"),
            Map.entry(CATEGORY_AI_LABEL, "在正文末尾加上 AI 生成标识"));

    /** 提到的院校未引用时的修改建议 */
    static final String SCHOOL_SUGGESTION = "在引用依据中加上提到的院校或它的项目，或删去校名";

    private static final Pattern CLAUSE = Pattern.compile("[。；;！!？?\\n]+");

    private static final Pattern PART = Pattern.compile("[，、]|,(?!\\d{3})");

    private static final Pattern DATE_FULL = Pattern.compile("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?");

    private static final Pattern DATE_MD = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]");

    private static final Pattern DATE_CONTEXT = Pattern.compile("截止|截至|deadline|ddl|轮|开放|递交|网申|开始申请", Pattern.CASE_INSENSITIVE);

    private static final Pattern IELTS = Pattern.compile("(?:雅思|ielts)\\s*(?:总分|总成绩|成绩|要求|需要|达到)?\\s*[:：]?\\s*(\\d(?:\\.\\d)?)(?![\\d.])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IELTS_SUB = Pattern.compile("(?:小分|单项)\\s*(?:均)?\\s*(?:不低于|不少于|最低|要求|需要)?\\s*[:：]?\\s*(\\d(?:\\.\\d)?)(?![\\d.])");

    private static final Pattern MONEY = Pattern.compile("(HK\\$|S\\$|A\\$|US\\$|[£$€¥])?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(万)?\\s*"
            + "(英镑|澳元|澳币|新币|新加坡元|港币|港元|美元|欧元|日元|韩元|人民币|GBP|AUD|SGD|HKD|USD|EUR|JPY|KRW|CNY)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(个月|年)");

    private static final Pattern GPA_CONTEXT = Pattern.compile("均分|平均分|gpa", Pattern.CASE_INSENSITIVE);

    private static final Pattern GPA = Pattern.compile("(?<![\\d.])(\\d{2,3}(?:\\.\\d)?)(?![\\d.])");

    private static final Map<String, String> CURRENCIES = Map.ofEntries(
            Map.entry("£", "GBP"), Map.entry("英镑", "GBP"), Map.entry("GBP", "GBP"),
            Map.entry("A$", "AUD"), Map.entry("澳元", "AUD"), Map.entry("澳币", "AUD"), Map.entry("AUD", "AUD"),
            Map.entry("S$", "SGD"), Map.entry("新币", "SGD"), Map.entry("新加坡元", "SGD"), Map.entry("SGD", "SGD"),
            Map.entry("HK$", "HKD"), Map.entry("港币", "HKD"), Map.entry("港元", "HKD"), Map.entry("HKD", "HKD"),
            Map.entry("US$", "USD"), Map.entry("$", "USD"), Map.entry("美元", "USD"), Map.entry("USD", "USD"),
            Map.entry("€", "EUR"), Map.entry("欧元", "EUR"), Map.entry("EUR", "EUR"),
            Map.entry("日元", "JPY"), Map.entry("JPY", "JPY"), Map.entry("韩元", "KRW"), Map.entry("KRW", "KRW"),
            Map.entry("人民币", "CNY"), Map.entry("CNY", "CNY"));

    private static final BigDecimal WAN = new BigDecimal("10000");

    /**
     * 待检查的内容或消息
     *
     * @param platform 目标平台（字典 abr_platform；消息可为渠道编码；null 只用全平台规则）
     * @param wordFields 检查词表的字段（字段名 → 文字，如标题、正文、话题、图卡文字）
     * @param factFields 做事实核对的字段（图卡数字由模板取自院校库，不在其中）
     * @param aiGenerated 是否 AI 生成
     * @param aiLabel AI 生成标识文字（为空不检查）
     * @param labelField 须带标识的字段
     * @param caseContent 是否案例类内容（须引用案例或 offer 记录）
     */
    public record Input(String platform, Map<String, String> wordFields, Map<String, String> factFields, boolean aiGenerated, String aiLabel,
            String labelField, boolean caseContent)
    {
    }

    /**
     * 检查结果
     *
     * @param passed 是否通过
     * @param blocked 拦截项
     * @param cited 已有引用依据而放行的“需核实的说法”
     * @param facts 事实核对明细
     * @param notices 提示（不影响结果）
     * @param ruleCount 参与检查的规则数
     */
    public record Result(boolean passed, List<Map<String, Object>> blocked, List<Map<String, Object>> cited, List<Map<String, Object>> facts,
            List<String> notices, int ruleCount)
    {
        /**
         * 写入闸门明细的 JSON 对象
         *
         * @return 对象
         */
        public Map<String, Object> toMap()
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("checker", AGENT);
            m.put("version", VERSION);
            m.put("passed", passed);
            m.put("blocked", blocked);
            m.put("cited", cited);
            m.put("facts", facts);
            m.put("notices", notices);
            m.put("ruleCount", ruleCount);
            return m;
        }
    }

    private ComplianceChecker()
    {
    }

    /**
     * 检查
     *
     * @param input 内容
     * @param rules 词表
     * @param facts 院校库依据
     * @return 结果
     */
    public static Result check(Input input, ComplianceRuleSet rules, ComplianceFacts facts)
    {
        List<Map<String, Object>> blocked = new ArrayList<>();
        List<Map<String, Object>> cited = new ArrayList<>();
        List<Map<String, Object>> factList = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        boolean hasStat = facts.citationTypes().stream().anyMatch(STAT_CITATIONS::contains);
        for (Map.Entry<String, String> f : input.wordFields().entrySet())
        {
            for (ComplianceRuleSet.Hit h : rules.scan(f.getKey(), f.getValue(), input.platform()))
            {
                Map<String, Object> item = hit(h);
                if (ComplianceRuleSet.ACTION_CITE.equals(h.rule().action()) && hasStat)
                {
                    cited.add(item);
                }
                else
                {
                    blocked.add(item);
                }
            }
        }
        for (String p : facts.citationProblems())
        {
            blocked.add(system(CATEGORY_CITATION, null, p, null));
        }
        if (input.caseContent() && facts.citationTypes().stream().noneMatch(t -> "case".equals(t) || "offer".equals(t)))
        {
            blocked.add(system(CATEGORY_CITATION, null, "案例类内容须引用案例或 offer 记录（真实、已授权、已脱敏）", null));
        }
        for (Map.Entry<String, String> f : input.factFields().entrySet())
        {
            factList.addAll(facts(f.getKey(), f.getValue(), facts));
        }
        for (Map<String, Object> fact : factList)
        {
            if (!FACT_OK.equals(fact.get("status")))
            {
                Map<String, Object> item = system(CATEGORY_FACT, (String) fact.get("field"), (String) fact.get("detail"), (String) fact.get("text"));
                if ("school".equals(fact.get("kind")))
                {
                    item.put("suggestion", SCHOOL_SUGGESTION);
                }
                blocked.add(item);
            }
        }
        if (input.aiGenerated() && StringUtils.isNotBlank(input.aiLabel()))
        {
            String text = input.wordFields().get(input.labelField());
            if (text == null || !text.contains(input.aiLabel()))
            {
                blocked.add(system(CATEGORY_AI_LABEL, input.labelField(), "AI 生成的内容须带标识“" + input.aiLabel() + "”", null));
            }
            if (input.platform() != null && AI_DECLARE_PLATFORMS.contains(input.platform()))
            {
                notices.add("发布时在平台上打开“AI 生成内容”声明");
            }
        }
        return new Result(blocked.isEmpty(), blocked, cited, factList, notices, rules.size());
    }

    private static Map<String, Object> hit(ComplianceRuleSet.Hit h)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", h.rule().category());
        m.put("ruleId", h.rule().ruleId());
        m.put("rule", h.rule().pattern());
        m.put("field", h.field());
        m.put("matched", h.matched());
        m.put("snippet", h.snippet());
        m.put("suggestion", SUGGESTIONS.getOrDefault(h.rule().category(), ""));
        return m;
    }

    private static Map<String, Object> system(String category, String field, String detail, String matched)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("field", field);
        m.put("matched", matched);
        m.put("detail", detail);
        m.put("suggestion", SUGGESTIONS.get(category));
        return m;
    }

    /**
     * 事实核对：逐句找出截止日、雅思、学费、学制、均分与院校名，与引用的院校库记录比对
     *
     * @param field 字段
     * @param text 文字
     * @param facts 依据
     * @return 明细
     */
    static List<Map<String, Object>> facts(String field, String text, ComplianceFacts facts)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        if (StringUtils.isBlank(text))
        {
            return out;
        }
        for (String clause : CLAUSE.split(text))
        {
            if (DATE_CONTEXT.matcher(clause).find())
            {
                dates(field, clause, facts, out);
            }
            ielts(field, clause, facts, out);
            // 学费、学制、均分按逗号再分（“学费 3.5 万英镑，生活费 1.2 万英镑”只核对前半句）；数字中的千分位逗号不分
            for (String part : PART.split(clause))
            {
                if (part.contains("学费"))
                {
                    tuition(field, part, facts, out);
                }
                if (part.contains("学制"))
                {
                    duration(field, part, facts, out);
                }
                if (GPA_CONTEXT.matcher(part).find())
                {
                    gpa(field, part, facts, out);
                }
            }
        }
        schools(field, text, facts, out);
        return out;
    }

    private static void dates(String field, String clause, ComplianceFacts facts, List<Map<String, Object>> out)
    {
        StringBuilder masked = new StringBuilder(clause);
        Matcher m = DATE_FULL.matcher(clause);
        while (m.find())
        {
            LocalDate d = date(m.group(1), m.group(2), m.group(3));
            if (d != null)
            {
                out.add(compare(field, "deadline", m.group().trim(), facts, p -> p.dates().contains(d), "截止日"));
            }
            for (int i = m.start(); i < m.end(); i++)
            {
                masked.setCharAt(i, ' ');
            }
        }
        m = DATE_MD.matcher(masked);
        while (m.find())
        {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31)
            {
                out.add(compare(field, "deadline", m.group(), facts,
                        p -> p.dates().stream().anyMatch(x -> x.getMonthValue() == month && x.getDayOfMonth() == day), "截止日"));
            }
        }
    }

    private static LocalDate date(String y, String mo, String d)
    {
        try
        {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(mo), Integer.parseInt(d));
        }
        catch (DateTimeException e)
        {
            return null;
        }
    }

    private static void ielts(String field, String clause, ComplianceFacts facts, List<Map<String, Object>> out)
    {
        Matcher m = IELTS.matcher(clause);
        while (m.find())
        {
            BigDecimal v = new BigDecimal(m.group(1));
            out.add(contains(facts.caseScores(), v) ? caseScore(field, "ielts", m.group())
                    : compare(field, "ielts", m.group(), facts, p -> contains(p.ieltsOverall(), v), "雅思总分要求"));
        }
        m = IELTS_SUB.matcher(clause);
        while (m.find())
        {
            BigDecimal v = new BigDecimal(m.group(1));
            out.add(compare(field, "ielts_sub", m.group(), facts, p -> contains(p.ieltsSub(), v), "雅思小分要求"));
        }
    }

    private static void tuition(String field, String clause, ComplianceFacts facts, List<Map<String, Object>> out)
    {
        Matcher m = MONEY.matcher(clause);
        while (m.find())
        {
            boolean wan = m.group(3) != null;
            String sign = m.group(1);
            String word = m.group(4);
            if (sign == null && word == null && !wan)
            {
                continue;
            }
            String currency = word != null ? CURRENCIES.get(word.toUpperCase(Locale.ROOT).matches("[A-Z]+") ? word.toUpperCase(Locale.ROOT) : word)
                    : sign == null || "¥".equals(sign) ? null : CURRENCIES.get(sign.toUpperCase(Locale.ROOT));
            BigDecimal amount = new BigDecimal(m.group(2).replace(",", ""));
            BigDecimal value = wan ? amount.multiply(WAN) : amount;
            BigDecimal tolerance = wan ? new BigDecimal("500") : null;
            out.add(compare(field, "tuition", m.group().trim(), facts, p -> p.tuition() != null
                    && (currency == null || p.currency().isEmpty() || currency.equals(p.currency()))
                    && value.subtract(p.tuition()).abs().compareTo(tolerance != null ? tolerance
                            : p.tuition().multiply(new BigDecimal("0.005")).max(BigDecimal.ONE)) <= 0,
                    "学费"));
        }
    }

    private static void duration(String field, String clause, ComplianceFacts facts, List<Map<String, Object>> out)
    {
        Matcher m = DURATION.matcher(clause);
        while (m.find())
        {
            BigDecimal n = new BigDecimal(m.group(1));
            int months = "年".equals(m.group(2)) ? n.multiply(new BigDecimal("12")).setScale(0, RoundingMode.HALF_UP).intValue() : n.intValue();
            out.add(compare(field, "duration", m.group(), facts, p -> p.durationMonths() != null && p.durationMonths() == months, "学制"));
        }
    }

    private static void gpa(String field, String clause, ComplianceFacts facts, List<Map<String, Object>> out)
    {
        Matcher m = GPA.matcher(clause);
        while (m.find())
        {
            BigDecimal v = new BigDecimal(m.group(1));
            if (v.compareTo(new BigDecimal("60")) >= 0 && v.compareTo(new BigDecimal("100")) <= 0)
            {
                out.add(contains(facts.caseScores(), v) ? caseScore(field, "gpa", m.group())
                        : compare(field, "gpa", m.group(), facts, p -> contains(p.gpa(), v), "均分要求"));
            }
        }
    }

    private static boolean contains(Set<BigDecimal> set, BigDecimal v)
    {
        return set.stream().anyMatch(x -> x.compareTo(v) == 0);
    }

    /**
     * 与引用的案例中的学生成绩一致（案例内容写学生的均分区间、雅思，不是项目要求）
     */
    private static Map<String, Object> caseScore(String field, String kind, String text)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("field", field);
        m.put("text", text);
        m.put("status", FACT_OK);
        m.put("detail", "与引用的案例中的学生成绩一致");
        return m;
    }

    /**
     * 与引用的项目比对：未核对过期的项目里有 → ok；只在过期项目里有 → stale；都没有 → mismatch；没有引用项目 → uncited
     */
    private static Map<String, Object> compare(String field, String kind, String text, ComplianceFacts facts,
            Predicate<ComplianceFacts.ProgramFact> match, String name)
    {
        String status;
        String detail;
        List<String> labels = new ArrayList<>();
        if (facts.programs().isEmpty())
        {
            status = FACT_UNCITED;
            detail = "“" + text + "”：" + name + "须引用院校库中的项目或申请要求";
        }
        else
        {
            facts.programs().stream().filter(p -> !p.stale()).filter(match).forEach(p -> labels.add(p.label()));
            if (!labels.isEmpty())
            {
                status = FACT_OK;
                detail = "与 " + String.join("、", labels) + " 一致";
            }
            else if (facts.programs().stream().filter(ComplianceFacts.ProgramFact::stale).anyMatch(match))
            {
                status = FACT_STALE;
                detail = "“" + text + "”：引用的项目超过 " + ComplianceFacts.STALE_DAYS + " 天未核对，" + name + "须写“以官网为准”";
            }
            else
            {
                status = FACT_MISMATCH;
                detail = "“" + text + "”与引用的院校库记录中的" + name + "不一致";
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("field", field);
        m.put("text", text);
        m.put("status", status);
        m.put("detail", detail);
        return m;
    }

    /**
     * 提到的院校：按校名从长到短识别（长名先占位，避免“伦敦大学学院”同时算作“伦敦大学”），须在引用依据中
     */
    private static void schools(String field, String text, ComplianceFacts facts, List<Map<String, Object>> out)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean[] used = new boolean[text.length()];
        Set<Long> reported = new LinkedHashSet<>();
        List<Map.Entry<String, Long>> names = new ArrayList<>(facts.schoolNames().entrySet());
        names.sort(Comparator.comparingInt((Map.Entry<String, Long> e) -> e.getKey().length()).reversed());
        for (Map.Entry<String, Long> e : names)
        {
            String name = e.getKey().toLowerCase(Locale.ROOT);
            int from = 0;
            int at;
            while ((at = lower.indexOf(name, from)) >= 0)
            {
                from = at + name.length();
                if (free(used, at, from))
                {
                    for (int i = at; i < from; i++)
                    {
                        used[i] = true;
                    }
                    if (reported.add(e.getValue()))
                    {
                        boolean ok = facts.citedSchools().contains(e.getValue());
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("kind", "school");
                        m.put("field", field);
                        m.put("text", text.substring(at, from));
                        m.put("status", ok ? FACT_OK : FACT_UNCITED);
                        m.put("detail", ok ? "已引用" : "提到了“" + text.substring(at, from) + "”，但引用依据中没有这所院校或它的项目");
                        out.add(m);
                    }
                }
            }
        }
    }

    private static boolean free(boolean[] used, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            if (used[i])
            {
                return false;
            }
        }
        return true;
    }
}
