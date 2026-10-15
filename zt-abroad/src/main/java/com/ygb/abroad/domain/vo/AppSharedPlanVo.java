package com.ygb.abroad.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * C 端：家长只读视图（凭分享链接查看学生的选校单、最近截止与材料完成度；不含学生的分数等个人信息与联系方式）
 *
 * @param basis 计算口径（full / quick / none）
 * @param items 选校单项目（逐项达标只给结论与要求，不给学生的分数）
 * @param tierCounts 各分组的项目数
 * @param hints 档位分布提示
 * @param deadlines 未过的截止事件（按日期，最多 10 个）
 * @param materialsDone 已完成的材料数
 * @param materialsTotal 材料总数
 * @author ygb
 */
public record AppSharedPlanVo(String basis, List<AppPlanVo.Item> items, Map<String, Integer> tierCounts, List<AppPlanVo.Hint> hints,
        List<AppCalendarVo.Event> deadlines, int materialsDone, int materialsTotal)
{
}
