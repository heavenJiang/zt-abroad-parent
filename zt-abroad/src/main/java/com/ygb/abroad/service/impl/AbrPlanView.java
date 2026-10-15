package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.engine.HardThresholdChecker;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.service.IAbrSelectionService;
import tools.jackson.databind.JsonNode;

/**
 * 我的选校单的纯函数部分：逐个项目的当前分组、逐项达标与档位分布提示（不访问数据库）
 *
 * 只取引擎的分组与硬门槛明细，不含综合分、录取概率与参考线；院校背景类检查项不展示要求与学生情况；
 * 项目超过 90 天未核实时不展示要求数值。分布提示以首选国家 / 地区（选校单中项目最多的国家）引擎配置的推荐组合为参照。
 *
 * @author ygb
 */
final class AbrPlanView
{
    /** 口径：完整档案 */
    static final String BASIS_FULL = "full";

    /** 口径：只有院校与均分，仅按学术估算 */
    static final String BASIS_QUICK = "quick";

    /** 口径：背景信息不足，不计算 */
    static final String BASIS_NONE = "none";

    /** 项目状态：已计算 */
    static final String STATUS_OK = "ok";

    /** 项目状态：补充背景后显示 */
    static final String STATUS_NEED_PROFILE = "need_profile";

    /** 分组顺序（tierCounts 的键） */
    static final List<String> GROUPS = List.of("reach", "match", "safety", "unrated", SelectionEngine.NEEDS_IMPROVEMENT, SelectionEngine.EXCLUDED);

    private AbrPlanView()
    {
    }

    /**
     * 组装选校单
     *
     * @param basis 计算口径
     * @param items 选校单条目（按加入顺序，含已下线的）
     * @param programs 项目ID → 院校库记录（取核对日期）
     * @param run 引擎评估（basis 为 none 时为 null）
     * @param countryLabel 国家 / 地区代码 → 名称
     * @param missingInfo 背景信息不足时建议补充的题目
     * @param today 今天（北京时间）
     * @return 选校单
     */
    static AppPlanVo build(String basis, List<AbrPlanItem> items, Map<Long, AbrProgram> programs, IAbrSelectionService.PlanRun run,
            Function<String, String> countryLabel, List<String> missingInfo, LocalDate today)
    {
        Map<Long, SelectionEngine.ProgramEvaluation> evaluated = new HashMap<>();
        if (run != null)
        {
            run.evaluation().results().forEach(e -> evaluated.put(e.program().programId(), e));
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        GROUPS.forEach(g -> counts.put(g, 0));
        Map<String, Integer> countryCounts = new LinkedHashMap<>();
        List<AppPlanVo.Item> list = new ArrayList<>();
        for (AbrPlanItem item : items)
        {
            AbrProgram program = programs.get(item.getProgramId());
            boolean stale = AbrLookupTools.stale(program, today);
            SelectionEngine.ProgramEvaluation e = evaluated.get(item.getProgramId());
            String status;
            if (!Boolean.TRUE.equals(item.getActive()))
            {
                status = AbrSelectionServiceImpl.PLAN_OFFLINE;
            }
            else if (e != null)
            {
                status = STATUS_OK;
            }
            else if (run != null && run.skipped().containsKey(item.getProgramId()))
            {
                status = run.skipped().get(item.getProgramId());
            }
            else
            {
                status = STATUS_NEED_PROFILE;
            }
            String tier = null;
            List<AppPlanVo.Check> checks = new ArrayList<>();
            List<String> flags = new ArrayList<>();
            if (STATUS_OK.equals(status))
            {
                tier = e.group();
                counts.merge(tier, 1, Integer::sum);
                countryCounts.merge(e.program().country(), 1, Integer::sum);
                for (HardThresholdChecker.Item it : e.threshold().items())
                {
                    if (HardThresholdChecker.OUT_OF_SCOPE.equals(it.outcome()))
                    {
                        continue;
                    }
                    boolean school = AbrQuickTool.ITEM_TIER.equals(it.key());
                    checks.add(new AppPlanVo.Check(it.key(), it.outcome(), school || stale ? null : AbrQuickTool.requiredText(it),
                            school ? null : it.actual()));
                }
                for (String f : e.threshold().flags())
                {
                    if (AbrAssessmentReport.FLAGS.contains(f) && !flags.contains(f))
                    {
                        flags.add(f);
                    }
                }
            }
            String atAdd = item.getTierSnapshot();
            Date verified = program == null ? null : program.getVerifiedTime();
            list.add(new AppPlanVo.Item(item.getProgramId(), item.getSchoolName(), item.getProgramName(), item.getCountry(),
                    countryLabel.apply(item.getCountry()), status, tier, atAdd, tier != null && atAdd != null && !atAdd.equals(tier), checks, flags,
                    verified == null ? null : verified.toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate().toString(), stale, item.getSource(),
                    item.getCreateTime()));
        }
        JsonNode mix = null;
        if (run != null && !countryCounts.isEmpty())
        {
            String main = countryCounts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            JsonNode config = run.configs().get(main);
            mix = config == null ? null : config.path("portfolio").path("default_mix");
        }
        return new AppPlanVo(basis, list, counts, hints(counts, mix), missingInfo);
    }

    /**
     * 档位分布提示
     *
     * @param counts 各分组项目数
     * @param mix 推荐组合（reach / match / safety → 数量；为空时只给不依赖数量的提示）
     * @return 提示（按严重程度）
     */
    static List<AppPlanVo.Hint> hints(Map<String, Integer> counts, JsonNode mix)
    {
        int reach = counts.getOrDefault("reach", 0);
        int match = counts.getOrDefault("match", 0);
        int safety = counts.getOrDefault("safety", 0);
        int blocked = counts.getOrDefault(SelectionEngine.NEEDS_IMPROVEMENT, 0) + counts.getOrDefault(SelectionEngine.EXCLUDED, 0);
        int tiered = reach + match + safety;
        List<AppPlanVo.Hint> hints = new ArrayList<>();
        if (tiered + blocked + counts.getOrDefault("unrated", 0) == 0)
        {
            return hints;
        }
        if (blocked >= 2 && blocked > tiered)
        {
            hints.add(new AppPlanVo.Hint("mostly_blocked", null));
        }
        if (tiered > 0 && safety == 0)
        {
            hints.add(new AppPlanVo.Hint("no_safety", suggested(mix, Set.of("safety"))));
        }
        if (tiered > 0 && match == 0)
        {
            hints.add(new AppPlanVo.Hint("no_match", suggested(mix, Set.of("match"))));
        }
        if (reach >= 2 && reach > match + safety)
        {
            hints.add(new AppPlanVo.Hint("reach_heavy", null));
        }
        Integer total = suggested(mix, Set.of("reach", "match", "safety"));
        if (total != null && tiered < total)
        {
            hints.add(new AppPlanVo.Hint("too_few", total));
        }
        return hints;
    }

    private static Integer suggested(JsonNode mix, Set<String> tiers)
    {
        if (mix == null || !mix.isObject())
        {
            return null;
        }
        int n = 0;
        for (String t : tiers)
        {
            n += mix.path(t).asInt(0);
        }
        return n > 0 ? n : null;
    }
}
