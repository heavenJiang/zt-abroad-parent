package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrAssessmentResult;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.vo.AppQuickResultVo;
import com.ygb.abroad.domain.vo.AppReportVo;
import com.ygb.abroad.engine.AnswerVisibility;
import com.ygb.abroad.engine.HardThresholdChecker;
import com.ygb.abroad.engine.RecommendationPlanner;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.engine.SubScoreCalculator;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 测评报告结构化部分的组装（纯函数，不访问数据库）
 *
 * 只取已保存的选校结果（abr_assessment_result）与院校库事实；综合分、录取概率、参考线、权重、院校层次一律不进入报告。
 * 子分只用于画像条的长度，取整到 5。项目超过 90 天未核实时不展示要求数值。
 *
 * @author ygb
 */
final class AbrAssessmentReport
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 模式：引擎已出结果 */
    static final String MODE_FULL = "full";

    /** 模式：该学段或方向由顾问评估 */
    static final String MODE_CONSULTANT = AbrSelectionServiceImpl.MODE_CONSULTANT;

    /** 模式：结果未生成（提交后计算失败，顾问会重新计算） */
    static final String MODE_PENDING = "pending";

    /** 每档展示的代表校数（产品文档：每档 2–3 所） */
    static final int EXAMPLES = 3;

    /** 最多展示的短板数（产品文档：最关键的 1–2 个） */
    static final int SHORTCOMINGS = 2;

    /** 提示码：未成年 */
    static final String NOTICE_MINOR = "minor";

    /** 提示码：不展示“分享 / 邀请同学解锁”类激励（未成年） */
    static final String NOTICE_NO_REFERRAL_UNLOCK = "no_referral_unlock";

    /** 短板：冲刺进入主攻 */
    static final String KIND_UPGRADE = "upgrade";

    /** 短板：补足后可申变为可申 */
    static final String KIND_UNLOCK = "unlock";

    /** 向学生展示的方案提示（over_budget 带项目ID，不展示） */
    static final Set<String> HINTS = Set.of("tier_shortage", "no_safety_in_first_choice", "mix_application", "employment_data_missing");

    /** 向学生展示的项目提示码 */
    static final Set<String> FLAGS = Set.of("requirement_unverified", "pre_sessional", "language_pending");

    /** 画像维度的学生侧名称（引擎配置里的名称含“院校层次”等内部口径，不直接展示） */
    static final Map<String, String> DIMENSION_LABELS = Map.of("academic", "学术背景", "major_relevance", "专业相关度", "test_scores", "标化成绩",
            "research", "科研", "work_experience", "实习 / 工作", "other_soft", "其他软背景", "course_match", "课程匹配");

    /** 补足动作：雅思提高 0.5（与引擎 GapAnalyzer 一致；短板按次数区分） */
    private static final String IELTS_ACTION = "ielts_plus_0_5";

    /** 跳过题目时的“不清楚”选项（与跳过同样提示补充） */
    private static final String UNKNOWN = "unknown";

    private AbrAssessmentReport()
    {
    }

    /**
     * 组装报告
     *
     * @param assessmentId 测评ID
     * @param q 测评所用问卷
     * @param answers 提交后的答案（只含可见题目）
     * @param flags 流程标志（is_minor 等）
     * @param result 选校结果（未生成为 null）
     * @param programs 报告涉及项目的院校库记录（取核对日期；可缺项）
     * @param today 今天（北京时间）
     * @return 报告
     */
    static AppReportVo build(Long assessmentId, AbrAssessmentAnswers.Questionnaire q, JsonNode answers, JsonNode flags, AbrAssessmentResult result,
            Map<Long, AbrProgram> programs, LocalDate today)
    {
        Map<String, String> labels = countryLabels(q);
        List<String> notices = new ArrayList<>();
        String minorNotice = null;
        if (flags.path("is_minor").asBoolean(false))
        {
            notices.add(NOTICE_MINOR);
            minorNotice = minorNotice(q, answers);
        }
        if (flags.path("exclude_from_referral_unlock").asBoolean(false))
        {
            notices.add(NOTICE_NO_REFERRAL_UNLOCK);
        }
        List<AppReportVo.SkipHint> skipHints = skipHints(q, answers);
        if (result == null || MODE_CONSULTANT.equals(result.getMode()))
        {
            String mode = result == null ? MODE_PENDING : MODE_CONSULTANT;
            if (result != null)
            {
                strings(result.getNotices()).forEach(n -> addOnce(notices, n));
            }
            List<AppQuickResultVo.CountryRef> skipped = result == null ? List.of() : countries(result.getSkippedCountries(), labels);
            String headline = result == null ? "结果正在生成，顾问会为你重新计算后通知你" : "该学段的选校由顾问一对一评估，可预约免费解读";
            return new AppReportVo(assessmentId, mode, headline, notices, minorNotice, List.of(), emptyTiers(), 0, List.of(), List.of(), 0,
                    List.of(), skipped, skipHints, new AppReportVo.DataNote(null, null, 0, computedTime(result)), null);
        }
        strings(result.getNotices()).forEach(n -> addOnce(notices, n));

        Map<Long, JsonNode> rows = new LinkedHashMap<>();
        for (JsonNode row : array(result.getPrograms()))
        {
            rows.put(row.path("programId").asLong(), row);
        }
        Map<Long, JsonNode> gaps = new HashMap<>();
        for (JsonNode gap : array(result.getGaps()))
        {
            gaps.put(gap.path("programId").asLong(), gap);
        }
        JsonNode portfolio = object(result.getPortfolio());
        Map<String, Integer> counts = Map.of("reach", nz(result.getReachCount()), "match", nz(result.getMatchCount()), "safety",
                nz(result.getSafetyCount()));
        List<AppReportVo.Tier> tiers = new ArrayList<>();
        for (String tier : RecommendationPlanner.TIERS)
        {
            List<AppReportVo.Program> list = new ArrayList<>();
            for (JsonNode id : portfolio.path(tier))
            {
                JsonNode row = rows.get(id.asLong());
                if (row != null && list.size() < EXAMPLES)
                {
                    list.add(program(row, gaps.get(id.asLong()), programs, labels, today));
                }
            }
            tiers.add(new AppReportVo.Tier(tier, counts.get(tier), list));
        }
        List<JsonNode> blocked = rows.values().stream().filter(r -> SelectionEngine.NEEDS_IMPROVEMENT.equals(r.path("group").asString())).toList();
        List<AppReportVo.Program> needsImprovement = blocked.stream().limit(EXAMPLES)
                .map(r -> program(r, gaps.get(r.path("programId").asLong()), programs, labels, today)).toList();
        int unrated = (int) rows.values().stream().filter(r -> "unrated".equals(r.path("group").asString())).count();

        List<String> hints = new ArrayList<>();
        for (JsonNode h : array(result.getHints()))
        {
            String code = h.path("code").asString("");
            if (HINTS.contains(code))
            {
                addOnce(hints, code);
            }
        }
        return new AppReportVo(assessmentId, MODE_FULL, headline(counts, nz(result.getNeedsImprovementCount())), notices, minorNotice,
                profile(object(result.getSubScores()), answers), tiers, unrated, shortcomings(rows, gaps), needsImprovement,
                nz(result.getNeedsImprovementCount()), hints, countries(result.getSkippedCountries(), labels), skipHints,
                dataNote(rows.values(), programs, today, result), null);
    }

    /**
     * 一句话定位（模板句；AI 解读上线后由 report_explainer 替换）
     */
    static String headline(Map<String, Integer> counts, int needsImprovement)
    {
        int reach = counts.get("reach");
        int match = counts.get("match");
        int safety = counts.get("safety");
        if (reach + match + safety > 0)
        {
            return "按你目前的背景，目标国家 / 地区中主攻档有 " + match + " 个项目可选，另有冲刺 " + reach + " 个、保底 " + safety + " 个";
        }
        if (needsImprovement > 0)
        {
            return "目前还没有可直接申请的项目，补足语言等要求后有 " + needsImprovement + " 个项目可申";
        }
        return "暂未匹配到合适的项目，建议预约顾问一对一评估";
    }

    /**
     * 竞争力画像：首选国家 / 地区（学生选择的第一个已计算国家）的子分
     */
    private static List<AppReportVo.Dimension> profile(JsonNode subScores, JsonNode answers)
    {
        JsonNode chosen = null;
        for (JsonNode c : answers.path("target_countries"))
        {
            if (chosen == null && subScores.has(c.asString("")))
            {
                chosen = subScores.get(c.asString(""));
            }
        }
        if (chosen == null && !subScores.isEmpty())
        {
            chosen = subScores.iterator().next();
        }
        List<AppReportVo.Dimension> list = new ArrayList<>();
        if (chosen == null)
        {
            return list;
        }
        JsonNode scores = chosen.path("scores");
        for (String key : SubScoreCalculator.DIMENSIONS)
        {
            JsonNode v = scores.get(key);
            if (v != null && v.isNumber())
            {
                list.add(new AppReportVo.Dimension(key, DIMENSION_LABELS.getOrDefault(key, key), level(v.decimalValue())));
            }
        }
        return list;
    }

    /**
     * 子分取整到 5（只用于画条）
     */
    static int level(BigDecimal score)
    {
        BigDecimal clamped = score.max(BigDecimal.ZERO).min(new BigDecimal("100"));
        return clamped.divide(new BigDecimal("5"), 0, RoundingMode.HALF_UP).intValue() * 5;
    }

    private static AppReportVo.Program program(JsonNode row, JsonNode gap, Map<Long, AbrProgram> programs, Map<String, String> labels,
            LocalDate today)
    {
        Long id = row.path("programId").asLong();
        AbrProgram program = programs.get(id);
        boolean stale = AbrLookupTools.stale(program, today);
        List<String> met = new ArrayList<>();
        List<AppReportVo.Gap> missing = new ArrayList<>();
        for (JsonNode item : row.path("thresholdItems"))
        {
            String key = item.path("key").asString("");
            String outcome = item.path("outcome").asString("");
            if (HardThresholdChecker.MET.equals(outcome))
            {
                met.add(key);
            }
            else if (HardThresholdChecker.NOT_MET.equals(outcome))
            {
                boolean tier = AbrQuickTool.ITEM_TIER.equals(key);
                HardThresholdChecker.Item it = new HardThresholdChecker.Item(key, outcome, text(item.get("required")), text(item.get("actual")),
                        text(item.get("source")));
                missing.add(new AppReportVo.Gap(key, tier || stale ? null : AbrQuickTool.requiredText(it), tier ? null : it.actual()));
            }
        }
        List<String> flags = new ArrayList<>();
        for (JsonNode f : row.path("thresholdFlags"))
        {
            if (FLAGS.contains(f.asString("")))
            {
                addOnce(flags, f.asString());
            }
        }
        List<String> upgrade = List.of();
        Boolean inTime = null;
        List<AppReportVo.Fix> fixes = new ArrayList<>();
        if (gap != null)
        {
            if (!gap.path("requiredC").isNull() && gap.has("requiredC") && gap.path("achievable").asBoolean(false))
            {
                upgrade = strings(gap.path("plan"));
                inTime = bool(gap.get("inTime"));
            }
            for (JsonNode f : gap.path("thresholdFixes"))
            {
                fixes.add(new AppReportVo.Fix(f.path("item").asString(""), text(f.get("action")), f.path("steps").asInt(0), integer(f.get("months"))));
            }
        }
        String country = row.path("country").asString("");
        return new AppReportVo.Program(id, program == null ? text(row.get("schoolName")) : program.getSchoolName(),
                program == null ? text(row.get("programName")) : program.getProgramName(), country, labels.getOrDefault(country, country), met,
                missing, flags, upgrade, inTime, fixes, verifiedDate(program), stale);
    }

    /**
     * 最关键的 1–2 个短板：冲刺校按“补上哪些动作可进入主攻”、补足后可申按“补足动作”分组，受益项目多的在前，其次月数少的在前
     */
    static List<AppReportVo.Shortcoming> shortcomings(Map<Long, JsonNode> rows, Map<Long, JsonNode> gaps)
    {
        Map<String, Acc> groups = new LinkedHashMap<>();
        for (Map.Entry<Long, JsonNode> e : rows.entrySet())
        {
            JsonNode gap = gaps.get(e.getKey());
            String group = e.getValue().path("group").asString("");
            if (gap == null)
            {
                continue;
            }
            if ("reach".equals(group) && gap.path("achievable").asBoolean(false) && !gap.path("plan").isEmpty())
            {
                List<String> actions = strings(gap.path("plan"));
                groups.computeIfAbsent(KIND_UPGRADE + ":" + actions, k -> new Acc(KIND_UPGRADE, actions, 1))
                        .add(gap.path("planMonths").asInt(0), bool(gap.get("inTime")));
            }
            else if (SelectionEngine.NEEDS_IMPROVEMENT.equals(group) && !gap.path("thresholdFixes").isEmpty())
            {
                List<String> actions = new ArrayList<>();
                int steps = 1;
                Integer months = 0;
                boolean fixable = true;
                for (JsonNode f : gap.path("thresholdFixes"))
                {
                    String action = text(f.get("action"));
                    if (action == null)
                    {
                        fixable = false;
                        break;
                    }
                    actions.add(action);
                    if (IELTS_ACTION.equals(action))
                    {
                        steps = Math.max(steps, f.path("steps").asInt(1));
                    }
                    Integer m = integer(f.get("months"));
                    months = months == null || m == null ? null : Math.max(months, m);
                }
                if (fixable)
                {
                    int s = steps;
                    groups.computeIfAbsent(KIND_UNLOCK + ":" + actions + ":" + s, k -> new Acc(KIND_UNLOCK, actions, s)).add(months, null);
                }
            }
        }
        List<Acc> list = new ArrayList<>(groups.values());
        list.sort(Comparator.comparingInt((Acc a) -> -a.count)
                .thenComparing(a -> a.months, Comparator.nullsLast(Comparator.naturalOrder())));
        return list.stream().limit(SHORTCOMINGS)
                .map(a -> new AppReportVo.Shortcoming(a.kind, List.copyOf(a.actions), a.steps, a.count, a.months, a.inTime())).toList();
    }

    /**
     * 同一组短板的累计
     */
    private static final class Acc
    {
        final String kind;

        final List<String> actions;

        final int steps;

        int count;

        Integer months = 0;

        boolean anyLate;

        boolean anyUnknown;

        Acc(String kind, List<String> actions, int steps)
        {
            this.kind = kind;
            this.actions = actions;
            this.steps = steps;
        }

        void add(Integer m, Boolean inTime)
        {
            count++;
            months = months == null || m == null ? null : Math.max(months, m);
            if (Boolean.FALSE.equals(inTime))
            {
                anyLate = true;
            }
            else if (inTime == null)
            {
                anyUnknown = true;
            }
        }

        Boolean inTime()
        {
            return anyLate ? Boolean.FALSE : anyUnknown ? null : Boolean.TRUE;
        }
    }

    /**
     * 跳过的题目：可见、带 skip_hint，且未回答、选了“不清楚”或成绩类题目没填选填的小分
     */
    static List<AppReportVo.SkipHint> skipHints(AbrAssessmentAnswers.Questionnaire q, JsonNode answers)
    {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        answers.properties().forEach(e -> map.put(e.getKey(), e.getValue()));
        List<AppReportVo.SkipHint> list = new ArrayList<>();
        for (JsonNode question : q.questions().values())
        {
            String hint = question.path("skip_hint").asString("");
            if (StringUtils.isBlank(hint) || !AnswerVisibility.isVisible(question.get("visible_if"), map))
            {
                continue;
            }
            String id = question.path("id").asString("");
            JsonNode v = map.get(id);
            if (v == null || v.isNull() || UNKNOWN.equals(v.asString("")) || missingSub(question, v))
            {
                list.add(new AppReportVo.SkipHint(id, question.path("title").asString(id), hint));
            }
        }
        return list;
    }

    /**
     * 成绩类题目：该考试类型有选填的小分而学生没填
     */
    private static boolean missingSub(JsonNode question, JsonNode v)
    {
        if (!v.isObject() || !v.has("total"))
        {
            return false;
        }
        String type = v.path("type").asString("");
        for (JsonNode t : question.path("score_types"))
        {
            if (type.equals(t.path("value").asString()) && t.path("min_sub").isObject())
            {
                return v.path("min_sub").isNull() || v.path("min_sub").isMissingNode();
            }
        }
        return false;
    }

    /**
     * 未成年提示文字：触发 is_minor 的那道题、那个选项上的 notice
     */
    private static String minorNotice(AbrAssessmentAnswers.Questionnaire q, JsonNode answers)
    {
        for (JsonNode question : q.questions().values())
        {
            JsonNode v = answers.get(question.path("id").asString(""));
            JsonNode rule = v == null || !v.isString() ? null : question.path("on_answer").get(v.asString());
            if (rule != null && rule.path("flags").path("is_minor").asBoolean(false) && rule.path("notice").path("text").isString())
            {
                return rule.path("notice").path("text").asString();
            }
        }
        return null;
    }

    private static AppReportVo.DataNote dataNote(Iterable<JsonNode> rows, Map<Long, AbrProgram> programs, LocalDate today,
            AbrAssessmentResult result)
    {
        LocalDate from = null;
        LocalDate to = null;
        int unverified = 0;
        for (JsonNode row : rows)
        {
            if (SelectionEngine.EXCLUDED.equals(row.path("group").asString()))
            {
                continue;
            }
            AbrProgram program = programs.get(row.path("programId").asLong());
            if (AbrLookupTools.stale(program, today))
            {
                unverified++;
            }
            LocalDate day = day(program);
            if (day != null)
            {
                from = from == null || day.isBefore(from) ? day : from;
                to = to == null || day.isAfter(to) ? day : to;
            }
        }
        return new AppReportVo.DataNote(from == null ? null : from.toString(), to == null ? null : to.toString(), unverified, computedTime(result));
    }

    /**
     * 报告涉及的项目ID（取院校库核对日期用；不含不在考虑范围的项目）
     *
     * @param result 选校结果
     * @return 项目ID
     */
    static List<Long> programIds(AbrAssessmentResult result)
    {
        List<Long> ids = new ArrayList<>();
        if (result == null)
        {
            return ids;
        }
        for (JsonNode row : array(result.getPrograms()))
        {
            if (!SelectionEngine.EXCLUDED.equals(row.path("group").asString()))
            {
                ids.add(row.path("programId").asLong());
            }
        }
        return ids;
    }

    private static String verifiedDate(AbrProgram program)
    {
        LocalDate day = day(program);
        return day == null ? null : day.toString();
    }

    private static LocalDate day(AbrProgram program)
    {
        Date verified = program == null ? null : program.getVerifiedTime();
        return verified == null ? null : verified.toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate();
    }

    private static String computedTime(AbrAssessmentResult result)
    {
        return result == null || result.getComputedTime() == null ? null : DateUtils.parseDateToStr("yyyy-MM-dd HH:mm", result.getComputedTime());
    }

    private static List<AppReportVo.Tier> emptyTiers()
    {
        return RecommendationPlanner.TIERS.stream().map(t -> new AppReportVo.Tier(t, 0, List.<AppReportVo.Program>of())).toList();
    }

    private static Map<String, String> countryLabels(AbrAssessmentAnswers.Questionnaire q)
    {
        Map<String, String> labels = new LinkedHashMap<>();
        q.root().path("countries").forEach(c -> labels.put(c.path("value").asString(), c.path("label").asString()));
        return labels;
    }

    private static List<AppQuickResultVo.CountryRef> countries(String csv, Map<String, String> labels)
    {
        List<AppQuickResultVo.CountryRef> list = new ArrayList<>();
        if (StringUtils.isNotBlank(csv))
        {
            for (String c : csv.split(","))
            {
                if (StringUtils.isNotBlank(c))
                {
                    list.add(new AppQuickResultVo.CountryRef(c.trim(), labels.getOrDefault(c.trim(), c.trim())));
                }
            }
        }
        return list;
    }

    private static void addOnce(List<String> list, String value)
    {
        if (StringUtils.isNotBlank(value) && !list.contains(value))
        {
            list.add(value);
        }
    }

    private static int nz(Integer v)
    {
        return v == null ? 0 : v;
    }

    private static String text(JsonNode n)
    {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asString();
    }

    private static Integer integer(JsonNode n)
    {
        return n == null || !n.isNumber() ? null : n.asInt();
    }

    private static Boolean bool(JsonNode n)
    {
        return n == null || !n.isBoolean() ? null : n.asBoolean();
    }

    private static List<String> strings(JsonNode array)
    {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.asString()));
        return list;
    }

    private static List<String> strings(String json)
    {
        return strings(array(json));
    }

    private static JsonNode array(String json)
    {
        JsonNode n = parse(json);
        return n != null && n.isArray() ? n : MAPPER.createArrayNode();
    }

    private static JsonNode object(String json)
    {
        JsonNode n = parse(json);
        return n != null && n.isObject() ? n : MAPPER.createObjectNode();
    }

    private static JsonNode parse(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return null;
        }
        try
        {
            return MAPPER.readTree(json);
        }
        catch (JacksonException e)
        {
            return null;
        }
    }
}
