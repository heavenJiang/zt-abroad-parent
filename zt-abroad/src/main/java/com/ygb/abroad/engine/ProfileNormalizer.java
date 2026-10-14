package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/**
 * 归一化：可见题目的答案 → 背景档案（问卷 normalization 与 derived_fields 的实现）
 * 
 * 纯函数：院校层次查询、当前日期都由调用方传入，同一输入得到同一输出。
 * 
 * @author ygb
 */
public final class ProfileNormalizer
{
    /** 百分制均分保留位数 */
    static final int GPA_SCALE = 1;

    /** 估算入学月份（9 月） */
    static final int INTAKE_MONTH = 9;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private ProfileNormalizer()
    {
    }

    /**
     * 归一化
     * 
     * @param answers 可见题目的答案（已按 {@link AnswerVisibility} 剔除隐藏题）
     * @param rules 换算表
     * @param tierLookup 国内高校ID → 层次（c9 / 985 / 211 / double_first_class / non_key），查不到返回 null
     * @param today 计算日期（用于距入学月数）
     * @return 背景档案
     */
    public static BackgroundProfile normalize(Map<String, JsonNode> answers, NormalizationRules rules,
            Function<Long, String> tierLookup, LocalDate today)
    {
        BackgroundProfile p = new BackgroundProfile();
        school(p, answers.get("undergrad_school"), tierLookup);
        gpa(p, answers.get("gpa"), rules);
        String rank = text(answers.get("class_rank"));
        p.setClassRank("unknown".equals(rank) ? null : rank);
        goal(p, answers, rules, today);
        major(p, answers, rules);
        english(p, answers, rules);
        stdTest(p, answers.get("std_test"));
        experience(p, answers);
        p.setBudgetTotal(text(answers.get("budget_total")));
        p.setRankVsEmployment(text(answers.get("rank_vs_employment")));
        p.setCityPreference(text(answers.get("city_preference")));
        p.setPriorityTop2(AnswerVisibility.strings(answers.get("priority_top2")));
        p.setJlptLevel(text(answers.get("jlpt_level")));
        p.setTopikLevel(text(answers.get("topik_level")));
        p.setDeApsStatus(text(answers.get("de_aps_status")));
        p.setMissingPrereqCount(0);
        return p;
    }

    private static void school(BackgroundProfile p, JsonNode school, Function<Long, String> tierLookup)
    {
        if (school == null)
        {
            return;
        }
        String kind = school.path("kind").asString("");
        if ("overseas".equals(kind))
        {
            p.setUndergradTier("overseas");
            return;
        }
        Long id = "cn".equals(kind) && school.path("university_id").isNumber() ? school.path("university_id").asLong() : null;
        String tier = id == null ? null : tierLookup.apply(id);
        if (tier == null)
        {
            p.setUndergradTier("unknown");
            p.getWarnings().add("tier_unknown");
        }
        else
        {
            p.setUndergradTier(tier);
            p.setUndergradUniversityId(id);
        }
    }

    private static void gpa(BackgroundProfile p, JsonNode gpa, NormalizationRules rules)
    {
        if (gpa == null)
        {
            return;
        }
        String scale = gpa.path("type").asString("");
        BigDecimal total = AnswerVisibility.number(gpa.get("total"));
        BigDecimal max = AnswerVisibility.number(gpa.get("max"));
        p.setGpaScale(scale);
        p.setGpaRaw(total);
        p.setGpaMax(max);
        if (total == null)
        {
            return;
        }
        String method = rules.gpaMethod(scale);
        BigDecimal percent = null;
        if ("identity".equals(method))
        {
            percent = total;
        }
        else if ("piecewise_linear".equals(method))
        {
            percent = interpolate(rules.gpaPoints(scale), total);
        }
        else if ("proportional_low_confidence".equals(method) && max != null && max.signum() > 0)
        {
            percent = total.multiply(HUNDRED).divide(max, 6, RoundingMode.HALF_UP);
            p.getWarnings().add("gpa_low_confidence");
        }
        if (percent == null)
        {
            p.getWarnings().add("gpa_unconverted");
            return;
        }
        p.setGpaPercent(clamp(percent, BigDecimal.ZERO, HUNDRED).setScale(GPA_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 折线插值；超出范围取端点值
     * 
     * @param points 折线点（按 x 升序）
     * @param x 原始分
     * @return 百分制，没有点返回 null
     */
    static BigDecimal interpolate(List<BigDecimal[]> points, BigDecimal x)
    {
        if (points.isEmpty())
        {
            return null;
        }
        if (x.compareTo(points.get(0)[0]) <= 0)
        {
            return points.get(0)[1];
        }
        for (int i = 1; i < points.size(); i++)
        {
            BigDecimal[] a = points.get(i - 1);
            BigDecimal[] b = points.get(i);
            if (x.compareTo(b[0]) <= 0)
            {
                BigDecimal dx = b[0].subtract(a[0]);
                if (dx.signum() == 0)
                {
                    return b[1];
                }
                return a[1].add(b[1].subtract(a[1]).multiply(x.subtract(a[0])).divide(dx, 6, RoundingMode.HALF_UP));
            }
        }
        return points.get(points.size() - 1)[1];
    }

    private static void goal(BackgroundProfile p, Map<String, JsonNode> answers, NormalizationRules rules, LocalDate today)
    {
        p.setTargetCountries(AnswerVisibility.strings(answers.get("target_countries")));
        p.setTargetDegree(text(answers.get("target_degree")));
        String field = text(answers.get("target_field"));
        JsonNode major = answers.get("undergrad_major");
        String majorValue = major == null ? null : text(major.get("value"));
        boolean matched = major != null && major.path("matched").asBoolean(false) && majorValue != null && !"other".equals(majorValue);
        if ("undecided".equals(field) && matched)
        {
            field = majorValue;
            p.getWarnings().add("target_field_from_major");
        }
        p.setTargetField(field);
        String group = field == null ? null : rules.fieldGroup(field);
        p.setTargetFieldGroup(group == null && field != null ? "undecided" : group);
        String intake = text(answers.get("intake_year"));
        p.setIntakeYear(intake);
        if (intake != null && intake.matches("\\d{4}"))
        {
            int months = (Integer.parseInt(intake) * 12 + INTAKE_MONTH) - (today.getYear() * 12 + today.getMonthValue());
            p.setMonthsToIntake(Math.max(0, months));
        }
    }

    private static void major(BackgroundProfile p, Map<String, JsonNode> answers, NormalizationRules rules)
    {
        JsonNode major = answers.get("undergrad_major");
        if (major == null)
        {
            return;
        }
        String value = text(major.get("value"));
        p.setUndergradMajor(value);
        String relevance;
        if (major.path("matched").asBoolean(false) && value != null && !"other".equals(value) && p.getTargetField() != null)
        {
            String a = rules.fieldGroup(value);
            if (value.equals(p.getTargetField()))
            {
                relevance = "same";
            }
            else if (a != null && a.equals(rules.fieldGroup(p.getTargetField())))
            {
                relevance = "related";
            }
            else
            {
                relevance = "cross";
            }
            p.setMajorRelevanceSource("matched");
        }
        else
        {
            relevance = text(answers.get("major_relevance_self"));
            p.setMajorRelevanceSource(relevance == null ? null : "self_reported");
            p.getWarnings().add("major_self_reported");
        }
        p.setMajorRelevance(relevance);
        if ("cross".equals(relevance))
        {
            p.getWarnings().add("prereq_unverified");
        }
    }

    private static void english(BackgroundProfile p, Map<String, JsonNode> answers, NormalizationRules rules)
    {
        String status = text(answers.get("english_status"));
        p.setEnglishStatus(status);
        JsonNode score = answers.get("english_score");
        if ("none".equals(status) || status == null || score == null)
        {
            if ("none".equals(status))
            {
                p.getWarnings().add("english_none");
            }
            return;
        }
        String test = score.path("type").asString("");
        BigDecimal total = AnswerVisibility.number(score.get("total"));
        BigDecimal minSub = AnswerVisibility.number(score.get("min_sub"));
        p.setEnglishTest(test);
        p.setEnglishTotal(total);
        p.setEnglishMinSub(minSub);
        p.setEnglishEstimated("estimated".equals(status));
        if (total == null)
        {
            return;
        }
        TreeMap<BigDecimal, BigDecimal> table = rules.englishTable(test);
        if (table == null)
        {
            p.getWarnings().add("english_unmapped");
            return;
        }
        if (table.isEmpty())
        {
            p.setIeltsEquivTotal(total);
            p.setIeltsEquivMinSub(minSub);
            return;
        }
        BigDecimal band = null;
        for (Map.Entry<BigDecimal, BigDecimal> e : table.entrySet())
        {
            if (e.getValue().compareTo(total) <= 0)
            {
                band = e.getKey();
            }
        }
        p.setIeltsEquivTotal(band != null ? band : table.firstKey().subtract(HALF));
    }

    private static void stdTest(BackgroundProfile p, JsonNode std)
    {
        String type = std == null ? null : text(std.get("type"));
        if (type == null || "none".equals(type))
        {
            p.setStdTestType("none");
            return;
        }
        p.setStdTestType(type);
        BigDecimal total = AnswerVisibility.number(std.get("total"));
        p.setStdTestTotal(total == null ? null : total.intValue());
        p.setStdTestEstimated(std.path("estimated").asBoolean(false));
    }

    private static void experience(BackgroundProfile p, Map<String, JsonNode> answers)
    {
        JsonNode research = answers.get("exp_research");
        if (has(research))
        {
            p.setResearchLevel(text(research.get("level")));
            p.setResearchCount(integer(research.get("count"), 1));
            p.setResearchRelevant(text(research.get("relevant")));
        }
        else if (research != null)
        {
            p.setResearchLevel("none");
            p.setResearchCount(0);
        }
        JsonNode work = answers.get("exp_work");
        if (has(work))
        {
            p.setWorkCount(integer(work.get("count"), 1));
            p.setWorkMonths(integer(work.get("total_months"), 0));
            p.setWorkFulltimeMonths(integer(work.get("fulltime_months"), 0));
            p.setWorkCompanyTier(text(work.get("company_tier")));
            p.setWorkRelevant(text(work.get("relevant")));
        }
        else if (work != null)
        {
            p.setWorkCount(0);
            p.setWorkMonths(0);
            p.setWorkFulltimeMonths(0);
        }
        JsonNode awards = answers.get("exp_awards");
        if (has(awards))
        {
            p.setAwardsLevel(text(awards.get("level")));
            p.setAwardsCount(integer(awards.get("count"), 1));
            p.setAwardsRelevant(text(awards.get("relevant")));
        }
        else if (awards != null)
        {
            p.setAwardsLevel("none");
            p.setAwardsCount(0);
        }
    }

    private static boolean has(JsonNode exp)
    {
        return exp != null && "yes".equals(exp.path("has").asString(""));
    }

    private static Integer integer(JsonNode node, int defaultValue)
    {
        BigDecimal n = AnswerVisibility.number(node);
        return n == null ? defaultValue : n.intValue();
    }

    private static String text(JsonNode node)
    {
        return node == null || !node.isString() || node.asString().isEmpty() ? null : node.asString();
    }

    /**
     * 截断到区间
     * 
     * @param v 值
     * @param min 下限
     * @param max 上限
     * @return 截断后的值
     */
    static BigDecimal clamp(BigDecimal v, BigDecimal min, BigDecimal max)
    {
        return v.max(min).min(max);
    }
}
