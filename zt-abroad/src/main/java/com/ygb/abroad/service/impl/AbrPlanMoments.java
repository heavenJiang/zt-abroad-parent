package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.engine.SelectionEngine;
import tools.jackson.databind.JsonNode;

/**
 * 转化时刻判断的纯函数部分（不访问数据库）；阈值来自转化时刻规则的 params
 *
 * @author ygb
 */
final class AbrPlanMoments
{
    /** 冲保失衡 */
    static final String IMBALANCE = "imbalance";

    /** 要求变化后不达标 */
    static final String REQUIREMENT_UNMET = "requirement_unmet";

    /** 截止前材料不齐 */
    static final String MATERIALS_BEHIND = "materials_behind";

    /** 家长查看 */
    static final String PARENT_VIEW = "parent_view";

    /** 反复修改 */
    static final String REPEATED_EDITS = "repeated_edits";

    /** 可申的分组 */
    private static final Set<String> TIERED = Set.of("reach", "match", "safety");

    /** 不可申的分组 */
    private static final Set<String> BLOCKED = Set.of(SelectionEngine.NEEDS_IMPROVEMENT, SelectionEngine.EXCLUDED);

    private AbrPlanMoments()
    {
    }

    /**
     * 冲保失衡：已分档项目不少于 min_tiered 个，且没有保底或冲刺多于主攻 + 保底
     *
     * @param plan 选校单
     * @param params 阈值
     * @return 是否发生
     */
    static boolean imbalance(AppPlanVo plan, JsonNode params)
    {
        int reach = plan.tierCounts().getOrDefault("reach", 0);
        int match = plan.tierCounts().getOrDefault("match", 0);
        int safety = plan.tierCounts().getOrDefault("safety", 0);
        return reach + match + safety >= params.path("min_tiered").asInt(3) && (safety == 0 || reach > match + safety);
    }

    /**
     * 要求变化后不达标：加入时可申（冲 / 稳 / 保），申请要求在加入之后更新，现在变为补足后可申或不符合条件
     *
     * @param plan 选校单
     * @param requirementChanged 项目ID → 最新申请要求的更新时间（没有更新取创建时间）
     * @return 受影响的项目ID
     */
    static List<Long> requirementUnmet(AppPlanVo plan, Map<Long, Date> requirementChanged)
    {
        List<Long> ids = new ArrayList<>();
        for (AppPlanVo.Item i : plan.items())
        {
            Date changed = requirementChanged.get(i.programId());
            if (i.tierAtAdd() != null && TIERED.contains(i.tierAtAdd()) && i.tier() != null && BLOCKED.contains(i.tier()) && changed != null
                    && i.addedTime() != null && changed.after(i.addedTime()))
            {
                ids.add(i.programId());
            }
        }
        return ids;
    }

    /**
     * 截止前材料不齐：距最近截止不超过 days_before 天，且该项目材料完成度低于 completion_below
     *
     * @param materials 材料清单
     * @param today 今天（北京时间）
     * @param params 阈值
     * @return 受影响的项目ID
     */
    static List<Long> materialsBehind(AppMaterialsVo materials, LocalDate today, JsonNode params)
    {
        int days = params.path("days_before").asInt(30);
        double below = params.path("completion_below").asDouble(0.5);
        List<Long> ids = new ArrayList<>();
        for (AppMaterialsVo.ProgramMaterials p : materials.programs())
        {
            if (p.nextDeadline() == null || p.totalCount() == 0)
            {
                continue;
            }
            LocalDate deadline = LocalDate.parse(p.nextDeadline());
            if (!deadline.isBefore(today) && !deadline.isAfter(today.plusDays(days)) && (double) p.doneCount() / p.totalCount() < below)
            {
                ids.add(p.programId());
            }
        }
        return ids;
    }

    /**
     * 反复修改：统计窗口内加入或移除项目不少于 min_edits 次，且分布在不少于 min_days 天（北京时间）
     *
     * @param editTimes 窗口内的修改时间
     * @param params 阈值
     * @return 是否发生
     */
    static boolean repeatedEdits(List<Date> editTimes, JsonNode params)
    {
        Set<LocalDate> days = new HashSet<>();
        editTimes.forEach(t -> days.add(t.toInstant().atZone(AbrLookupTools.BEIJING).toLocalDate()));
        return editTimes.size() >= params.path("min_edits").asInt(8) && days.size() >= params.path("min_days").asInt(3);
    }

    /**
     * 反复修改的统计窗口天数
     *
     * @param params 阈值
     * @return 天数
     */
    static int windowDays(JsonNode params)
    {
        return Math.max(1, params.path("window_days").asInt(14));
    }
}
