package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ygb.common.utils.StringUtils;

/**
 * 内容配比边界内调参（纯计算）：按各内容类型“每条内容带来的工具使用人数”微调剧本配比
 * <ul>
 * <li>只看发布数达到下限的类型；少于 2 类达标时不调；</li>
 * <li>达标类型的人均（每条内容）高于整体平均 threshold 以上加 step，低于 threshold 以上减 step；</li>
 * <li>每类不超出边界；之后逐点补齐到合计 100：先动没有调整的类型，再回退调整过的类型；补不齐则不调；</li>
 * <li>配比中没有的类型不新增，不在边界配置中的类型不动。</li>
 * </ul>
 *
 * @author ygb
 */
final class AbrMixTuner
{
    /**
     * 一类内容的表现
     *
     * @param contents 发布的内容数
     * @param toolUsers 这些内容带来的用过工具的访客数
     */
    record Perf(long contents, long toolUsers)
    {
        double perContent()
        {
            return contents <= 0 ? 0 : (double) toolUsers / contents;
        }
    }

    /**
     * 调参结果
     *
     * @param mix 新配比（没变化时与原配比相同）
     * @param changed 是否变化
     * @param reason 依据
     */
    record Result(Map<String, Integer> mix, boolean changed, String reason)
    {
    }

    private AbrMixTuner()
    {
    }

    /**
     * 解析边界配置：kind:min-max,...
     *
     * @param config 配置
     * @return 类型 → [min, max]
     */
    static Map<String, int[]> bounds(String config)
    {
        Map<String, int[]> out = new LinkedHashMap<>();
        if (StringUtils.isBlank(config))
        {
            return out;
        }
        for (String item : config.split(","))
        {
            String[] kv = item.trim().split(":");
            if (kv.length != 2)
            {
                continue;
            }
            String[] mm = kv[1].trim().split("-");
            try
            {
                int min = Integer.parseInt(mm[0].trim());
                int max = Integer.parseInt(mm[1].trim());
                if (min >= 0 && max <= 100 && min <= max)
                {
                    out.put(kv[0].trim(), new int[] { min, max });
                }
            }
            catch (RuntimeException e)
            {
                // 格式不对的项跳过
            }
        }
        return out;
    }

    /**
     * 调参
     *
     * @param current 当前配比（合计应为 100）
     * @param perf 各类型表现
     * @param bounds 边界
     * @param step 每次最多调几个百分点
     * @param minContents 每类最少发布数
     * @param threshold 相对整体平均的差异门槛（如 0.2 表示 ±20%）
     * @return 结果
     */
    static Result tune(Map<String, Integer> current, Map<String, Perf> perf, Map<String, int[]> bounds, int step, long minContents, double threshold)
    {
        Map<String, Integer> mix = new LinkedHashMap<>(current);
        int sum = mix.values().stream().mapToInt(Integer::intValue).sum();
        if (sum != 100)
        {
            return new Result(current, false, "当前配比合计不是 100，不调整");
        }
        List<String> qualified = new ArrayList<>();
        long contents = 0;
        long users = 0;
        for (String k : mix.keySet())
        {
            Perf p = perf.get(k);
            if (p != null && p.contents() >= minContents && bounds.containsKey(k))
            {
                qualified.add(k);
                contents += p.contents();
                users += p.toolUsers();
            }
        }
        if (qualified.size() < 2 || contents == 0)
        {
            return new Result(current, false, "达到 " + minContents + " 条发布的内容类型不足 2 类，保持上周配比");
        }
        double avg = (double) users / contents;
        Map<String, Integer> delta = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (String k : qualified)
        {
            double r = perf.get(k).perContent();
            int d = 0;
            if (avg > 0 && r >= avg * (1 + threshold))
            {
                d = step;
            }
            else if (r <= avg * (1 - threshold) && (avg > 0 || r < avg))
            {
                d = -step;
            }
            int[] b = bounds.get(k);
            int next = Math.max(b[0], Math.min(b[1], mix.get(k) + d));
            if (next != mix.get(k))
            {
                delta.put(k, next - mix.get(k));
                mix.put(k, next);
            }
            notes.add(k + " " + perf.get(k).contents() + " 条 / 人均 " + AbrExperimentRules.round(r, 2));
        }
        if (delta.isEmpty())
        {
            return new Result(current, false, "各类型表现接近（整体人均 " + AbrExperimentRules.round(avg, 2) + "）或已到边界，保持上周配比；" + String.join("，", notes));
        }
        if (!rebalance(mix, current, delta, bounds))
        {
            return new Result(current, false, "边界内无法把配比补齐到 100，保持上周配比");
        }
        return new Result(mix, !mix.equals(current), "整体人均 " + AbrExperimentRules.round(avg, 2) + "；" + String.join("，", notes));
    }

    /**
     * 逐点补齐到 100：先在没有调整的类型（边界内）之间轮流加减，不够再把调整过的类型往回退
     */
    private static boolean rebalance(Map<String, Integer> mix, Map<String, Integer> original, Map<String, Integer> delta, Map<String, int[]> bounds)
    {
        int diff = 100 - mix.values().stream().mapToInt(Integer::intValue).sum();
        List<String> free = new ArrayList<>();
        for (String k : mix.keySet())
        {
            if (!delta.containsKey(k) && bounds.containsKey(k))
            {
                free.add(k);
            }
        }
        diff = spread(mix, free, diff, bounds, null);
        if (diff != 0)
        {
            diff = spread(mix, new ArrayList<>(delta.keySet()), diff, bounds, original);
        }
        return diff == 0;
    }

    /**
     * 在 keys 之间每次 1 点轮流调整，直到 diff 为 0 或都到边界（limitTo 不为空时不越过原值）
     */
    private static int spread(Map<String, Integer> mix, List<String> keys, int diff, Map<String, int[]> bounds, Map<String, Integer> limitTo)
    {
        boolean moved = true;
        while (diff != 0 && moved)
        {
            moved = false;
            for (String k : keys)
            {
                if (diff == 0)
                {
                    break;
                }
                int[] b = bounds.get(k);
                int v = mix.get(k);
                int next = v + Integer.signum(diff);
                boolean inBounds = next >= b[0] && next <= b[1];
                boolean backward = limitTo == null || Math.abs(next - limitTo.get(k)) < Math.abs(v - limitTo.get(k));
                if (inBounds && backward)
                {
                    mix.put(k, next);
                    diff -= Integer.signum(diff);
                    moved = true;
                }
            }
        }
        return diff;
    }
}
