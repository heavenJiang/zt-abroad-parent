package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 综合分：C = Σ w·s（设计文档「智能选校测评算法」§2，引擎配置 weights 节）
 *
 * 权重取 weights.by_field[专业大类]，没有时取 weights.default；缺子分或被剔除的维度（项目不要求标化、快测未采集）
 * 从计算中去掉，其余权重按比例放大（renormalize）。纯函数，结果按 probability.rounding 取整。
 *
 * @author ygb
 */
public final class CompositeScorer
{
    private CompositeScorer()
    {
    }

    /**
     * 计算综合分
     *
     * @param subScores 维度 → 子分（没有的维度视为不参与）
     * @param config 引擎配置根节点
     * @param fieldGroup 学生目标专业大类（可为 null）
     * @param dropped 需要剔除的维度
     * @return 综合分与实际使用的权重
     */
    public static Composite compute(Map<String, BigDecimal> subScores, JsonNode config, String fieldGroup, Set<String> dropped)
    {
        JsonNode weights = selectWeights(config.path("weights"), fieldGroup);
        Map<String, BigDecimal> used = new LinkedHashMap<>();
        List<String> removed = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (String dim : SubScoreCalculator.DIMENSIONS)
        {
            JsonNode w = weights.get(dim);
            if (w == null || !w.isNumber())
            {
                throw new IllegalArgumentException("引擎配置缺少权重 weights." + dim);
            }
            BigDecimal weight = w.decimalValue();
            if (weight.signum() == 0)
            {
                continue;
            }
            if (dropped.contains(dim) || subScores.get(dim) == null)
            {
                removed.add(dim);
                continue;
            }
            used.put(dim, weight);
            total = total.add(weight);
        }
        if (total.signum() <= 0)
        {
            throw new IllegalArgumentException("可用维度的权重之和为 0，无法计算综合分");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : used.entrySet())
        {
            sum = sum.add(e.getValue().multiply(subScores.get(e.getKey())));
        }
        int scale = config.path("probability").path("rounding").path("c_scale").asInt(2);
        RoundingMode mode = roundingMode(config);
        BigDecimal c = sum.divide(total, scale + 6, RoundingMode.HALF_UP).setScale(scale, mode);
        if (!removed.isEmpty())
        {
            // 记录放大后的实际权重，便于留痕和顾问核对
            BigDecimal weightSum = total;
            used.replaceAll((k, v) -> v.divide(weightSum, 6, RoundingMode.HALF_UP).stripTrailingZeros());
        }
        return new Composite(c, Collections.unmodifiableMap(used), List.copyOf(removed));
    }

    /**
     * 选择权重组：by_field 中有该专业大类时用它，否则用 default
     */
    static JsonNode selectWeights(JsonNode weights, String fieldGroup)
    {
        JsonNode byField = fieldGroup == null ? null : weights.path("by_field").get(fieldGroup);
        if (byField != null && byField.isObject())
        {
            return byField;
        }
        JsonNode def = weights.get("default");
        if (def == null || !def.isObject())
        {
            throw new IllegalArgumentException("引擎配置缺少 weights.default");
        }
        return def;
    }

    /**
     * 取整方式（probability.rounding.mode，缺省 HALF_UP）
     */
    static RoundingMode roundingMode(JsonNode config)
    {
        return RoundingMode.valueOf(config.path("probability").path("rounding").path("mode").asString("HALF_UP"));
    }

    /**
     * 综合分结果
     *
     * @param c 综合分（0–100）
     * @param weights 实际使用的权重（有维度被剔除时为放大后的值）
     * @param removed 被剔除的维度
     */
    public record Composite(BigDecimal c, Map<String, BigDecimal> weights, List<String> removed)
    {
    }
}
