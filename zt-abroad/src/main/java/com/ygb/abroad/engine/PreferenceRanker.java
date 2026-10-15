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
 * 偏好匹配分：预算、排名 vs 就业、城市、学制各 0–100，按学生选的重要程度加权平均（设计文档「智能选校测评算法」§5，引擎配置 preference 节）
 *
 * 偏好只影响档内排序和推荐组合，不进综合分。院校库缺信息的项不计分，只给提示码。纯函数。
 *
 * @author ygb
 */
public final class PreferenceRanker
{
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    private static final BigDecimal TWELVE = new BigDecimal("12");

    /** 问卷 priority_top2 选项 → 偏好维度 */
    private static final Map<String, String> PRIORITY_KEYS = Map.of("budget", "budget", "rank_or_employment", "rank_or_employment",
            "city", "city", "short_duration", "duration");

    private PreferenceRanker()
    {
    }

    /**
     * 计算一个项目的偏好匹配分
     *
     * @param p 背景档案
     * @param program 项目事实
     * @param config 该项目所在国家的引擎配置根节点（需含 preference 节）
     * @return 偏好匹配分
     */
    public static Preference score(BackgroundProfile p, ProgramFacts program, JsonNode config)
    {
        JsonNode cfg = config.path("preference");
        if (!cfg.isObject())
        {
            throw new IllegalArgumentException("引擎配置缺少 preference 节");
        }
        ProgramFacts.Listing listing = program.listing();
        List<String> flags = new ArrayList<>();
        Map<String, BigDecimal> parts = new LinkedHashMap<>();
        if (listing == null)
        {
            flags.add("listing_missing");
        }

        // 预算
        BigDecimal cost = listing == null ? null : costWan(listing, cfg.path("cost"));
        BigDecimal limit = budgetLimit(p.getBudgetTotal(), cfg.path("budget_buckets_wan"));
        Boolean overBudget = null;
        if (limit != null)
        {
            if (cost == null)
            {
                flags.add("cost_unknown");
            }
            else
            {
                overBudget = cost.compareTo(limit) > 0;
                parts.put("budget", overBudget ? BigDecimal.ZERO : HUNDRED);
            }
        }

        // 排名 vs 就业：院校库暂无就业数据
        String rankPref = p.getRankVsEmployment() == null ? "balanced" : p.getRankVsEmployment();
        if ("employment".equals(rankPref))
        {
            flags.add("employment_data_missing");
        }
        else if (listing != null)
        {
            Integer rank = listing.ranks() == null ? null : listing.ranks().get(cfg.path("ranking_list").asString("QS"));
            if (rank == null)
            {
                flags.add("rank_missing");
            }
            else
            {
                parts.put("rank_or_employment", points(cfg.path("ranking_points"), BigDecimal.valueOf(rank), "ranking_points"));
            }
        }

        // 城市
        String cityPref = p.getCityPreference();
        if (cityPref != null && !"no_pref".equals(cityPref) && listing != null)
        {
            if (listing.city() == null)
            {
                flags.add("city_missing");
            }
            else
            {
                boolean metro = false;
                for (JsonNode c : cfg.path("metropolis_cities"))
                {
                    metro |= c.asString("").equalsIgnoreCase(listing.city());
                }
                parts.put("city", metro == "metropolis".equals(cityPref) ? HUNDRED : BigDecimal.ZERO);
            }
        }

        // 学制
        if (listing != null && listing.durationMonths() != null)
        {
            parts.put("duration", points(cfg.path("duration_points"), BigDecimal.valueOf(listing.durationMonths()), "duration_points"));
        }

        BigDecimal score = null;
        if (!parts.isEmpty())
        {
            BigDecimal high = number(cfg.path("importance"), "high");
            BigDecimal normal = number(cfg.path("importance"), "normal");
            List<String> chosen = new ArrayList<>();
            for (String v : p.getPriorityTop2())
            {
                if (PRIORITY_KEYS.containsKey(v))
                {
                    chosen.add(PRIORITY_KEYS.get(v));
                }
            }
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal weights = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : parts.entrySet())
            {
                BigDecimal w = chosen.contains(e.getKey()) ? high : normal;
                sum = sum.add(w.multiply(e.getValue()));
                weights = weights.add(w);
            }
            score = sum.divide(weights, 2, RoundingMode.HALF_UP);
        }
        return new Preference(score, Collections.unmodifiableMap(parts), cost, overBudget, List.copyOf(flags));
    }

    /**
     * 全程费用（万元人民币，1 位小数）：学费 × 学制年数 × 汇率 + 月生活费 × 学制月数；缺学费、学制或汇率时为 null
     */
    static BigDecimal costWan(ProgramFacts.Listing listing, JsonNode cost)
    {
        if (listing.tuition() == null || listing.durationMonths() == null || listing.currency() == null)
        {
            return null;
        }
        JsonNode fx = cost.path("fx_to_cny").get(listing.currency());
        JsonNode living = cost.get("living_cny_per_month");
        if (fx == null || !fx.isNumber() || living == null || !living.isNumber())
        {
            return null;
        }
        BigDecimal months = BigDecimal.valueOf(listing.durationMonths());
        BigDecimal tuition = listing.tuition().multiply(fx.decimalValue()).multiply(months).divide(TWELVE, 6, RoundingMode.HALF_UP);
        BigDecimal total = tuition.add(living.decimalValue().multiply(months));
        return total.divide(TEN_THOUSAND, 1, RoundingMode.HALF_UP);
    }

    /**
     * 预算上限（万元）；不限、未填或档位未配置时为 null
     */
    static BigDecimal budgetLimit(String bucket, JsonNode buckets)
    {
        JsonNode v = bucket == null ? null : buckets.get(bucket);
        return v != null && v.isNumber() ? v.decimalValue() : null;
    }

    private static BigDecimal points(JsonNode table, BigDecimal x, String name)
    {
        List<BigDecimal[]> pts = new ArrayList<>();
        for (JsonNode pt : table)
        {
            pts.add(new BigDecimal[] { pt.get(0).decimalValue(), pt.get(1).decimalValue() });
        }
        if (pts.isEmpty())
        {
            throw new IllegalArgumentException("引擎配置缺少 preference." + name);
        }
        pts.sort((a, b) -> a[0].compareTo(b[0]));
        return ProfileNormalizer.interpolate(pts, x).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal number(JsonNode node, String key)
    {
        JsonNode v = node.get(key);
        if (v == null || !v.isNumber())
        {
            throw new IllegalArgumentException("引擎配置缺少数值 preference.importance." + key);
        }
        return v.decimalValue();
    }

    /**
     * 偏好匹配分
     *
     * @param score 0–100（2 位小数；没有可计分的项时为 null）
     * @param parts 各项分值（budget / rank_or_employment / city / duration）
     * @param costWan 估算全程费用（万元人民币；未知为 null）
     * @param overBudget 是否超预算（学生不限预算或费用未知时为 null）
     * @param flags 提示码（listing_missing、cost_unknown、employment_data_missing、rank_missing、city_missing）
     */
    public record Preference(BigDecimal score, Map<String, BigDecimal> parts, BigDecimal costWan, Boolean overBudget, List<String> flags)
    {
    }
}
