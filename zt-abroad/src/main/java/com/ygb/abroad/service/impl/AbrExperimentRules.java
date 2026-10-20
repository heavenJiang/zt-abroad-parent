package com.ygb.abroad.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * A/B 实验规则（纯计算）：分组校验、确定性分组、双比例检验与结论
 * <ul>
 * <li>分组：按“实验ID:单元”的 CRC32 落到权重区间，同一单元总在同一组；</li>
 * <li>评估：每个实验组与对照组做双比例 z 检验（双侧）；各组样本都达到下限后，比对照显著更好的组中比率最高者胜出；</li>
 * <li>到最长天数仍没有胜出组时保留对照（样本不足也一样），结论写明原因。</li>
 * </ul>
 *
 * @author ygb
 */
final class AbrExperimentRules
{
    static final String TYPE_HOOK_COPY = "hook_copy";

    static final List<String> METRICS = List.of("click_rate", "tool_rate");

    static final String STATUS_DRAFT = "draft";

    static final String STATUS_RUNNING = "running";

    static final String STATUS_READY = "ready";

    static final String STATUS_CONCLUDED = "concluded";

    static final String STATUS_STOPPED = "stopped";

    /** 文末引导最长字数 */
    static final int TEMPLATE_MAX = 200;

    /**
     * 分组
     */
    record Variant(String code, String name, String template, int weight, boolean control)
    {
    }

    /**
     * 一组的样本：n 分母、x 转化
     */
    record Stat(String code, long n, long x)
    {
        double rate()
        {
            return n <= 0 ? 0 : (double) Math.min(x, n) / n;
        }
    }

    /**
     * 评估结论
     *
     * @param decision continue 继续 / winner 有胜出组 / keep_control 保留对照
     * @param winner 胜出组（保留对照时为对照组）
     * @param reason 说明
     * @param rows 各组 {code, n, x, rate, pValue, better}
     */
    record Decision(String decision, String winner, String reason, List<Map<String, Object>> rows)
    {
    }

    private AbrExperimentRules()
    {
    }

    /**
     * 解析并校验分组：2–4 组、编码不重复、恰好一个对照、权重 1–100、文末引导须含 {hook}（平台引导含口令时须含 {codeword}）
     *
     * @param node 分组 JSON 数组
     * @param needCodeword 是否须含 {codeword}
     * @return 分组
     */
    static List<Variant> variants(JsonNode node, boolean needCodeword)
    {
        if (node == null || !node.isArray() || node.size() < 2 || node.size() > 4)
        {
            throw new ServiceException("实验须有 2–4 个分组");
        }
        List<Variant> out = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        int controls = 0;
        for (JsonNode v : node)
        {
            String code = v.path("code").asString("").trim();
            if (!code.matches("^[A-Za-z0-9_]{1,16}$") || !codes.add(code))
            {
                throw new ServiceException("分组编码须为 1–16 位字母、数字或下划线且不重复");
            }
            String name = StringUtils.substring(v.path("name").asString(code).trim(), 0, 50);
            String template = v.path("template").asString("").trim();
            if (template.isEmpty() || template.length() > TEMPLATE_MAX)
            {
                throw new ServiceException("分组 " + code + " 的文末引导须为 1–" + TEMPLATE_MAX + " 字");
            }
            if (!template.contains("{hook}"))
            {
                throw new ServiceException("分组 " + code + " 的文末引导须包含 {hook}（工具名称）");
            }
            if (needCodeword && !template.contains("{codeword}"))
            {
                throw new ServiceException("该平台靠私信口令进入工具，分组 " + code + " 的文末引导须包含 {codeword}");
            }
            int weight = v.path("weight").asInt(0);
            if (weight < 1 || weight > 100)
            {
                throw new ServiceException("分组 " + code + " 的权重须为 1–100");
            }
            boolean control = v.path("control").asBoolean(false);
            controls += control ? 1 : 0;
            out.add(new Variant(code, StringUtils.isBlank(name) ? code : name, template, weight, control));
        }
        if (controls != 1)
        {
            throw new ServiceException("须恰好有一个对照组");
        }
        return out;
    }

    /**
     * 确定性分组
     *
     * @param experimentId 实验ID
     * @param unitKey 单元（如 topic:12）
     * @param variants 分组
     * @return 分组
     */
    static Variant assign(Long experimentId, String unitKey, List<Variant> variants)
    {
        int total = variants.stream().mapToInt(Variant::weight).sum();
        CRC32 crc = new CRC32();
        crc.update((experimentId + ":" + unitKey).getBytes(StandardCharsets.UTF_8));
        long bucket = crc.getValue() % total;
        long acc = 0;
        for (Variant v : variants)
        {
            acc += v.weight();
            if (bucket < acc)
            {
                return v;
            }
        }
        return variants.get(variants.size() - 1);
    }

    /**
     * 评估
     *
     * @param stats 各组样本（含对照）
     * @param control 对照组编码
     * @param minSample 每组最少样本
     * @param alpha 显著性水平
     * @param elapsedDays 已运行天数
     * @param maxDays 最长天数
     * @return 结论
     */
    static Decision evaluate(List<Stat> stats, String control, long minSample, double alpha, long elapsedDays, int maxDays)
    {
        Stat c = stats.stream().filter(s -> s.code().equals(control)).findFirst().orElse(new Stat(control, 0, 0));
        boolean enough = stats.stream().allMatch(s -> s.n() >= minSample);
        List<Map<String, Object>> rows = new ArrayList<>();
        Stat best = null;
        for (Stat s : stats)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", s.code());
            row.put("n", s.n());
            row.put("x", Math.min(s.x(), s.n()));
            row.put("rate", round(s.rate(), 4));
            if (!s.code().equals(control))
            {
                double p = pValue(c, s);
                boolean better = enough && p < alpha && s.rate() > c.rate();
                row.put("pValue", round(p, 4));
                row.put("better", better);
                if (better && (best == null || s.rate() > best.rate()))
                {
                    best = s;
                }
            }
            rows.add(row);
        }
        if (best != null)
        {
            return new Decision("winner", best.code(), "分组 " + best.code() + " 比对照组显著更好（各组样本均达到 " + minSample + "）", rows);
        }
        if (elapsedDays >= maxDays)
        {
            String why = enough ? "已运行 " + elapsedDays + " 天，没有分组比对照组显著更好，保留对照"
                    : "已运行 " + elapsedDays + " 天，样本仍不足 " + minSample + "，保留对照";
            return new Decision("keep_control", control, why, rows);
        }
        return new Decision("continue", null, enough ? "暂无显著差异，继续运行" : "样本不足（每组至少 " + minSample + "），继续运行", rows);
    }

    /**
     * 双比例 z 检验（双侧）p 值；任一组样本为 0 或合并比率为 0 / 1 时为 1
     */
    static double pValue(Stat a, Stat b)
    {
        if (a.n() <= 0 || b.n() <= 0)
        {
            return 1;
        }
        double xa = Math.min(a.x(), a.n());
        double xb = Math.min(b.x(), b.n());
        double pool = (xa + xb) / (a.n() + b.n());
        if (pool <= 0 || pool >= 1)
        {
            return 1;
        }
        double se = Math.sqrt(pool * (1 - pool) * (1.0 / a.n() + 1.0 / b.n()));
        double z = Math.abs(xa / a.n() - xb / b.n()) / se;
        return Math.min(1, 2 * (1 - normalCdf(z)));
    }

    /**
     * 标准正态分布函数（Abramowitz–Stegun 7.1.26 误差函数近似，误差 &lt; 1.5e-7）
     */
    static double normalCdf(double z)
    {
        double x = Math.abs(z) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * x);
        double erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
    }

    static double round(double v, int scale)
    {
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }
}
