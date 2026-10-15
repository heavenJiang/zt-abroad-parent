package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 推荐方案：档内偏好排序 → 推荐组合（默认 2 冲 + 4 稳 + 2 保）与约束 → 差距反算 → 提示（设计文档「智能选校测评算法」§5、§6、§8）
 *
 * 组合约束：超预算的项目不进组合；至少一所保底在首选国家 / 地区（第一个目标国家），没有时用首选国家最好的保底项目替换；
 * 学生目标中有“保底国家”（澳洲）时，保底位优先从该国项目补足。纯函数，排序在分数相同时按 P、项目ID 决定，保证同一输入同一输出。
 *
 * @author ygb
 */
public final class RecommendationPlanner
{
    /** 参与组合的档位（与 portfolio.default_mix 的键一致） */
    public static final List<String> TIERS = List.of(AdmissionEstimator.REACH, AdmissionEstimator.MATCH, AdmissionEstimator.SAFETY);

    /** 没有偏好分的项目排序时视为 50 分 */
    private static final BigDecimal NEUTRAL = new BigDecimal("50");

    /** 档内排序：偏好分高在前，其次 P 高在前，最后项目ID 小在前 */
    private static final Comparator<Ranked> ORDER = Comparator
            .comparing((Ranked r) -> r.preference().score() == null ? NEUTRAL : r.preference().score(), Comparator.reverseOrder())
            .thenComparing(r -> r.evaluation().estimate().p(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(r -> r.evaluation().program().programId(), Comparator.nullsLast(Comparator.naturalOrder()));

    private RecommendationPlanner()
    {
    }

    /**
     * 生成推荐方案
     *
     * @param p 背景档案
     * @param evaluation 引擎评估结果
     * @param configsByCountry 国家 / 地区代码 → 引擎配置根节点
     * @return 推荐方案
     */
    public static Plan plan(BackgroundProfile p, SelectionEngine.Evaluation evaluation, Map<String, JsonNode> configsByCountry)
    {
        Map<String, List<Ranked>> tiers = new LinkedHashMap<>();
        TIERS.forEach(t -> tiers.put(t, new ArrayList<>()));
        List<Ranked> needsImprovement = new ArrayList<>();
        List<Ranked> unrated = new ArrayList<>();
        List<GapAnalyzer.Gap> gaps = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>();
        for (SelectionEngine.ProgramEvaluation e : evaluation.results())
        {
            JsonNode config = config(configsByCountry, e.program().country());
            if (SelectionEngine.EXCLUDED.equals(e.group()))
            {
                continue;
            }
            Ranked r = new Ranked(e, PreferenceRanker.score(p, e.program(), config));
            flags.addAll(r.preference().flags());
            if (tiers.containsKey(e.group()))
            {
                tiers.get(e.group()).add(r);
            }
            else if (SelectionEngine.NEEDS_IMPROVEMENT.equals(e.group()))
            {
                needsImprovement.add(r);
            }
            else
            {
                unrated.add(r);
            }
            GapAnalyzer.Gap gap = GapAnalyzer.analyze(p, e, config);
            if (gap != null)
            {
                gaps.add(gap);
            }
        }
        tiers.values().forEach(list -> list.sort(ORDER));
        needsImprovement.sort(ORDER);
        unrated.sort(ORDER);

        String first = p.getTargetCountries().isEmpty() ? null : p.getTargetCountries().get(0);
        JsonNode mixConfig = first != null && configsByCountry.containsKey(first) ? configsByCountry.get(first)
                : evaluation.results().isEmpty() ? null : config(configsByCountry, evaluation.results().get(0).program().country());
        List<Hint> hints = new ArrayList<>();
        Map<String, List<Ranked>> portfolio = new LinkedHashMap<>();
        if (mixConfig != null)
        {
            portfolio = portfolio(tiers, mixConfig, first, configsByCountry, p, hints);
        }
        countryHints(evaluation, configsByCountry, hints);
        if (flags.contains("employment_data_missing"))
        {
            hints.add(new Hint("employment_data_missing", null, List.of()));
        }
        return new Plan(copy(tiers), List.copyOf(needsImprovement), List.copyOf(unrated), copy(portfolio), List.copyOf(gaps), List.copyOf(hints),
                evaluation.notices());
    }

    /**
     * 推荐组合
     */
    private static Map<String, List<Ranked>> portfolio(Map<String, List<Ranked>> tiers, JsonNode config, String first,
            Map<String, JsonNode> configs, BackgroundProfile p, List<Hint> hints)
    {
        JsonNode mix = config.path("portfolio").path("default_mix");
        Set<String> constraints = new LinkedHashSet<>();
        config.path("portfolio").path("constraints").forEach(c -> constraints.add(c.asString()));
        boolean budget = constraints.contains("total_budget_within_limit");
        Map<String, List<Ranked>> picks = new LinkedHashMap<>();
        List<Long> overBudget = new ArrayList<>();
        for (String tier : TIERS)
        {
            List<Ranked> candidates = new ArrayList<>();
            for (Ranked r : tiers.get(tier))
            {
                if (budget && Boolean.TRUE.equals(r.preference().overBudget()))
                {
                    overBudget.add(r.evaluation().program().programId());
                }
                else
                {
                    candidates.add(r);
                }
            }
            if (AdmissionEstimator.SAFETY.equals(tier))
            {
                // 保底国家（如澳洲）的项目优先补足保底位，其余保持偏好顺序
                candidates.sort(Comparator.comparing((Ranked r) -> !safetyCountry(configs, r.evaluation().program().country(), p)));
            }
            int n = mix.path(tier).asInt(0);
            List<Ranked> chosen = new ArrayList<>(candidates.subList(0, Math.min(n, candidates.size())));
            if (AdmissionEstimator.SAFETY.equals(tier) && n > 0 && first != null
                    && constraints.contains("at_least_one_safety_in_first_choice_country")
                    && chosen.stream().noneMatch(r -> first.equals(r.evaluation().program().country())))
            {
                Ranked home = candidates.stream().filter(r -> first.equals(r.evaluation().program().country())).findFirst().orElse(null);
                if (home == null)
                {
                    hints.add(new Hint("no_safety_in_first_choice", first, List.of()));
                }
                else
                {
                    if (chosen.size() >= n)
                    {
                        chosen.remove(chosen.size() - 1);
                    }
                    chosen.add(home);
                }
            }
            if (chosen.size() < n)
            {
                hints.add(new Hint("tier_shortage", null, List.of(tier, String.valueOf(n - chosen.size()))));
            }
            picks.put(tier, chosen);
        }
        if (!overBudget.isEmpty())
        {
            hints.add(new Hint("over_budget", null, overBudget.stream().map(String::valueOf).toList()));
        }
        return picks;
    }

    /**
     * 国家特例提示：保底档不足时提示混申（新加坡、中国香港）
     */
    private static void countryHints(SelectionEngine.Evaluation evaluation, Map<String, JsonNode> configs, List<Hint> hints)
    {
        Set<String> countries = new LinkedHashSet<>();
        evaluation.results().forEach(r -> countries.add(r.program().country()));
        for (String country : countries)
        {
            JsonNode rule = config(configs, country).path("country_rules").path("mix_application_hint");
            if (!rule.isObject())
            {
                continue;
            }
            long safety = evaluation.results().stream()
                    .filter(r -> country.equals(r.program().country()) && AdmissionEstimator.SAFETY.equals(r.group())).count();
            if (safety < rule.path("when_safety_count_below").asInt(1))
            {
                List<String> suggest = new ArrayList<>();
                rule.path("suggest_countries").forEach(c -> suggest.add(c.asString()));
                hints.add(new Hint("mix_application", country, List.copyOf(suggest)));
            }
        }
    }

    private static boolean safetyCountry(Map<String, JsonNode> configs, String country, BackgroundProfile p)
    {
        return p.getTargetCountries().contains(country)
                && config(configs, country).path("country_rules").path("safety_country").path("value").asBoolean(false);
    }

    private static JsonNode config(Map<String, JsonNode> configs, String country)
    {
        JsonNode config = configs.get(country);
        if (config == null)
        {
            throw new IllegalArgumentException("缺少国家 / 地区 " + country + " 的引擎配置");
        }
        return config;
    }

    private static Map<String, List<Ranked>> copy(Map<String, List<Ranked>> map)
    {
        Map<String, List<Ranked>> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(out);
    }

    /**
     * 带偏好分的项目评估
     *
     * @param evaluation 引擎评估
     * @param preference 偏好匹配分
     */
    public record Ranked(SelectionEngine.ProgramEvaluation evaluation, PreferenceRanker.Preference preference)
    {
    }

    /**
     * 提示
     *
     * @param code 提示码（tier_shortage 某档不足、no_safety_in_first_choice 首选国家没有保底、over_budget 超预算未进组合、
     *            mix_application 建议混申、employment_data_missing 院校库无就业数据）
     * @param country 相关国家 / 地区（可为 null）
     * @param detail 补充信息（tier_shortage：[档位, 缺几所]；over_budget：项目ID；mix_application：建议混申的国家）
     */
    public record Hint(String code, String country, List<String> detail)
    {
    }

    /**
     * 推荐方案
     *
     * @param tiers 冲 / 稳 / 保各档全部项目（档内按偏好排序）
     * @param needsImprovement 补足后可申（按偏好排序）
     * @param unrated 通过硬门槛但缺参考线、待顾问定档的项目（按偏好排序）
     * @param portfolio 推荐组合（各档选中的项目）
     * @param gaps 差距反算（冲刺校与补足后可申的项目）
     * @param hints 提示
     * @param notices 需要向学生展示的提示码（来自评估，如快测）
     */
    public record Plan(Map<String, List<Ranked>> tiers, List<Ranked> needsImprovement, List<Ranked> unrated, Map<String, List<Ranked>> portfolio,
            List<GapAnalyzer.Gap> gaps, List<Hint> hints, List<String> notices)
    {
    }
}
