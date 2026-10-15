package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 差距反算（设计文档「智能选校测评算法」§6，引擎配置 gap_analysis 节）
 *
 * 1. 冲刺校：反算 P 进入目标档（target_p）所需的综合分 C*，只在可改变的维度（标化、科研、实习）里试算动作，
 *    选出达到 C* 的最小动作组合（动作数最少，其次月数最少），并按距入学月数判断是否来得及；
 * 2. 补足后可申：对未过的硬门槛给出补足动作（雅思每次提高 0.5、德国 APS），其余项（均分、层次、先修课等）不能靠短期动作补足。
 * 纯函数：在背景档案副本上试算，不修改传入的档案。
 *
 * @author ygb
 */
public final class GapAnalyzer
{
    /** 动作：雅思提高 0.5 */
    static final String IELTS_ACTION = "ielts_plus_0_5";

    /** 雅思最多试算的提高次数（3 分） */
    static final int MAX_IELTS_STEPS = 6;

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static final BigDecimal NINE = new BigDecimal("9");

    private GapAnalyzer()
    {
    }

    /**
     * 对一个项目做差距反算
     *
     * @param p 背景档案
     * @param e 该项目的评估
     * @param config 该项目所在国家的引擎配置根节点
     * @return 差距；无需反算（已在目标档以上、或无法分档）时为 null
     */
    public static Gap analyze(BackgroundProfile p, SelectionEngine.ProgramEvaluation e, JsonNode config)
    {
        boolean blocked = SelectionEngine.NEEDS_IMPROVEMENT.equals(e.group());
        boolean reach = AdmissionEstimator.REACH.equals(e.estimate().tier());
        if (!blocked && !reach)
        {
            return null;
        }
        JsonNode gapCfg = config.path("gap_analysis");
        List<ThresholdFix> fixes = blocked ? thresholdFixes(p, e, config, gapCfg) : List.of();
        if (!reach)
        {
            return new Gap(e.program().programId(), null, null, List.of(), List.of(), null, 0, true, null, fixes);
        }

        BigDecimal targetP = number(gapCfg, "target_p");
        double logit = StrictMath.log(targetP.doubleValue() / (1 - targetP.doubleValue()));
        BigDecimal required = e.estimate().tEff().add(BigDecimal.valueOf(logit / e.estimate().a().doubleValue()))
                .setScale(2, RoundingMode.CEILING);
        BigDecimal gap = required.subtract(e.composite().c()).max(BigDecimal.ZERO);

        Set<String> dropped = new HashSet<>(e.composite().removed());
        List<Action> actions = new ArrayList<>();
        List<ActionGain> options = new ArrayList<>();
        Set<String> changeable = new HashSet<>();
        gapCfg.path("changeable").forEach(c -> changeable.add(c.asString()));
        for (JsonNode a : gapCfg.path("actions"))
        {
            String dim = a.path("dimension").asString("");
            Action action = new Action(a.path("code").asString(""), dim, a.path("min_months").asInt(0));
            if (!changeable.contains(dim) || "language".equals(dim) || !applicable(p, action))
            {
                continue;
            }
            BigDecimal gain = composite(apply(p, List.of(action)), config, dropped).subtract(e.composite().c());
            if (gain.signum() > 0)
            {
                actions.add(action);
                options.add(new ActionGain(action.code(), dim, gain, action.months()));
            }
        }

        // 枚举动作组合（最多 4 个动作，16 种），取达到 C* 的最小组合
        List<Action> best = null;
        BigDecimal bestGain = BigDecimal.ZERO;
        int bestMonths = 0;
        for (int mask = 1; mask < (1 << actions.size()); mask++)
        {
            List<Action> combo = new ArrayList<>();
            int months = 0;
            for (int i = 0; i < actions.size(); i++)
            {
                if ((mask & (1 << i)) != 0)
                {
                    combo.add(actions.get(i));
                    months += actions.get(i).months();
                }
            }
            BigDecimal gain = composite(apply(p, combo), config, dropped).subtract(e.composite().c());
            if (gain.compareTo(gap) < 0)
            {
                continue;
            }
            if (best == null || combo.size() < best.size() || combo.size() == best.size() && months < bestMonths)
            {
                best = combo;
                bestGain = gain;
                bestMonths = months;
            }
        }
        boolean achievable = best != null;
        if (!achievable)
        {
            // 全部动作都做也不够：给出全部动作的效果，交顾问判断
            best = actions;
            bestMonths = actions.stream().mapToInt(Action::months).sum();
            bestGain = actions.isEmpty() ? BigDecimal.ZERO : composite(apply(p, actions), config, dropped).subtract(e.composite().c());
        }
        Boolean inTime = null;
        if (p.getMonthsToIntake() != null && !best.isEmpty())
        {
            inTime = bestMonths <= p.getMonthsToIntake() - gapCfg.path("application_lead_months").asInt(0);
        }
        return new Gap(e.program().programId(), required, gap, List.copyOf(options), best.stream().map(Action::code).toList(), bestGain,
                bestMonths, achievable, inTime, fixes);
    }

    /**
     * 未过硬门槛的补足动作
     */
    private static List<ThresholdFix> thresholdFixes(BackgroundProfile p, SelectionEngine.ProgramEvaluation e, JsonNode config,
            JsonNode gapCfg)
    {
        List<ThresholdFix> fixes = new ArrayList<>();
        for (String item : e.threshold().missing())
        {
            if ("language_en".equals(item))
            {
                fixes.add(ielts(p, e, config, gapCfg));
            }
            else if ("aps".equals(item))
            {
                int lead = config.path("country_rules").path("aps").path("lead_time_months").asInt(0);
                fixes.add(new ThresholdFix(item, "do_aps", 1, lead));
            }
            else
            {
                // 均分、层次、先修课、工作年限、跨专业、课程匹配：不能靠短期动作补足，交顾问
                fixes.add(new ThresholdFix(item, null, 0, null));
            }
        }
        return List.copyOf(fixes);
    }

    /**
     * 雅思：每次提高 0.5（总分与小分同时提高）直到语言达标；按原始分比较（托福等）或没有雅思等效分时无法试算
     */
    private static ThresholdFix ielts(BackgroundProfile p, SelectionEngine.ProgramEvaluation e, JsonNode config, JsonNode gapCfg)
    {
        int months = 0;
        for (JsonNode a : gapCfg.path("actions"))
        {
            if (IELTS_ACTION.equals(a.path("code").asString("")))
            {
                months = a.path("min_months").asInt(0);
            }
        }
        if (p.getIeltsEquivTotal() != null)
        {
            for (int k = 1; k <= MAX_IELTS_STEPS; k++)
            {
                BackgroundProfile c = p.copy();
                BigDecimal step = HALF.multiply(BigDecimal.valueOf(k));
                c.setIeltsEquivTotal(p.getIeltsEquivTotal().add(step).min(NINE));
                if (p.getIeltsEquivMinSub() != null)
                {
                    c.setIeltsEquivMinSub(p.getIeltsEquivMinSub().add(step).min(NINE));
                }
                HardThresholdChecker.Result r = HardThresholdChecker.check(c, e.program(), config, e.threshold().flags().contains("quick_mode"));
                if (!r.missing().contains("language_en"))
                {
                    return new ThresholdFix("language_en", IELTS_ACTION, k, k * months);
                }
            }
        }
        return new ThresholdFix("language_en", null, 0, null);
    }

    private static boolean applicable(BackgroundProfile p, Action a)
    {
        if ("test_scores".equals(a.dimension()))
        {
            // 只对已考 GRE 的学生试算“GRE 提高 10 分”；GMAT 与未考的提升交顾问
            return "gre_plus_10".equals(a.code()) && "gre".equals(p.getStdTestType()) && p.getStdTestTotal() != null;
        }
        return "research".equals(a.dimension()) || "work_experience".equals(a.dimension());
    }

    /**
     * 在副本上应用动作
     */
    static BackgroundProfile apply(BackgroundProfile p, List<Action> actions)
    {
        BackgroundProfile c = p.copy();
        for (Action a : actions)
        {
            switch (a.dimension())
            {
                case "test_scores":
                    c.setStdTestTotal(Math.min(340, c.getStdTestTotal() + 10));
                    break;
                case "research":
                    if (c.getResearchLevel() == null || "none".equals(c.getResearchLevel()))
                    {
                        c.setResearchLevel("lab_project");
                        c.setResearchCount(1);
                        c.setResearchRelevant("yes");
                    }
                    else
                    {
                        c.setResearchCount((c.getResearchCount() == null ? 1 : c.getResearchCount()) + 1);
                    }
                    break;
                default:
                    // work_experience：新增 3 个月相关实习
                    c.setWorkMonths((c.getWorkMonths() == null ? 0 : c.getWorkMonths()) + 3);
                    c.setWorkCount((c.getWorkCount() == null ? 0 : c.getWorkCount()) + 1);
                    if (c.getWorkRelevant() == null)
                    {
                        c.setWorkRelevant("yes");
                    }
                    if (c.getWorkCompanyTier() == null)
                    {
                        c.setWorkCompanyTier("normal");
                    }
                    break;
            }
        }
        return c;
    }

    private static BigDecimal composite(BackgroundProfile p, JsonNode config, Set<String> dropped)
    {
        return CompositeScorer.compute(SubScoreCalculator.compute(p, config.path("scoring")).scores(), config, p.getTargetFieldGroup(), dropped)
                .c();
    }

    private static BigDecimal number(JsonNode node, String key)
    {
        JsonNode v = node.get(key);
        if (v == null || !v.isNumber())
        {
            throw new IllegalArgumentException("引擎配置缺少数值 gap_analysis." + key);
        }
        return v.decimalValue();
    }

    /**
     * 可改变维度上的一个动作
     *
     * @param code 动作编码
     * @param dimension 维度
     * @param months 所需月数
     */
    record Action(String code, String dimension, int months)
    {
    }

    /**
     * 单个动作对综合分的提升
     *
     * @param code 动作编码（gre_plus_10 / add_lab_research / add_related_internship_3m）
     * @param dimension 维度
     * @param gainC 综合分提升
     * @param months 所需月数
     */
    public record ActionGain(String code, String dimension, BigDecimal gainC, int months)
    {
    }

    /**
     * 未过硬门槛项的补足方式
     *
     * @param item 检查项
     * @param action 补足动作（ielts_plus_0_5 / do_aps；不能靠短期动作补足时为 null）
     * @param steps 动作次数
     * @param months 所需月数（不能补足时为 null）
     */
    public record ThresholdFix(String item, String action, int steps, Integer months)
    {
    }

    /**
     * 差距反算结果
     *
     * @param programId 项目ID
     * @param requiredC 进入目标档所需综合分（只对冲刺档计算，否则为 null）
     * @param gapC 还差的综合分
     * @param options 各动作单独的提升
     * @param plan 推荐的动作组合（达到目标的最小组合；达不到时为全部动作）
     * @param planGain 组合后的综合分提升
     * @param planMonths 组合所需月数（按依次完成累加）
     * @param achievable 组合能否达到目标档
     * @param inTime 是否来得及（距入学月数未知时为 null）
     * @param thresholdFixes 未过硬门槛项的补足方式（只对补足后可申的项目）
     */
    public record Gap(Long programId, BigDecimal requiredC, BigDecimal gapC, List<ActionGain> options, List<String> plan,
            BigDecimal planGain, int planMonths, boolean achievable, Boolean inTime, List<ThresholdFix> thresholdFixes)
    {
    }
}
