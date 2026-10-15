package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * 引擎测试台：用已知结果的案例批量运行引擎并统计命中率（口径见 seed/engine-cases/README.md 第 4 节）
 *
 * 1. 档位命中率：只统计通过硬门槛、能分档、且有顾问档位的行；引擎档位 = 顾问档位记为命中；输出每档命中率与 3×3 混淆矩阵；
 * 2. 严重错档：顾问与引擎相差两档（冲 ↔ 保）；
 * 3. 硬门槛：引擎判“补足后可申”的行单独列出，实际被录取的记为硬门槛误判；案例填了 expected_threshold（按官网要求判断）时统计硬门槛一致率；
 * 4. 结果一致性：按引擎档位分组的实际录取率，每组少于 10 行标为样本不足。
 * 纯函数：同一输入得到同一输出。
 *
 * @author ygb
 */
public final class CaseBench
{
    /** 全部国家的汇总键 */
    public static final String ALL = "ALL";

    /** 结果一致性：每组至少多少行才有参考意义 */
    public static final int MIN_GROUP_SIZE = 10;

    private CaseBench()
    {
    }

    /**
     * 运行全部案例
     *
     * @param cases 案例
     * @param configsByCountry 国家 / 地区代码 → 引擎配置根节点
     * @return 报告
     */
    public static Report run(List<BenchCase> cases, Map<String, JsonNode> configsByCountry)
    {
        List<CaseResult> results = new ArrayList<>();
        for (BenchCase c : cases)
        {
            JsonNode config = configsByCountry.get(c.program().country());
            if (config == null)
            {
                results.add(new CaseResult(c, null, null, null, null, null, null, List.of(), null, false, null, false, "没有该国家 / 地区的引擎配置"));
                continue;
            }
            SelectionEngine.ProgramEvaluation e = SelectionEngine
                    .evaluate(c.profile(), List.of(c.program()), Map.of(c.program().country(), config), Map.of(), false).results().get(0);
            String group = e.group();
            boolean tiered = AdmissionEstimator.REACH.equals(group) || AdmissionEstimator.MATCH.equals(group)
                    || AdmissionEstimator.SAFETY.equals(group);
            Boolean tierHit = tiered && c.consultantTier() != null ? c.consultantTier().equals(group) : null;
            boolean severe = tiered && c.consultantTier() != null && distance(c.consultantTier(), group) == 2;
            String engineThreshold = SelectionEngine.NEEDS_IMPROVEMENT.equals(group) ? SelectionEngine.NEEDS_IMPROVEMENT
                    : SelectionEngine.EXCLUDED.equals(group) ? SelectionEngine.EXCLUDED : "pass";
            Boolean thresholdHit = c.expectedThreshold() == null ? null : c.expectedThreshold().equals(engineThreshold);
            boolean misjudged = SelectionEngine.NEEDS_IMPROVEMENT.equals(group) && Boolean.TRUE.equals(c.admitted());
            results.add(new CaseResult(c, group, e.estimate().tier(), e.composite().c(), e.estimate().p(), e.estimate().t(),
                    e.estimate().tSource(), e.threshold().missing(), tierHit, severe, thresholdHit, misjudged, null));
        }
        Map<String, Summary> summaries = new LinkedHashMap<>();
        summaries.put(ALL, summarize(results));
        results.stream().map(r -> r.bench().program().country()).distinct().sorted()
                .forEach(country -> summaries.put(country, summarize(results.stream().filter(r -> country.equals(r.bench().program().country())).toList())));
        return new Report(List.copyOf(results), summaries);
    }

    private static Summary summarize(List<CaseResult> rows)
    {
        Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
        Map<String, Rate> perTier = new LinkedHashMap<>();
        for (String t : RecommendationPlanner.TIERS)
        {
            Map<String, Integer> row = new LinkedHashMap<>();
            RecommendationPlanner.TIERS.forEach(x -> row.put(x, 0));
            confusion.put(t, row);
        }
        int counted = 0;
        int hits = 0;
        int severe = 0;
        int blocked = 0;
        int other = 0;
        int errors = 0;
        int thresholdChecked = 0;
        int thresholdHits = 0;
        int misjudged = 0;
        Map<String, int[]> outcome = new LinkedHashMap<>();
        RecommendationPlanner.TIERS.forEach(t -> outcome.put(t, new int[2]));
        for (CaseResult r : rows)
        {
            if (r.error() != null)
            {
                errors++;
                continue;
            }
            if (r.tierHit() != null)
            {
                counted++;
                hits += r.tierHit() ? 1 : 0;
                confusion.get(r.bench().consultantTier()).merge(r.engineGroup(), 1, Integer::sum);
            }
            severe += r.severe() ? 1 : 0;
            if (SelectionEngine.NEEDS_IMPROVEMENT.equals(r.engineGroup()))
            {
                blocked++;
            }
            else if (!outcome.containsKey(r.engineGroup()))
            {
                other++;
            }
            if (r.thresholdHit() != null)
            {
                thresholdChecked++;
                thresholdHits += r.thresholdHit() ? 1 : 0;
            }
            misjudged += r.thresholdMisjudged() ? 1 : 0;
            if (outcome.containsKey(r.engineGroup()) && r.bench().admitted() != null)
            {
                int[] o = outcome.get(r.engineGroup());
                o[0]++;
                o[1] += r.bench().admitted() ? 1 : 0;
            }
        }
        for (String t : RecommendationPlanner.TIERS)
        {
            int total = confusion.get(t).values().stream().mapToInt(Integer::intValue).sum();
            perTier.put(t, new Rate(total, confusion.get(t).get(t), rate(confusion.get(t).get(t), total)));
        }
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        outcome.forEach((t, o) -> outcomes.put(t, new Outcome(o[0], o[1], rate(o[1], o[0]), o[0] < MIN_GROUP_SIZE)));
        return new Summary(rows.size(), counted, hits, rate(hits, counted), perTier, confusion, severe, blocked, other, errors,
                thresholdChecked, thresholdHits, rate(thresholdHits, thresholdChecked), misjudged, outcomes);
    }

    private static int distance(String a, String b)
    {
        return Math.abs(RecommendationPlanner.TIERS.indexOf(a) - RecommendationPlanner.TIERS.indexOf(b));
    }

    private static BigDecimal rate(int part, int total)
    {
        return total == 0 ? null : BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    /**
     * 一个案例
     *
     * @param caseId 案例编号
     * @param verificationLevel 核验级别（A / B / C；X 为 AI 构造的示例）
     * @param profile 申请时的背景档案
     * @param program 申请的项目
     * @param consultantTier 顾问档位（reach / match / safety；未填为 null，不进档位统计）
     * @param admitted 是否录取（候补按最终结果；未知为 null）
     * @param borderline 是否边界案例
     * @param expectedThreshold 按官网要求判断的硬门槛结果（pass / needs_improvement；未填为 null）
     */
    public record BenchCase(String caseId, String verificationLevel, BackgroundProfile profile, ProgramFacts program, String consultantTier,
            Boolean admitted, boolean borderline, String expectedThreshold)
    {
    }

    /**
     * 单个案例的结果
     *
     * @param bench 案例
     * @param engineGroup 引擎分组（reach / match / safety / unrated / needs_improvement / excluded）
     * @param engineTier 引擎按 P 的档位（未过硬门槛时也给出，供分析）
     * @param c 综合分
     * @param p 录取可能性
     * @param t 参考线
     * @param tSource 参考线来源
     * @param missing 未过的硬门槛项
     * @param tierHit 档位是否命中（不参与档位统计时为 null）
     * @param severe 是否严重错档
     * @param thresholdHit 硬门槛是否与 expected_threshold 一致（未填为 null）
     * @param thresholdMisjudged 硬门槛误判：引擎判补足后可申，实际被录取
     * @param error 无法计算的原因（正常为 null）
     */
    public record CaseResult(BenchCase bench, String engineGroup, String engineTier, BigDecimal c, BigDecimal p, BigDecimal t, String tSource,
            List<String> missing, Boolean tierHit, boolean severe, Boolean thresholdHit, boolean thresholdMisjudged, String error)
    {
    }

    /**
     * 命中率
     *
     * @param total 分母
     * @param hits 命中数
     * @param rate 命中率（4 位小数；分母为 0 时为 null）
     */
    public record Rate(int total, int hits, BigDecimal rate)
    {
    }

    /**
     * 结果一致性（按引擎档位的实际录取率）
     *
     * @param decided 有最终结果的行数
     * @param admitted 录取行数
     * @param rate 录取率
     * @param insufficient 样本不足（少于 10 行，仅供参考）
     */
    public record Outcome(int decided, int admitted, BigDecimal rate, boolean insufficient)
    {
    }

    /**
     * 汇总
     *
     * @param rows 行数
     * @param counted 参与档位统计的行数
     * @param hits 档位命中数
     * @param overall 整体命中率
     * @param perTier 每档（按顾问档位）命中率
     * @param confusion 混淆矩阵（顾问档位 → 引擎档位 → 行数）
     * @param severe 严重错档数
     * @param blocked 引擎判补足后可申的行数
     * @param other 不在考虑范围或待定档的行数
     * @param errors 无法计算的行数
     * @param thresholdChecked 填了 expected_threshold 的行数
     * @param thresholdHits 硬门槛一致的行数
     * @param thresholdRate 硬门槛一致率
     * @param thresholdMisjudged 硬门槛误判数（判补足后可申却被录取）
     * @param outcomes 结果一致性（引擎档位 → 录取率）
     */
    public record Summary(int rows, int counted, int hits, BigDecimal overall, Map<String, Rate> perTier,
            Map<String, Map<String, Integer>> confusion, int severe, int blocked, int other, int errors, int thresholdChecked,
            int thresholdHits, BigDecimal thresholdRate, int thresholdMisjudged, Map<String, Outcome> outcomes)
    {
    }

    /**
     * 测试台报告
     *
     * @param results 各案例结果（按输入顺序）
     * @param summaries 汇总（ALL 为全部，其余为各国家 / 地区）
     */
    public record Report(List<CaseResult> results, Map<String, Summary> summaries)
    {
    }
}
