package com.ygb.abroad.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/**
 * 选校引擎入口（硕士）：子分 → 硬门槛 → 综合分 C → 录取可能性 P → 冲 / 稳 / 保分档
 *
 * 提供两种模式：完整测评（full）和快测（quick，只用院校层次、均分、目标国家 / 地区、专业方向）。
 * 纯函数：配置、项目、参考线都由调用方传入，同一输入得到同一输出；结果按传入项目的顺序返回。
 *
 * @author ygb
 */
public final class SelectionEngine
{
    /** 模式：完整测评 */
    public static final String MODE_FULL = "full";

    /** 模式：快测 */
    public static final String MODE_QUICK = "quick";

    /** 分组：补足后可申（未过硬门槛，不进冲稳保） */
    public static final String NEEDS_IMPROVEMENT = "needs_improvement";

    /** 分组：不在学生考虑范围 */
    public static final String EXCLUDED = HardThresholdChecker.EXCLUDED;

    /** 快测采集的题目 */
    public static final List<String> QUICK_QUESTIONS = List.of("undergrad_school", "gpa", "target_countries", "target_field");

    /** 提示：快测仅按院校层次和均分估算（结果页须展示，见 M4-02） */
    public static final String NOTICE_QUICK_ACADEMIC_ONLY = "quick_academic_only";

    /** 快测参与综合分的维度（其余维度未采集，权重按比例放大到这些维度上） */
    public static final Set<String> QUICK_DIMENSIONS = Set.of("academic");

    private SelectionEngine()
    {
    }

    /**
     * 完整测评或快测：对候选项目逐个评估
     *
     * @param p 背景档案
     * @param programs 候选项目（由调用方按目标国家、学位、专业方向选出）
     * @param configsByCountry 国家 / 地区代码 → 启用的引擎配置根节点
     * @param benchmarks 项目ID → 项目自己的参考线（没有的项目不放）
     * @param quick 是否快测
     * @return 评估结果
     */
    public static Evaluation evaluate(BackgroundProfile p, List<ProgramFacts> programs, Map<String, JsonNode> configsByCountry,
            Map<Long, ProgramBenchmark> benchmarks, boolean quick)
    {
        Map<String, SubScoreCalculator.Result> subScores = new LinkedHashMap<>();
        List<ProgramEvaluation> results = new ArrayList<>();
        for (ProgramFacts program : programs)
        {
            JsonNode config = configsByCountry.get(program.country());
            if (config == null)
            {
                throw new IllegalArgumentException("缺少国家 / 地区 " + program.country() + " 的引擎配置");
            }
            SubScoreCalculator.Result sub = subScores.computeIfAbsent(program.country(),
                    k -> SubScoreCalculator.compute(p, config.path("scoring")));
            HardThresholdChecker.Result threshold = HardThresholdChecker.check(p, program, config, quick);
            CompositeScorer.Composite composite = CompositeScorer.compute(sub.scores(), config, p.getTargetFieldGroup(),
                    dropped(program, config, quick));
            AdmissionEstimator.Estimate estimate = AdmissionEstimator.estimate(composite.c(), program.difficulty(),
                    benchmarks.get(program.programId()), config);
            String group;
            if (HardThresholdChecker.PASS.equals(threshold.status()))
            {
                group = estimate.tier();
            }
            else
            {
                group = EXCLUDED.equals(threshold.status()) ? EXCLUDED : NEEDS_IMPROVEMENT;
            }
            results.add(new ProgramEvaluation(program, config.path("version").asString(""), threshold, composite, estimate, group));
        }
        return new Evaluation(quick ? MODE_QUICK : MODE_FULL, Collections.unmodifiableMap(subScores), List.copyOf(results),
                quick ? List.of(NOTICE_QUICK_ACADEMIC_ONLY) : List.of());
    }

    /**
     * 快测：只取快测题目的答案归一化后评估
     *
     * @param answers 答案（只使用 {@link #QUICK_QUESTIONS} 中的题目）
     * @param rules 换算表
     * @param tierLookup 国内高校ID → 层次
     * @param today 计算日期
     * @param programs 候选项目
     * @param configsByCountry 国家 / 地区代码 → 启用的引擎配置根节点
     * @param benchmarks 项目ID → 项目自己的参考线
     * @return 评估结果
     */
    public static Evaluation quick(Map<String, JsonNode> answers, NormalizationRules rules, Function<Long, String> tierLookup,
            LocalDate today, List<ProgramFacts> programs, Map<String, JsonNode> configsByCountry, Map<Long, ProgramBenchmark> benchmarks)
    {
        Map<String, JsonNode> quickAnswers = new LinkedHashMap<>();
        for (String q : QUICK_QUESTIONS)
        {
            if (answers.get(q) != null)
            {
                quickAnswers.put(q, answers.get(q));
            }
        }
        BackgroundProfile p = ProfileNormalizer.normalize(quickAnswers, rules, tierLookup, today);
        return evaluate(p, programs, configsByCountry, benchmarks, true);
    }

    /**
     * 不参与综合分的维度：快测只保留快测维度；项目不要求标化且配置为 renormalize 时去掉标化
     */
    private static Set<String> dropped(ProgramFacts program, JsonNode config, boolean quick)
    {
        Set<String> dropped = new HashSet<>();
        if (quick)
        {
            for (String dim : SubScoreCalculator.DIMENSIONS)
            {
                if (!QUICK_DIMENSIONS.contains(dim))
                {
                    dropped.add(dim);
                }
            }
            return dropped;
        }
        ProgramFacts.Requirement req = program.requirement();
        if (req != null && "none".equals(req.testRequirement())
                && "renormalize".equals(config.path("scoring").path("test_scores").path("not_required_policy").asString("")))
        {
            dropped.add("test_scores");
        }
        return dropped;
    }

    /**
     * 单个项目的评估
     *
     * @param program 项目事实
     * @param configVersion 所用引擎配置版本
     * @param threshold 硬门槛结果
     * @param composite 综合分
     * @param estimate 录取可能性与档位（P 只给管理端）
     * @param group 分组：reach / match / safety / unrated / needs_improvement / excluded
     */
    public record ProgramEvaluation(ProgramFacts program, String configVersion, HardThresholdChecker.Result threshold,
            CompositeScorer.Composite composite, AdmissionEstimator.Estimate estimate, String group)
    {
    }

    /**
     * 评估结果
     *
     * @param mode full / quick
     * @param subScores 国家 / 地区代码 → 子分
     * @param results 各项目评估（按传入顺序）
     * @param notices 需要向学生展示的提示码（快测：quick_academic_only，仅按院校层次和均分估算，未计入科研、实习、标化等）
     */
    public record Evaluation(String mode, Map<String, SubScoreCalculator.Result> subScores, List<ProgramEvaluation> results,
            List<String> notices)
    {
        /**
         * 取某一分组的项目
         *
         * @param group 分组
         * @return 该组的项目评估（保持顺序）
         */
        public List<ProgramEvaluation> group(String group)
        {
            return results.stream().filter(r -> group.equals(r.group())).toList();
        }
    }
}
