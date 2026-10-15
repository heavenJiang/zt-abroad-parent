package com.ygb.abroad.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.vo.AppCalendarVo;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.abroad.domain.vo.AppPlanVo;
import com.ygb.abroad.domain.vo.AppSharedPlanVo;
import com.ygb.abroad.engine.SelectionEngine;
import com.ygb.abroad.growth.card.Card;
import com.ygb.common.utils.StringUtils;

/**
 * 选校单分享的纯函数部分：家长只读视图（去掉学生分数）、选校单分享卡（只含档位与校名）
 *
 * @author ygb
 */
final class AbrPlanShare
{
    /** 分享对象类型：家长只读视图 */
    static final String KIND_VIEW = "plan_view";

    /** 分享对象类型：选校单分享卡 */
    static final String KIND_CARD = "plan_card";

    /** 只读视图最多展示的截止事件数 */
    static final int DEADLINES = 10;

    /** 分享卡每组最多显示的校名数 */
    static final int SCHOOLS = 4;

    /** 分享卡上的分组（顺序） */
    static final List<String> CARD_GROUPS = List.of("reach", "match", "safety", SelectionEngine.NEEDS_IMPROVEMENT);

    private AbrPlanShare()
    {
    }

    /**
     * 家长只读视图
     *
     * @param plan 选校单
     * @param calendar 申请日历
     * @param materials 材料清单
     * @return 只读视图
     */
    static AppSharedPlanVo view(AppPlanVo plan, AppCalendarVo calendar, AppMaterialsVo materials)
    {
        List<AppPlanVo.Item> items = new ArrayList<>();
        for (AppPlanVo.Item i : plan.items())
        {
            List<AppPlanVo.Check> checks = i.checks().stream().map(c -> new AppPlanVo.Check(c.item(), c.outcome(), c.required(), null)).toList();
            items.add(new AppPlanVo.Item(i.programId(), i.schoolName(), i.programName(), i.country(), i.countryLabel(), i.status(), i.tier(),
                    i.tierAtAdd(), i.tierChanged(), checks, i.flags(), i.verifiedDate(), i.unverified(), i.source(), i.addedTime()));
        }
        List<AppCalendarVo.Event> deadlines = calendar.events().stream().filter(e -> !e.passed()).limit(DEADLINES).toList();
        return new AppSharedPlanVo(plan.basis(), items, plan.tierCounts(), plan.hints(), deadlines, materials.doneCount(), materials.totalCount());
    }

    /**
     * 选校单分享卡：只含各组项目数与校名，不含学生的任何个人信息
     *
     * @param plan 选校单
     * @param brand 品牌名
     * @param qrUrl 二维码链接（带分享码）
     * @return 图卡
     */
    static Card card(AppPlanVo plan, String brand, String qrUrl)
    {
        List<AppPlanVo.Item> active = plan.items().stream().filter(i -> !AbrSelectionServiceImpl.PLAN_OFFLINE.equals(i.status())).toList();
        List<Card.Section> sections = new ArrayList<>();
        Map<String, List<AppPlanVo.Item>> byGroup = new LinkedHashMap<>();
        CARD_GROUPS.forEach(g -> byGroup.put(g, new ArrayList<>()));
        List<AppPlanVo.Item> others = new ArrayList<>();
        for (AppPlanVo.Item i : active)
        {
            (i.tier() != null && byGroup.containsKey(i.tier()) ? byGroup.get(i.tier()) : others).add(i);
        }
        byGroup.forEach((g, list) -> {
            if (!list.isEmpty())
            {
                sections.add(section(AbrReportExplainer.TIER_LABELS.get(g), list));
            }
        });
        if (!others.isEmpty())
        {
            sections.add(section(sections.isEmpty() ? "选校单" : "待评估", others));
        }
        Set<String> labels = new LinkedHashSet<>();
        active.forEach(i -> {
            if (StringUtils.isNotBlank(i.countryLabel()))
            {
                labels.add(i.countryLabel());
            }
        });
        String countries = String.join(" / ", labels);
        return new Card(brand, countries.isEmpty() ? "我的选校单" : "我的选校单 · " + countries, "共 " + active.size() + " 个项目", sections, List.of(),
                qrUrl, "扫码做一份你的选校单", "档位由选校引擎按院校库计算，仅供参考，不代表录取承诺");
    }

    private static Card.Section section(String label, List<AppPlanVo.Item> items)
    {
        Set<String> schools = new LinkedHashSet<>();
        for (AppPlanVo.Item i : items)
        {
            if (StringUtils.isNotBlank(i.schoolName()))
            {
                schools.add(i.schoolName());
            }
        }
        List<String> lines = new ArrayList<>(schools.stream().limit(SCHOOLS).toList());
        if (schools.size() > SCHOOLS)
        {
            lines.add("等 " + schools.size() + " 所院校");
        }
        return new Card.Section(label, items.size() + " 个项目", lines);
    }
}
