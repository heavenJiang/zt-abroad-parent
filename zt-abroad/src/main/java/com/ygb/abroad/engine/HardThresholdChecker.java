package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 硬门槛筛选：背景档案 × 项目要求 → 通过 / 补足后可申 / 不在考虑范围（设计文档「智能选校测评算法」§3，引擎配置 hard_thresholds 与 country_rules）
 *
 * 取值顺序：项目库（abr_requirement）该项有值时只用项目库；该项缺值时用配置中按难度档的兜底值，并在结果上打 requirement_unverified（要求待核实）。
 * 学生信息不全（未考语言、未填均分等）的项记为 unchecked，不据此筛掉项目，只给出提示码。
 * 纯函数：同一输入得到同一输出。
 *
 * @author ygb
 */
public final class HardThresholdChecker
{
    /** 结果：全部通过 */
    public static final String PASS = "pass";

    /** 结果：不在学生考虑范围（如学生只考虑英语授课，项目为日语授课） */
    public static final String EXCLUDED = "excluded";

    /** 检查项：达标 */
    public static final String MET = "met";

    /** 检查项：不达标 */
    public static final String NOT_MET = "not_met";

    /** 检查项：语言差距在课前语言范围内，可读语言班（不判不达标） */
    public static final String PRE_SESSIONAL = "pre_sessional";

    /** 检查项：信息不全，未判定（不筛掉项目） */
    public static final String UNCHECKED = "unchecked";

    /** 检查项：风险项，需顾问核实（不筛掉项目） */
    public static final String RISK = "risk";

    /** 检查项：不在学生考虑范围 */
    public static final String OUT_OF_SCOPE = "out_of_scope";

    /** 要求来源：项目库 */
    public static final String SOURCE_PROGRAM = "program";

    /** 要求来源：配置兜底值 */
    public static final String SOURCE_DEFAULT = "default";

    /** 要求来源：没有可用要求 */
    public static final String SOURCE_NONE = "none";

    /** 学生英语考试 → 项目 otherLanguage 中的考试键 */
    private static final Map<String, String> ENGLISH_TEST_KEYS = Map.of("toefl_ibt", "toefl", "pte", "pte", "duolingo", "duolingo");

    /** 学生英语考试中文名 */
    private static final Map<String, String> ENGLISH_TEST_NAMES = Map.of("toefl", "托福", "pte", "PTE", "duolingo", "多邻国");

    /** 德语考试键 */
    private static final List<String> GERMAN_TESTS = List.of("testdaf", "dsh", "goethe");

    private static final Pattern JLPT = Pattern.compile("N\\s*([1-5])", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIGIT = Pattern.compile("([1-6])");

    private HardThresholdChecker()
    {
    }

    /**
     * 检查一个项目
     *
     * @param p 背景档案
     * @param program 项目事实
     * @param config 该项目所在国家的引擎配置（根节点，需含 hard_thresholds，country_rules 可缺省）
     * @return 检查结果
     */
    public static Result check(BackgroundProfile p, ProgramFacts program, JsonNode config)
    {
        JsonNode ht = config.path("hard_thresholds");
        JsonNode onFail = ht.get("on_fail");
        if (onFail == null || !onFail.isString() || onFail.asString().isBlank())
        {
            throw new IllegalArgumentException("引擎配置缺少 hard_thresholds.on_fail");
        }
        Check c = new Check(p, program, ht, config.path("country_rules"));
        c.language();
        c.gpa();
        c.prerequisites();
        c.workYears();
        c.crossMajor();
        c.aps();
        c.courseMatch();

        List<String> missing = new ArrayList<>();
        boolean excluded = false;
        for (Item item : c.items)
        {
            if (NOT_MET.equals(item.outcome()))
            {
                missing.add(item.key());
            }
            excluded |= OUT_OF_SCOPE.equals(item.outcome());
        }
        String status = excluded ? EXCLUDED : missing.isEmpty() ? PASS : onFail.asString();
        return new Result(status, Collections.unmodifiableList(c.items), Collections.unmodifiableList(missing),
                List.copyOf(c.flags));
    }

    /**
     * 批量筛选：按项目所在国家取配置，分为通过、补足后可申、不在考虑范围三组（保持传入顺序）
     *
     * @param p 背景档案
     * @param programs 候选项目
     * @param configsByCountry 国家 / 地区代码 → 引擎配置根节点
     * @return 分组结果
     */
    public static Screening screen(BackgroundProfile p, List<ProgramFacts> programs, Map<String, JsonNode> configsByCountry)
    {
        List<Screened> passed = new ArrayList<>();
        List<Screened> needsImprovement = new ArrayList<>();
        List<Screened> excluded = new ArrayList<>();
        for (ProgramFacts program : programs)
        {
            JsonNode config = configsByCountry.get(program.country());
            if (config == null)
            {
                throw new IllegalArgumentException("缺少国家 / 地区 " + program.country() + " 的引擎配置");
            }
            Result r = check(p, program, config);
            Screened s = new Screened(program, r);
            if (PASS.equals(r.status()))
            {
                passed.add(s);
            }
            else if (EXCLUDED.equals(r.status()))
            {
                excluded.add(s);
            }
            else
            {
                needsImprovement.add(s);
            }
        }
        return new Screening(List.copyOf(passed), List.copyOf(needsImprovement), List.copyOf(excluded));
    }

    /**
     * 单个项目的一次检查过程
     */
    private static final class Check
    {
        private final BackgroundProfile p;

        private final ProgramFacts program;

        private final ProgramFacts.Requirement req;

        private final JsonNode ht;

        private final JsonNode rules;

        private final String difficulty;

        private final List<Item> items = new ArrayList<>();

        private final Set<String> flags = new LinkedHashSet<>();

        Check(BackgroundProfile p, ProgramFacts program, JsonNode ht, JsonNode rules)
        {
            this.p = p;
            this.program = program;
            this.req = program.requirement();
            this.ht = ht;
            this.rules = rules;
            this.difficulty = program.difficulty() == null || program.difficulty().isBlank() ? null : program.difficulty();
        }

        private void add(String key, String outcome, String required, String actual, String source)
        {
            items.add(new Item(key, outcome, required, actual, source));
        }

        /**
         * 取按难度档的兜底节点；未定难度档或缺该档时返回 null
         */
        private JsonNode byDifficulty(JsonNode table)
        {
            if (difficulty == null)
            {
                return null;
            }
            JsonNode n = table.get(difficulty);
            return n == null || n.isNull() ? null : n;
        }

        private void usedDefault()
        {
            if (ht.path("unverified_flag_when_default_used").asBoolean(true))
            {
                flags.add("requirement_unverified");
            }
        }

        /** 本科层次：未知按双非计算（与子分口径一致），并提示待核定 */
        private String tier()
        {
            String tier = p.getUndergradTier();
            if (tier == null || "unknown".equals(tier))
            {
                flags.add("tier_unknown");
                return "non_key";
            }
            return tier;
        }

        // ---------------------------------------------------------------- 语言

        void language()
        {
            String lang = program.teachingLanguage() == null || program.teachingLanguage().isBlank() ? "en" : program.teachingLanguage();
            switch (lang)
            {
                case "en":
                    english();
                    break;
                case "ja":
                    leveled("language_ja", "jlpt", jlptOf(p.getJlptLevel()), p.getJlptLevel(), "none_jp_course");
                    break;
                case "ko":
                    leveled("language_ko", "topik", topikOf(p.getTopikLevel()), p.getTopikLevel(), "none_kr_course");
                    break;
                case "de":
                    german();
                    break;
                default:
                    // 中文或其他授课语言：不设语言门槛
                    break;
            }
        }

        private void english()
        {
            String key = "language_en";
            String test = ENGLISH_TEST_KEYS.get(p.getEnglishTest());
            BigDecimal reqTotal = null;
            BigDecimal reqSub = null;
            String scale = null;
            String source = SOURCE_NONE;
            JsonNode same = req == null || req.otherLanguage() == null || test == null ? null : req.otherLanguage().get(test);
            if (same != null && same.path("overall").isNumber())
            {
                // 项目写明了学生所考考试的要求：直接比原始分，比换算更准
                reqTotal = same.path("overall").decimalValue();
                reqSub = same.path("minSub").isNumber() ? same.path("minSub").decimalValue() : null;
                scale = test;
                source = SOURCE_PROGRAM;
            }
            else if (req != null && (req.ieltsOverall() != null || req.ieltsMinSub() != null))
            {
                reqTotal = req.ieltsOverall();
                reqSub = req.ieltsMinSub();
                scale = "ielts";
                source = SOURCE_PROGRAM;
            }
            else
            {
                JsonNode d = byDifficulty(ht.path("language").path("defaults_by_difficulty"));
                if (d != null)
                {
                    reqTotal = d.path("overall").isNumber() ? d.path("overall").decimalValue() : null;
                    reqSub = d.path("min_sub").isNumber() ? d.path("min_sub").decimalValue() : null;
                    scale = "ielts";
                    source = SOURCE_DEFAULT;
                    usedDefault();
                }
            }
            if (scale == null)
            {
                flags.add("requirement_missing");
                add(key, UNCHECKED, null, englishActual(null), SOURCE_NONE);
                return;
            }
            String required = scoreText(scale, reqTotal, reqSub);
            String status = p.getEnglishStatus();
            if (status == null || "none".equals(status))
            {
                // 问卷约定：没考也估不出分时不按语言过滤，标「语言待考」
                flags.add("language_pending");
                add(key, UNCHECKED, required, "未考", source);
                return;
            }
            if (Boolean.TRUE.equals(p.getEnglishEstimated()) || "estimated".equals(status))
            {
                flags.add("language_estimated");
            }
            boolean ielts = "ielts".equals(scale);
            BigDecimal total = ielts ? p.getIeltsEquivTotal() : p.getEnglishTotal();
            BigDecimal sub = ielts ? p.getIeltsEquivMinSub() : p.getEnglishMinSub();
            if (total == null)
            {
                flags.add("language_unconverted");
                add(key, UNCHECKED, required, englishActual(scale), source);
                return;
            }
            BigDecimal gap = BigDecimal.ZERO;
            if (reqTotal != null)
            {
                gap = gap.max(reqTotal.subtract(total));
            }
            if (reqSub != null)
            {
                if (sub == null)
                {
                    flags.add("sub_score_unchecked");
                }
                else
                {
                    gap = gap.max(reqSub.subtract(sub));
                }
            }
            String actual = englishActual(scale);
            if (gap.signum() <= 0)
            {
                add(key, MET, required, actual, source);
                return;
            }
            // 课前语言：项目明确不接受时不适用；可接受差距取国家规则；项目未注明时按国家规则并标待核实
            String accept = req == null ? null : req.acceptPreSessional();
            JsonNode max = rules.path("pre_sessional").path("max_overall_gap");
            if (ielts && !"0".equals(accept) && max.isNumber() && gap.compareTo(max.decimalValue()) <= 0)
            {
                flags.add("pre_sessional");
                if (!"1".equals(accept))
                {
                    flags.add("pre_sessional_unverified");
                }
                add(key, PRE_SESSIONAL, required, actual, source);
                return;
            }
            add(key, NOT_MET, required, actual, source);
        }

        private String englishActual(String scale)
        {
            if (!"ielts".equals(scale) && scale != null)
            {
                return scoreText(scale, p.getEnglishTotal(), p.getEnglishMinSub());
            }
            return p.getIeltsEquivTotal() == null ? "无雅思等效分" : scoreText("ielts", p.getIeltsEquivTotal(), p.getIeltsEquivMinSub());
        }

        /**
         * 分级语言考试（JLPT、TOPIK）：统一换成“数值越大越好”比较
         */
        private void leveled(String key, String test, Integer studentLevel, String raw, String noCertValue)
        {
            if ("english_only".equals(raw))
            {
                add(key, OUT_OF_SCOPE, null, "只考虑英语授课", SOURCE_NONE);
                return;
            }
            Integer required = null;
            String source = SOURCE_NONE;
            JsonNode own = req == null || req.otherLanguage() == null ? null : req.otherLanguage().path(test).get("level");
            if (own != null && own.isValueNode())
            {
                required = "jlpt".equals(test) ? jlptOf(own.asString()) : topikOf(own.asString());
                source = required == null ? SOURCE_NONE : SOURCE_PROGRAM;
            }
            if (required == null)
            {
                String lang = "jlpt".equals(test) ? "ja" : "ko";
                JsonNode d = byDifficulty(rules.path("teaching_language_thresholds").path(lang).path(test + "_min_level_by_difficulty"));
                if (d != null)
                {
                    required = "jlpt".equals(test) ? jlptOf(d.asString()) : topikOf(d.asString());
                    source = required == null ? SOURCE_NONE : SOURCE_DEFAULT;
                    if (required != null)
                    {
                        usedDefault();
                    }
                }
            }
            String requiredText = required == null ? null : levelText(test, required);
            if (required == null)
            {
                flags.add("requirement_missing");
            }
            if (raw == null)
            {
                flags.add("language_pending");
                add(key, UNCHECKED, requiredText, "未填写", source);
                return;
            }
            String actual = noCertValue.equals(raw) ? "未考" : levelText(test, studentLevel);
            if (required == null)
            {
                add(key, UNCHECKED, null, actual, SOURCE_NONE);
                return;
            }
            add(key, studentLevel != null && studentLevel >= required ? MET : NOT_MET, requiredText, actual, source);
        }

        private void german()
        {
            List<String> tests = new ArrayList<>();
            if (req != null && req.otherLanguage() != null)
            {
                for (String t : GERMAN_TESTS)
                {
                    if (req.otherLanguage().has(t))
                    {
                        JsonNode level = req.otherLanguage().path(t).path("level");
                        tests.add(t.toUpperCase() + (level.isValueNode() && !level.asString().isBlank() ? " " + level.asString() : ""));
                    }
                }
            }
            // 问卷 v1 不采集德语水平：只列出要求，交给顾问核实
            flags.add("german_to_verify");
            add("language_de", UNCHECKED, tests.isEmpty() ? null : String.join("、", tests), "问卷未采集",
                    tests.isEmpty() ? SOURCE_NONE : SOURCE_PROGRAM);
        }

        // ---------------------------------------------------------------- 院校层次与均分

        void gpa()
        {
            String tier = tier();
            Set<String> notAccepted = new HashSet<>();
            String naSource = SOURCE_NONE;
            if (req != null && req.tiersNotAccepted() != null && !req.tiersNotAccepted().isEmpty())
            {
                notAccepted.addAll(req.tiersNotAccepted());
                naSource = SOURCE_PROGRAM;
            }
            else
            {
                JsonNode d = byDifficulty(ht.path("min_gpa_by_undergrad_tier").path("undergrad_tiers_not_accepted_by_difficulty"));
                if (d != null && d.isArray() && !d.isEmpty())
                {
                    d.forEach(t -> notAccepted.add(t.asString()));
                    naSource = SOURCE_DEFAULT;
                    usedDefault();
                }
            }
            if (!notAccepted.isEmpty())
            {
                add("tier_accepted", notAccepted.contains(tier) ? NOT_MET : MET, "不接收：" + String.join("、", sorted(notAccepted)), tier,
                        naSource);
            }

            BigDecimal min = null;
            String source = SOURCE_NONE;
            if (req != null && req.minGpa() != null && req.minGpa().get(tier) != null)
            {
                min = req.minGpa().get(tier);
                source = SOURCE_PROGRAM;
            }
            else
            {
                JsonNode d = byDifficulty(ht.path("min_gpa_by_undergrad_tier").path("defaults_by_difficulty"));
                if (d != null && d.path(tier).isNumber())
                {
                    min = d.path(tier).decimalValue();
                    source = SOURCE_DEFAULT;
                    usedDefault();
                }
            }
            String actual = p.getGpaPercent() == null ? "未填写" : plain(p.getGpaPercent());
            if (min == null)
            {
                flags.add("requirement_missing");
                add("gpa", UNCHECKED, null, actual, SOURCE_NONE);
                return;
            }
            String required = tier + " ≥ " + plain(min);
            if (p.getGpaPercent() == null)
            {
                flags.add("gpa_missing");
                add("gpa", UNCHECKED, required, actual, source);
                return;
            }
            add("gpa", p.getGpaPercent().compareTo(min) >= 0 ? MET : NOT_MET, required, actual, source);
        }

        // ---------------------------------------------------------------- 先修课、工作年限、跨专业（只取项目库）

        void prerequisites()
        {
            if (req == null || req.prerequisites() == null || req.prerequisites().isBlank())
            {
                return;
            }
            int missing = p.getMissingPrereqCount() == null ? 0 : p.getMissingPrereqCount();
            String required = req.prerequisites() + (req.prerequisitesRequired() ? "（硬性）" : "");
            if (missing > 0)
            {
                if (req.prerequisitesRequired())
                {
                    add("prerequisites", NOT_MET, required, "缺 " + missing + " 门", SOURCE_PROGRAM);
                }
                else
                {
                    flags.add("prereq_risk");
                    add("prerequisites", RISK, required, "缺 " + missing + " 门", SOURCE_PROGRAM);
                }
                return;
            }
            if ("same".equals(p.getMajorRelevance()))
            {
                add("prerequisites", MET, required, "本专业", SOURCE_PROGRAM);
                return;
            }
            // 问卷 v1 不采集所修课程：非本专业时无法判断，记风险交顾问核实
            flags.add("prereq_to_verify");
            add("prerequisites", RISK, required, "待核实", SOURCE_PROGRAM);
        }

        void workYears()
        {
            if (req == null || req.workYears() == null || req.workYears() <= 0)
            {
                return;
            }
            int months = p.getWorkFulltimeMonths() == null ? 0 : p.getWorkFulltimeMonths();
            add("work_years", months >= req.workYears() * 12 ? MET : NOT_MET, req.workYears() + " 年", "全职 " + months + " 个月",
                    SOURCE_PROGRAM);
        }

        void crossMajor()
        {
            if (!"cross".equals(p.getMajorRelevance()))
            {
                return;
            }
            String accept = req == null ? null : req.acceptCrossMajor();
            if ("0".equals(accept))
            {
                add("cross_major", NOT_MET, "不接受跨专业", "跨专业", SOURCE_PROGRAM);
            }
            else if ("1".equals(accept))
            {
                add("cross_major", MET, "接受跨专业", "跨专业", SOURCE_PROGRAM);
            }
            else
            {
                flags.add("cross_major_unknown");
                add("cross_major", RISK, null, "跨专业", req == null ? SOURCE_NONE : SOURCE_PROGRAM);
            }
        }

        // ---------------------------------------------------------------- 国家特例

        /** 德国 APS：未办理（不了解按未办理）归入补足后可申 */
        void aps()
        {
            JsonNode aps = rules.get("aps");
            if (aps == null || !aps.isObject())
            {
                return;
            }
            boolean required = program.apsRequired() || aps.path("required_for_cn_degree").asBoolean(false) && !"overseas".equals(p.getUndergradTier());
            if (!required)
            {
                return;
            }
            String status = p.getDeApsStatus();
            if (status == null || "unknown".equals(status))
            {
                flags.add("aps_unknown");
                status = aps.path("unknown_treated_as").asString("not_started");
            }
            if ("passed".equals(status))
            {
                add("aps", MET, "需通过 APS", "已通过", program.apsRequired() ? SOURCE_PROGRAM : SOURCE_DEFAULT);
            }
            else if ("in_progress".equals(status))
            {
                flags.add("aps_in_progress");
                add("aps", MET, "需通过 APS", "审核中", program.apsRequired() ? SOURCE_PROGRAM : SOURCE_DEFAULT);
            }
            else
            {
                add("aps", NOT_MET, "需通过 APS", "未办理", program.apsRequired() ? SOURCE_PROGRAM : SOURCE_DEFAULT);
            }
        }

        /** 德国课程匹配度：项目没有学分要求记录，用配置的兜底比例 */
        void courseMatch()
        {
            JsonNode cm = rules.get("course_match");
            if (cm == null || !cm.isObject() || !cm.path("min_ratio_fallback").isNumber())
            {
                return;
            }
            BigDecimal min = cm.path("min_ratio_fallback").decimalValue();
            String required = "课程匹配 ≥ " + plain(min);
            if (p.getCourseMatchRatio() == null)
            {
                flags.add("course_match_missing");
                add("course_match", UNCHECKED, required, "未解析成绩单", SOURCE_DEFAULT);
                return;
            }
            usedDefault();
            add("course_match", p.getCourseMatchRatio().compareTo(min) >= 0 ? MET : NOT_MET, required, plain(p.getCourseMatchRatio()),
                    SOURCE_DEFAULT);
        }
    }

    // -------------------------------------------------------------------- 工具

    /** JLPT 等级 → 数值（N1 = 5 … N5 = 1，越大越好）；N4_N5 记 N4；未考或无法识别为 null */
    static Integer jlptOf(String v)
    {
        if (v == null)
        {
            return null;
        }
        if ("N4_N5".equals(v))
        {
            return 2;
        }
        Matcher m = JLPT.matcher(v);
        return m.find() ? 6 - Integer.parseInt(m.group(1)) : null;
    }

    /** TOPIK 级别 → 数值（1–6）；1_2 记 2；未考或无法识别为 null */
    static Integer topikOf(String v)
    {
        if (v == null)
        {
            return null;
        }
        if ("1_2".equals(v))
        {
            return 2;
        }
        Matcher m = DIGIT.matcher(v);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static String levelText(String test, Integer level)
    {
        if (level == null)
        {
            return "未考";
        }
        return "jlpt".equals(test) ? "N" + (6 - level) : "TOPIK " + level + " 级";
    }

    private static String scoreText(String scale, BigDecimal total, BigDecimal sub)
    {
        String name = "ielts".equals(scale) ? "雅思" : ENGLISH_TEST_NAMES.getOrDefault(scale, scale);
        boolean ielts = "ielts".equals(scale);
        StringBuilder sb = new StringBuilder(name);
        if (total != null)
        {
            sb.append(' ').append(ielts ? total.setScale(1, RoundingMode.HALF_UP).toPlainString() : plain(total));
        }
        if (sub != null)
        {
            sb.append("（小分 ").append(ielts ? sub.setScale(1, RoundingMode.HALF_UP).toPlainString() : plain(sub)).append('）');
        }
        return sb.toString();
    }

    private static String plain(BigDecimal v)
    {
        return v.stripTrailingZeros().toPlainString();
    }

    private static List<String> sorted(Set<String> set)
    {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    /**
     * 单个检查项
     *
     * @param key 检查项（language_en / language_ja / language_ko / language_de / tier_accepted / gpa / prerequisites / work_years / cross_major / aps / course_match）
     * @param outcome 结论（met / not_met / pre_sessional / unchecked / risk / out_of_scope）
     * @param required 要求（展示用文字；没有可用要求为 null）
     * @param actual 学生情况（展示用文字）
     * @param source 要求来源（program / default / none）
     */
    public record Item(String key, String outcome, String required, String actual, String source)
    {
    }

    /**
     * 一个项目的检查结果
     *
     * @param status pass / 配置 on_fail（needs_improvement，补足后可申）/ excluded
     * @param items 检查项（按检查顺序）
     * @param missing 不达标的检查项（即“缺什么”，按检查顺序）
     * @param flags 提示码（如 requirement_unverified 要求待核实、language_pending 语言待考、pre_sessional 可读语言班、pre_sessional_unverified 项目是否接受语言班待核实）
     */
    public record Result(String status, List<Item> items, List<String> missing, List<String> flags)
    {
    }

    /**
     * 批量筛选中的一个项目
     *
     * @param program 项目事实
     * @param result 检查结果
     */
    public record Screened(ProgramFacts program, Result result)
    {
    }

    /**
     * 批量筛选结果
     *
     * @param passed 通过（进入综合分与分档）
     * @param needsImprovement 补足后可申
     * @param excluded 不在考虑范围
     */
    public record Screening(List<Screened> passed, List<Screened> needsImprovement, List<Screened> excluded)
    {
    }
}
