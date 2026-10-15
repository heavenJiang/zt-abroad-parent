package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import tools.jackson.databind.JsonNode;

/**
 * 录取可能性与分档：P = 1 / (1 + e^{-a(C − T_eff)})，T_eff = T + t_offset（设计文档「智能选校测评算法」§4）
 *
 * T、a 优先取项目自己的参考线；没有时用项目难度档在 benchmark_defaults 中的 T 与 probability.a_default。
 * 分档用取整后的 P：P &lt; reach_below → 冲；P &gt; safety_above → 保；其余为稳。项目既无参考线又未定难度档时不分档（unrated）。
 * 指数用 StrictMath 计算，保证不同机器上同一输入得到同一输出。
 *
 * @author ygb
 */
public final class AdmissionEstimator
{
    /** 档位：冲 */
    public static final String REACH = "reach";

    /** 档位：稳 */
    public static final String MATCH = "match";

    /** 档位：保 */
    public static final String SAFETY = "safety";

    /** 档位：无法分档（缺参考线） */
    public static final String UNRATED = "unrated";

    private AdmissionEstimator()
    {
    }

    /**
     * 估计录取可能性并分档
     *
     * @param c 综合分
     * @param difficulty 项目难度档（可为 null）
     * @param benchmark 项目自己的参考线（可为 null）
     * @param config 引擎配置根节点
     * @return 估计结果
     */
    public static Estimate estimate(BigDecimal c, String difficulty, ProgramBenchmark benchmark, JsonNode config)
    {
        JsonNode prob = config.path("probability");
        BigDecimal t;
        String source;
        BigDecimal a = benchmark == null ? null : benchmark.a();
        if (benchmark != null && benchmark.t() != null)
        {
            t = benchmark.t();
            source = benchmark.source();
        }
        else
        {
            JsonNode level = difficulty == null ? null : config.path("benchmark_defaults").path("difficulty_levels").path(difficulty).get("t");
            if (level == null || !level.isNumber())
            {
                return new Estimate(null, null, null, null, null, UNRATED);
            }
            t = level.decimalValue();
            source = config.path("benchmark_defaults").path("default_source").asString("consultant_difficulty");
        }
        if (a == null)
        {
            a = number(prob, "a_default");
        }
        BigDecimal offset = prob.path("t_offset").isNumber() ? prob.path("t_offset").decimalValue() : BigDecimal.ZERO;
        BigDecimal tEff = t.add(offset);
        double exponent = -a.doubleValue() * c.subtract(tEff).doubleValue();
        double raw = 1.0 / (1.0 + StrictMath.exp(exponent));
        int scale = prob.path("rounding").path("p_scale").asInt(4);
        RoundingMode mode = CompositeScorer.roundingMode(config);
        BigDecimal p = new BigDecimal(raw).setScale(scale, mode);
        return new Estimate(t, source, a, tEff, p, tier(p, config.path("tier_cutoffs")));
    }

    /**
     * 按分界分档
     *
     * @param p 取整后的录取可能性
     * @param cutoffs 引擎配置 tier_cutoffs 节
     * @return reach / match / safety
     */
    static String tier(BigDecimal p, JsonNode cutoffs)
    {
        BigDecimal reachBelow = number(cutoffs, "reach_below");
        BigDecimal safetyAbove = number(cutoffs, "safety_above");
        if (p.compareTo(reachBelow) < 0)
        {
            return REACH;
        }
        return p.compareTo(safetyAbove) > 0 ? SAFETY : MATCH;
    }

    private static BigDecimal number(JsonNode node, String key)
    {
        JsonNode v = node.get(key);
        if (v == null || !v.isNumber())
        {
            throw new IllegalArgumentException("引擎配置缺少数值 " + key);
        }
        return v.decimalValue();
    }

    /**
     * 估计结果（P 只给管理端和测试台，学生端只看档位）
     *
     * @param t 参考线 T（不分档时为 null）
     * @param tSource T 的来源（own_cases / official_stats / consultant_difficulty）
     * @param a 稳定度
     * @param tEff 加整体偏移后的参考线
     * @param p 录取可能性（取整后）
     * @param tier reach / match / safety / unrated
     */
    public record Estimate(BigDecimal t, String tSource, BigDecimal a, BigDecimal tEff, BigDecimal p, String tier)
    {
    }
}
