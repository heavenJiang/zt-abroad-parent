package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * 子分：背景档案 → 各维度 0–100 子分（引擎配置 scoring 节的实现，见 seed/engine-config/README.md）
 * 
 * 纯函数：参数全部来自传入的 scoring 配置，同一输入得到同一输出；结果保留 2 位小数（HALF_UP）。
 * 
 * @author ygb
 */
public final class SubScoreCalculator
{
    /** 维度顺序（与引擎配置 dimensions.keys 一致） */
    public static final List<String> DIMENSIONS = List.of("academic", "major_relevance", "test_scores", "research",
            "work_experience", "other_soft", "course_match");

    /** 子分保留位数 */
    static final int SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SubScoreCalculator()
    {
    }

    /**
     * 计算子分
     * 
     * @param p 背景档案
     * @param scoring 引擎配置的 scoring 节
     * @return 子分与提示
     */
    public static Result compute(BackgroundProfile p, JsonNode scoring)
    {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        scores.put("academic", academic(p, scoring.path("academic"), warnings));
        scores.put("major_relevance", majorRelevance(p, scoring.path("major_relevance"), warnings));
        scores.put("test_scores", testScores(p, scoring.path("test_scores")));
        scores.put("research", leveled(p.getResearchLevel(), p.getResearchCount(), p.getResearchRelevant(), scoring.path("research"), "research"));
        scores.put("work_experience", work(p, scoring.path("work_experience")));
        scores.put("other_soft", leveled(p.getAwardsLevel(), p.getAwardsCount(), null, scoring.path("other_soft"), "other_soft"));
        JsonNode course = scoring.path("course_match");
        if (course.path("applies").asBoolean(false))
        {
            if (p.getCourseMatchRatio() == null)
            {
                warnings.add("course_match_missing");
            }
            else
            {
                scores.put("course_match", clamp(p.getCourseMatchRatio().multiply(HUNDRED)));
            }
        }
        scores.replaceAll((k, v) -> v.setScale(SCALE, RoundingMode.HALF_UP));
        return new Result(Collections.unmodifiableMap(scores), Collections.unmodifiableList(warnings));
    }

    private static BigDecimal academic(BackgroundProfile p, JsonNode cfg, List<String> warnings)
    {
        String tier = p.getUndergradTier();
        if (tier == null || "unknown".equals(tier))
        {
            // 问卷约定：搜不到学校按双非计算，线索卡片提示“院校层次待核定”
            tier = "non_key";
        }
        BigDecimal base = num(cfg.path("tier_base"), tier, "academic.tier_base");
        BigDecimal value = base;
        if (p.getGpaPercent() == null)
        {
            warnings.add("academic_gpa_missing");
        }
        else
        {
            BigDecimal ref = num(cfg.path("tier_reference_gpa"), tier, "academic.tier_reference_gpa");
            value = value.add(num(cfg, "gpa_slope", "academic").multiply(p.getGpaPercent().subtract(ref)));
        }
        String rank = p.getClassRank() == null ? "null" : p.getClassRank();
        JsonNode bonus = cfg.path("rank_bonus").get(rank);
        if (bonus != null && bonus.isNumber())
        {
            value = value.add(bonus.decimalValue());
        }
        return clamp(value);
    }

    private static BigDecimal majorRelevance(BackgroundProfile p, JsonNode cfg, List<String> warnings)
    {
        String level = p.getMajorRelevance();
        if (level == null)
        {
            // 没有相关度信息时按跨专业保守计算
            level = "cross";
            warnings.add("major_relevance_missing");
        }
        BigDecimal score = num(cfg.path("level_score"), level, "major_relevance.level_score");
        int missing = p.getMissingPrereqCount() == null ? 0 : p.getMissingPrereqCount();
        int counted = Math.min(missing, num(cfg, "prereq_penalty_max_count", "major_relevance").intValue());
        return score.subtract(num(cfg, "prereq_penalty", "major_relevance").multiply(BigDecimal.valueOf(counted))).max(BigDecimal.ZERO);
    }

    private static BigDecimal testScores(BackgroundProfile p, JsonNode cfg)
    {
        String type = p.getStdTestType();
        Integer total = p.getStdTestTotal();
        String reference;
        String slope;
        if ("gre".equals(type))
        {
            reference = "gre_reference";
            slope = "gre_slope";
        }
        else if ("gmat_focus".equals(type))
        {
            reference = "gmat_focus_reference";
            slope = "gmat_slope";
        }
        else if ("gmat_classic".equals(type))
        {
            reference = "gmat_classic_reference";
            slope = "gmat_slope";
        }
        else
        {
            return num(cfg, "missing_score", "test_scores");
        }
        if (total == null)
        {
            return num(cfg, "missing_score", "test_scores");
        }
        BigDecimal delta = BigDecimal.valueOf(total).subtract(num(cfg, reference, "test_scores"));
        return clamp(new BigDecimal("60").add(num(cfg, slope, "test_scores").multiply(delta)));
    }

    /**
     * 按级别计分的维度（科研、其他软背景）：无经历取 none_score；否则级别分 × 相关系数，再按段数加分，封顶 100
     */
    private static BigDecimal leveled(String level, Integer count, String relevant, JsonNode cfg, String name)
    {
        BigDecimal none = num(cfg, "none_score", name);
        if (level == null || "none".equals(level))
        {
            return none;
        }
        BigDecimal score = num(cfg.path("level_score"), level, name + ".level_score");
        JsonNode factors = cfg.get("relevance_factor");
        if (factors != null && relevant != null && factors.path(relevant).isNumber())
        {
            score = score.multiply(factors.path(relevant).decimalValue());
        }
        int n = count == null ? 1 : Math.max(1, count);
        score = score.max(none).add(num(cfg, "extra_item_bonus", name).multiply(BigDecimal.valueOf(n - 1L)));
        return score.min(HUNDRED);
    }

    private static BigDecimal work(BackgroundProfile p, JsonNode cfg)
    {
        BigDecimal none = num(cfg, "none_score", "work_experience");
        int months = p.getWorkMonths() == null ? 0 : p.getWorkMonths();
        if (months <= 0)
        {
            return none;
        }
        BigDecimal company = factor(cfg.path("company_factor"), p.getWorkCompanyTier());
        BigDecimal relevance = factor(cfg.path("relevance_factor"), p.getWorkRelevant());
        BigDecimal effective = BigDecimal.valueOf(months).multiply(company).multiply(relevance);
        BigDecimal saturation = num(cfg, "saturation_months", "work_experience");
        JsonNode byField = cfg.path("saturation_months_by_field").get(String.valueOf(p.getTargetFieldGroup()));
        if (byField != null && byField.isNumber())
        {
            saturation = byField.decimalValue();
        }
        BigDecimal ratio = effective.divide(saturation, 10, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        return none.add(HUNDRED.subtract(none).multiply(ratio));
    }

    /**
     * 取系数：缺少或未知时按 1
     */
    private static BigDecimal factor(JsonNode factors, String key)
    {
        JsonNode f = key == null ? null : factors.get(key);
        return f != null && f.isNumber() ? f.decimalValue() : BigDecimal.ONE;
    }

    private static BigDecimal num(JsonNode node, String key, String where)
    {
        JsonNode v = node.get(key);
        if (v == null || !v.isNumber())
        {
            throw new IllegalArgumentException("引擎配置缺少数值 scoring." + where + "." + key);
        }
        return v.decimalValue();
    }

    private static BigDecimal clamp(BigDecimal v)
    {
        return ProfileNormalizer.clamp(v, BigDecimal.ZERO, HUNDRED);
    }

    /**
     * 子分结果
     * 
     * @param scores 维度 → 子分（0–100，2 位小数；不适用的维度不出现）
     * @param warnings 计算中的提示码
     */
    public record Result(Map<String, BigDecimal> scores, List<String> warnings)
    {
    }
}
